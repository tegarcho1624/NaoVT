package com.naovt.app.ui.room

import android.net.Uri
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.naovt.app.R
import java.io.File

/**
 * Video de fondo con ExoPlayer (Media3): en bucle, mudo, sin controles y recortado para llenar la pantalla.
 *
 * Ahorro de batería: solo reproduce mientras la app está en primer plano (RESUMED) y [paused] es false.
 * Al pasar a segundo plano se pausa y al volver continúa. El reproductor se libera al salir de la pantalla.
 * Si el video no se puede reproducir, se muestra la Sala de estar y se avisa.
 */
@OptIn(UnstableApi::class)
@Composable
fun VideoBackground(path: String, paused: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var failed by remember(path) { mutableStateOf(false) }

    if (failed) {
        Image(
            painter = painterResource(R.drawable.sala_de_estar),
            contentDescription = null,
            modifier = modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        LaunchedEffect(path) {
            Toast.makeText(context, R.string.rooms_video_failed, Toast.LENGTH_LONG).show()
        }
        return
    }

    val player = remember(path) {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_ONE // en bucle
            volume = 0f // silenciado
            addListener(object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    failed = true
                }
            })
            setMediaItem(MediaItem.fromUri(Uri.fromFile(File(path))))
            prepare()
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }

    // Pausa en segundo plano (onPause) y reanuda al volver (onResume).
    val lifecycleOwner = LocalLifecycleOwner.current
    var resumed by remember {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> resumed = true
                Lifecycle.Event.ON_PAUSE -> resumed = false
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(player, resumed, paused) {
        player.playWhenReady = resumed && !paused
    }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { ctx ->
            PlayerView(ctx).apply {
                // Ocupa todo el espacio que le da el AndroidView (no se ajusta a su contenido).
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                useController = false // sin controles
                // ZOOM: mantiene la proporción, llena la pantalla y recorta el sobrante, centrado (como la imagen).
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            }
        },
        update = { view -> view.player = player },
    )
}
