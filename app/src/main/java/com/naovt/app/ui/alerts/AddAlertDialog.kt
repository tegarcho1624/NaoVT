package com.naovt.app.ui.alerts

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.naovt.app.R
import com.naovt.app.ui.avatar.NaoExpression
import java.util.Calendar
import java.util.Date

private const val MAX_TEXT_LENGTH = 100

/** Formulario de nueva alerta: tarea, fecha, hora y expresión de Nao. */
@Composable
fun AddAlertDialog(
    onSave: (text: String, triggerAtMillis: Long, expression: NaoExpression) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var text by rememberSaveable { mutableStateOf("") }
    var triggerAt by rememberSaveable { mutableStateOf(defaultTriggerTime()) }
    var expression by rememberSaveable { mutableStateOf(NaoExpression.Neutral) }
    var menuOpen by remember { mutableStateOf(false) }

    val isFuture = triggerAt > System.currentTimeMillis()
    val dateText = DateFormat.getMediumDateFormat(context).format(Date(triggerAt))
    val timeText = DateFormat.getTimeFormat(context).format(Date(triggerAt))

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.alerts_new_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { if (it.length <= MAX_TEXT_LENGTH) text = it },
                    label = { Text(stringResource(R.string.alerts_task_label)) },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { pickDate(context, triggerAt) { triggerAt = it } },
                        modifier = Modifier.weight(1f),
                    ) { Text(dateText) }
                    OutlinedButton(
                        onClick = { pickTime(context, triggerAt) { triggerAt = it } },
                        modifier = Modifier.weight(1f),
                    ) { Text(timeText) }
                }
                Box {
                    OutlinedButton(
                        onClick = { menuOpen = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            stringResource(R.string.alerts_expression_label) + ": " +
                                stringResource(expression.labelRes()),
                        )
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        SelectableExpressions.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.displayName()) },
                                onClick = {
                                    expression = option
                                    menuOpen = false
                                },
                            )
                        }
                    }
                }
                if (!isFuture) {
                    Text(
                        text = stringResource(R.string.alerts_past_error),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(text.trim(), triggerAt, expression) },
                enabled = text.isNotBlank() && isFuture,
            ) { Text(stringResource(R.string.alerts_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.alerts_cancel)) }
        },
    )
}

/** Por defecto: dentro de una hora. */
private fun defaultTriggerTime(): Long = Calendar.getInstance().apply {
    add(Calendar.HOUR_OF_DAY, 1)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

/** Selector de fecha nativo (DatePicker). Conserva la hora ya elegida. */
private fun pickDate(context: Context, current: Long, onPicked: (Long) -> Unit) {
    val now = Calendar.getInstance().apply { timeInMillis = current }
    DatePickerDialog(
        context,
        { _, year, month, day ->
            val updated = Calendar.getInstance().apply {
                timeInMillis = current
                set(year, month, day)
            }
            onPicked(updated.timeInMillis)
        },
        now.get(Calendar.YEAR),
        now.get(Calendar.MONTH),
        now.get(Calendar.DAY_OF_MONTH),
    ).apply {
        datePicker.minDate = System.currentTimeMillis() - 1000 // no permite días pasados
    }.show()
}

/** Selector de hora nativo (TimePicker). Conserva la fecha ya elegida. */
private fun pickTime(context: Context, current: Long, onPicked: (Long) -> Unit) {
    val now = Calendar.getInstance().apply { timeInMillis = current }
    TimePickerDialog(
        context,
        { _, hour, minute ->
            val updated = Calendar.getInstance().apply {
                timeInMillis = current
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            onPicked(updated.timeInMillis)
        },
        now.get(Calendar.HOUR_OF_DAY),
        now.get(Calendar.MINUTE),
        DateFormat.is24HourFormat(context),
    ).show()
}
