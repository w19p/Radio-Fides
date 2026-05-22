package com.radiofides.ui.screens

import android.content.Intent
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
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
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.radiofides.R
import com.radiofides.ui.theme.VerdeClaro
import com.radiofides.ui.theme.VerdeMedio
import com.radiofides.ui.theme.VerdeMuyClaro
import com.radiofides.viewmodel.FidesViewModel
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FidesHome(viewModel: FidesViewModel, navController: NavController) {
    val isPlaying = viewModel.isPlaying
    val isNetworkAvailable = viewModel.isNetworkAvailable
    val context = LocalContext.current
    val currentProgram = viewModel.currentProgram

    // [APRENDIZAJE] Función para formatear los segundos del temporizador a 00:00:00
    val tiempoFormateado = remember(viewModel.tiempoTemporizador) {
        val totalSegundos = viewModel.tiempoTemporizador
        val horas = totalSegundos / 3600
        val minutos = (totalSegundos % 3600) / 60
        val segundos = totalSegundos % 60
        if (horas > 0) {
            "%02d:%02d:%02d".format(horas, minutos, segundos)
        } else {
            "%02d:%02d".format(minutos, segundos)
        }
    }

    var showSocialMenu by remember { mutableStateOf(false) }

    LaunchedEffect(isNetworkAvailable) {
        if (!isNetworkAvailable) {
            navController.navigate("no_internet")
        }
    }

    val timeFormatter = remember { SimpleDateFormat("HH:mm:ss a", Locale.getDefault()) }
    var currentTime by remember { mutableStateOf(timeFormatter.format(Date())) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            currentTime = timeFormatter.format(Date())
        }
    }


    // DESPUÉS — Un solo motor para las 3 animaciones
    val infiniteTransition = rememberInfiniteTransition(label = "fides_pulse")

    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "live_alpha"
    )

    val recordingAlpha by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "recording_alpha"
    )

    val sleepAlpha by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 0.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "sleep_alpha"
    )
    //variable del scroll

    val scrollState = rememberScrollState()


    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {},
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = VerdeMedio, // fondo
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                ),
                navigationIcon = {
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
                                modifier = Modifier.size(24.dp),
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = "ENLACES",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.ExtraBold
                                ),
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }

                        DropdownMenu(
                            expanded = showSocialMenu,
                            onDismissRequest = { showSocialMenu = false },
                            modifier = Modifier.width(220.dp)
                        ) {
                            val openUrl = { url: String ->
                                val intent = Intent(Intent.ACTION_VIEW, url.toUri())
                                context.startActivity(intent)
                                showSocialMenu = false
                            }

                            DropdownMenuItem(
                                text = { Text("Página Oficial", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground) },
                                onClick = { openUrl("https://radiofides.com/es/") },
                                leadingIcon = { Image(painter = painterResource(id = R.drawable.logo_fides_oficial), contentDescription = null, modifier = Modifier.size(24.dp)) }
                            )
                            DropdownMenuItem(
                                text = { Text("Clínica Fides", fontWeight = FontWeight.Bold,color = MaterialTheme.colorScheme.onBackground) },
                                onClick = { openUrl("https://www.clinicafides.com/") },
                                leadingIcon = { Image(painter = painterResource(id = R.drawable.logo_clinca_fides), contentDescription = null, modifier = Modifier.size(24.dp)) }
                            )

                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                            DropdownMenuItem(
                                text = { Text("Radio Fides Bolivia", color = MaterialTheme.colorScheme.onBackground) },
                                onClick = { openUrl("https://www.facebook.com/RadioFidesBolivia/?locale=es_LA") },
                                leadingIcon = { Image(painter = painterResource(id = R.drawable.iconfacebook), contentDescription = null, modifier = Modifier.size(22.dp)) },
                            )
                            DropdownMenuItem(
                                text = { Text("radio fides bolivia", color = MaterialTheme.colorScheme.onBackground) },
                                onClick = { openUrl("https://www.instagram.com/radiofidesbolivia/") },
                                leadingIcon = { Image(painter = painterResource(id = R.drawable.iconinstagram), contentDescription = null, modifier = Modifier.size(22.dp)) },
                            )
                            DropdownMenuItem(
                                text = { Text("radio fides bolivia oficial",color = MaterialTheme.colorScheme.onBackground) },
                                onClick = { openUrl("https://www.tiktok.com/@radiofidesboliviaoficial") },
                                leadingIcon = { Image(painter = painterResource(id = R.drawable.icontiktok), contentDescription = null, modifier = Modifier.size(22.dp)) },
                            )
                            DropdownMenuItem(
                                text = { Text("Grupo Fides", color = MaterialTheme.colorScheme.onBackground) },
                                onClick = { openUrl("https://x.com/GrupoFides?mx=2") },
                                leadingIcon = { Image(painter = painterResource(id = R.drawable.icontwitter), contentDescription = null, modifier = Modifier.size(22.dp)) },
                            )
                            DropdownMenuItem(
                                text = { Text("Radio Fides de Bolivia", color = MaterialTheme.colorScheme.onBackground) },
                                onClick = { openUrl("https://www.youtube.com/@RadioFidesdeBolivia/") },
                                leadingIcon = { Image(painter = painterResource(id = R.drawable.iconyoutube), contentDescription = null, modifier = Modifier.size(22.dp)) },
                            )
                        }
                    }
                },
                actions = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .padding(end = 16.dp)
                                .clickable { navController.navigate("schedule") }
                        ) {
                            Icon(
                                imageVector = Icons.Default.DateRange,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = "PROGRAMACIÓN",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.ExtraBold
                                ),
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }

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
                                    modifier = Modifier.size(24.dp),
                                    tint = MaterialTheme.colorScheme.onBackground
                                )
                            }
                            Text(
                                text = "GRABACIONES",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.ExtraBold
                                ),
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(scrollState).padding(paddingValues).background(MaterialTheme.colorScheme.surface),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // --- SECCIÓN 1: El Logo con Fondo de Imagen ---
            ElevatedCard(    modifier = Modifier.size(280.dp),
                shape = RoundedCornerShape(32.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = Color.Transparent),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(VerdeMedio, VerdeClaro, VerdeMuyClaro),
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.logo2),
                        contentDescription = "Logo Fides",
                        modifier = Modifier
                            .size(200.dp)
                            .padding(16.dp)
                    )
                }
            }

            // --- SECCIÓN 2: Información Inteligente ---
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(110.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 8.dp)
                ) {
                    Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            modifier = Modifier.padding(12.dp).size(85.dp).shadow(10.dp, RoundedCornerShape(12.dp), clip = true),
                            shape = RoundedCornerShape(12.dp), color = Color.White
                        ) {
                            if (currentProgram.isMusical && !viewModel.currentImageUrl.isNullOrEmpty()) {
                                AsyncImage(
                                    model = viewModel.currentImageUrl,
                                    contentDescription = "Portada",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Image(
                                    painter = painterResource(id = R.drawable.logo_reproductor),
                                    contentDescription = null,
                                    modifier = Modifier.padding(8.dp),
                                    contentScale = ContentScale.Fit
                                )
                            }
                        }
                        Column(modifier = Modifier.fillMaxHeight().padding(end = 16.dp, top = 12.dp, bottom = 12.dp), verticalArrangement = Arrangement.Center) {
                            Text(
                                text = if (currentProgram.isMusical) viewModel.currentTitle else currentProgram.name,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold, fontSize = 18.sp),
                                maxLines = 1,
                                modifier = Modifier.fillMaxWidth().basicMarquee(iterations = Int.MAX_VALUE)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (currentProgram.isMusical) viewModel.currentArtist else currentProgram.conductor,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                modifier = Modifier.fillMaxWidth().basicMarquee(iterations = Int.MAX_VALUE)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                AudioVisualizer(isPlaying = isPlaying)
                Spacer(modifier = Modifier.height(20.dp))
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f), tonalElevation = 2.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                        Box(modifier = Modifier.size(8.dp).graphicsLayer(alpha = alpha).background(Color.Red, CircleShape))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "101.5 FM", color = Color.Red, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.labelLarge)
                        VerticalDivider(modifier = Modifier.height(16.dp).padding(horizontal = 12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
                        Text(text = buildAnnotatedString {
                            val parts = currentTime.split(" ")
                            if (parts.size >= 2) {
                                withStyle(style = SpanStyle(fontWeight = FontWeight.Black, fontSize = 16.sp)) { append(parts[0]) }
                                append(" ")
                                withStyle(style = SpanStyle(fontWeight = FontWeight.Bold, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)) { append(parts[1]) }
                            } else { append(currentTime) }

                            if (viewModel.tiempoTemporizador > 0) {
                                append("  ")
                                withStyle(style = SpanStyle(fontWeight = FontWeight.Black, fontSize = 14.sp, color = Color.Cyan)) {
                                    append("⏲ $tiempoFormateado")
                                }
                            }
                        })
                    }
                }
            }

            // --- SECCIÓN 3: Controles ---
            Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(
                        onClick = { if (isPlaying || viewModel.isRecording) { viewModel.iniciarDetenerGrabacion() } },
                        enabled = isPlaying || viewModel.isRecording,
                        modifier = Modifier.size(48.dp).graphicsLayer(alpha = if (viewModel.isRecording) recordingAlpha else if (isPlaying) 1f else 0.5f).background(if (viewModel.isRecording) Color.Red else MaterialTheme.colorScheme.error.copy(alpha = if (isPlaying) 1f else 0.5f), CircleShape)
                    ) {
                        Icon(painter = painterResource(id = if (viewModel.isRecording) R.drawable.ic_grabar_off else R.drawable.pruebaxml), contentDescription = "Grabar", tint = MaterialTheme.colorScheme.onPrimary.copy(alpha = if (isPlaying || viewModel.isRecording) {1f} else 0.5f))
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

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(
                        onClick = { viewModel.showSleepDialog = true },
                        modifier = Modifier
                            .size(48.dp)
                            .graphicsLayer(alpha = if (viewModel.tiempoTemporizador > 0) sleepAlpha else 1f)
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_timer),
                            contentDescription = "Temporizador",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (viewModel.tiempoTemporizador > 0) "DETENER" else "APAGAR",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                        color = if (viewModel.tiempoTemporizador > 0) Color.Cyan else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.graphicsLayer(
                            alpha = if (viewModel.tiempoTemporizador > 0) sleepAlpha else 1f
                        )
                    )
                }
            }

            // Diálogos
            if (viewModel.showSleepDialog) {
                AlertDialog(onDismissRequest = { viewModel.showSleepDialog = false }, title = { Text("Temporizador") }, text = {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        Button(onClick = { viewModel.programarApagado(15) }) { Text("15m") }
                        Button(onClick = { viewModel.programarApagado(30) }) { Text("30m") }
                        Button(onClick = { viewModel.programarApagado(60) }) { Text("60m") }
                    }
                }, confirmButton = {})
            }

            if (viewModel.showSaveDialog) {
                val focusRequester = remember { FocusRequester() }
                val keyboardController = LocalSoftwareKeyboardController.current
                
                LaunchedEffect(Unit) {
                    delay(300) // Retraso para asegurar que el diálogo esté asentado
                    focusRequester.requestFocus()
                    keyboardController?.show() // Forzamos la apertura del teclado
                }

                AlertDialog(
                    onDismissRequest = { viewModel.showSaveDialog = false },
                    title = { Text(text = "Guardar Noticia", fontWeight = FontWeight.Bold) },
                    text = {
                        Column {
                            Text("Escribe un nombre para este marcador informativo:")
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = viewModel.nuevoNombreMarcador, 
                                onValueChange = { viewModel.nuevoNombreMarcador = it }, 
                                label = { Text("Ej: Nombre del archivo") }, 
                                singleLine = true, 
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(focusRequester)
                            )
                        }
                    },
                    confirmButton = { Button(onClick = { if (viewModel.nuevoNombreMarcador.isNotBlank()) viewModel.guardarEnPlaylist(viewModel.nuevoNombreMarcador) }) { Text("Guardar") } },
                    dismissButton = { TextButton(onClick = { viewModel.showSaveDialog = false }) { Text("Cancelar") } }
                )
            }
        }
    }
}

@Composable
fun AudioVisualizer(isPlaying: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "audio_visualizer")
    val heights = (0 until 8).map { i ->
        infiniteTransition.animateFloat(
            initialValue = 0.1f,
            targetValue = if (isPlaying) 1f else 0.1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 400 + (i * 150), easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "bar_$i"
        )
    }

    Row(modifier = Modifier.height(30.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        heights.forEach { heightState ->
            val height by heightState
            Box(modifier = Modifier.width(4.dp).fillMaxHeight(height).background(color = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, shape = RoundedCornerShape(2.dp)))
        }
    }
}
