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
import androidx.annotation.OptIn
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

@OptIn(UnstableApi::class)
class FidesViewModel(application: Application) : AndroidViewModel(application) {

    private var browser: MediaController? = null
    var isPlaying by mutableStateOf(false)

    // 1. Creamos las variables para guardar lo que encontremos
    var currentTitle by mutableStateOf("Radio Fides")
    var currentArtist by mutableStateOf("La voz que camina con el pueblo")
    var currentImageUrl by mutableStateOf<String?>(null)

    init {
        val sessionToken = SessionToken(application, ComponentName(application, FidesMediaService::class.java))
        val controllerFuture = MediaController.Builder(application, sessionToken).buildAsync()

        controllerFuture.addListener({
            try {
                browser = controllerFuture.get()
                browser?.addListener(object : Player.Listener {

                    // --- AQUÍ ESTÁ LA PRUEBA DE FUEGO ---
                    override fun onMediaMetadataChanged(mediaMetadata: androidx.media3.common.MediaMetadata) {
                        val titulo = mediaMetadata.title?.toString()
                        val artista = mediaMetadata.artist?.toString()

                        // Esto imprimirá en tu Logcat de Android Studio
                        if (titulo != null || artista != null) {
                            android.util.Log.d("FIDES_DEBUG", "✅ DATOS RECIBIDOS: $titulo - $artista")
                            currentTitle = titulo ?: "Radio Fides"
                            currentArtist = artista ?: "En vivo"
                        } else {
                            android.util.Log.d("FIDES_DEBUG", "❌ EL LINK NO TRAE METADATOS")
                        }
                    }

                    override fun onIsPlayingChanged(playing: Boolean) {
                        isPlaying = playing
                    }
                })
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, MoreExecutors.directExecutor())
        startMetadataRefresh()
    }

    fun togglePlayPause() {
        if (isPlaying) browser?.pause() else browser?.play()
    }

    //función para buscar los datos
    private fun fetchMetadata() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // La URL que encontraste
                val url = URL("https://api.instant.audio/data/playlist/43/radio-fides")
                val connection = url.openConnection() as HttpURLConnection
                val jsonText = connection.inputStream.bufferedReader().readText()

                // Leemos el JSON paso a paso
                val root = JSONObject(jsonText)
                val results = root.getJSONArray("result")

                if (results.length() > 0) {
                    // El primer elemento [0] es lo que suena ahora
                    val current = results.getJSONObject(0)

                    // Extraemos los datos que vimos en el código JS
                    val title = current.getString("track_title")
                    val artist = current.getString("track_artist")
                    val image = current.optString("track_image", null)

                    // Volvemos al hilo principal para actualizar la pantalla
                    withContext(Dispatchers.Main) {
                        currentTitle = title
                        currentArtist = artist
                        currentImageUrl = image
                    }
                }
            } catch (e: Exception) {
                Log.e("FidesVM", "Error al obtener datos: ${e.message}")
            }
        }
    }

    // 3. Crea un bucle que se repita cada minuto
    fun startMetadataRefresh() {
        viewModelScope.launch {
            while (true) {
                fetchMetadata()
                delay(60000) // Espera 60 segundos (igual que el JS que encontraste)
            }
        }
    }
    // Esta clase representa una sola canción o programa del JSON
    data class SongInfo(
        val title: String,
        val artist: String,
        val imageUrl: String?
    )
}