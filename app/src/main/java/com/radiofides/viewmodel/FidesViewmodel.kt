package com.radiofides.viewmodel

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import androidx.annotation.OptIn
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import com.radiofides.R
import com.radiofides.service.FidesMediaService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

@OptIn(UnstableApi::class)
class FidesViewModel(application: Application) : AndroidViewModel(application) {

    // El 'browser' es el control remoto que se conecta al Servicio de audio
    private var browser: MediaController? = null

    // --- NUEVA VARIABLE DE CARGA DE LA RADIO ---
    var isBuffering by mutableStateOf(false)

    // Estados que la UI (Compose) observa para cambiar iconos y textos
    var isPlaying by mutableStateOf(false)
    var currentTitle by mutableStateOf("Radio Fides")
    var currentArtist by mutableStateOf("La voz que camina con el pueblo")
    var currentImageUrl by mutableStateOf<String?>(null)

    init {
        // Configuramos la conexión con el servicio FidesMediaService
        val sessionToken = SessionToken(
            application,
            ComponentName(application, FidesMediaService::class.java)
        )
        // Creamos el controlador de forma asíncrona (para no trabar la app)
        val controllerFuture = MediaController.Builder(application, sessionToken).buildAsync()

        controllerFuture.addListener({
            try {
                // Una vez conectado, guardamos el controlador en 'browser'
                browser = controllerFuture.get()

                // Escuchamos si el reproductor cambia de estado (Play/Pause) para avisar a la UI
                browser?.addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(playing: Boolean) {
                        isPlaying = playing
                    }
                    // --- DETECTA SI ESTÁ CARGANDO ---
                    override fun onPlaybackStateChanged(state: Int) {
                        // Si el estado es BUFFERING, activamos la carga.
                        isBuffering = (state == Player.STATE_BUFFERING)
                    }
                })

                // Pedimos los datos de la canción apenas conectamos
                fetchMetadata()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, MoreExecutors.directExecutor())

        // Iniciamos el ciclo que pide datos cada 30 segundos
        startMetadataRefresh()
    }

    /**
     * Lógica del botón principal. Maneja la sincronización al vivo.
     */
    fun togglePlayPause() {
        browser?.let { controller ->
            if (isPlaying) {
                // Si ya suena, pausa simple
                controller.pause()
            } else {
                // Al reanudar, forzamos que vaya al "tiempo real" (punta del vivo)
                // 1. Salta a la posición más actual del stream
                controller.seekToDefaultPosition()

                // 2. Reconecta el stream si se perdió la conexión
                controller.prepare()

                // 3. Inicia la reproducción
                controller.play()
            }
        }
    }

    /**
     * Cierra la aplicación por completo enviando un comando al servicio
     */
    fun exitApp() {
        val intent = Intent(getApplication(), FidesMediaService::class.java).apply {
            action = "ACTION_EXIT"
        }
        getApplication<Application>().startService(intent)
        // Mata el proceso de la app para que no quede nada en memoria
        android.os.Process.killProcess(android.os.Process.myPid())
    }

    /**
     * Se conecta a la API para obtener título, artista e imagen
     */
    private fun fetchMetadata() {
        viewModelScope.launch(Dispatchers.IO) { // Se ejecuta en hilo secundario (Internet)
            try {
                val url = URL("https://api.instant.audio/data/playlist/43/radio-chacaltaya")
                val connection = url.openConnection() as HttpURLConnection
                val jsonText = connection.inputStream.bufferedReader().readText()

                // Procesamos el JSON recibido
                val root = JSONObject(jsonText)
                val results = root.getJSONArray("result")

                if (results.length() > 0) {
                    val current = results.getJSONObject(0)
                    val title = current.getString("track_title")
                    val artist = current.getString("track_artist")
                    val image = current.optString("track_image", null)

                    // Cambiamos al hilo principal para actualizar la pantalla y la notificación
                    withContext(Dispatchers.Main) {
                        currentTitle = title
                        currentArtist = artist
                        currentImageUrl = image

                        browser?.let { controller ->
                            // Imagen por defecto si la API no manda foto
                            val logoUri =
                                "android.resource://${getApplication<Application>().packageName}/${R.drawable.logo_fides_oficial}".toUri()

                            // Creamos los metadatos visuales
                            val infoVisual = MediaMetadata.Builder()
                                .setTitle(title)
                                .setArtist(artist)
                                .setArtworkUri(if (!image.isNullOrEmpty()) image.toUri() else logoUri)
                                .build()

                            // Actualizamos la notificación del sistema sin detener la música
                            val itemActualizado = controller.currentMediaItem?.buildUpon()
                                ?.setMediaMetadata(infoVisual)
                                ?.build()

                            if (itemActualizado != null) {
                                // Reemplaza la info actual. 0 es el índice del stream en la lista.
                                controller.replaceMediaItem(0, itemActualizado)
                            }
                        }
                    }

                }
            } catch (e: Exception) {
                Log.e("FidesVM", "Error al obtener datos: ${e.message}")
            }
        }
    }

    /**
     * Bucle infinito que refresca los datos de la radio cada 30 segundos
     */
    fun startMetadataRefresh() {
        viewModelScope.launch {
            while (true) {
                delay(30000) // Espera 30 segundos
                fetchMetadata()
            }
        }
    }

    /**
     * Se ejecuta cuando el ViewModel se destruye: libera el controlador
     */
    override fun onCleared() {
        super.onCleared()
        browser?.release()
        browser = null
    }
}