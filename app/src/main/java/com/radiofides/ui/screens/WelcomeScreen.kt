package com.radiofides.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.radiofides.R
import com.radiofides.viewmodel.FidesViewModel
import kotlinx.coroutines.delay

@Composable
fun WelcomeScreen(navController: NavController, viewModel: FidesViewModel) {
    var startLogoAnimation by remember { mutableStateOf(false) }
    var startMessageAnimation by remember { mutableStateOf(false) }

    // Obtenemos el estado de red del ViewModel
    val isNetworkAvailable = viewModel.isNetworkAvailable

    LaunchedEffect(Unit) {
        delay(800)
        startLogoAnimation = true
        delay(1000)
        startMessageAnimation = true
    }

    // Lógica de navegación condicionada al internet
    LaunchedEffect(isNetworkAvailable) {
        if (isNetworkAvailable) {
            // Esperamos un momento para que el usuario vea la bienvenida antes de saltar
            delay(3500)
            navController.navigate("home") {
                popUpTo("welcome") { inclusive = true }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primary,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            AnimatedVisibility(
                visible = startLogoAnimation,
                enter = scaleIn(animationSpec = tween(700)) + fadeIn()
            ) {
                ElevatedCard(
                    modifier = Modifier.size(220.dp),
                    shape = CircleShape,
                    elevation = CardDefaults.elevatedCardElevation(20.dp)
                ) {
                    Box(modifier = Modifier.fillMaxSize().background(Color.White), contentAlignment = Alignment.Center) {
                        Image(
                            painter = painterResource(id = R.drawable.logo_fides_oficial),
                            contentDescription = "Logo",
                            modifier = Modifier.size(160.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(40.dp))

            AnimatedVisibility(
                visible = startMessageAnimation,
                enter = slideInVertically(initialOffsetY = { 40 }) + fadeIn(animationSpec = tween(1000))
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "RADIO FIDES",
                        color = Color.White,
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontWeight = FontWeight.Black,
                            letterSpacing = 2.sp
                        )
                    )

                    Text(
                        text = "La voz que camina con el pueblo",
                        color = Color.White.copy(alpha = 0.9f),
                        style = MaterialTheme.typography.bodyLarge,
                        fontStyle = FontStyle.Italic
                    )

                    Spacer(modifier = Modifier.height(60.dp))

                    if (isNetworkAvailable) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(30.dp),
                            color = Color.White,
                            strokeWidth = 3.dp
                        )
                        Text(
                            text = "Sintonizando...",
                            color = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.padding(top = 10.dp),
                            style = MaterialTheme.typography.labelMedium
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = Color.Yellow,
                            modifier = Modifier.size(40.dp)
                        )
                        Text(
                            text = "Sin conexión a internet",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 10.dp)
                        )
                        Text(
                            text = "Por favor, verifica tu red para continuar",
                            color = Color.White.copy(alpha = 0.8f),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}