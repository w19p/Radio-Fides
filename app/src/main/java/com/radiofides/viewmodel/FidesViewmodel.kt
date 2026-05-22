package com.radiofides.viewmodel

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.Environment
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
import com.radiofides.data.ScheduleProvider
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


@OptIn(UnstableApi::class)
class FidesViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val METADATA_URL = "https://api.instant.audio/data/playlist/43/radio-fides"
        private val BLACKLIST = listOf("Bilirrubina", "Bomba Estereo")
    }

    private var browser: MediaController? = null

    // --- ESTADOS DE LA INTERFAZ ---
    var isBuffering by mutableStateOf(false)
    var isPlaying by mutableStateOf(false)

    // --- METADATA ---
    var currentTitle by mutableStateOf("Radio Fides")
    var currentArtist by mutableStateOf("La voz que camina con el pueblo")
    var currentImageUrl by mutableStateOf<String?>(null)

    // --- CRONOGRAMA ---
    var currentProgram by mutableStateOf(ScheduleProvider.getCurrentProgram())

    // --- GRABACIÓN ---
    var showSaveDialog by mutableStateOf(false)
    var nuevoNombreMarcador by mutableStateOf("")
    var isRecording by mutableStateOf(false)
    var contadorNuevasGrabaciones by mutableStateOf(0)
    private var recordingJob: Job? = null
    private var currentRecordingTimestamp: Long = 0

    // --- TEMPORIZADOR ---
    var tiempoTemporizador by mutableStateOf(0)
    var showSleepDialog by mutableStateOf(false)
    private var sleepJob: Job? = null

    // --- RED ---
    var isNetworkAvailable by mutableStateOf(true)
    private val connectivityManager =
        application.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            viewModelScope.launch(Dispatchers.Main) {
                isNetworkAvailable = true
            }
        }

        override fun onLost(network: Network) {
            viewModelScope.launch(Dispatchers.Main) {
                // Pequeña validación: ¿Realmente no hay ninguna red activa?
                val activeNetwork = connectivityManager.activeNetwork
                if (activeNetwork == null) {
                    isNetworkAvailable = false
                    if (isRecording) {
                        isRecording = false
                        recordingJob?.cancel()
                        showSaveDialog = false
                    }
                }
            }
        }

        override fun onCapabilitiesChanged(
            network: Network,
            networkCapabilities: NetworkCapabilities
        ) {
            val hasInternet = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            viewModelScope.launch(Dispatchers.Main) {
                if (isNetworkAvailable != hasInternet) {
                    isNetworkAvailable = hasInternet
                }
            }
        }
    }

    // --- ALMACENAMIENTO ---
    private val folderMetadata = File(application.filesDir, "Grabaciones radio fides")

    private val folderMusica: File =
        File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
            "Radio Fides"
        )

    var tienePermisoAlmacenamiento by mutableStateOf(
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
    )
        private set

    fun onStoragePermissionResult(granted: Boolean) {
        tienePermisoAlmacenamiento = granted
        if (granted && !folderMusica.exists()) {
            folderMusica.mkdirs()
        }
    }

    fun getAudioFile(timestamp: Long): File =
        File(if (tienePermisoAlmacenamiento) folderMusica else folderMetadata, "audio_$timestamp.mp3")

    fun getTxtFile(timestamp: Long): File =
        File(folderMetadata, "grabacion_$timestamp.txt")

    // --- PLAYLIST ---
    var playlist by mutableStateOf<List<SavedBookmark>>(emptyList())
        private set

    data class SavedBookmark(
        val customName: String,
        val title: String,
        val artist: String,
        val imageUrl: String?,
        val timestamp: Long
    )

    // --- AUTO PLAY ---
    private var yaHizoAutoPlay = false

    fun autoPlay() {
        if (yaHizoAutoPlay) return
        if (!isPlaying && isNetworkAvailable) {
            playStream()
            yaHizoAutoPlay = true
        }
    }

    init {
        checkInitialNetwork()
        registerNetworkCallback()

        val sessionToken =
            SessionToken(application, ComponentName(application, FidesMediaService::class.java))
        val controllerFuture = MediaController.Builder(application, sessionToken).buildAsync()

        controllerFuture.addListener({
            viewModelScope.launch(Dispatchers.Main) {
                try {
                    browser = controllerFuture.get()
                    browser?.addListener(object : Player.Listener {
                        override fun onIsPlayingChanged(playing: Boolean) {
                            isPlaying = playing
                        }
                        override fun onPlaybackStateChanged(state: Int) {
                            isBuffering = (state == Player.STATE_BUFFERING)
                        }
                    })
                    fetchMetadata()
                    autoPlay()
                } catch (e: Exception) {
                    Log.e("FidesVM", "Error conexión MediaController: ${e.message}")
                }
            }
        }, MoreExecutors.directExecutor())

        startMetadataRefresh()
        startScheduleRefresh()

        if (!folderMetadata.exists()) folderMetadata.mkdirs()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (!folderMusica.exists()) folderMusica.mkdirs()
        }

        cargarPlaylist()
    }

    private fun startScheduleRefresh() {
        viewModelScope.launch {
            while (true) {
                currentProgram = ScheduleProvider.getCurrentProgram()
                delay(60_000L)
            }
        }
    }

    fun fetchMetadata() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val url = URL("$METADATA_URL?t=${System.currentTimeMillis()}")
                val connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 8_000
                connection.readTimeout = 8_000

                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
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
                            } else {
                                currentTitle = title
                                currentArtist = artist
                                currentImageUrl = current.optString("track_image", "").ifEmpty { null }
                                
                                val mediaTitle = if (currentProgram.isMusical) currentTitle else currentProgram.name
                                val mediaArtist = if (currentProgram.isMusical) currentArtist else currentProgram.conductor
                                val mediaImage = if (currentProgram.isMusical) currentImageUrl else null
                                updateMediaSession(mediaTitle, mediaArtist, mediaImage)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("FidesVM", "Error metadata: ${e.message}")
            }
        }
    }

    private fun resetToDefaultMetadata() {
        currentTitle = "Radio Fides"
        currentArtist = "La voz que camina con el pueblo"
        currentImageUrl = null
        updateMediaSession(currentProgram.name, currentProgram.conductor, null)
    }

    private fun startMetadataRefresh() {
        viewModelScope.launch {
            while (true) {
                delay(20_000L)
                if (isNetworkAvailable && isPlaying) fetchMetadata()
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
            val currentItem = controller.currentMediaItem?.buildUpon()?.setMediaMetadata(metadata)?.build()
            if (currentItem != null) controller.replaceMediaItem(0, currentItem)
        }
    }

    fun togglePlayPause() {
        browser?.let { if (isPlaying) it.pause() else playStream() }
    }

    fun pausarRadio() {
        browser?.pause()
    }

    private fun playStream() {
        if (!isNetworkAvailable) return
        browser?.let {
            it.currentMediaItem?.let { item -> it.setMediaItem(item) }
            it.seekToDefaultPosition()
            it.prepare()
            it.play()
        }
    }

    fun programarApagado(minutos: Int) {
        sleepJob?.cancel()
        tiempoTemporizador = minutos * 60
        showSleepDialog = false
        if (minutos > 0) {
            sleepJob = viewModelScope.launch {
                while (tiempoTemporizador > 0) {
                    delay(1_000L)
                    tiempoTemporizador--
                }
                browser?.pause()
            }
        }
    }

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
                val carpetaDestino = if (tienePermisoAlmacenamiento) folderMusica else folderMetadata
                val audioFile = File(carpetaDestino, "audio_$timestamp.mp3")
                val url = URL(STREAM_URL)
                val connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 10_000
                connection.readTimeout = 30_000
                connection.connect()

                connection.inputStream.use { input ->
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
                Log.e("FidesVM", "Error grabación: ${e.message}")
                withContext(Dispatchers.Main) { isRecording = false }
            }
        }
    }

    fun guardarEnPlaylist(nombreElegido: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val timestamp = currentRecordingTimestamp
                val nuevoMarcador = SavedBookmark(nombreElegido, currentTitle, currentArtist, currentImageUrl, timestamp)
                val archivoTxt = getTxtFile(timestamp)
                val contenido = listOf(
                    nuevoMarcador.customName.replace("|", "/"),
                    nuevoMarcador.title.replace("|", "/"),
                    nuevoMarcador.artist.replace("|", "/"),
                    nuevoMarcador.imageUrl ?: "null"
                ).joinToString("|")
                archivoTxt.writeText(contenido)

                withContext(Dispatchers.Main) {
                    playlist = listOf(nuevoMarcador) + playlist
                    showSaveDialog = false
                    nuevoNombreMarcador = ""
                    isRecording = false
                    contadorNuevasGrabaciones++
                }
            } catch (_: Exception) { }
        }
    }

    private fun cargarPlaylist() {
        viewModelScope.launch(Dispatchers.IO) {
            val listaTemporal = mutableListOf<SavedBookmark>()
            if (folderMetadata.exists()) {
                folderMetadata.listFiles()?.forEach { archivo ->
                    if (archivo.name.endsWith(".txt") && archivo.name.startsWith("grabacion_")) {
                        try {
                            val datos = archivo.readText().split("|")
                            if (datos.size >= 4) {
                                val timestamp = archivo.name.removePrefix("grabacion_").removeSuffix(".txt").toLong()
                                listaTemporal.add(SavedBookmark(datos[0], datos[1], datos[2], if (datos[3] == "null") null else datos[3], timestamp))
                            }
                        } catch (e: Exception) { }
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
            val archivoTxt = getTxtFile(marcador.timestamp)
            val archivoMp3 = getAudioFile(marcador.timestamp)
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
        try {
            val request = NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build()
            connectivityManager.registerNetworkCallback(request, networkCallback)
        } catch (e: Exception) { }
    }

    override fun onCleared() {
        super.onCleared()
        try { connectivityManager.unregisterNetworkCallback(networkCallback) } catch (e: Exception) { }
        browser?.release()
    }
}
