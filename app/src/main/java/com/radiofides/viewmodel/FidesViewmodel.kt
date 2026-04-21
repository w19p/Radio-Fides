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

    private var browser: MediaController? = null
    var isPlaying by mutableStateOf(false)

    var currentTitle by mutableStateOf("Radio Fides")
    var currentArtist by mutableStateOf("La voz que camina con el pueblo")
    var currentImageUrl by mutableStateOf<String?>(null)

    init {
        val sessionToken = SessionToken(
            application,
            ComponentName(application, FidesMediaService::class.java)
        )
        val controllerFuture = MediaController.Builder(application, sessionToken).buildAsync()

        controllerFuture.addListener({
            try {
                browser = controllerFuture.get()
                browser?.addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(playing: Boolean) {
                        isPlaying = playing
                    }
                })
                // Intentar una carga inicial apenas se conecte el controlador
                fetchMetadata()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, MoreExecutors.directExecutor())

        startMetadataRefresh()
    }

    fun togglePlayPause() {
        browser?.let { controller ->
            if (isPlaying) {
                // Si está sonando, simplemente pausamos
                controller.pause()
            } else {
                // Si estaba pausado, antes de darle Play, lo sincronizamos al presente
                // 1. Buscamos la posición por defecto (la punta del vivo)
                controller.seekToDefaultPosition()

                // 2. Preparamos por si el stream se quedó "dormido" por la pausa
                controller.prepare()

                // 3. Ahora sí, Play
                controller.play()
            }
        }
    }

    fun exitApp() {
        val intent = Intent(getApplication(), FidesMediaService::class.java).apply {
            action = "ACTION_EXIT"
        }
        getApplication<Application>().startService(intent)
        android.os.Process.killProcess(android.os.Process.myPid())
    }

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

                    withContext(Dispatchers.Main) {
                        currentTitle = title
                        currentArtist = artist
                        currentImageUrl = image

                        browser?.let { controller ->
                            // 1. Creamos la "información visual" con lo que llegó del JSON
                            val logoUri =
                                "android.resource://${getApplication<Application>().packageName}/${R.drawable.logo_fides_oficial}".toUri()

                            val infoVisual = MediaMetadata.Builder()
                                .setTitle(title)
                                .setArtist(artist)
                                .setArtworkUri(if (!image.isNullOrEmpty()) image.toUri() else logoUri)
                                .build()

                            // 2. FORZAMOS el refresco de la barra superior
                            // Esto le dice al servicio: "El stream sigue siendo el mismo, pero su información cambió"
                            val itemActualizado = controller.currentMediaItem?.buildUpon()
                                ?.setMediaMetadata(infoVisual)
                                ?.build()

                            if (itemActualizado != null) {
                                // Reemplazamos el item en la posición 0 (el actual) sin detener el audio
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

    fun startMetadataRefresh() {
        viewModelScope.launch {
            while (true) {
                delay(30000)
                fetchMetadata()
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        browser?.release()
        browser = null
    }
}