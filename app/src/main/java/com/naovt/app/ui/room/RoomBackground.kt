package com.naovt.app.ui.room

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.naovt.app.R
import com.naovt.app.data.Habitacion
import com.naovt.app.data.TipoHabitacion
import java.io.File
import kotlin.math.roundToInt

/**
 * TEMPORAL: muestra arriba, en una línea, los tamaños reales (contenedor del fondo, ventana de la app y
 * archivo) para descubrir por qué el fondo no llena la pantalla. Ponlo en false cuando ya no haga falta.
 */
private const val MOSTRAR_DIAGNOSTICO = true

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
    val view = LocalView.current
    val density = LocalDensity.current.density
    var contenedor by remember { mutableStateOf(IntSize.Zero) }
    var ventana by remember { mutableStateOf(IntSize.Zero) }
    var archivo by remember(habitacion.id) { mutableStateOf("?") }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .onSizeChanged {
                contenedor = it
                ventana = IntSize(view.rootView.width, view.rootView.height)
            },
    ) {
        when (habitacion.tipo) {
            TipoHabitacion.IMAGEN -> RoomImage(habitacion) { size ->
                // Size.Unspecified (NaN) = el painter no declara tamaño propio (por ejemplo, un vector).
                if (size != Size.Unspecified) archivo = "${size.width.roundToInt()}x${size.height.roundToInt()}"
            }
            TipoHabitacion.VIDEO -> key(habitacion.id) {
                VideoBackground(
                    path = habitacion.rutaArchivo,
                    paused = paused,
                    onVideoSize = { w, h -> archivo = "${w}x$h" },
                )
            }
        }

        if (MOSTRAR_DIAGNOSTICO) {
            Text(
                text = "DIAG ${habitacion.tipo.name} | fondo ${contenedor.width}x${contenedor.height}px" +
                    " | ventana ${ventana.width}x${ventana.height}px | archivo $archivo px" +
                    " | densidad ${"%.2f".format(density)}",
                color = Color.White,
                fontSize = 11.sp,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .background(Color.Black.copy(alpha = 0.7f))
                    .padding(horizontal = 6.dp, vertical = 3.dp),
            )
        }
    }
}

@Composable
private fun RoomImage(habitacion: Habitacion, onMediaSize: (Size) -> Unit) {
    if (habitacion.recurso != 0) {
        val painter = painterResource(habitacion.recurso)
        LaunchedEffect(painter) { onMediaSize(painter.intrinsicSize) }
        Image(
            painter = painter,
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
            onSuccess = { state -> onMediaSize(state.painter.intrinsicSize) },
            // Si el archivo ya no existe o no se puede leer, se ve la Sala de estar (nunca negro).
            error = painterResource(R.drawable.sala_de_estar),
        )
    }
}
