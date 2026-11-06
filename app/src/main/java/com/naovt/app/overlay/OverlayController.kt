package com.naovt.app.overlay

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.naovt.app.R

/**
 * Lado de la app del Modo Flotante: pide los permisos en orden y arranca o detiene el servicio.
 *
 * Flujo de [start]: permiso "Aparecer encima de otras apps" (abre Ajustes si falta)
 * -> permiso de notificaciones en Android 13+ -> inicia el servicio.
 */
class OverlayController(private val context: Context) {

    // Los conecta rememberOverlayController con los lanzadores de Compose.
    var openOverlaySettings: (Intent) -> Unit = {}
    var requestNotificationPermission: () -> Unit = {}

    fun start() {
        if (NaoOverlayService.isRunning) return

        if (!Settings.canDrawOverlays(context)) {
            toast(R.string.overlay_permission_needed)
            openOverlaySettings(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}"),
                )
            )
            return
        }
        if (needsNotificationPermission()) {
            requestNotificationPermission()
            return
        }
        launchService()
    }

    fun stop() {
        context.stopService(Intent(context, NaoOverlayService::class.java))
    }

    /** El usuario volvió de Ajustes: si ya concedió el permiso, seguimos con el flujo. */
    internal fun onOverlaySettingsResult() {
        if (Settings.canDrawOverlays(context)) {
            start()
        } else {
            toast(R.string.overlay_permission_denied)
        }
    }

    internal fun onNotificationPermissionResult(granted: Boolean) {
        // Sin notificación el servicio funciona igual, pero no habrá botón "Detener" en la barra.
        if (!granted) toast(R.string.overlay_notifications_denied)
        launchService()
    }

    private fun needsNotificationPermission(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED

    private fun launchService() {
        if (NaoOverlayService.isRunning) return
        ContextCompat.startForegroundService(context, Intent(context, NaoOverlayService::class.java))
    }

    private fun toast(messageRes: Int) {
        Toast.makeText(context, messageRes, Toast.LENGTH_LONG).show()
    }
}

@Composable
fun rememberOverlayController(): OverlayController {
    val context = LocalContext.current
    val controller = remember { OverlayController(context) }

    val settingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { controller.onOverlaySettingsResult() }

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> controller.onNotificationPermissionResult(granted) }

    SideEffect {
        controller.openOverlaySettings = { settingsLauncher.launch(it) }
        controller.requestNotificationPermission = {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    return controller
}
