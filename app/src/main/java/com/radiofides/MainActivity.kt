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

    // Launcher para pedir el permiso de escritura
    // Solo se activa en Android 9 y anteriores (API 28-)
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        // Si el usuario niega el permiso, las grabaciones
        // se guardarán en almacenamiento interno como fallback
        viewModel.onStoragePermissionResult(isGranted)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Solo pedimos permiso en Android 9 y anteriores
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            when {
                checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        == PackageManager.PERMISSION_GRANTED -> {
                    // Ya tiene permiso, avisamos al ViewModel
                    viewModel.onStoragePermissionResult(true)
                }
                else -> {
                    // Pedimos el permiso
                    requestPermissionLauncher.launch(
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                    )
                }
            }
        }

        setContent {
            RadioFidesTheme {
                NavGraph(viewModel)
            }
        }
    }
}