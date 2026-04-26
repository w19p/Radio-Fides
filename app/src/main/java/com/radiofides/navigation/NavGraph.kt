package com.radiofides.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.radiofides.ui.screens.FidesHome
import com.radiofides.ui.screens.NoInternetScreen
import com.radiofides.ui.screens.PlaylistScreen
import com.radiofides.ui.screens.ScheduleScreen
import com.radiofides.ui.screens.WelcomeScreen
import com.radiofides.viewmodel.FidesViewModel

@Composable
fun NavGraph(viewModel: FidesViewModel) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "welcome"
    ) {
        composable("welcome") {
            WelcomeScreen(navController, viewModel)
        }

        composable("home") {
            FidesHome(viewModel, navController)
        }

        composable("no_internet") {
            NoInternetScreen(navController, viewModel)
        }

        // [APRENDIZAJE] Añadimos la ruta "playlist" para que podamos navegar a la pantalla de grabaciones
        composable("playlist") {
            PlaylistScreen(viewModel, navController)
        }

        // [APRENDIZAJE] Añadimos la ruta "schedule" para ver la programación completa
        composable("schedule") {
            ScheduleScreen(viewModel, navController)
        }
    }
}
