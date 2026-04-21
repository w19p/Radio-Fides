package com.radiofides.viewmodel

import android.content.ComponentName
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import com.radiofides.playback.FidesMediaService
import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.annotation.OptIn
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import androidx.core.net.toUri
import com.radiofides.R

@OptIn(UnstableApi::class)
class FidesViewModel(application: Application) : AndroidViewModel(application) {

    // El "browser" es nuestro puente con el FidesMediaService
    private var browser: MediaController? = null

    // Estado de reproducción observable por la UI de Compose
    var isPlaying by mutableStateOf(false)

    // Variables de estado para la pantalla (Título, Artista e Imagen)
    var currentTitle by mutableStateOf("Radio Fides")
    var currentArtist by mutableStateOf("La voz que camina con el pueblo")
    var currentImageUrl by mutableStateOf<String?>(null)

    // Nuevo estado para el loading

    var isBuffering by mutableStateOf(false) // Nuevo estado para el loading

    init {
        // Conexión inicial al servicio de Media3
        val sessionToken = SessionToken(
            application,
            ComponentName(application, FidesMediaService::class.java)
        )
        val controllerFuture = MediaController.Builder(application, sessionToken).buildAsync()

        controllerFuture.addListener({
            try {
                // Obtenemos el controlador una vez que la conexión es exitosa
                browser = controllerFuture.get()

                // Escuchamos cambios en el estado del reproductor (Play/Pause externo)
                browser?.addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(playing: Boolean) {
                        isPlaying = playing
                    }

                    // ESTO DETECTA SI ESTÁ CARGANDO
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        isBuffering = (playbackState == Player.STATE_BUFFERING)
                    }
                })

                // Pedimos los datos de la canción inmediatamente al conectar
                fetchMetadata()
            } catch (e: Exception) {
                Log.e("FidesVM", "Error al conectar MediaController: ${e.message}")
            }
        }, MoreExecutors.directExecutor())

        // Iniciamos el ciclo de actualización de datos cada 30 segundos
        startMetadataRefresh()
    }

    /**
     * Lógica de Play/Pause optimizada para Radio en Vivo.
     * Si se pausa y se vuelve a dar Play, salta al "ahora" (Live).
     */
    fun togglePlayPause() {
        browser?.let { controller ->
            if (isPlaying) {
                controller.pause()
            } else {
                // Sincronización con el presente (punta del stream)
                controller.seekToDefaultPosition()
                controller.prepare() // Asegura que el stream esté listo tras una pausa larga
                controller.play()
            }
        }
    }

    /**
     * Cierre total de la aplicación enviando el comando ACTION_EXIT al servicio.
     */
    fun exitApp() {
        val intent = Intent(getApplication(), FidesMediaService::class.java).apply {
            action = "ACTION_EXIT"
        }
        getApplication<Application>().startService(intent)
        // Matamos el proceso para asegurar una limpieza total de memoria
        android.os.Process.killProcess(android.os.Process.myPid())
    }

    /**
     * Consulta la API externa para obtener la canción actual.
     * Se ejecuta en un hilo secundario (IO) para no congelar la pantalla.
     */
    private fun fetchMetadata() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val url = URL("https://api.instant.audio/data/playlist/43/radio-chacaltaya")
                val connection = url.openConnection() as HttpURLConnection
                val jsonText = connection.inputStream.bufferedReader().readText()

                val root = JSONObject(jsonText)
                val results = root.getJSONArray("result")

                if (results.length() > 0) {
                    val current = results.getJSONObject(0)
                    val title = current.getString("track_title")
                    val artist = current.getString("track_artist")
                    val image = current.optString("track_image", null)

                    // Cambiamos al hilo principal para actualizar la UI y el Servicio
                    withContext(Dispatchers.Main) {
                        currentTitle = title
                        currentArtist = artist
                        currentImageUrl = image

                        browser?.let { controller ->
                            val logoUri = Uri.parse("android.resource://${getApplication<Application>().packageName}/${R.drawable.logo_fides_oficial}")

                            val infoVisual = MediaMetadata.Builder()
                                .setTitle(title)
                                .setArtist(artist)
                                .setArtworkUri(if (!image.isNullOrEmpty()) Uri.parse(image) else logoUri)
                                .build()

                            // 1. Actualizamos la metadata de la playlist (Para el sistema)
                            controller.setPlaylistMetadata(infoVisual)

                            // 2. ACTUALIZACIÓN DINÁMICA: Editamos el item que está sonando ahora mismo
                            // Esto no detiene el audio y obliga a la notificación a refrescarse.
                            controller.currentMediaItem?.let { item ->
                                val itemEditado = item.buildUpon()
                                    .setMediaMetadata(infoVisual)
                                    .build()

                                // Usamos setMediaItem con 'false' para que NO reinicie la reproducción
                                controller.setMediaItem(itemEditado, false)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("FidesVM", "Error en fetchMetadata: ${e.message}")
            }
        }
    }

    /**
     * Bucle infinito que refresca los datos cada 30 segundos.
     */
    fun startMetadataRefresh() {
        viewModelScope.launch {
            while (true) {
                fetchMetadata()
                delay(30000) // 30 segundos entre actualizaciones
            }
        }
    }

    /**
     * Limpieza de recursos cuando el ViewModel se destruye.
     */
    override fun onCleared() {
        super.onCleared()
        browser?.release()
        browser = null
    }
}