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
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
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
import kotlinx.coroutines.delay

@Composable
fun WelcomeScreen(navController: NavController) {
    // Controladores de visibilidad para la animación
    var startLogoAnimation by remember { mutableStateOf(false) }
    var startMessageAnimation by remember { mutableStateOf(false) }

    // Disparamos las animaciones en secuencia
    LaunchedEffect(Unit) {
        delay(800) // Pequeña pausa inicial
        startLogoAnimation = true
        delay(1000) // Esperamos a que el logo crezca
        startMessageAnimation = true

        delay(2000) // Tiempo total de lectura/conexión
        navController.navigate("home") {
            popUpTo("welcome") { inclusive = true }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient( // Un degradado elegante
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
            // --- ANIMACIÓN 1: EL LOGO ---
            AnimatedVisibility(
                visible = startLogoAnimation,
                enter = scaleIn(animationSpec = tween(700)) + fadeIn()
            ) {
                ElevatedCard(
                    modifier = Modifier.size(220.dp),
                    shape = CircleShape, // Logo circular para la bienvenida
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

            // --- ANIMACIÓN 2: MENSAJE Y CARGA ---
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

                    // Indicador de que la app se está "conectando"
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
                }
            }
        }
    }
}