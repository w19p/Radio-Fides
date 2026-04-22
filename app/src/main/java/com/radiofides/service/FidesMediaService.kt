package com.radiofides.service

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
import com.radiofides.R


@UnstableApi
class FidesMediaService : MediaSessionService() {
    // El 'mediaSession' es la conexión entre el reproductor y el sistema Android (Notificación)
    private var mediaSession: MediaSession? = null
    // El 'player' es el motor ExoPlayer que realmente descarga y reproduce el sonido
    private lateinit var player: ExoPlayer

    override fun onCreate() {
        super.onCreate()

        // 1. INICIALIZACIÓN DEL MOTOR
        player = ExoPlayer.Builder(this)
            .setLivePlaybackSpeedControl(
                DefaultLivePlaybackSpeedControl.Builder()
                    .setFallbackMaxPlaybackSpeed(1.04f) // Si el audio se atrasa por internet, acelera un 4% para alcanzar el vivo
                    .build()
            )
            .build()

        // Metadatos iniciales vacíos para que la radio empiece limpia
        val defaultMetadata = MediaMetadata.Builder()
            .setTitle("")
            .setArtist("")
            .build()

        // 2. CONFIGURACIÓN DEL STREAM
        val mediaItem = MediaItem.Builder()
            .setUri("https://cast6.asurahosting.com/proxy/irfradio/stream/1/") // URL de la radio
            .setMediaMetadata(defaultMetadata)
            .build()

        // 3. BOTÓN DE CIERRE (X)
        // Creamos un comando personalizado para que el botón de la notificación pueda cerrar la app
        val exitCommand = SessionCommand("ACTION_EXIT", Bundle.EMPTY)
        val exitButton = CommandButton.Builder()
            .setDisplayName("Cerrar")
            .setIconResId(R.drawable.ic_exit) // Icono de la X
            .setSessionCommand(exitCommand)
            .build()

        // Cargamos el link y preparamos el motor (pero no suena hasta que se le dé Play)
        player.setMediaItem(mediaItem)
        player.prepare()

        // 4. CONFIGURACIÓN DE LA SESIÓN (La interfaz con Android)
        mediaSession = MediaSession.Builder(this, player)
            .setCallback(object : MediaSession.Callback {

                // Se ejecuta cuando el ViewModel o el Sistema se conectan al servicio
                override fun onConnect(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo
                ): MediaSession.ConnectionResult {

                    // Definimos qué botones de sesión están disponibles
                    val availableSessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                        .add(exitCommand)
                        .build()

                    // IMPORTANTE: Damos permiso total para que el ViewModel pueda cambiar
                    // títulos y artistas sin errores de seguridad
                    val availablePlayerCommands = Player.Commands.Builder()
                        .addAllCommands()
                        .build()

                    return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                        .setAvailableSessionCommands(availableSessionCommands)
                        .setAvailablePlayerCommands(availablePlayerCommands)
                        .setCustomLayout(listOf(exitButton)) // Colocamos la X en la notificación
                        .build()
                }

                // Escucha cuando el usuario presiona el botón "Cerrar" en la notificación
                override fun onCustomCommand(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    customCommand: SessionCommand,
                    args: Bundle
                ): ListenableFuture<SessionResult> {
                    if (customCommand.customAction == "ACTION_EXIT") {
                        exitEverything() // Llamamos a la limpieza total
                    }
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
            })
            .build()
    }

    // Gestiona cómo se comporta la notificación cuando hay actualizaciones
    override fun onUpdateNotification(session: MediaSession, startInForegroundRequired: Boolean) {
        super.onUpdateNotification(session, startInForegroundRequired)
    }

    /**
     * LIMPIEZA TOTAL (El botón de pánico)
     * Detiene el audio, libera la memoria y mata el proceso para que no consuma batería
     */
    private fun exitEverything() {
        if (::player.isInitialized) {
            player.stop()
            player.release() // Suelta el motor de audio
        }
        mediaSession?.let {
            it.release() // Suelta la sesión de media
            mediaSession = null
        }
        stopForeground(STOP_FOREGROUND_REMOVE) // Quita la notificación
        stopSelf() // Detiene el servicio

        // Mata el proceso del celular (asegura que la app se cierre de verdad)
        android.os.Process.killProcess(android.os.Process.myPid())
    }

    // Si el usuario desliza la app hacia afuera en la lista de tareas
    override fun onTaskRemoved(rootIntent: Intent?) {
        exitEverything()
        super.onTaskRemoved(rootIntent)
    }

    // Si el sistema decide que ya no necesita el servicio
    override fun onDestroy() {
        exitEverything()
        super.onDestroy()
    }

    // Método obligatorio para que el sistema encuentre la sesión de música
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession
}

