package com.radiofides.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

@UnstableApi
class FidesMediaService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    private lateinit var player: ExoPlayer

    override fun onCreate() {
        super.onCreate()
        // 1. Configuramos el reproductor (ExoPlayer)
        player = ExoPlayer.Builder(this).build()

        // 2. Cargamos tu link de Radio Fides
        val mediaItem = MediaItem.fromUri("https://usa7.fastcast4u.com/proxy/grflores?mp=/1")
        player.setMediaItem(mediaItem)
        player.prepare()

        // 3. Creamos la sesión de medios (lo que conecta con la notificación)
        mediaSession = MediaSession.Builder(this, player).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}