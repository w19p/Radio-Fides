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
        startDestination = "welcome"
    ) {
        composable("welcome") {
            WelcomeScreen(navController, viewModel)
        }

        composable("home") {
            FidesHome(viewModel, navController)
        }
    }
}