package com.radiofides.ui.screens

import android.content.Intent
import android.util.Log
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import com.radiofides.viewmodel.FidesViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.content.ActivityNotFoundException
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistScreen(viewModel: FidesViewModel, navController: NavController) {
    val context = LocalContext.current
    val playlist = viewModel.playlist

    // Reanuda la radio al salir de la pantalla
    val lifecycleOwner = LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, _ -> }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            if (!viewModel.isPlaying) {
                viewModel.togglePlayPause()
            }
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Marcador pendiente de eliminar — null significa que no hay diálogo abierto
    var marcadorAEliminar by remember { mutableStateOf<FidesViewModel.SavedBookmark?>(null) }

    // Diálogo de confirmación de eliminación
    marcadorAEliminar?.let { marcador ->
        AlertDialog(
            onDismissRequest = { marcadorAEliminar = null },
            title = {
                Text(
                    text = "¿Eliminar grabación?",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Se eliminará \"${marcador.customName}\" de tu lista y del almacenamiento. Esta acción no se puede deshacer."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.eliminarMarcador(marcador)
                        marcadorAEliminar = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Red
                    )
                ) {
                    Text("Eliminar", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { marcadorAEliminar = null }) {
                    Text("Cancelar")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Grabaciones Radio Fides",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver"
                        )
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
                            Text(
                                text = marcador.customName,
                                fontWeight = FontWeight.Black,
                                fontSize = 18.sp
                            )
                            Text(
                                text = "${marcador.title} - ${marcador.artist}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            val fecha = SimpleDateFormat(
                                "dd/MM/yyyy HH:mm",
                                Locale.getDefault()
                            ).format(Date(marcador.timestamp))
                            Text(
                                text = "Guardado el: $fecha",
                                fontSize = 10.sp,
                                color = Color.Gray
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                // BOTÓN REPRODUCIR
                                IconButton(onClick = {
                                    try {
                                        val audioFile = viewModel.getAudioFile(marcador.timestamp)
                                        if (audioFile.exists()) {
                                            viewModel.pausarRadio()
                                            val contentUri = FileProvider.getUriForFile(
                                                context,
                                                "${context.packageName}.fileprovider",
                                                audioFile
                                            )
                                            val playIntent = Intent(Intent.ACTION_VIEW).apply {
                                                setDataAndType(contentUri, "audio/*")
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            }
                                            context.startActivity(
                                                Intent.createChooser(playIntent, "Abrir con...")
                                            )
                                        } else {
                                            Toast.makeText(
                                                context,
                                                "El archivo de audio no existe",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    } catch (e: ActivityNotFoundException) {
                                        // No hay ninguna app que pueda abrir audio
                                        Toast.makeText(
                                            context,
                                            "No hay reproductor de audio instalado",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    } catch (e: Exception) {
                                        Log.e(
                                            "PlaylistScreen",
                                            "Error reproductor: ${e.message} — ${e.javaClass.simpleName}"
                                        )
                                        Toast.makeText(
                                            context,
                                            "Error: ${e.javaClass.simpleName}",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                }) {
                                    Icon(
                                        Icons.Default.PlayArrow,
                                        contentDescription = "Reproductor Externo",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }

                                // BOTÓN COMPARTIR
                                IconButton(onClick = {
                                    try {
                                        val audioFile =
                                            viewModel.getAudioFile(marcador.timestamp)
                                        if (audioFile.exists()) {
                                            val contentUri = FileProvider.getUriForFile(
                                                context,
                                                "${context.packageName}.fileprovider",
                                                audioFile
                                            )
                                            val shareIntent =
                                                Intent(Intent.ACTION_SEND).apply {
                                                    type = "audio/*"
                                                    putExtra(
                                                        Intent.EXTRA_STREAM,
                                                        contentUri
                                                    )
                                                    putExtra(
                                                        Intent.EXTRA_TEXT,
                                                        "Grabación de Radio Fides: ${marcador.customName}"
                                                    )
                                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                }
                                            context.startActivity(
                                                Intent.createChooser(
                                                    shareIntent,
                                                    "Compartir audio vía..."
                                                )
                                            )
                                        } else {
                                            Toast.makeText(
                                                context,
                                                "El archivo de audio no existe",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    } catch (e: Exception) {
                                        Log.e(
                                            "PlaylistScreen",
                                            "Error compartir: ${e.message} — ${e.javaClass.simpleName}"
                                        )
                                        Toast.makeText(
                                            context,
                                            "Error: ${e.javaClass.simpleName}",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                }) {
                                    Icon(
                                        Icons.Default.Share,
                                        contentDescription = "Compartir Audio",
                                        tint = MaterialTheme.colorScheme.secondary
                                    )
                                }

                                // BOTÓN ELIMINAR — solo abre el diálogo, no elimina directo
                                IconButton(onClick = {
                                    marcadorAEliminar = marcador
                                }) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Eliminar",
                                        tint = Color.Red
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}