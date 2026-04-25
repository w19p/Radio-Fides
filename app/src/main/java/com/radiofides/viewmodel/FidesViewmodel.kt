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
val BLACKLIST = listOf("Bilirrubina", "Bomba Estereo")

@OptIn(UnstableApi::class)
class FidesViewModel(application: Application) : AndroidViewModel(application) {

     /*1. EL CONTROLADOR (El Control Remoto)
     Este "browser" es el que se conecta al FidesMediaService.
     Todo lo que le ordenemos al browser, el servicio lo ejecutará.*/
    private var browser: MediaController? = null

    // 2. ESTADOS DE LA INTERFAZ (UI State)
    // Usamos 'mutableStateOf' para que Compose sepa que debe redibujar la pantalla cuando cambien.
    var isBuffering by mutableStateOf(false)  // ¿Está cargando el audio?
    var isPlaying by mutableStateOf(false)    // ¿Está sonando ahora?

    var currentTitle by mutableStateOf("Radio Fides")
    var currentArtist by mutableStateOf("La voz que camina con el pueblo")
    var currentImageUrl by mutableStateOf<String?>(null)

    // 3. GESTIÓN DE INTERNET
    // Es vital saber si hay red para no intentar cargar el stream y gastar recursos en vano.
    var isNetworkAvailable by mutableStateOf(true)
    private val connectivityManager = application.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) { isNetworkAvailable = true }
        override fun onLost(network: Network) { isNetworkAvailable = false }
    }

    init {
        // Al iniciar, revisamos internet y nos conectamos al servicio de audio
        checkInitialNetwork()
        registerNetworkCallback()

        // Creamos un "Token" para encontrar nuestro servicio en el sistema
        val sessionToken = SessionToken(application, ComponentName(application, FidesMediaService::class.java))

        // Intentamos conectar el controlador de forma asíncrona (en segundo plano)
        val controllerFuture = MediaController.Builder(application, sessionToken).buildAsync()

        controllerFuture.addListener({
            try {
                // Si la conexión es exitosa, guardamos el "control remoto" (browser)
                browser = controllerFuture.get()

                // Le ponemos un "oído" (Listener) al reproductor para saber qué está pasando
                browser?.addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(playing: Boolean) {
                        isPlaying = playing // Avisa a la UI si cambió a Play o Pause
                    }
                    override fun onPlaybackStateChanged(state: Int) {
                        isBuffering = (state == Player.STATE_BUFFERING) // Avisa si está cargando
                    }

                    override fun onMediaMetadataChanged(metadata: MediaMetadata) {
                        // Si no tenemos datos del JSON, usamos lo que venga en el stream de audio
                        if (currentTitle == "Radio Fides") {
                            val streamTitle = metadata.title?.toString()
                            if (!streamTitle.isNullOrEmpty()) {
                                currentTitle = streamTitle
                                currentArtist = metadata.artist?.toString() ?: "En vivo"
                            }
                        }
                    }
                })

                // Una vez conectados, buscamos la info de la canción y activamos el AutoPlay
                fetchMetadata()
                autoPlay()

            } catch (e: Exception) {
                Log.e("FidesVM", "Error al conectar con el servicio: ${e.message}")
            }
        }, MoreExecutors.directExecutor())

        // Iniciamos el bucle que refresca la canción cada 20 segundos
        startMetadataRefresh()
    }

    // --- FUNCIONES DE RED ---
    private fun checkInitialNetwork() {
        val activeNetwork = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
        isNetworkAvailable = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }

    private fun registerNetworkCallback() {
        val request = NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build()
        connectivityManager.registerNetworkCallback(request, networkCallback)
    }

    // --- GESTIÓN DE DATOS (JSON) ---
    // Esta función va a internet, baja el JSON y extrae la canción, el autor y la foto.
    fun fetchMetadata() {
        viewModelScope.launch(Dispatchers.IO) { // Se ejecuta en un hilo de red (IO)
            try {
                val timestamp = System.currentTimeMillis() // Evita que el celular use datos viejos guardados
                val url = URL("$METADATA_URL?t=$timestamp")

                val connection = url.openConnection() as HttpURLConnection
                val jsonText = connection.inputStream.bufferedReader().readText()

                val root = JSONObject(jsonText)
                val results = root.optJSONArray("result")

                if (results != null && results.length() > 0) {
                    val current = results.getJSONObject(0)
                    val title = current.optString("track_title", "Radio Fides")
                    val artist = current.optString("track_artist", "La voz que camina con el pueblo")
                    val image = current.optString("track_image", "")

                    // Volvemos al hilo principal (Main) para actualizar la pantalla
                    withContext(Dispatchers.Main) {
                        // Filtro de lista negra
                        val isBlocked = BLACKLIST.any { word ->
                            title.contains(word, ignoreCase = true) || artist.contains(word, ignoreCase = true)
                        }

                        if (isBlocked) {
                            resetToDefaultMetadata()
                            return@withContext
                        }

                        // Si la canción cambió, actualizamos todo
                        if (currentTitle != title || currentArtist != artist) {
                            currentTitle = title
                            currentArtist = artist
                            currentImageUrl = image.ifEmpty { null }

                            // ¡IMPORTANTE! Mandamos la nueva info al Servicio para que se vea en la notificación
                            updateMediaSession(currentTitle, currentArtist, currentImageUrl)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("FidesVM", "Error al obtener metadata JSON: ${e.message}")
            }
        }
    }

    private fun resetToDefaultMetadata() {
        currentTitle = "Radio Fides"
        currentArtist = "La voz que camina con el pueblo"
        currentImageUrl = null
        updateMediaSession(currentTitle, currentArtist, null)
    }

    private fun startMetadataRefresh() {
        viewModelScope.launch {
            while (true) {
                delay(20000) // Espera 20 segundos antes de volver a preguntar
                if (isNetworkAvailable) fetchMetadata()
            }
        }
    }

    // --- SINCRONIZACIÓN CON EL SERVICIO ---
    // Esta función le dice al servicio: "Oye, actualiza la notificación con esta foto y título"
    private fun updateMediaSession(title: String, artist: String, image: String?) {
        browser?.let { controller ->
            val logoUri = "android.resource://${getApplication<Application>().packageName}/${R.drawable.logo_fides_oficial}".toUri()
            val metadata = MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setArtworkUri(if (!image.isNullOrEmpty()) image.toUri() else logoUri)
                .build()

            // Reemplazamos los datos del stream actual con la nueva metadata
            val currentItem = controller.currentMediaItem?.buildUpon()?.setMediaMetadata(metadata)?.build()
            if (currentItem != null) {
                controller.replaceMediaItem(0, currentItem)
            }
        }
    }

    // --- CONTROLES DE AUDIO ---
    fun togglePlayPause() {
        browser?.let { controller ->
            if (isPlaying) controller.pause() else playStream()
        }
    }

    private fun playStream() {
        if (!isNetworkAvailable) return
        browser?.let { controller ->
            controller.currentMediaItem?.let { item -> controller.setMediaItem(item) }
            controller.seekToDefaultPosition() // Salta al vivo
            controller.prepare()
            controller.play()
        }
    }

    // Función que intenta arrancar la radio apenas la app abre
    fun autoPlay() {
        viewModelScope.launch {
            // Esperamos un momento a que el controlador esté listo
            while (browser == null) { delay(100) }
            if (!isPlaying && isNetworkAvailable) playStream()
        }
    }

    override fun onCleared() {
        super.onCleared()
        connectivityManager.unregisterNetworkCallback(networkCallback)
        browser?.release() // Soltamos el control remoto al cerrar la app
        browser = null
    }
}