package com.naovt.app.ui.phrases

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.naovt.app.R
import com.naovt.app.data.Phrase
import com.naovt.app.data.PhraseCategory
import com.naovt.app.voice.VoicePreferences
import com.naovt.app.voice.rememberNaoVoice
import kotlinx.coroutines.launch

/**
 * Pantalla "Frases de Nao": lista con su categoría. Tocar una frase la hace sonar con la voz de Nao;
 * mantenerla presionada abre el menú Editar / Eliminar.
 */
@Composable
fun PhrasesScreen(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PhrasesViewModel = viewModel(),
) {
    val phrases = viewModel.phrases
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val voice = rememberNaoVoice()
    val mutedMessage = stringResource(R.string.phrases_muted)

    var showNewForm by rememberSaveable { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Phrase?>(null) }
    var menuForId by remember { mutableStateOf<Int?>(null) }
    var deleting by remember { mutableStateOf<Phrase?>(null) }

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
                FloatingActionButton(onClick = { showNewForm = true }) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.phrases_add))
                }
            },
        ) { padding ->
            Column(modifier = Modifier.padding(padding).fillMaxSize()) {
                PhrasesHeader(count = phrases.size, onClose = onClose)
                Text(
                    text = stringResource(R.string.phrases_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
                if (phrases.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = stringResource(R.string.phrases_empty),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(32.dp),
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        // El espacio inferior evita que el botón "+" tape la última frase.
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(phrases, key = { it.id }) { phrase ->
                            Box {
                                PhraseCard(
                                    phrase = phrase,
                                    onClick = {
                                        if (VoicePreferences.isEnabled(context)) {
                                            voice.speak(phrase.text)
                                        } else {
                                            scope.launch { snackbarHostState.showSnackbar(mutedMessage) }
                                        }
                                    },
                                    onLongClick = { menuForId = phrase.id },
                                )
                                DropdownMenu(
                                    expanded = menuForId == phrase.id,
                                    onDismissRequest = { menuForId = null },
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.phrases_edit)) },
                                        onClick = {
                                            menuForId = null
                                            editing = phrase
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.phrases_delete)) },
                                        onClick = {
                                            menuForId = null
                                            deleting = phrase
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Formulario: nueva frase o edición de una existente.
    if (showNewForm || editing != null) {
        val target = editing
        PhraseDialog(
            initial = target,
            onSave = { text, category ->
                if (target == null) {
                    viewModel.addPhrase(text, category)
                } else {
                    viewModel.updatePhrase(target.id, text, category)
                }
                showNewForm = false
                editing = null
            },
            onDismiss = {
                showNewForm = false
                editing = null
            },
        )
    }

    // Confirmación antes de eliminar.
    deleting?.let { phrase ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.phrases_delete_title)) },
            text = { Text(phrase.text) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deletePhrase(phrase.id)
                    deleting = null
                }) { Text(stringResource(R.string.phrases_delete_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) {
                    Text(stringResource(R.string.phrases_cancel))
                }
            },
        )
    }
}

@Composable
private fun PhrasesHeader(count: Int, onClose: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.phrases_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            if (count > 0) {
                Text(
                    text = stringResource(R.string.phrases_count, count),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        IconButton(onClick = onClose) {
            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.phrases_close))
        }
    }
}

@Composable
private fun PhraseCard(phrase: Phrase, onClick: () -> Unit, onLongClick: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            // La clave es la frase completa: si se edita, el gesto se reinicia con el texto nuevo.
            .pointerInput(phrase) {
                detectTapGestures(
                    onTap = { onClick() },
                    onLongPress = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onLongClick()
                    },
                )
            },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = phrase.text,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            CategoryTag(phrase.category)
        }
    }
}

/** Etiqueta pequeña con la categoría de la frase. */
@Composable
private fun CategoryTag(category: PhraseCategory) {
    val colors = MaterialTheme.colorScheme
    val (container, content) = when (category) {
        PhraseCategory.Normal -> colors.primaryContainer to colors.onPrimaryContainer
        PhraseCategory.Consuelo -> colors.tertiaryContainer to colors.onTertiaryContainer
        PhraseCategory.Meme -> colors.secondaryContainer to colors.onSecondaryContainer
    }
    Surface(shape = RoundedCornerShape(50), color = container) {
        Text(
            text = stringResource(category.labelRes()),
            style = MaterialTheme.typography.labelSmall,
            color = content,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
        )
    }
}
