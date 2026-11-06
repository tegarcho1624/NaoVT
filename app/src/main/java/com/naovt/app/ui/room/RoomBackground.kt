package com.naovt.app.ui.room

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource

/** Fondo de pantalla completa (ContentScale.Crop) con transición suave entre habitaciones. */
@Composable
fun RoomBackground(room: Room, modifier: Modifier = Modifier) {
    Crossfade(targetState = room, modifier = modifier.fillMaxSize(), label = "room") { current ->
        Image(
            painter = painterResource(current.drawable),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
    }
}
