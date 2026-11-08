package com.naovt.app.ui.alerts

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.naovt.app.R
import com.naovt.app.alerts.AlertNotifier
import com.naovt.app.data.Alert
import com.naovt.app.data.AlertRepeat
import com.naovt.app.ui.avatar.NaoExpression
import kotlinx.coroutines.launch
import java.util.Date

/** Una alerta pendiente de guardar mientras se pide el permiso de notificaciones. */
private data class NewAlert(
    val text: String,
    val triggerAt: Long,
    val expression: NaoExpression,
    val repeat: AlertRepeat,
)

/** Pantalla "Mis Alertas": lista de recordatorios, botón + para añadir y eliminar. */
@Composable
fun AlertsScreen(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AlertsViewModel = viewModel(),
) {
    val alerts = viewModel.alerts
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var showForm by rememberSaveable { mutableStateOf(false) }
    var pendingSave by remember { mutableStateOf<NewAlert?>(null) }

    // Ajustes de "alarmas y recordatorios" (Android 12+). Al volver se reprograma todo.
    val exactAlarmLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { viewModel.onScreenResumed() }

    // Permiso de notificaciones (Android 13+): se pide al guardar y se guarda decida lo que decida.
    val notificationsDeniedMessage = stringResource(R.string.alerts_notifications_denied)
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        pendingSave?.let { viewModel.addAlert(it.text, it.triggerAt, it.expression, it.repeat) }
        pendingSave = null
        if (!granted) scope.launch { snackbarHostState.showSnackbar(notificationsDeniedMessage) }
    }

    // Sin permiso de alarmas exactas: avisa y ofrece activarlo.
    val exactMissingMessage = stringResource(R.string.alerts_exact_missing)
    val exactActionLabel = stringResource(R.string.alerts_exact_action)
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                AlertsEvent.NoExactPermission -> {
                    val result = snackbarHostState.showSnackbar(
                        message = exactMissingMessage,
                        actionLabel = exactActionLabel,
                        duration = SnackbarDuration.Long,
                    )
                    if (result == SnackbarResult.ActionPerformed &&
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                    ) {
                        exactAlarmLauncher.launch(
                            Intent(
                                Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                Uri.parse("package:${context.packageName}"),
                            )
                        )
                    }
                }
            }
        }
    }

    // Cada vez que se vuelve a ver la pantalla: lista al día y alarmas reprogramadas.
    LifecycleResumeEffect(Unit) {
        viewModel.onScreenResumed()
        onPauseOrDispose { }
    }

    BackHandler(onBack = onClose)

    Surface(
        modifier = modifier
            .fillMaxSize()
            // Evita que los toques atraviesen y lleguen a la pantalla de abajo.
            .pointerInput(Unit) { detectTapGestures { } },
        color = MaterialTheme.colorScheme.background,
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            floatingActionButton = {
                FloatingActionButton(onClick = { showForm = true }) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.alerts_add))
                }
            },
        ) { padding ->
            Column(modifier = Modifier.padding(padding).fillMaxSize()) {
                AlertsHeader(count = alerts.size, onClose = onClose)
                if (alerts.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = stringResource(R.string.alerts_empty),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(32.dp),
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        // El espacio inferior evita que el botón "+" tape la última alerta.
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(alerts, key = { it.id }) { alert ->
                            AlertCard(alert = alert, onDelete = { viewModel.deleteAlert(alert) })
                        }
                    }
                }
            }
        }
    }

    if (showForm) {
        AddAlertDialog(
            onSave = { text, triggerAt, expression, repeat ->
                showForm = false
                if (AlertNotifier.canNotify(context)) {
                    viewModel.addAlert(text, triggerAt, expression, repeat)
                } else {
                    pendingSave = NewAlert(text, triggerAt, expression, repeat)
                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            },
            onDismiss = { showForm = false },
        )
    }
}

@Composable
private fun AlertsHeader(count: Int, onClose: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.alerts_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            if (count > 0) {
                Text(
                    text = stringResource(R.string.alerts_count, count),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        IconButton(onClick = onClose) {
            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.alerts_close_screen))
        }
    }
}

@Composable
private fun AlertCard(alert: Alert, onDelete: () -> Unit) {
    val context = LocalContext.current
    val whenText = remember(alert.triggerAtMillis) {
        val date = Date(alert.triggerAtMillis)
        DateFormat.getMediumDateFormat(context).format(date) + " · " +
            DateFormat.getTimeFormat(context).format(date)
    }
    val status = when {
        alert.fired -> stringResource(R.string.alerts_status_fired)
        alert.triggerAtMillis < System.currentTimeMillis() -> stringResource(R.string.alerts_status_missed)
        else -> null
    }
    val repeatText = if (alert.repeat != AlertRepeat.None) stringResource(alert.repeat.labelRes()) else null
    val details = listOfNotNull(repeatText, stringResource(alert.expression.labelRes()), status)
        .joinToString(" · ")

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (status != null) 0.6f else 1f),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = alert.text,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = whenText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = details,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.alerts_delete))
            }
        }
    }
}
