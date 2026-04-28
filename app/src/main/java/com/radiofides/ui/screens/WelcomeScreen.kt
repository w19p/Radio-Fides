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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.radiofides.R
import com.radiofides.ui.theme.VerdeClaro
import com.radiofides.ui.theme.VerdeMenta
import com.radiofides.ui.theme.VerdeOscuro
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

    // CORRECCIÓN: LaunchedEffect(Unit) en lugar de LaunchedEffect(isNetworkAvailable)
    // Antes: si la red fluctuaba durante el splash, el delay se reiniciaba
    // y el usuario podía quedar atrapado en la pantalla de bienvenida para siempre.
    // Ahora: esperamos exactamente 3500ms y luego evaluamos el estado de red una sola vez.
    LaunchedEffect(Unit) {
        delay(3500)
        if (isNetworkAvailable) {
            navController.navigate("home") {
                popUpTo("welcome") { inclusive = true }
            }
        } else {
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
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        VerdeOscuro,
                                        VerdeClaro,
                                        VerdeMenta
                                    ),
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
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
                        color = MaterialTheme.colorScheme.onBackground,
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontWeight = FontWeight.Black,
                            letterSpacing = 2.sp
                        )
                    )

                    Text(
                        text = "La voz que camina con el pueblo",
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodyLarge,
                        fontStyle = FontStyle.Italic
                    )

                    Spacer(modifier = Modifier.height(60.dp))

                    // Mostramos él cargando mientras se decide la navegación
                    CircularProgressIndicator(
                        modifier = Modifier.size(30.dp),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 3.dp
                    )
                    Text(
                        text = "Sintonizando...",
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(top = 10.dp),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}