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

// --- CONFIGURACIÓN GLOBAL ---
const val METADATA_URL = "https://api.instant.audio/data/playlist/43/radio-fides"
val BLACKLIST = listOf("Bilirrubina", "Bomba Estereo")

@OptIn(UnstableApi::class)
class FidesViewModel(application: Application) : AndroidViewModel(application) {

    private var browser: MediaController? = null

    // --- ESTADOS DE LA INTERFAZ ---
    var isBuffering by mutableStateOf(false)
    var isPlaying by mutableStateOf(false)

    // Metadata dinámica (JSON)
    var currentTitle by mutableStateOf("Radio Fides")
    var currentArtist by mutableStateOf("La voz que camina con el pueblo")
    var currentImageUrl by mutableStateOf<String?>(null)

    // ESTADOS DEL CRONOGRAMA
    var currentProgram by mutableStateOf(ScheduleProvider.getCurrentProgram())

    // --- ESTADOS DE GRABACIÓN ---
    var showSaveDialog by mutableStateOf(false)
    var nuevoNombreMarcador by mutableStateOf("")
    var isRecording by mutableStateOf(false)
    var contadorNuevasGrabaciones by mutableStateOf(0)

    private var recordingJob: Job? = null
    private var currentRecordingTimestamp: Long = 0

    // --- ESTADOS PARA EL TEMPORIZADOR (SLEEP TIMER) ---
    var tiempoTemporizador by mutableStateOf(0)
    var showSleepDialog by mutableStateOf(false)
    private var sleepJob: Job? = null

    // --- GESTIÓN DE INTERNET ---
    var isNetworkAvailable by mutableStateOf(true)
    private val connectivityManager =
        application.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    // [CORREGIDO] El NetworkCallback puede ser llamado desde hilos de sistema.
    // Usamos viewModelScope.launch para actualizar el estado en el hilo principal.
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            viewModelScope.launch(Dispatchers.Main) {
                isNetworkAvailable = true
            }
        }

        override fun onLost(network: Network) {
            viewModelScope.launch(Dispatchers.Main) {
                isNetworkAvailable = false
                // CORRECCIÓN: si se pierde la red mientras se grababa,
                // detenemos la grabación inmediatamente en lugar de
                // esperar hasta 30 segundos a que el readTimeout expire.
                if (isRecording) {
                    isRecording = false
                    recordingJob?.cancel()
                    showSaveDialog = false // No tiene sentido guardar una grabación cortada
                }
            }
        }

        override fun onCapabilitiesChanged(
            network: Network,
            networkCapabilities: NetworkCapabilities
        ) {
            val hasInternet =
                networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                        networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            viewModelScope.launch(Dispatchers.Main) {
                // CORRECCIÓN: solo actuamos si el estado realmente cambió
                // para evitar actualizaciones innecesarias de la UI
                if (isNetworkAvailable != hasInternet) {
                    isNetworkAvailable = hasInternet
                    if (!hasInternet && isRecording) {
                        isRecording = false
                        recordingJob?.cancel()
                        showSaveDialog = false
                    }
                }
            }
        }
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

        val sessionToken =
            SessionToken(application, ComponentName(application, FidesMediaService::class.java))
        val controllerFuture = MediaController.Builder(application, sessionToken).buildAsync()

        controllerFuture.addListener({
            try {
                browser = controllerFuture.get()
                browser?.addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(playing: Boolean) {
                        isPlaying = playing
                        // Si se detiene la reproducción, detener grabación automáticamente
                        if (!playing && isRecording) {
                            viewModelScope.launch { iniciarDetenerGrabacion() }
                        }
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
        }, MoreExecutors.directExecutor())

        startMetadataRefresh()
        startScheduleRefresh()

        if (!folderGrabaciones.exists()) {
            folderGrabaciones.mkdirs()
        }
        cargarPlaylist()
    }

    /**
     * Actualiza el programa actual cada minuto.
     */
    private fun startScheduleRefresh() {
        viewModelScope.launch {
            while (true) {
                currentProgram = ScheduleProvider.getCurrentProgram()
                delay(60_000L)
            }
        }
    }

    // --- LÓGICA DE METADATA (JSON) ---
    fun fetchMetadata() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val url = URL("$METADATA_URL?t=${System.currentTimeMillis()}")
                val connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 8_000
                connection.readTimeout = 8_000
                connection.connect()

                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    Log.w("FidesVM", "Metadata: respuesta ${connection.responseCode}")
                    return@launch
                }

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

                            if (currentProgram.isMusical) {
                                updateMediaSession(currentTitle, currentArtist, currentImageUrl)
                            } else {
                                updateMediaSession(currentProgram.name, currentProgram.conductor, null)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("FidesVM", "Error obteniendo metadata: ${e.message}")
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
                if (isNetworkAvailable) fetchMetadata()
            }
        }
    }

    private fun updateMediaSession(title: String, artist: String, image: String?) {
        browser?.let { controller ->
            val logoUri =
                "android.resource://${getApplication<Application>().packageName}/${R.drawable.logo_fides_oficial}".toUri()
            val metadata = MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setArtworkUri(if (!image.isNullOrEmpty()) image.toUri() else logoUri)
                .build()
            val currentItem = controller.currentMediaItem?.buildUpon()
                ?.setMediaMetadata(metadata)?.build()
            if (currentItem != null) {
                controller.replaceMediaItem(0, currentItem)
            }
        }
    }

    // --- CONTROLES DE AUDIO ---
    fun togglePlayPause() {
        browser?.let { if (isPlaying) it.pause() else playStream() }
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

    // --- Dentro de FidesViewModel.kt ---

    private var yaHizoAutoPlay = false // Variable privada para control interno

    fun autoPlay() {
        // Si ya lo hizo una vez, salimos de la función y no hacemos nada
        if (yaHizoAutoPlay) return

        viewModelScope.launch {
            while (browser == null) delay(100)
            if (!isPlaying && isNetworkAvailable) {
                playStream()
                yaHizoAutoPlay = true // Marcamos que ya se ejecutó el primer arranque
            }
        }
    }

    // --- LÓGICA DEL TEMPORIZADOR ---
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
                // [CORREGIDO] Verificar espacio disponible antes de grabar
                val espacioLibre = folderGrabaciones.freeSpace
                if (espacioLibre < 50 * 1024 * 1024) { // Menos de 50MB libre
                    Log.w("FidesVM", "Espacio insuficiente para grabar")
                    withContext(Dispatchers.Main) {
                        isRecording = false
                        showSaveDialog = false
                    }
                    return@launch
                }

                val audioFile = File(folderGrabaciones, "audio_$timestamp.mp3")
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
                Log.e("FidesVM", "Error en grabación: ${e.message}")
                withContext(Dispatchers.Main) {
                    isRecording = false
                }
            }
        }
    }

    fun guardarEnPlaylist(nombreElegido: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val timestamp = currentRecordingTimestamp
                val nuevoMarcador = SavedBookmark(
                    nombreElegido, currentTitle, currentArtist, currentImageUrl, timestamp
                )
                val archivoTxt = File(folderGrabaciones, "grabacion_$timestamp.txt")
                // [CORREGIDO] Escapar el separador "|" en los campos por si el título lo contiene
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
            } catch (e: Exception) {
                Log.e("FidesVM", "Error guardando en playlist: ${e.message}")
            }
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
                                val timestamp = archivo.name
                                    .removePrefix("grabacion_")
                                    .removeSuffix(".txt")
                                    .toLong()
                                listaTemporal.add(
                                    SavedBookmark(
                                        datos[0], datos[1], datos[2],
                                        if (datos[3] == "null") null else datos[3],
                                        timestamp
                                    )
                                )
                            }
                        } catch (e: Exception) {
                            Log.e("FidesVM", "Error leyendo grabación: ${archivo.name}")
                        }
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
        isNetworkAvailable =
            capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true &&
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private fun registerNetworkCallback() {
        try {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            connectivityManager.registerNetworkCallback(request, networkCallback)
        } catch (e: SecurityException) {
            // En algunos dispositivos el permiso puede no estar listo aún.
            // Si falla el callback, al menos dejamos el estado inicial correcto
            // con checkInitialNetwork() que ya se llamó antes en el init{}.
            Log.e("FidesVM", "No se pudo registrar NetworkCallback: ${e.message}")
        } catch (e: RuntimeException) {
            // Capturamos RuntimeException también porque algunos fabricantes
            // (Huawei, Xiaomi) lanzan excepciones propias que extienden RuntimeException
            Log.e("FidesVM", "Error inesperado al registrar NetworkCallback: ${e.message}")
        }
    }

    override fun onCleared() {
        super.onCleared()
        // CORRECCIÓN: también protegemos el unregister con try/catch
        // porque si el register falló, el unregister también fallará
        // y causaría un segundo crash al cerrar la app
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        } catch (e: RuntimeException) {
            Log.e("FidesVM", "Error al desregistrar NetworkCallback: ${e.message}")
        }
        browser?.release()
    }
}