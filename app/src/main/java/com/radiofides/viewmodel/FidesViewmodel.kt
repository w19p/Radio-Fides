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
import com.radiofides.service.STREAM_URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

// --- CONFIGURACIÓN GLOBAL ---
const val METADATA_URL = "https://api.instant.audio/data/playlist/43/radio-fides"
val BLACKLIST = listOf("Bilirrubina", "Bomba Estereo")

@OptIn(UnstableApi::class)
class FidesViewModel(application: Application) : AndroidViewModel(application) {

    private var browser: MediaController? = null

    // --- ESTADOS DE LA INTERFAZ ---
    var isBuffering by mutableStateOf(false)
    var isPlaying by mutableStateOf(false)
    var currentTitle by mutableStateOf("Radio Fides")
    var currentArtist by mutableStateOf("La voz que camina con el pueblo")
    var currentImageUrl by mutableStateOf<String?>(null)

    // --- ESTADOS DE GRABACIÓN ---
    var showSaveDialog by mutableStateOf(false)
    var nuevoNombreMarcador by mutableStateOf("")
    var isRecording by mutableStateOf(false)
    var contadorNuevasGrabaciones by mutableStateOf(0)
    
    private var recordingJob: Job? = null
    private var currentRecordingTimestamp: Long = 0

    // [NUEVO] ESTADOS PARA EL TEMPORIZADOR (SLEEP TIMER)
    var tiempoTemporizador by mutableStateOf(0) // AHORA SON SEGUNDOS RESTANTES
    var showSleepDialog by mutableStateOf(false)
    private var sleepJob: Job? = null

    // --- GESTIÓN DE INTERNET ---
    var isNetworkAvailable by mutableStateOf(true)
    private val connectivityManager = application.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) { isNetworkAvailable = true }
        override fun onLost(network: Network) { isNetworkAvailable = false }
    }

    // --- PLAYLIST ---
    var playlist by mutableStateOf<List<SavedBookmark>>(emptyList())
        private set

    val folderGrabaciones = File(application.filesDir, "Grabaciones radio fides")

    data class SavedBookmark(
        val customName: String,
        val title: String,
        val artist: String,
        val imageUrl: String?,
        val timestamp: Long
    )

    init {
        checkInitialNetwork()
        registerNetworkCallback()

        val sessionToken = SessionToken(application, ComponentName(application, FidesMediaService::class.java))
        val controllerFuture = MediaController.Builder(application, sessionToken).buildAsync()

        controllerFuture.addListener({
            try {
                browser = controllerFuture.get()
                browser?.addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
                    override fun onPlaybackStateChanged(state: Int) { isBuffering = (state == Player.STATE_BUFFERING) }
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
                autoPlay()
            } catch (e: Exception) {
                Log.e("FidesVM", "Error conexión: ${e.message}")
            }
        }, MoreExecutors.directExecutor())

        startMetadataRefresh()
        if (!folderGrabaciones.exists()) { folderGrabaciones.mkdirs() }
        cargarPlaylist()
    }

    // --- LÓGICA DEL TEMPORIZADOR (SLEEP TIMER) ---

    fun programarApagado(minutos: Int) {
        sleepJob?.cancel() 
        tiempoTemporizador = minutos * 60 // Convertimos minutos a segundos
        showSleepDialog = false
        
        if (minutos > 0) {
            sleepJob = viewModelScope.launch {
                while (tiempoTemporizador > 0) {
                    delay(1000) // Descontamos cada 1 segundo
                    tiempoTemporizador--
                }
                // ¡Tiempo cumplido! Apagamos la radio
                browser?.pause()
                Log.d("FidesVM", "Sleep Timer: Radio apagada automáticamente")
            }
        }
    }

    // --- LÓGICA DE GRABACIÓN ---

    fun iniciarDetenerGrabacion() {
        if (!isRecording) {
            if (!isPlaying) return 
            isRecording = true
            currentRecordingTimestamp = System.currentTimeMillis()
            startAudioRecording(currentRecordingTimestamp)
        } else {
            isRecording = false
            recordingJob?.cancel()
            showSaveDialog = true 
        }
    }

    private fun startAudioRecording(timestamp: Long) {
        recordingJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val audioFile = File(folderGrabaciones, "audio_$timestamp.mp3")
                val url = URL(STREAM_URL)
                val connection = url.openConnection()
                connection.connect()

                connection.getInputStream().use { input ->
                    FileOutputStream(audioFile).use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        while (isRecording) {
                            bytesRead = input.read(buffer)
                            if (bytesRead == -1) break
                            output.write(buffer, 0, bytesRead)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("FidesVM", "Error grabar audio")
                withContext(Dispatchers.Main) { isRecording = false }
            }
        }
    }

    fun guardarEnPlaylist(nombreElegido: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val timestamp = currentRecordingTimestamp
                val nuevoMarcador = SavedBookmark(nombreElegido, currentTitle, currentArtist, currentImageUrl, timestamp)
                val archivoTxt = File(folderGrabaciones, "grabacion_$timestamp.txt")
                archivoTxt.writeText("${nuevoMarcador.customName}|${nuevoMarcador.title}|${nuevoMarcador.artist}|${nuevoMarcador.imageUrl}")

                withContext(Dispatchers.Main) {
                    playlist = (listOf(nuevoMarcador) + playlist)
                    showSaveDialog = false
                    nuevoNombreMarcador = ""
                    isRecording = false 
                    contadorNuevasGrabaciones++
                }
            } catch (e: Exception) { Log.e("FidesVM", "Error guardado") }
        }
    }

    private fun cargarPlaylist() {
        viewModelScope.launch(Dispatchers.IO) {
            val listaTemporal = mutableListOf<SavedBookmark>()
            if (folderGrabaciones.exists()) {
                folderGrabaciones.listFiles()?.forEach { archivo ->
                    if (archivo.name.endsWith(".txt") && archivo.name.startsWith("grabacion_")) {
                        try {
                            val datos = archivo.readText().split("|")
                            if (datos.size >= 4) {
                                val timestamp = archivo.name.removePrefix("grabacion_").removeSuffix(".txt").toLong()
                                listaTemporal.add(SavedBookmark(datos[0], datos[1], datos[2], if (datos[3] == "null") null else datos[3], timestamp))
                            }
                        } catch (e: Exception) { Log.e("FidesVM", "Error archivo") }
                    }
                }
            }
            withContext(Dispatchers.Main) {
                playlist = listaTemporal.sortedByDescending { it.timestamp }
            }
        }
    }

    fun eliminarMarcador(marcador: SavedBookmark) {
        viewModelScope.launch(Dispatchers.IO) {
            val archivoTxt = File(folderGrabaciones, "grabacion_${marcador.timestamp}.txt")
            val archivoMp3 = File(folderGrabaciones, "audio_${marcador.timestamp}.mp3")
            if (archivoTxt.exists()) archivoTxt.delete()
            if (archivoMp3.exists()) archivoMp3.delete()
            withContext(Dispatchers.Main) {
                playlist = playlist.filter { it.timestamp != marcador.timestamp }
            }
        }
    }

    private fun checkInitialNetwork() {
        val activeNetwork = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
        isNetworkAvailable = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }

    private fun registerNetworkCallback() {
        val request = NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build()
        connectivityManager.registerNetworkCallback(request, networkCallback)
    }

    fun fetchMetadata() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val url = URL("$METADATA_URL?t=${System.currentTimeMillis()}")
                val connection = url.openConnection() as HttpURLConnection
                val jsonText = connection.inputStream.bufferedReader().readText()
                val root = JSONObject(jsonText)
                val results = root.optJSONArray("result")
                if (results != null && results.length() > 0) {
                    val current = results.getJSONObject(0)
                    withContext(Dispatchers.Main) {
                        val title = current.optString("track_title", "Radio Fides")
                        val artist = current.optString("track_artist", "La voz que camina con el pueblo")
                        if (BLACKLIST.any { title.contains(it, true) || artist.contains(it, true) }) {
                            resetToDefaultMetadata()
                        } else if (currentTitle != title || currentArtist != artist) {
                            currentTitle = title
                            currentArtist = artist
                            currentImageUrl = current.optString("track_image", "").ifEmpty { null }
                            updateMediaSession(currentTitle, currentArtist, currentImageUrl)
                        }
                    }
                }
            } catch (e: Exception) { Log.e("FidesVM", "Error JSON") }
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
                delay(20000)
                if (isNetworkAvailable) fetchMetadata()
            }
        }
    }

    private fun updateMediaSession(title: String, artist: String, image: String?) {
        browser?.let { controller ->
            val logoUri = "android.resource://${getApplication<Application>().packageName}/${R.drawable.logo_fides_oficial}".toUri()
            val metadata = MediaMetadata.Builder().setTitle(title).setArtist(artist)
                .setArtworkUri(if (!image.isNullOrEmpty()) image.toUri() else logoUri).build()
            val currentItem = controller.currentMediaItem?.buildUpon()?.setMediaMetadata(metadata)?.build()
            if (currentItem != null) controller.replaceMediaItem(0, currentItem)
        }
    }

    fun togglePlayPause() { browser?.let { if (isPlaying) it.pause() else playStream() } }

    private fun playStream() {
        if (!isNetworkAvailable) return
        browser?.let {
            it.currentMediaItem?.let { item -> it.setMediaItem(item) }
            it.seekToDefaultPosition()
            it.prepare()
            it.play()
        }
    }

    fun autoPlay() {
        viewModelScope.launch {
            while (browser == null) delay(100)
            if (!isPlaying && isNetworkAvailable) playStream()
        }
    }

    override fun onCleared() {
        super.onCleared()
        connectivityManager.unregisterNetworkCallback(networkCallback)
        browser?.release()
    }
}
