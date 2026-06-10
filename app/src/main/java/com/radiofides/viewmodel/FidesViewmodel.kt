package com.radiofides.viewmodel

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.media.MediaScannerConnection
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.annotation.OptIn
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
    }

    private var browser: MediaController? = null

    // --- ESTADOS DE LA INTERFAZ ---
    var isBuffering by mutableStateOf(false)
    var isPlaying by mutableStateOf(false)
    var currentTitle by mutableStateOf(application.getString(R.string.app_name))
    var currentArtist by mutableStateOf(application.getString(R.string.eslogan))
    var currentImageUrl by mutableStateOf<String?>(null)
    var currentProgram by mutableStateOf(ScheduleProvider.getCurrentProgram())

    // --- ESTADOS DE GRABACIÓN ---
    var showSaveDialog by mutableStateOf(false)
    var nuevoNombreMarcador by mutableStateOf("")
    var isRecording by mutableStateOf(false)
    var contadorNuevasGrabaciones by mutableIntStateOf(0)
    private var recordingJob: Job? = null
    private var currentRecordingTimestamp: Long = 0

    // --- TEMPORIZADOR ---
    var tiempoTemporizador by mutableIntStateOf(0)
    var showSleepDialog by mutableStateOf(false)
    private var sleepJob: Job? = null

    // --- RED ---
    var isNetworkAvailable by mutableStateOf(true)
    private val connectivityManager = application.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) { viewModelScope.launch(Dispatchers.Main) { isNetworkAvailable = true } }
        override fun onLost(network: Network) {
            viewModelScope.launch(Dispatchers.Main) {
                if (connectivityManager.activeNetwork == null) {
                    isNetworkAvailable = false
                    if (isRecording) iniciarDetenerGrabacion()
                    if (tiempoTemporizador > 0) programarApagado(0)
                }
            }
        }
    }

    // --- ALMACENAMIENTO ---
    val folderGrabaciones = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "Radio Fides")

    var tienePermisoAlmacenamiento by mutableStateOf(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            application.checkSelfPermission(android.Manifest.permission.READ_MEDIA_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            application.checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
    )
        private set

    fun onStoragePermissionResult(granted: Boolean) {
        tienePermisoAlmacenamiento = granted
        if (granted && !folderGrabaciones.exists()) folderGrabaciones.mkdirs()
        cargarPlaylist() 
    }

    fun getAudioFile(timestamp: Long): File {
        return folderGrabaciones.listFiles()?.find { it.name.contains("_$timestamp") }
            ?: File(folderGrabaciones, "temp_$timestamp.mp3")
    }

    // --- PLAYLIST ---
    var playlist by mutableStateOf<List<SavedBookmark>>(emptyList())
        private set

    data class SavedBookmark(
        val customName: String,
        val title: String,
        val artist: String,
        val timestamp: Long
    )

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

        val sessionToken = SessionToken(application, ComponentName(application, FidesMediaService::class.java))
        val controllerFuture = MediaController.Builder(application, sessionToken).buildAsync()

        controllerFuture.addListener({
            viewModelScope.launch(Dispatchers.Main) {
                try {
                    browser = withContext(Dispatchers.IO) { controllerFuture.get() }
                    browser?.addListener(object : Player.Listener {
                        override fun onIsPlayingChanged(playing: Boolean) {
                            isPlaying = playing
                            if (!playing && tiempoTemporizador > 0) programarApagado(0)
                        }
                        override fun onPlaybackStateChanged(state: Int) { isBuffering = (state == Player.STATE_BUFFERING) }
                    })
                    fetchMetadata()
                    autoPlay()
                } catch (_: Exception) { Log.e("FidesVM", "Error MediaController") }
            }
        }, MoreExecutors.directExecutor())

        startMetadataRefresh()
        startScheduleRefresh()
        
        if (tienePermisoAlmacenamiento || Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (!folderGrabaciones.exists()) folderGrabaciones.mkdirs()
        }
        cargarPlaylist()
    }

    private fun startScheduleRefresh() {
        viewModelScope.launch {
            while (true) {
                currentProgram = ScheduleProvider.getCurrentProgram()
                delay(60000)
            }
        }
    }

    fun fetchMetadata() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val url = URL("$METADATA_URL?t=${System.currentTimeMillis()}")
                val connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 8000
                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val jsonText = connection.inputStream.bufferedReader().readText()
                    val root = JSONObject(jsonText)
                    val results = root.optJSONArray("result")
                    if (results != null && results.length() > 0) {
                        val current = results.getJSONObject(0)
                        withContext(Dispatchers.Main) {
                            val title = current.optString("track_title", getApplication<Application>().getString(R.string.app_name))
                            val artist = current.optString("track_artist", getApplication<Application>().getString(R.string.eslogan))
                            currentTitle = title
                            currentArtist = artist
                            currentImageUrl = current.optString("track_image", "").ifEmpty { null }
                            
                            val mediaTitle = if (currentProgram.isMusical) currentTitle else currentProgram.name
                            val mediaArtist = if (currentProgram.isMusical) currentArtist else currentProgram.conductor
                            updateMediaSession(mediaTitle, mediaArtist, if (currentProgram.isMusical) currentImageUrl else null)
                        }
                    }
                }
            } catch (_: Exception) { }
        }
    }

    private fun startMetadataRefresh() {
        viewModelScope.launch {
            while (true) {
                delay(20000)
                if (isNetworkAvailable && isPlaying) fetchMetadata()
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
    fun pausarRadio() { browser?.pause() }

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
                    delay(1000)
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
                val audioFile = File(folderGrabaciones, "temp_$timestamp.mp3")
                val url = URL(STREAM_URL)
                val connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 10000
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
            } catch (_: Exception) { withContext(Dispatchers.Main) { isRecording = false } }
        }
    }

    fun guardarEnPlaylist(nombreElegido: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val timestamp = currentRecordingTimestamp
                // [NUEVO] Separador seguro '---' en lugar de '|'
                val safeName = nombreElegido.replace("-", " ").trim()
                val safeProg = currentProgram.name.replace("-", " ").trim()
                val finalName = "Fides---${safeName}---${safeProg}---_$timestamp.mp3"
                
                val tempFile = File(folderGrabaciones, "temp_$timestamp.mp3")
                val finalFile = File(folderGrabaciones, finalName)
                
                if (tempFile.exists()) {
                    tempFile.renameTo(finalFile)
                    MediaScannerConnection.scanFile(getApplication(), arrayOf(finalFile.absolutePath), null, null)
                }

                withContext(Dispatchers.Main) {
                    cargarPlaylist()
                    showSaveDialog = false
                    nuevoNombreMarcador = ""
                    contadorNuevasGrabaciones++
                }
            } catch (_: Exception) { }
        }
    }

    /**
     *  Sistema de recuperación híbrido: Carpeta + MediaStore
     */
    private fun cargarPlaylist() {
        viewModelScope.launch(Dispatchers.IO) {
            val mapTemporal = mutableMapOf<Long, SavedBookmark>()
            
            // 1. Escaneo directo de la carpeta (Para archivos nuevos y Android 10)
            if (folderGrabaciones.exists()) {
                folderGrabaciones.listFiles()?.forEach { archivo ->
                    parseFileName(archivo.name)?.let { bookmark -> mapTemporal[bookmark.timestamp] = bookmark }
                }
            }

            // 2. Escaneo de MediaStore (Para archivos recuperados tras reinstalar)
            val projection = arrayOf(MediaStore.Audio.Media.DISPLAY_NAME)
            val selection = "${MediaStore.Audio.Media.DISPLAY_NAME} LIKE ?"
            val selectionArgs = arrayOf("Fides---%")
            
            getApplication<Application>().contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection, selection, selectionArgs, null
            )?.use { cursor ->
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                while (cursor.moveToNext()) {
                    parseFileName(cursor.getString(nameColumn))?.let { bookmark ->
                        if (!mapTemporal.containsKey(bookmark.timestamp)) {
                            mapTemporal[bookmark.timestamp] = bookmark
                        }
                    }
                }
            }

            withContext(Dispatchers.Main) {
                playlist = mapTemporal.values.sortedByDescending { it.timestamp }
            }
        }
    }

    private fun parseFileName(fileName: String): SavedBookmark? {
        if (!fileName.startsWith("Fides---") || !fileName.endsWith(".mp3")) return null
        return try {
            val parts = fileName.removeSuffix(".mp3").split("---")
            if (parts.size >= 4) {
                val name = parts[1]
                val prog = parts[2]
                val time = parts[3].removePrefix("_").toLong()
                SavedBookmark(name, "Grabación", prog, time)
            } else null
        } catch (_: Exception) { null }
    }

    fun eliminarMarcador(marcador: SavedBookmark) {
        viewModelScope.launch(Dispatchers.IO) {
            val file = getAudioFile(marcador.timestamp)
            if (file.exists()) file.delete()
            val selection = "${MediaStore.Audio.Media.DISPLAY_NAME} LIKE ?"
            val selectionArgs = arrayOf("%${marcador.timestamp}.mp3")
            getApplication<Application>().contentResolver.delete(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, selection, selectionArgs)
            withContext(Dispatchers.Main) { playlist = playlist.filter { it.timestamp != marcador.timestamp } }
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
        } catch (_: Exception) { }
    }

    override fun onCleared() {
        super.onCleared()
        try { connectivityManager.unregisterNetworkCallback(networkCallback) } catch (_: Exception) { }
        browser?.release()
    }
}
