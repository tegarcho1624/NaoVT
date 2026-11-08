package com.naovt.app.ui.pomodoro

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.naovt.app.R
import com.naovt.app.alerts.AlertNotifier
import com.naovt.app.pomodoro.PomodoroPhase
import com.naovt.app.pomodoro.PomodoroScheduler
import com.naovt.app.pomodoro.PomodoroState
import com.naovt.app.ui.avatar.NaoExpression
import com.naovt.app.ui.avatar.NaoHeadBadge
import com.naovt.app.ui.avatar.NaoMouth
import com.naovt.app.ui.avatar.rememberBlink
import com.naovt.app.ui.avatar.rememberTalkingMouth
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val CHEER_DURATION_MS = 4000L

/** Modo Estudio (Pomodoro): temporizador grande, estado, controles y duraciones. */
@Composable
fun PomodoroScreen(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PomodoroViewModel = viewModel(),
) {
    val state = viewModel.state
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // --- Permisos: notificaciones (Android 13+) y alarmas exactas (Android 12+) ---
    val notificationsDeniedMessage = stringResource(R.string.pomodoro_notifications_denied)
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.start()
        if (!granted) scope.launch { snackbarHostState.showSnackbar(notificationsDeniedMessage) }
    }
    val exactAlarmLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { viewModel.refresh() }

    val exactMissingMessage = stringResource(R.string.pomodoro_exact_missing)
    val exactActionLabel = stringResource(R.string.pomodoro_exact_action)
    LaunchedEffect(state.running) {
        if (state.running && !PomodoroScheduler.canScheduleExact(context)) {
            val result = snackbarHostState.showSnackbar(
                message = exactMissingMessage,
                actionLabel = exactActionLabel,
                duration = SnackbarDuration.Long,
            )
            if (result == SnackbarResult.ActionPerformed && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                exactAlarmLauncher.launch(
                    Intent(
                        Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                        Uri.parse("package:${context.packageName}"),
                    )
                )
            }
        }
    }

    // --- Reloj: se actualiza cada segundo solo mientras la pantalla se ve y el temporizador corre ---
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(state.running, lifecycleOwner) {
        if (state.running) {
            lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    now = System.currentTimeMillis()
                    viewModel.refresh()
                    delay(1000)
                }
            }
        }
    }

    // --- Nao: ánimo (ojos felices + boca abierta) al empezar y en cada cambio de fase ---
    var seenCheer by remember { mutableIntStateOf(state.cheerSerial) }
    var cheering by remember { mutableStateOf(false) }
    LaunchedEffect(state.cheerSerial) {
        if (state.cheerSerial != seenCheer) {
            seenCheer = state.cheerSerial
            cheering = true
            delay(CHEER_DURATION_MS)
            cheering = false
        }
    }
    val talkingMouth = rememberTalkingMouth(isSpeaking = cheering, openMouth = NaoMouth.Happy)
    val blinking = rememberBlink(canBlink = !cheering)

    BackHandler(onBack = onClose)

    val remainingMs = if (state.running) (state.endsAtMillis - now).coerceAtLeast(0L) else state.remainingMillis
    val totalSeconds = ((remainingMs + 999) / 1000).toInt()
    val timeText = "%02d:%02d".format(totalSeconds / 60, totalSeconds % 60)
    val progress = (1f - remainingMs.toFloat() / state.phaseMillis().coerceAtLeast(1L)).coerceIn(0f, 1f)

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
        ) { padding ->
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.pomodoro_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.pomodoro_close))
                    }
                }

                Spacer(Modifier.height(8.dp))
                NaoHeadBadge(
                    expression = NaoExpression.Neutral,
                    size = 96.dp,
                    mouthOverride = talkingMouth,
                    isBlinking = cheering || blinking,
                )

                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(
                        if (state.phase == PomodoroPhase.Study) R.string.pomodoro_status_study
                        else R.string.pomodoro_status_break
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                )

                Text(
                    text = timeText,
                    fontSize = 80.sp,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 8.dp),
                )

                // Barra de progreso de la fase actual.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.surface),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress)
                            .height(8.dp)
                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(50)),
                    )
                }

                Spacer(Modifier.height(24.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (state.running) {
                        Button(onClick = viewModel::pause) {
                            Text(stringResource(R.string.pomodoro_pause))
                        }
                    } else {
                        Button(onClick = {
                            if (AlertNotifier.canNotify(context)) {
                                viewModel.start()
                            } else {
                                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        }) {
                            Text(
                                stringResource(
                                    if (state.isFresh) R.string.pomodoro_start else R.string.pomodoro_resume
                                )
                            )
                        }
                    }
                    OutlinedButton(
                        onClick = viewModel::reset,
                        enabled = state.running || !state.isFresh,
                    ) { Text(stringResource(R.string.pomodoro_reset)) }
                }

                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.pomodoro_completed, state.completedPomodoros),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                )

                Spacer(Modifier.height(24.dp))
                // Las duraciones solo se cambian antes de empezar; para cambiarlas después, Reiniciar.
                DurationRow(
                    label = stringResource(R.string.pomodoro_study_duration),
                    minutes = state.studyMinutes,
                    enabled = state.isFresh,
                    min = PomodoroState.MIN_MINUTES,
                    max = PomodoroState.MAX_STUDY_MINUTES,
                    onChange = { viewModel.setDurations(it, state.breakMinutes) },
                )
                DurationRow(
                    label = stringResource(R.string.pomodoro_break_duration),
                    minutes = state.breakMinutes,
                    enabled = state.isFresh,
                    min = PomodoroState.MIN_MINUTES,
                    max = PomodoroState.MAX_BREAK_MINUTES,
                    onChange = { viewModel.setDurations(state.studyMinutes, it) },
                )
            }
        }
    }
}

/** Fila "Etiqueta   − 25 min +" con pasos de 5 minutos (el mínimo es 1). */
@Composable
private fun DurationRow(
    label: String,
    minutes: Int,
    enabled: Boolean,
    min: Int,
    max: Int,
    onChange: (Int) -> Unit,
) {
    val step = PomodoroViewModel.STEP_MINUTES
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = if (enabled) 1f else 0.5f),
            modifier = Modifier.weight(1f),
        )
        TextButton(
            onClick = { onChange(if (minutes > step) ((minutes - 1) / step) * step else min) },
            enabled = enabled && minutes > min,
        ) { Text("−") }
        Text(
            text = stringResource(R.string.pomodoro_minutes_short, minutes),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = if (enabled) 1f else 0.5f),
        )
        TextButton(
            onClick = { onChange(((minutes / step) + 1) * step) },
            enabled = enabled && minutes < max,
        ) { Text("+") }
    }
}
