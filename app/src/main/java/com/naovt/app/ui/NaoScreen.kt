package com.naovt.app.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.naovt.app.R
import com.naovt.app.ui.avatar.NaoExpression
import com.naovt.app.ui.avatar.NaoVTAvatar
import com.naovt.app.ui.room.Room
import com.naovt.app.ui.room.RoomBackground
import kotlinx.coroutines.delay

private const val LOVED_DURATION_MS = 2000L

/** Pantalla principal: fondo + avatar + botón para cambiar de habitación. */
@Composable
fun NaoScreen() {
    var room by rememberSaveable { mutableStateOf(Room.NaoRoom) }
    var tapCount by remember { mutableIntStateOf(0) }
    var expression by remember { mutableStateOf(NaoExpression.Neutral) }

    // Cada toque reinicia este efecto: cara de cariño 2 s y luego vuelve a la normalidad.
    LaunchedEffect(tapCount) {
        if (tapCount > 0) {
            expression = NaoExpression.Loved
            delay(LOVED_DURATION_MS)
            expression = NaoExpression.Neutral
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        RoomBackground(room = room)

        NaoVTAvatar(
            expression = expression,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxHeight(0.85f)
                .pointerInput(Unit) { detectTapGestures { tapCount++ } },
        )

        ExtendedFloatingActionButton(
            onClick = { room = room.next() },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .safeDrawingPadding()
                .padding(12.dp),
        ) {
            Text(stringResource(R.string.change_room))
        }
    }
}
