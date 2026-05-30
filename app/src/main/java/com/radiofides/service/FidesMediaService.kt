package com.radiofides.service

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLivePlaybackSpeedControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.radiofides.MainActivity
import com.radiofides.R

const val STREAM_URL = "https://cast6.asurahosting.com/proxy/irfradio/stream/1/"

@UnstableApi
class FidesMediaService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private var exoPlayer: ExoPlayer? = null
    private var player: ForwardingPlayer? = null

    private fun buildMediaItem(): MediaItem {
        return exoPlayer?.currentMediaItem ?: MediaItem.Builder()
            .setUri(STREAM_URL)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(getString(R.string.app_name))
                    .setArtist(getString(R.string.eslogan))
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
                builtExoPlayer.setMediaItem(buildMediaItem())
                builtExoPlayer.seekToDefaultPosition()
                builtExoPlayer.prepare()
                super.play()
            }
        }

        builtExoPlayer.setMediaItem(buildMediaItem())
        builtExoPlayer.prepare()

        exoPlayer = builtExoPlayer
        player = builtPlayer

        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 
            0, 
            intent, 
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        mediaSession = MediaSession.Builder(this, builtPlayer)
            .setSessionActivity(pendingIntent)
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
        // [CORREGIDO] 
        // Al activar 'exitEverything()', la radio SE APAGARÁ 
        // y la notificación DESAPARECERÁ al deslizar la App para cerrarla.
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
