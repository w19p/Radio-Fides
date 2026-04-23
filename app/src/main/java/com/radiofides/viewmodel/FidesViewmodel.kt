package com.radiofides.viewmodel

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import androidx.annotation.OptIn
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
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

// --- CONFIGURACIÓN GLOBAL ---
const val METADATA_URL = "https://api.instant.audio/data/playlist/43/radio-fides"

// --- LISTA NEGRA (Añade aquí cualquier palabra o artista que quieras bloquear) ---
val BLACKLIST = listOf("Bilirrubina", "Bomba Estereo", "Chacaltaya")

@OptIn(UnstableApi::class)
class FidesViewModel(application: Application) : AndroidViewModel(application) {

    private var browser: MediaController? = null

    var isBuffering by mutableStateOf(false)
    var isPlaying by mutableStateOf(false)
    
    var currentTitle by mutableStateOf("Radio Fides")
    var currentArtist by mutableStateOf("La voz que camina con el pueblo")
    var currentImageUrl by mutableStateOf<String?>(null)

    var isNetworkAvailable by mutableStateOf(true)

    private val connectivityManager =
        application.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) { isNetworkAvailable = true }
        override fun onLost(network: Network) { isNetworkAvailable = false }
    }

    init {
        checkInitialNetwork()
        registerNetworkCallback()

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
                    override fun onPlaybackStateChanged(state: Int) {
                        isBuffering = (state == Player.STATE_BUFFERING)
                    }

                    override fun onMediaMetadataChanged(metadata: MediaMetadata) {
                        if (currentTitle == "Radio Fides") {
                            val streamTitle = metadata.title?.toString()
                            if (!streamTitle.isNullOrEmpty()) {
                                currentTitle = streamTitle
                                currentArtist = metadata.artist?.toString() ?: "En vivo"
                            }
                        }
                    }
                })
                
                fetchMetadata()

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, MoreExecutors.directExecutor())

        startMetadataRefresh()
    }

    private fun checkInitialNetwork() {
        val activeNetwork = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
        isNetworkAvailable = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }

    private fun registerNetworkCallback() {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.registerNetworkCallback(request, networkCallback)
    }

    fun fetchMetadata() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val timestamp = System.currentTimeMillis()
                val url = URL("$METADATA_URL?t=$timestamp")

                val connection = url.openConnection() as HttpURLConnection
                connection.useCaches = false
                connection.setRequestProperty("Cache-Control", "no-cache, no-store, must-revalidate")
                connection.setRequestProperty("Pragma", "no-cache")
                
                val jsonText = connection.inputStream.bufferedReader().readText()
                Log.d("FidesMetadata", "Dato del servidor externo: $jsonText")

                val root = JSONObject(jsonText)
                val results = root.optJSONArray("result")

                if (results != null && results.length() > 0) {
                    val current = results.getJSONObject(0)
                    val title = current.optString("track_title", "Radio Fides")
                    val artist = current.optString("track_artist", "La voz que camina con el pueblo")
                    val image = current.optString("track_image", "")

                    withContext(Dispatchers.Main) {
                        // --- FILTRO DE SEGURIDAD GENERAL (BLACKLIST) ---
                        val isBlocked = BLACKLIST.any { word ->
                            title.contains(word, ignoreCase = true) || artist.contains(word, ignoreCase = true)
                        }

                        if (isBlocked) {
                            Log.w("FidesMetadata", "Dato bloqueado por Blacklist: $title")
                            currentTitle = "Radio Fides"
                            currentArtist = "La voz que camina con el pueblo"
                            currentImageUrl = null
                            return@withContext
                        }

                        if (currentTitle != title || currentArtist != artist) {
                            currentTitle = title
                            currentArtist = artist
                            currentImageUrl = if (image.isNotEmpty()) image else null
                            updateMediaSession(currentTitle, currentArtist, currentImageUrl)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("FidesVM", "Error fetch: ${e.message}")
            }
        }
    }

    private fun startMetadataRefresh() {
        viewModelScope.launch {
            while (true) {
                delay(20000)
                if (isNetworkAvailable) {
                    fetchMetadata()
                }
            }
        }
    }

    private fun updateMediaSession(title: String, artist: String, image: String?) {
        browser?.let { controller ->
            val logoUri = "android.resource://${getApplication<Application>().packageName}/${R.drawable.logo_fides_oficial}".toUri()
            val metadata = MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setArtworkUri(if (!image.isNullOrEmpty()) image.toUri() else logoUri)
                .build()

            val currentItem = controller.currentMediaItem?.buildUpon()
                ?.setMediaMetadata(metadata)
                ?.build()

            if (currentItem != null) {
                controller.replaceMediaItem(0, currentItem)
            }
        }
    }

    fun togglePlayPause() {
        browser?.let { controller ->
            if (isPlaying) {
                controller.pause()
            } else {
                playStream()
            }
        }
    }

    private fun playStream() {
        browser?.let { controller ->
            controller.currentMediaItem?.let { item ->
                controller.setMediaItem(item)
            }
            controller.seekToDefaultPosition()
            controller.prepare()
            controller.play()
        }
    }

    fun autoPlay() {
        viewModelScope.launch {
            while (browser == null) {
                delay(500)
            }
            if (!isPlaying && isNetworkAvailable) {
                playStream()
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        connectivityManager.unregisterNetworkCallback(networkCallback)
        browser?.release()
        browser = null
    }
}