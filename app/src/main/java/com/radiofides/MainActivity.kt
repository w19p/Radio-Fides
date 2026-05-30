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

    // Launcher para pedir múltiples permisos (Notificaciones y Almacenamiento)
    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Resultado del permiso de almacenamiento (para Android 9-)
        val storageGranted = permissions[Manifest.permission.WRITE_EXTERNAL_STORAGE] ?: false
        viewModel.onStoragePermissionResult(storageGranted)
        
        // Resultado de notificaciones (para Android 13+)
        val notificationsGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions[Manifest.permission.POST_NOTIFICATIONS] ?: false
        } else true
        
        android.util.Log.d("FidesPermissions", "Notificaciones: $notificationsGranted, Almacenamiento: $storageGranted")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Preparamos la lista de permisos a pedir
        val permissionsToRequest = mutableListOf<String>()
        
        // 1. Permiso de Notificaciones (Obligatorio para controles de audio en Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // 2. Permiso de Almacenamiento (Solo Android 9-)
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            } else {
                viewModel.onStoragePermissionResult(true)
            }
        }

        // Lanzamos la petición si hay permisos pendientes
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
