package com.naovt.app.ui.room

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import coil.compose.AsyncImage
import com.naovt.app.R
import com.naovt.app.data.Habitacion
import com.naovt.app.data.TipoHabitacion
import java.io.File

/**
 * Fondo de pantalla completa de la habitación activa. Es la capa MÁS BAJA de la pantalla principal
 * (se dibuja primero; Nao y los botones van encima).
 *
 * Todo el fondo vive en un Box con `fillMaxSize()` (ocupa todo el espacio que le da la pantalla) y
 * fondo negro por si el archivo tarda en cargar. Ningún elemento tiene tamaño fijo.
 * - IMAGEN: `Image` / `AsyncImage` con `fillMaxSize()` y `ContentScale.Crop`.
 * - VIDEO: ver [VideoBackground] (`AndroidView` con `fillMaxSize()` y `RESIZE_MODE_ZOOM`).
 */
@Composable
fun RoomBackground(habitacion: Habitacion, modifier: Modifier = Modifier, paused: Boolean = false) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        when (habitacion.tipo) {
            TipoHabitacion.IMAGEN -> RoomImage(habitacion)
            TipoHabitacion.VIDEO -> key(habitacion.id) {
                VideoBackground(path = habitacion.rutaArchivo, paused = paused)
            }
        }
    }
}

@Composable
private fun RoomImage(habitacion: Habitacion) {
    if (habitacion.recurso != 0) {
        Image(
            painter = painterResource(habitacion.recurso),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(), // fluido: nunca un tamaño fijo
            alignment = Alignment.Center, // recorta parejo por ambos lados
            contentScale = ContentScale.Crop, // llena la pantalla sin deformar
        )
    } else {
        AsyncImage(
            model = File(habitacion.rutaArchivo),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            alignment = Alignment.Center,
            contentScale = ContentScale.Crop,
            // Si el archivo ya no existe o no se puede leer, se ve la Sala de estar (nunca negro).
            error = painterResource(R.drawable.sala_de_estar),
        )
    }
}
