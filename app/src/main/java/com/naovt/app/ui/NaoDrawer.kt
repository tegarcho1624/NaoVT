package com.naovt.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.naovt.app.R
import com.naovt.app.ui.avatar.NaoExpression
import com.naovt.app.ui.avatar.NaoHeadBadge
import com.naovt.app.ui.icons.TouchAppIcon

/**
 * Contenido del menú lateral: la foto y el nombre de Nao arriba y, debajo, todos los accesos
 * directos. Después de cada opción se llama a [afterClick] (la pantalla principal cierra el menú ahí).
 */
@Composable
fun NaoDrawerContent(
    isOverlayRunning: Boolean,
    onChat: () -> Unit,
    onAlerts: () -> Unit,
    onPhrases: () -> Unit,
    onPomodoro: () -> Unit,
    onVoiceSettings: () -> Unit,
    onGestures: () -> Unit,
    onChangeRoom: () -> Unit,
    onVault: () -> Unit,
    onWardrobe: () -> Unit,
    onToggleOverlay: () -> Unit,
    afterClick: () -> Unit,
) {
    ModalDrawerSheet {
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            // Perfil de Nao
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NaoHeadBadge(expression = NaoExpression.Neutral, size = 72.dp)
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(
                        text = stringResource(R.string.drawer_name),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(R.string.drawer_tagline),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            HorizontalDivider(modifier = Modifier.padding(bottom = 8.dp))

            DrawerItem(Icons.AutoMirrored.Filled.Send, stringResource(R.string.chat_button), onChat, afterClick)
            DrawerItem(Icons.Default.Notifications, stringResource(R.string.alerts_button), onAlerts, afterClick)
            DrawerItem(Icons.Default.Edit, stringResource(R.string.phrases_button), onPhrases, afterClick)
            DrawerItem(Icons.Default.Star, stringResource(R.string.pomodoro_button), onPomodoro, afterClick)
            DrawerItem(Icons.Default.Settings, stringResource(R.string.voice_settings_button), onVoiceSettings, afterClick)
            DrawerItem(TouchAppIcon, stringResource(R.string.gestures_button), onGestures, afterClick)
            DrawerItem(Icons.Default.Home, stringResource(R.string.change_room), onChangeRoom, afterClick)
            DrawerItem(Icons.Default.Person, stringResource(R.string.wardrobe_button), onWardrobe, afterClick)
            DrawerItem(Icons.Default.Favorite, stringResource(R.string.vault_button), onVault, afterClick)
            DrawerItem(
                icon = if (isOverlayRunning) Icons.Default.Close else Icons.Default.Face,
                label = stringResource(
                    if (isOverlayRunning) R.string.overlay_stop_button else R.string.overlay_button
                ),
                onClick = onToggleOverlay,
                afterClick = afterClick,
            )
            Spacer(Modifier.padding(bottom = 12.dp))
        }
    }
}

@Composable
private fun DrawerItem(icon: ImageVector, label: String, onClick: () -> Unit, afterClick: () -> Unit) {
    NavigationDrawerItem(
        icon = { Icon(icon, contentDescription = null) },
        label = { Text(label) },
        selected = false,
        onClick = {
            onClick()
            afterClick()
        },
        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
    )
}
