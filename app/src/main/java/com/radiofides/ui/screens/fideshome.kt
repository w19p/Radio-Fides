package com.radiofides.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.radiofides.R
import com.radiofides.viewmodel.FidesViewModel
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FidesHome(viewModel: FidesViewModel = viewModel(), navController: NavController) {
    val isPlaying = viewModel.isPlaying
    val isNetworkAvailable = viewModel.isNetworkAvailable
    val context = LocalContext.current

    // Estado para controlar la visibilidad del menú de enlaces
    var showSocialMenu by remember { mutableStateOf(false) }

    // --- SALTO AUTOMÁTICO SI SE PIERDE EL INTERNET ---
    LaunchedEffect(isNetworkAvailable) {
        if (!isNetworkAvailable) {
            navController.navigate("no_internet")
        }
    }

    // --- AUTO-PLAY AL ENTRAR ---
    LaunchedEffect(Unit) {
        viewModel.autoPlay()
    }

    // --- Lógica del RELOJ ---
    var currentTime by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        while (true) {
            val formatter = SimpleDateFormat("HH:mm:ss a", Locale.getDefault())
            currentTime = formatter.format(Date())
            delay(1000)
        }
    }

    // --- Lógica del parpadeo ---
    val infiniteTransition = rememberInfiniteTransition(label = "live_pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(1000, easing = LinearEasing), repeatMode = RepeatMode.Reverse),
        label = "live_alpha"
    )

    val recordingTransition = rememberInfiniteTransition(label = "recording_pulse")
    val recordingAlpha by recordingTransition.animateFloat(
        initialValue = 1f, targetValue = 0.3f,
        animationSpec = infiniteRepeatable(animation = tween(800, easing = LinearEasing), repeatMode = RepeatMode.Reverse),
        label = "recording_alpha"
    )

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(text = "Radio Fides", fontWeight = FontWeight.Black) },
                navigationIcon = {
                    // [APRENDIZAJE] Agrupamos Icono + Texto en una Columna para que parezcan uno solo
                    Box {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .padding(start = 16.dp)
                                .clickable { showSocialMenu = true }
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_enlaces),
                                contentDescription = null,
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = "ENLACES",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            )
                        }
                        
                        // Menú desplegable con todos los links oficiales
                        // ... dentro del Box del navigationIcon en fideshome.kt
                        DropdownMenu(
                            expanded = showSocialMenu,
                            onDismissRequest = { showSocialMenu = false },
                            modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                        ) {
                            val openUrl = { url: String ->
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                context.startActivity(intent)
                                showSocialMenu = false
                            }

                            // --- ENLACES INSTITUCIONALES ---
                            DropdownMenuItem(
                                text = { Text("Página Oficial", fontWeight = FontWeight.Medium) },
                                onClick = { openUrl("https://radiofides.com/es/") },
                                leadingIcon = {
                                    Image(painter = painterResource(id = R.drawable.logo_fides_oficial), contentDescription = null, modifier = Modifier.size(24.dp))
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Clínica Fides", fontWeight = FontWeight.Medium) },
                                onClick = { openUrl("https://www.clinicafides.com/") },
                                leadingIcon = {
                                    Image(painter = painterResource(id = R.drawable.logo_clinca_fides), contentDescription = null, modifier = Modifier.size(24.dp))
                                }
                            )

                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                            // --- REDES SOCIALES CON SUS LOGOS ---
                            DropdownMenuItem(
                                text = { Text("Facebook") },
                                onClick = { openUrl("https://www.facebook.com/RadioFidesBolivia/?locale=es_LA") },
                                leadingIcon = {
                                    Image(painter = painterResource(id = R.drawable.iconfacebook), contentDescription = null, modifier = Modifier.size(24.dp))
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Instagram") },
                                onClick = { openUrl("https://www.instagram.com/radiofidesbolivia/") },
                                leadingIcon = {
                                    Image(painter = painterResource(id = R.drawable.iconinstagram), contentDescription = null, modifier = Modifier.size(24.dp))
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("TikTok") },
                                onClick = { openUrl("https://www.tiktok.com/@radiofidesboliviaoficial") },
                                leadingIcon = {
                                    Image(painter = painterResource(id = R.drawable.icontiktok), contentDescription = null, modifier = Modifier.size(24.dp))
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Twitter (X)") },
                                onClick = { openUrl("https://x.com/GrupoFides?mx=2") },
                                leadingIcon = {
                                    Image(painter = painterResource(id = R.drawable.icontwitter), contentDescription = null, modifier = Modifier.size(24.dp))
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("YouTube") },
                                onClick = { openUrl("https://www.youtube.com/@RadioFidesdeBolivia/") },
                                leadingIcon = {
                                    Image(painter = painterResource(id = R.drawable.iconyoutube), contentDescription = null, modifier = Modifier.size(24.dp))
                                }
                            )
                        }
                    }
                },
                actions = {
                    // [APRENDIZAJE] Hacemos lo mismo para Grabaciones: Icono con Badge arriba y Texto abajo
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .padding(end = 16.dp)
                            .clickable {
                                viewModel.contadorNuevasGrabaciones = 0
                                navController.navigate("playlist")
                            }
                    ) {
                        BadgedBox(
                            badge = {
                                if (viewModel.contadorNuevasGrabaciones > 0) {
                                    Badge(containerColor = Color.Red) {
                                        Text(viewModel.contadorNuevasGrabaciones.toString(), color = Color.White)
                                    }
                                }
                            }
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_playlist), 
                                contentDescription = null,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Text(
                            text = "GRABACIONES",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
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
                        contentDescription = "Logo Fides",
                        modifier = Modifier
                            .size(200.dp)
                            .padding(16.dp)
                    )
                }
            }

            // --- SECCIÓN 2: Información ---
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .height(110.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 8.dp)
                ) {
                    Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            modifier = Modifier
                                .padding(12.dp)
                                .size(85.dp)
                                .shadow(10.dp, RoundedCornerShape(12.dp), clip = true),
                            shape = RoundedCornerShape(12.dp), color = Color.White
                        ) {
                            if (viewModel.currentImageUrl.isNullOrEmpty()) {
                                Image(painter = painterResource(id = R.drawable.logo_reproductor), contentDescription = null, modifier = Modifier.padding(8.dp), contentScale = ContentScale.Fit)
                            } else {
                                AsyncImage(model = viewModel.currentImageUrl, contentDescription = "Portada", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            }
                        }
                        Column(modifier = Modifier
                            .fillMaxHeight()
                            .padding(end = 16.dp, top = 12.dp, bottom = 12.dp), verticalArrangement = Arrangement.Center) {
                            Text(text = viewModel.currentTitle, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold, fontSize = 18.sp), maxLines = 1, modifier = Modifier
                                .fillMaxWidth()
                                .basicMarquee(iterations = Int.MAX_VALUE))
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(text = viewModel.currentArtist, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, maxLines = 1, modifier = Modifier
                                .fillMaxWidth()
                                .basicMarquee(iterations = Int.MAX_VALUE))
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                AudioVisualizer(isPlaying = isPlaying)
                Spacer(modifier = Modifier.height(20.dp))
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f), tonalElevation = 2.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                        Box(modifier = Modifier
                            .size(8.dp)
                            .graphicsLayer(alpha = alpha)
                            .background(Color.Red, CircleShape))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "101.5 FM", color = Color.Red, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.labelLarge)
                        VerticalDivider(modifier = Modifier
                            .height(16.dp)
                            .padding(horizontal = 12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
                        Text(text = buildAnnotatedString {
                            val parts = currentTime.split(" ")
                            if (parts.size >= 2) {
                                withStyle(style = SpanStyle(fontWeight = FontWeight.Black, fontSize = 16.sp)) { append(parts[0]) }
                                append(" ")
                                withStyle(style = SpanStyle(fontWeight = FontWeight.Bold, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)) { append(parts[1]) }
                            } else { append(currentTime) }
                        })
                    }
                }
            }

            // --- SECCIÓN 3: Controles ---
            Row(modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(
                        onClick = { if (isPlaying || viewModel.isRecording) { viewModel.iniciarDetenerGrabacion() } }, 
                        enabled = isPlaying || viewModel.isRecording,
                        modifier = Modifier
                            .size(48.dp)
                            .graphicsLayer(alpha = if (viewModel.isRecording) recordingAlpha else if (isPlaying) 1f else 0.5f)
                            .background(
                                if (viewModel.isRecording) Color.Red else MaterialTheme.colorScheme.error.copy(
                                    alpha = if (isPlaying) 1f else 0.5f
                                ), CircleShape
                            )
                    ) {
                        Icon(painter = painterResource(id = if (viewModel.isRecording) R.drawable.ic_exit else R.drawable.ic_grabadora), contentDescription = "Grabar", tint = Color.White.copy(alpha = if (isPlaying || viewModel.isRecording) 1f else 0.5f))
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = if (viewModel.isRecording) "GRABANDO..." else "GRABAR", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold), color = if (viewModel.isRecording) Color.Red.copy(alpha = recordingAlpha) else Color.Gray, modifier = Modifier.graphicsLayer(alpha = if (viewModel.isRecording) recordingAlpha else if (isPlaying) 1f else 0.5f))
                }

                Box(contentAlignment = Alignment.Center) {
                    Surface(modifier = Modifier.size(90.dp), shape = CircleShape, color = if (viewModel.isBuffering) MaterialTheme.colorScheme.surfaceVariant else if (isPlaying) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer, tonalElevation = 8.dp, onClick = { viewModel.togglePlayPause() }) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(painter = painterResource(id = if (isPlaying) R.drawable.ic_stop else R.drawable.ic_play), contentDescription = null, modifier = Modifier.padding(24.dp), tint = if (viewModel.isBuffering) Color.Transparent else if (isPlaying) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer)
                            if (viewModel.isBuffering) { CircularProgressIndicator(modifier = Modifier.size(45.dp), color = MaterialTheme.colorScheme.primary, strokeWidth = 4.dp) }
                        }
                    }
                }

                IconButton(onClick = { /* Acción para cerrar */ }, modifier = Modifier
                    .size(48.dp)
                    .background(MaterialTheme.colorScheme.error, CircleShape)
                ) {
                    Icon(painter = painterResource(id = R.drawable.ic_exit), contentDescription = "Cerrar", tint = MaterialTheme.colorScheme.onError)
                }
            }
            if (viewModel.showSaveDialog) {
                AlertDialog(
                    onDismissRequest = { viewModel.showSaveDialog = false },
                    title = { Text(text = "Guardar Noticia", fontWeight = FontWeight.Bold) },
                    text = {
                        Column {
                            Text("Escribe un nombre para este marcador informativo:")
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(value = viewModel.nuevoNombreMarcador, onValueChange = { viewModel.nuevoNombreMarcador = it }, label = { Text("Ej: Entrevista Política") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        }
                    },
                    confirmButton = { Button(onClick = { if (viewModel.nuevoNombreMarcador.isNotBlank()) { viewModel.guardarEnPlaylist(viewModel.nuevoNombreMarcador) } }) { Text("Guardar") } },
                    dismissButton = { TextButton(onClick = { viewModel.showSaveDialog = false }) { Text("Cancelar") } }
                )
            }
        }
    }
}

@Composable
fun AudioVisualizer(isPlaying: Boolean) {
    Row(modifier = Modifier.height(30.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(8) { i ->
            val infiniteTransition = rememberInfiniteTransition(label = "")
            val height by infiniteTransition.animateFloat(
                initialValue = 0.1f, targetValue = if (isPlaying) 1f else 0.1f,
                animationSpec = infiniteRepeatable(animation = tween(durationMillis = 400 + (i * 150), easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse), label = ""
            )
            Box(modifier = Modifier
                .width(4.dp)
                .fillMaxHeight(height)
                .background(
                    color = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    shape = RoundedCornerShape(2.dp)
                ))
        }
    }
}
