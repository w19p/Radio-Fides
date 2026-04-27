package com.radiofides.service

import android.content.Intent
import android.os.Bundle
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLivePlaybackSpeedControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

// --- URL GLOBAL PARA EL AUDIO ---
const val STREAM_URL = "https://cast6.asurahosting.com/proxy/irfradio/stream/1/"

@UnstableApi
class FidesMediaService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    private lateinit var player: Player

    override fun onCreate() {
        super.onCreate()

        // [MOTOR] Configuración del ExoPlayer
        val exoPlayer = ExoPlayer.Builder(this)
            .setLivePlaybackSpeedControl(
                DefaultLivePlaybackSpeedControl.Builder()
                    .setFallbackMaxPlaybackSpeed(1.04f)
                    .build()
            )
            .build()

        // [COMPORTAMIENTO] Forzar que el Play siempre sea en vivo
        player = object : ForwardingPlayer(exoPlayer) {
            override fun play() {
                if (!isPlaying) {
                    currentMediaItem?.let { setMediaItem(it) }
                    seekToDefaultPosition()
                    prepare()
                }
                super.play()
            }
        }

        // ============================================================
        // PARTE 1: DEFINICIÓN DE LOS BOTONES (Aquí creas "qué" es el botón)
        // ============================================================

        // --- BOTÓN 1: CERRAR ---
        /*val exitCommand = SessionCommand("ACTION_EXIT", Bundle.EMPTY)
        val exitButton = CommandButton.Builder()
            .setDisplayName("Cerrar")
            .setIconResId(R.drawable.ic_exit)
            .setSessionCommand(exitCommand)
            .build()*/

        /*// --- BOTÓN 2: COMPARTIR (Ejemplo de cómo añadir otro) ---
        // 1. Definimos el comando
        val shareCommand = SessionCommand("ACTION_SHARE", Bundle.EMPTY)
        // 2. Construimos el botón visual
        val shareButton = CommandButton.Builder()
            .setDisplayName("Compartir")
            .setIconResId(R.drawable.ic_radio) // Usamos ic_radio como ejemplo
            .setSessionCommand(shareCommand)
            .build()*/

        // [NOTIFICACIÓN] Metadata inicial
        val defaultMetadata = MediaMetadata.Builder()
            .setTitle("Radio Fides")
            .setArtist("La voz que camina con el pueblo")
            .build()

        val mediaItem = MediaItem.Builder()
            .setUri(STREAM_URL)
            .setMediaMetadata(defaultMetadata)
            .build()

        player.setMediaItem(mediaItem)
        player.prepare()

        // ============================================================
        // PARTE 2: LA SESIÓN Y EL ORDEN (Aquí decides el orden visual)
        // ============================================================
        mediaSession = MediaSession.Builder(this, player)
            .setCallback(object : MediaSession.Callback {

                override fun onConnect(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo
                ): MediaSession.ConnectionResult {

                    // A) REGISTRO: Aquí debes "añadir" todos los comandos que creaste arriba
                    // Si no los añades aquí, el botón aparecerá, pero no hará nada.
                    val availableSessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                        //.add(exitCommand)
                        //.add(shareCommand) // <-- Añadimos el nuevo comando
                        .build()

                    return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                        .setAvailableSessionCommands(availableSessionCommands)

                        // B) EL ORDEN: La lista dentro de listOf() define el orden en la notificación.
                        // Ejemplo: Si quieres que "Compartir" salga antes que "Cerrar", cámbialos aquí.
                        /*.setCustomLayout(listOf(shareButton, exitButton))*/
                        .build()
                }

                // ============================================================
                // PARTE 3: LA LÓGICA (Aquí programas qué hace cada botón al pulsarlo)
                // ============================================================
                override fun onCustomCommand(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    customCommand: SessionCommand,
                    args: Bundle
                ): ListenableFuture<SessionResult> {

                    /*when (customCommand.customAction) {
                        "ACTION_EXIT" -> {
                            exitEverything()
                        }
                        "ACTION_SHARE" -> {
                            // Aquí iría la lógica para abrir el menú de compartir
                            // Por ahora solo es un ejemplo
                        }
                    }*/

                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
            })
            .build()
    }

    // [LIMPIEZA] Función para apagar todo correctamente
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
