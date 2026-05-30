package com.radiofides.ui.screens

import android.content.ActivityNotFoundException
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import com.radiofides.R
import com.radiofides.viewmodel.FidesViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
                    text = stringResource(R.string.dialog_delete_title),
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.dialog_delete_desc, marcador.customName)
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
                    Text(stringResource(R.string.btn_eliminar), color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { marcadorAEliminar = null }) {
                    Text(stringResource(R.string.btn_cancelar))
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.title_grabaciones_fides),
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.label_volver)
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
                Text(stringResource(R.string.empty_playlist), color = Color.Gray)
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
                                text = stringResource(R.string.label_grabado_el, fecha),
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
                                                Intent.createChooser(playIntent, context.getString(R.string.label_abrir_con))
                                            )
                                        } else {
                                            Toast.makeText(
                                                context,
                                                context.getString(R.string.error_no_audio),
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    } catch (_: ActivityNotFoundException) {
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.error_no_player),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    } catch (e: Exception) {
                                        Log.e("PlaylistScreen", "Error: ${e.message}")
                                    }
                                }) {
                                    Icon(
                                        Icons.Default.PlayArrow,
                                        contentDescription = stringResource(R.string.desc_reproducir_externo),
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
                                                        context.getString(R.string.share_template, marcador.customName, marcador.title, marcador.artist)
                                                    )
                                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                }
                                            context.startActivity(
                                                Intent.createChooser(
                                                    shareIntent,
                                                    context.getString(R.string.label_compartir_via)
                                                )
                                            )
                                        } else {
                                            Toast.makeText(
                                                context,
                                                context.getString(R.string.error_no_audio),
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    } catch (e: Exception) {
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.error_sharing),
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                }) {
                                    Icon(
                                        Icons.Default.Share,
                                        contentDescription = stringResource(R.string.desc_compartir_audio),
                                        tint = MaterialTheme.colorScheme.secondary
                                    )
                                }

                                // BOTÓN ELIMINAR
                                IconButton(onClick = {
                                    marcadorAEliminar = marcador
                                }) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = stringResource(R.string.btn_eliminar),
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
