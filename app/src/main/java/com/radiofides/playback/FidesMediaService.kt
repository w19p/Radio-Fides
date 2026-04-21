package com.radiofides.playback

import android.content.Intent
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLivePlaybackSpeedControl
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture


@UnstableApi
class FidesMediaService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    private lateinit var player: ExoPlayer

    override fun onCreate() {
        super.onCreate()

        // --- OPTIMIZACIÓN DE CARGA RÁPIDA ---
        // Configuramos el LoadControl para que no espere a llenar un buffer gigante antes de sonar
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                2500, // Memoria mínima para empezar a sonar (2.5 segundos es ideal para radio)
                5000, // Memoria máxima de buffer
                1000, // Buffer necesario tras una pausa
                1500  // Buffer necesario para reanudar
            )
            .build()

        player = ExoPlayer.Builder(this)
            .setLoadControl(loadControl) // Aplicamos la carga rápida
            .setLivePlaybackSpeedControl(
                DefaultLivePlaybackSpeedControl.Builder()
                    .setFallbackMaxPlaybackSpeed(1.02f) // Ajuste sutil para no distorsionar el tono
                    .build()
            )
            .build()

        // Metadatos vacíos iniciales para evitar que la notificación parpadee al inicio
        val defaultMetadata = MediaMetadata.Builder()
            .setTitle("Radio Fides")
            .setArtist("Cargando...")
            .build()

        // Configuración del Item de Audio (Stream)
        val mediaItem = MediaItem.Builder()
            .setUri("https://cast6.asurahosting.com/proxy/irfradio/stream/1/")
            .setMediaMetadata(defaultMetadata)
            .build()

        // --- CONFIGURACIÓN DEL BOTÓN DE SALIDA (X) ---
        val exitCommand = SessionCommand("ACTION_EXIT", Bundle.EMPTY)
        val exitButton = CommandButton.Builder()
            .setDisplayName("Cerrar")
            .setIconResId(android.R.drawable.ic_menu_close_clear_cancel)
            .setSessionCommand(exitCommand)
            .build()

        // Preparamos el player pero NO le damos play automáticamente aquí
        player.setMediaItem(mediaItem)
        player.prepare()

        // --- CONFIGURACIÓN DE LA SESIÓN ---
        mediaSession = MediaSession.Builder(this, player)
            .setCallback(object : MediaSession.Callback {
                // Se ejecuta cuando el ViewModel (u otros) se conectan al servicio
                override fun onConnect(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo
                ): MediaSession.ConnectionResult {

                    // Comandos permitidos para la sesión (CustomLayout)
                    val availableSessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                        .add(exitCommand)
                        .build()

                    // PERMISO TOTAL: Permite que el ViewModel controle todo (Play, Pause, Metadatos)
                    val availablePlayerCommands = Player.Commands.Builder()
                        .addAllCommands()
                        .build()

                    return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                        .setAvailableSessionCommands(availableSessionCommands)
                        .setAvailablePlayerCommands(availablePlayerCommands)
                        .setCustomLayout(listOf(exitButton)) // Muestra la X en la notificación
                        .build()
                }

                // Se ejecuta cuando presionas el botón "Cerrar" (X)
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

    // Función para limpiar memoria y cerrar la App por completo
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
        // Cierre total del proceso para evitar que el servicio quede "zombie"
        android.os.Process.killProcess(android.os.Process.myPid())
    }

    // Si el usuario quita la app de la lista de tareas recientes
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
