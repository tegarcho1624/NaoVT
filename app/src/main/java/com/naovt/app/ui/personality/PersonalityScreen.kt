package com.naovt.app.ui.personality

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.naovt.app.R
import com.naovt.app.personality.PersonalityStore

private const val MAX_NAME = 40
private const val MAX_PERSONALITY = 1200

/** Menú de Personalidad y Memoria: cómo te llamas y cómo quieres que actúe Nao. */
@Composable
fun PersonalityScreen(onClose: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var userName by rememberSaveable { mutableStateOf(PersonalityStore.userName(context)) }
    var personality by rememberSaveable { mutableStateOf(PersonalityStore.personality(context)) }
    val savedMessage = stringResource(R.string.personality_saved)

    BackHandler(onBack = onClose)

    Surface(
        modifier = modifier
            .fillMaxSize()
            // Evita que los toques atraviesen y lleguen a la pantalla de abajo.
            .pointerInput(Unit) { detectTapGestures { } },
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.personality_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.personality_close))
                }
            }

            OutlinedTextField(
                value = userName,
                onValueChange = { userName = it.take(MAX_NAME) },
                label = { Text(stringResource(R.string.personality_name_label)) },
                placeholder = { Text(stringResource(R.string.personality_name_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = personality,
                onValueChange = { personality = it.take(MAX_PERSONALITY) },
                label = { Text(stringResource(R.string.personality_text_label)) },
                placeholder = { Text(stringResource(R.string.personality_text_hint)) },
                supportingText = { Text("${personality.length}/$MAX_PERSONALITY") },
                minLines = 6,
                maxLines = 14,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp),
            )

            Text(
                text = stringResource(R.string.personality_empty_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            )

            Button(
                onClick = {
                    PersonalityStore.save(context, userName, personality)
                    Toast.makeText(context, savedMessage, Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.personality_save)) }
        }
    }
}
