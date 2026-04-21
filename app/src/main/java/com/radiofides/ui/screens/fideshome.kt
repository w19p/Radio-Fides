package com.radiofides.ui.screens

import android.content.Context
import android.media.AudioManager
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.MarqueeDefaults
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.radiofides.R
import com.radiofides.viewmodel.FidesViewModel
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.foundation.basicMarquee
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.ui.platform.LocalContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FidesHome(viewModel: FidesViewModel = viewModel()) {
    val isPlaying = viewModel.isPlaying

    // --- Lógica del RELOJ ---
    var currentTime by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        while (true) {
            // "hh:mm a" -> Ejemplo: "09:45 PM"
            val formatter = SimpleDateFormat("HH:mm:ss a", Locale.getDefault())
            currentTime = formatter.format(Date())
            delay(1000)
        }
    }

    // --- Lógica del indicador LIVE parpadeante ---
    val infiniteTransition = rememberInfiniteTransition(label = "live_pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "live_alpha"
    )

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(text = stringResource(id = R.string.top_bar_title), fontWeight = FontWeight.Black) },
                actions = {
                    IconButton(onClick = { /* Lógica de compartir */ }) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = stringResource(id = R.string.action_compartir)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.surface),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly
        ) {
            // --- SECCIÓN 1: El Logo ---
            ElevatedCard(
                modifier = Modifier.size(280.dp),
                shape = RoundedCornerShape(32.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = Color.White),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 12.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Image(
                            painter = painterResource(id = R.drawable.logo_fides_oficial),
                            contentDescription = stringResource(id = R.string.desc_logo_fides),
                            modifier = Modifier.size(200.dp).padding(16.dp)
                        )
                }
            }

            // --- SECCIÓN 2: Información de Transmisión ---
            Column(horizontalAlignment = Alignment.CenterHorizontally) {

                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .height(110.dp), // Altura fija para un look de mini-player
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 1. LA FOTO (A la izquierda con sombra)
                        Surface(
                            modifier = Modifier
                                .padding(12.dp)
                                .size(85.dp)
                                .shadow(elevation = 10.dp, shape = RoundedCornerShape(12.dp), clip = true),
                            shape = RoundedCornerShape(12.dp),
                            color = Color.White
                        ) {
                            if (viewModel.currentImageUrl.isNullOrEmpty()) {
                                Image(
                                    painter = painterResource(id = R.drawable.logo_reproductor),
                                    contentDescription = null,
                                    modifier = Modifier.padding(8.dp),
                                    contentScale = ContentScale.Fit
                                )
                            } else {
                                AsyncImage(
                                    model = viewModel.currentImageUrl,
                                    contentDescription = "Portada",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }

                        // 2. TEXTOS (A la derecha)
                        Column(
                            modifier = Modifier
                                .fillMaxHeight()
                                .padding(end = 16.dp, top = 12.dp, bottom = 12.dp),
                            verticalArrangement = Arrangement.Center
                        ) {
                            // TEMA (Arriba)
                            // 1. TÍTULO (Tema)
                            Text(
                                text = viewModel.currentTitle,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 18.sp,
                                    letterSpacing = 0.5.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .basicMarquee(
                                        iterations = Int.MAX_VALUE,
                                        initialDelayMillis = 2000,
                                        velocity = 50.dp
                                        // Quitamos el parámetro 'spacing' para evitar el error de referencia
                                    )
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            // 2. ARTISTA / CANTANTE (Abajo)
                            Text(
                                text = viewModel.currentArtist,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 14.sp
                                ),
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .basicMarquee(
                                        iterations = Int.MAX_VALUE,
                                        initialDelayMillis = 3000,
                                        velocity = 40.dp
                                        // Quitamos el parámetro 'spacing' aquí también
                                    )
                            )
                        }
                    }
                }

                // Visualizador sutil justo debajo del card
                Spacer(modifier = Modifier.height(12.dp))
                // LLAMADA AL COMPOSABLE (Ahora corregido)
                AudioVisualizer(isPlaying = isPlaying)

                Spacer(modifier = Modifier.height(20.dp))

                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f), // Un poco más de opacidad para mejor fondo
                    tonalElevation = 2.dp
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp) // Un poco más de aire vertical
                    ) {
                        // Indicador parpadeante
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .graphicsLayer(alpha = alpha)
                                .background(Color.Red, CircleShape)
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        Text(
                            text = "101.3 FM",
                            style = MaterialTheme.typography.labelLarge, // Subimos a labelLarge
                            color = Color.Red,
                            fontWeight = FontWeight.ExtraBold, // Más grosor
                            letterSpacing = 0.5.sp
                        )

                        // Divisor más marcado
                        VerticalDivider(
                            modifier = Modifier
                                .height(16.dp)
                                .padding(horizontal = 12.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                        )

                        // HORA RESALTADA
                        Text(
                            text = buildAnnotatedString {
                                val parts = currentTime.split(" ")
                                if (parts.size >= 2) {
                                    // Los números (Ej: 09:45)
                                    withStyle(style = SpanStyle(
                                        fontWeight = FontWeight.Black, // El máximo grosor posible
                                        fontSize = 16.sp,             // Un poco más grande
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    ) {
                                        append(parts[0])
                                    }
                                    append(" ")
                                    // AM/PM (Ej: PM)
                                    withStyle(style = SpanStyle(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,             // Más pequeño para jerarquía
                                        color = MaterialTheme.colorScheme.primary
                                    )) {
                                        append(parts[1])
                                    }
                                } else {
                                    append(currentTime)
                                }
                            }
                        )
                    }
                }
            }

            // --- SECCIÓN 3: Controles ---
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // --- BOTÓN IZQUIERDO: VOLUMEN ---
                val context = LocalContext.current
                IconButton(
                    onClick = {
                        // Abre el panel de volumen del sistema
                        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                        audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
                    },
                    modifier = Modifier.size(48.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowUp, // Importa Icons.Default.VolumeUp
                        contentDescription = "Volumen",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // --- TU BOTÓN CENTRAL (PLAY/PAUSE) ---
                Box(contentAlignment = Alignment.Center) {
                    Surface(
                        modifier = Modifier.size(90.dp),
                        shape = CircleShape,
                        color = if (isPlaying) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
                        tonalElevation = 8.dp,
                        onClick = { viewModel.togglePlayPause() }
                    ) {
                        Icon(
                            painter = painterResource(
                                id = if (isPlaying) R.drawable.ic_stop else R.drawable.ic_play
                            ),
                            contentDescription = null,
                            modifier = Modifier.padding(24.dp),
                            tint = if (isPlaying) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                // --- BOTÓN DERECHO: CERRAR (X) ---
                IconButton(
                    onClick = { viewModel.exitApp() },
                    modifier = Modifier.size(48.dp).background(MaterialTheme.colorScheme.error, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cerrar Aplicación",
                        tint = MaterialTheme.colorScheme.onError
                    )
                }
            }
        }
    }
}

// --- FUNCIÓN INDEPENDIENTE (barras de sonido ) ---
@Composable
fun AudioVisualizer(isPlaying: Boolean) {
    Row(
        modifier = Modifier.height(30.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(8) { i ->
            val infiniteTransition = rememberInfiniteTransition(label = "")
            val height by infiniteTransition.animateFloat(
                initialValue = 0.1f,
                targetValue = if (isPlaying) 1f else 0.1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 400 + (i * 150), easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ), label = ""
            )

            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight(height)
                    .background(
                        color = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                        shape = RoundedCornerShape(2.dp)
                    )
            )
        }
    }
}