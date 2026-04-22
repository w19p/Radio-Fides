package com.radiofides.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.radiofides.ui.screens.FidesHome
import com.radiofides.ui.screens.WelcomeScreen
import com.radiofides.viewmodel.FidesViewModel

@Composable
fun NavGraph(viewModel: FidesViewModel) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "welcome" // La pantalla que inicia primero
    ) {
        // Ruta de Bienvenida
        composable("welcome") {
            WelcomeScreen(navController)
        }

        // Ruta de la Radio (Tu pantalla actual)
        composable("home") {
            FidesHome(viewModel)
        }

        /* En el futuro puedes añadir más aquí:
           composable("programacion") { ProgramacionScreen(navController) }
        */
    }
}