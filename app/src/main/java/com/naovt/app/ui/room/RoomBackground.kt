package com.naovt.app.ui.room

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import com.naovt.app.R
import com.naovt.app.data.Habitacion
import com.naovt.app.data.TipoHabitacion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Fondo de pantalla completa de la habitación activa. Es la capa MÁS BAJA de la pantalla principal
 * (se dibuja primero; Nao y los botones van encima).
 *
 * Todo el fondo vive en un Box con `fillMaxSize()` (ocupa todo el espacio que le da la pantalla) y
 * fondo negro por si el archivo tarda en cargar. Ningún elemento tiene tamaño fijo.
 * - IMAGEN: `Image` con `fillMaxSize()` y `ContentScale.Crop` (las importadas se decodifican en [RoomImageLoader]).
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
        RoomFileImage(path = habitacion.rutaArchivo)
    }
}

/**
 * Imagen importada. Se decodifica aquí (respetando la rotación EXIF) y se dibuja con el mismo `Image` +
 * `ContentScale.Crop` + `fillMaxSize()` que las habitaciones nativas, así que cubre toda la pantalla,
 * sin franjas ni deformar, recortando lo que sobre. Si no se puede leer, se ve la Sala de estar.
 */
@Composable
private fun RoomFileImage(path: String) {
    val metrics = LocalContext.current.resources.displayMetrics
    val screenWidth = metrics.widthPixels
    val screenHeight = metrics.heightPixels
    var failed by remember(path) { mutableStateOf(false) }
    val bitmap by produceState<ImageBitmap?>(initialValue = null, path, screenWidth, screenHeight) {
        value = withContext(Dispatchers.IO) { RoomImageLoader.load(path, screenWidth, screenHeight) }
        if (value == null) failed = true
    }

    val loaded = bitmap
    when {
        loaded != null -> Image(
            bitmap = loaded,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            alignment = Alignment.Center,
            contentScale = ContentScale.Crop,
        )
        failed -> Image(
            painter = painterResource(R.drawable.sala_de_estar),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            alignment = Alignment.Center,
            contentScale = ContentScale.Crop,
        )
    }
}
