package com.radiofides

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import com.radiofides.navigation.NavGraph
import com.radiofides.ui.theme.RadioFidesTheme
import com.radiofides.viewmodel.FidesViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: FidesViewModel by viewModels()

    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Si se acepta cualquier permiso de lectura/almacenamiento, avisamos al ViewModel
        val storageGranted = permissions[Manifest.permission.READ_EXTERNAL_STORAGE] ?: false ||
                             permissions[Manifest.permission.WRITE_EXTERNAL_STORAGE] ?: false ||
                             (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && 
                              permissions[Manifest.permission.READ_MEDIA_AUDIO] ?: false)
        
        viewModel.onStoragePermissionResult(storageGranted)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val permissionsToRequest = mutableListOf<String>()
        
        // 1. Notificaciones (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // 2. Almacenamiento (Crucial para RECUPERAR archivos al reinstalar)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+
            if (checkSelfPermission(Manifest.permission.READ_MEDIA_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.READ_MEDIA_AUDIO)
            } else {
                viewModel.onStoragePermissionResult(true)
            }
        } else {
            // Android 10, 11 y 12
            if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            } else {
                viewModel.onStoragePermissionResult(true)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            requestPermissionsLauncher.launch(permissionsToRequest.toTypedArray())
        }

        setContent {
            RadioFidesTheme {
                NavGraph(viewModel)
            }
        }
    }
}
