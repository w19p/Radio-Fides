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


@OptIn(UnstableApi::class)
class FidesViewModel(application: Application) : AndroidViewModel(application) {

    private var browser: MediaController? = null
    var isPlaying by mutableStateOf(false)

    init {
        // Usamos getApplication() que ya viene incluido en AndroidViewModel
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
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, MoreExecutors.directExecutor())
    }

    fun togglePlayPause() {
        if (isPlaying) browser?.pause() else browser?.play()
    }

    override fun onCleared() {
        super.onCleared()
        // Liberar el controlador al cerrar la app
        browser?.release()
    }
}