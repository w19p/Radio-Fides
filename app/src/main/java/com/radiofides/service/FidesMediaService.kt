package com.radiofides.service

import android.content.Intent
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLivePlaybackSpeedControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

const val STREAM_URL = "https://usa7.fastcast4u.com/proxy/grflores?mp=/stream/1/"

@UnstableApi
class FidesMediaService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private var exoPlayer: ExoPlayer? = null      // ← var + nullable en lugar de lateinit
    private var player: ForwardingPlayer? = null  // ← más seguro para el ciclo de vida

    // Extraemos el MediaItem como función para no duplicar código
    // y para que el ViewModel pueda actualizar la metadata sin recrearlo
    private fun buildMediaItem(): MediaItem {
        return exoPlayer?.currentMediaItem ?: MediaItem.Builder()
            .setUri(STREAM_URL)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle("Radio Fides")
                    .setArtist("La voz que camina con el pueblo")
                    .build()
            )
            .build()
    }

    override fun onCreate() {
        super.onCreate()

        val builtExoPlayer = ExoPlayer.Builder(this)
            .setLivePlaybackSpeedControl(
                DefaultLivePlaybackSpeedControl.Builder()
                    .setFallbackMaxPlaybackSpeed(1.04f)
                    .build()
            )
            .build()

        val builtPlayer = object : ForwardingPlayer(builtExoPlayer) {
            override fun play() {
                // Para radio en vivo siempre reconectamos al punto actual del stream.
                // Reanudar desde donde pausaste no tiene sentido porque
                // el stream ya avanzó en el tiempo.
                builtExoPlayer.setMediaItem(buildMediaItem())
                builtExoPlayer.seekToDefaultPosition()
                builtExoPlayer.prepare()
                super.play()
            }
        }

        // Preparamos el stream por primera vez
        builtExoPlayer.setMediaItem(buildMediaItem())
        builtExoPlayer.prepare()

        // Asignamos a las propiedades de clase una sola vez todo listo
        exoPlayer = builtExoPlayer
        player = builtPlayer

        mediaSession = MediaSession.Builder(this, builtPlayer)
            .setCallback(object : MediaSession.Callback {
                override fun onConnect(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo
                ): MediaSession.ConnectionResult {
                    return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                        .build()
                }
            })
            .build()
    }

    private fun exitEverything() {
        exoPlayer?.let {
            it.stop()
            it.release()
            exoPlayer = null
        }
        mediaSession?.let {
            it.release()
            mediaSession = null
        }
        player = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        exitEverything()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        exitEverything()
        super.onDestroy()
    }

    override fun onGetSession(
        controllerInfo: MediaSession.ControllerInfo
    ): MediaSession? = mediaSession
}