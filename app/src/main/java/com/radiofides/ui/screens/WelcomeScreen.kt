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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
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
            // Si hay internet, esperamos a que termine la animación y vamos al home
            delay(3500)
            navController.navigate("home") {
                popUpTo("welcome") { inclusive = true }
            }
        } else {
            // SI NO HAY INTERNET: Saltamos a la pantalla dedicada para poder tunearla
            navController.navigate("no_internet")
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            // [OPCIÓN 1] USAR FONDO AUTOMÁTICO (Claro u Oscuro según el sistema)
             .background(MaterialTheme.colorScheme.background),

            // [OPCIÓN 2] USAR DEGRADADO CON TUS COLORES DE "Color.kt"
            /*.background(
                Brush.verticalGradient(
                    colors = listOf(
                        // Aquí usamos las variables que creamos en ui/theme/Color.kt
                        AzulNocheTop,    // Color superior claro
                        AzulNocheBottom  // Color inferior claro
                    )
                )
            ),*/
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
                            painter = painterResource(id = R.drawable.fondofides), // <-- Tu imagen de fondo aquí
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop // Importante para que cubra todo el cuadro
                        )
                        Image(
                            painter = painterResource(id = R.drawable.logo2),
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

                    // Mostramos él cargando mientras se decide la navegación
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