package com.radiofides

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.radiofides.navigation.NavGraph
import com.radiofides.ui.theme.RadioFidesTheme
import com.radiofides.viewmodel.FidesViewModel

// [CORREGIDO] Se eliminó @AndroidEntryPoint — no se usa Hilt en ningún lugar del proyecto.
// El ViewModel se obtiene correctamente con viewModels() que usa AndroidViewModelFactory
// de forma automática, lo que es perfecto para AndroidViewModel.
class MainActivity : ComponentActivity() {

    // [CORREGIDO] Forma correcta de obtener un AndroidViewModel sin Hilt
    private val viewModel: FidesViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RadioFidesTheme {
                NavGraph(viewModel)
            }
        }
    }
}