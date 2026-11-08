package com.naovt.app.ui.phrases

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.naovt.app.R
import com.naovt.app.data.Phrase
import com.naovt.app.data.PhraseCategory

private const val MAX_PHRASE_LENGTH = 200

/** Formulario para añadir una frase ([initial] = null) o editar una existente. */
@Composable
fun PhraseDialog(
    initial: Phrase?,
    onSave: (text: String, category: PhraseCategory) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by rememberSaveable(initial?.id) { mutableStateOf(initial?.text ?: "") }
    var category by rememberSaveable(initial?.id) {
        mutableStateOf(initial?.category ?: PhraseCategory.Normal)
    }
    var menuOpen by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (initial == null) R.string.phrases_new_title else R.string.phrases_edit_title
                )
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { if (it.length <= MAX_PHRASE_LENGTH) text = it },
                    label = { Text(stringResource(R.string.phrases_text_label)) },
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
                Box {
                    OutlinedButton(
                        onClick = { menuOpen = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            stringResource(R.string.phrases_category_label) + ": " +
                                stringResource(category.labelRes()),
                        )
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        PhraseCategory.entries.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(stringResource(option.labelRes())) },
                                onClick = {
                                    category = option
                                    menuOpen = false
                                },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(text.trim(), category) },
                enabled = text.isNotBlank(),
            ) { Text(stringResource(R.string.phrases_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.phrases_cancel)) }
        },
    )
}
