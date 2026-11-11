package com.naovt.app.ui.room

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import coil.compose.AsyncImage
import com.naovt.app.R
import com.naovt.app.data.Habitacion
import com.naovt.app.data.TipoHabitacion
import java.io.File

/**
 * Fondo de pantalla completa (recortado para llenar la pantalla) de la habitación activa.
 * - IMAGEN: componente Image (o AsyncImage para las personalizadas), con transición suave.
 * - VIDEO: ExoPlayer en bucle, mudo y sin controles; se pausa en segundo plano y con [paused].
 */
@Composable
fun RoomBackground(habitacion: Habitacion, modifier: Modifier = Modifier, paused: Boolean = false) {
    when (habitacion.tipo) {
        TipoHabitacion.IMAGEN -> Crossfade(
            targetState = habitacion,
            modifier = modifier.fillMaxSize(),
            label = "room",
        ) { current -> RoomImage(current) }

        TipoHabitacion.VIDEO -> key(habitacion.id) {
            VideoBackground(path = habitacion.rutaArchivo, paused = paused, modifier = modifier.fillMaxSize())
        }
    }
}

@Composable
private fun RoomImage(habitacion: Habitacion) {
    if (habitacion.recurso != 0) {
        Image(
            painter = painterResource(habitacion.recurso),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
    } else {
        AsyncImage(
            model = File(habitacion.rutaArchivo),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            // Si el archivo ya no existe o no se puede leer, se ve la Sala de estar (nunca negro).
            error = painterResource(R.drawable.sala_de_estar),
        )
    }
}
