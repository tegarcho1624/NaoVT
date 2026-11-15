package com.naovt.app.ui.room

import android.net.Uri
import android.view.LayoutInflater
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.naovt.app.R
import java.io.File

/**
 * Video de fondo con ExoPlayer (Media3): en bucle, mudo, sin controles y agrandado/recortado para cubrir la pantalla.
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

    // Proporción real del video (ancho / alto, con la rotación ya aplicada); 0 = aún no se sabe.
    var videoAspect by remember(player) { mutableFloatStateOf(0f) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    val sideways = videoSize.unappliedRotationDegrees % 180 != 0
                    val width = videoSize.width * videoSize.pixelWidthHeightRatio
                    val height = videoSize.height.toFloat()
                    videoAspect = if (sideways) height / width else width / height
                }
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    // El video se agranda hasta CUBRIR toda la pantalla (sin franjas, sin deformar) y lo que sobra se recorta,
    // centrado: igual que `ContentScale.Crop` en las imágenes.
    BoxWithConstraints(modifier = modifier.fillMaxSize().clipToBounds(), contentAlignment = Alignment.Center) {
        val screenAspect = maxWidth / maxHeight
        val aspect = if (videoAspect > 0f) videoAspect else screenAspect
        val coverWidth = if (aspect > screenAspect) maxHeight * aspect else maxWidth
        val coverHeight = if (aspect > screenAspect) maxHeight else maxWidth / aspect

        AndroidView(
            modifier = Modifier.requiredSize(coverWidth, coverHeight),
            factory = { ctx ->
                // TextureView: se dibuja dentro de la jerarquía y respeta el recorte (ver room_player_view.xml).
                (LayoutInflater.from(ctx).inflate(R.layout.room_player_view, null) as PlayerView).apply {
                    useController = false
                    // FILL: el video llena la vista, que ya tiene exactamente su proporción.
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL
                }
            },
            update = { view -> view.player = player },
        )
    }
}
