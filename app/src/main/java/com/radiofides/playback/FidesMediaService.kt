package com.radiofides.playback

import android.content.Intent
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLivePlaybackSpeedControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.Dispatchers

@UnstableApi
class FidesMediaService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    private lateinit var player: ExoPlayer

    override fun onCreate() {
        super.onCreate()
        player = ExoPlayer.Builder(this)
            .setLivePlaybackSpeedControl(
                DefaultLivePlaybackSpeedControl.Builder()
                    .setFallbackMaxPlaybackSpeed(1.04f) // Ayuda a alcanzar el vivo si se atrasa
                    .build()
            )
            .build()

        val defaultMetadata = MediaMetadata.Builder()
            .setTitle("")
            .setArtist("")
            .build()

        val mediaItem = MediaItem.Builder()
            .setUri("https://cast6.asurahosting.com/proxy/irfradio/stream/1/")
            .setMediaMetadata(defaultMetadata)
            .build()

        val exitCommand = SessionCommand("ACTION_EXIT", Bundle.EMPTY)
        val exitButton = CommandButton.Builder()
            .setDisplayName("Cerrar")
            .setIconResId(android.R.drawable.ic_menu_close_clear_cancel)
            .setSessionCommand(exitCommand)
            .build()

        player.setMediaItem(mediaItem)
        player.prepare()

        mediaSession = MediaSession.Builder(this, player)
            .setCallback(object : MediaSession.Callback {
                override fun onConnect(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo
                ): MediaSession.ConnectionResult {
                    // 1. Comandos de sesión (Botón X)
                    val availableSessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                        .add(exitCommand)
                        .build()

                    // 2. PERMISO TOTAL (Para evitar el error de COMMAND_SET_METADATA)
                    val availablePlayerCommands = Player.Commands.Builder()
                        .addAllCommands()
                        .build()

                    return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                        .setAvailableSessionCommands(availableSessionCommands)
                        .setAvailablePlayerCommands(availablePlayerCommands)
                        .setCustomLayout(listOf(exitButton))
                        .build()
                }

                override fun onCustomCommand(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    customCommand: SessionCommand,
                    args: Bundle
                ): ListenableFuture<SessionResult> {
                    if (customCommand.customAction == "ACTION_EXIT") {
                        exitEverything()
                    }
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
            })
            .build()
    }

    override fun onUpdateNotification(session: MediaSession, startInForegroundRequired: Boolean) {
        super.onUpdateNotification(session, startInForegroundRequired)
    }

    private fun exitEverything() {
        if (::player.isInitialized) {
            player.stop()
            player.release()
        }
        mediaSession?.let {
            it.release()
            mediaSession = null
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        android.os.Process.killProcess(android.os.Process.myPid())
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        exitEverything()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        exitEverything()
        super.onDestroy()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession
}
