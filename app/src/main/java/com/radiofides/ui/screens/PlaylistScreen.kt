package com.radiofides.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.navigation.NavController
import com.radiofides.viewmodel.FidesViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistScreen(viewModel: FidesViewModel, navController: NavController) {
    val context = LocalContext.current
    val playlist = viewModel.playlist

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Grabaciones Radio Fides", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        }
    ) { paddingValues ->
        if (playlist.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text("No tienes grabaciones guardadas", color = Color.Gray)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(playlist) { marcador ->
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        elevation = CardDefaults.cardElevation(4.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(text = marcador.customName, fontWeight = FontWeight.Black, fontSize = 18.sp)
                            Text(text = "${marcador.title} - ${marcador.artist}", style = MaterialTheme.typography.bodySmall)
                            
                            val fecha = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(marcador.timestamp))
                            Text(text = "Guardado el: $fecha", fontSize = 10.sp, color = Color.Gray)

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                // BOTÓN REPRODUCIR (Abre reproductores externos: VLC, MX Player, etc.)
                                IconButton(onClick = { 
                                    try {
                                        val audioFile = File(viewModel.folderGrabaciones, "audio_${marcador.timestamp}.mp3")
                                        
                                        if (audioFile.exists()) {
                                            val contentUri: Uri = FileProvider.getUriForFile(
                                                context,
                                                "${context.packageName}.fileprovider",
                                                audioFile
                                            )

                                            val playIntent = Intent(Intent.ACTION_VIEW).apply {
                                                setDataAndType(contentUri, "audio/*")
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }

                                            context.startActivity(Intent.createChooser(playIntent, "Selecciona tu reproductor"))
                                        } else {
                                            Toast.makeText(context, "El archivo de audio no existe", Toast.LENGTH_SHORT).show()
                                        }
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Error al abrir el reproductor", Toast.LENGTH_SHORT).show()
                                    }
                                }) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = "Reproductor Externo", tint = MaterialTheme.colorScheme.primary)
                                }

                                // BOTÓN COMPARTIR
                                IconButton(onClick = {
                                    val sendIntent: Intent = Intent().apply {
                                        action = Intent.ACTION_SEND
                                        putExtra(Intent.EXTRA_TEXT, "¡Mira lo que escuché en Radio Fides!\n" +
                                                "Grabación: ${marcador.customName}\n" +
                                                "Tema: ${marcador.title}\n" +
                                                "Programa: ${marcador.artist}")
                                        type = "text/plain"
                                    }
                                    val shareIntent = Intent.createChooser(sendIntent, null)
                                    context.startActivity(shareIntent)
                                }) {
                                    Icon(Icons.Default.Share, contentDescription = "Compartir", tint = MaterialTheme.colorScheme.secondary)
                                }

                                // BOTÓN ELIMINAR
                                IconButton(onClick = { viewModel.eliminarMarcador(marcador) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Eliminar", tint = Color.Red)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
