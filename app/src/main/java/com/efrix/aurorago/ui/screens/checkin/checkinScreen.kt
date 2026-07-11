package com.efrix.aurorago.ui.screens.checkin

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.efrix.aurorago.util.SpritesheetHelper
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckinScreen(
    onNavigateBack: () -> Unit,
    viewModel: CheckinViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    
    val tipos = listOf("sombra_social", "nube_tormenta", "niebla_gris", "reloj_tembloroso")
    val nombres = mapOf(
        "sombra_social" to "Sombra del Juicio",
        "nube_tormenta" to "Nube de Tormenta",
        "niebla_gris" to "Niebla Gris",
        "reloj_tembloroso" to "Reloj Tembloroso"
    )

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // Contenido Scrolleable
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Título superior
            Surface(
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
            ) {
                Text(
                    text = "Check-in Diario",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(16.dp),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // Lista de Emociones
            tipos.forEachIndexed { index, tipo ->
                // Cada emoción con su propio índice de frame para animación asíncrona
                var frameIndex by remember(tipo) { mutableIntStateOf((index * 11 / 4) % 11) }
                LaunchedEffect(tipo) {
                    while (true) {
                        delay(150)
                        frameIndex = (frameIndex + 1) % 11
                    }
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Sprite / Icono
                        Box(
                            modifier = Modifier
                                .size(70.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surface),
                            contentAlignment = Alignment.Center
                        ) {
                            val frames = remember(tipo) {
                                SpritesheetHelper.loadFrames(context, SpritesheetHelper.getSheetResource(tipo), targetSizePx = 120)
                            }
                            if (frames.isNotEmpty()) {
                                Image(
                                    bitmap = frames[frameIndex % frames.size].asImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier.size(60.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = nombres[tipo] ?: tipo,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Slider(
                                value = (uiState.emocionesSeleccionadas[tipo] ?: 0).toFloat(),
                                onValueChange = { viewModel.actualizarEmocion(tipo, it.toInt()) },
                                valueRange = 0f..5f,
                                steps = 5,
                                colors = SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.primary,
                                    activeTrackColor = MaterialTheme.colorScheme.primary
                                )
                            )
                            Text(
                                text = "Intensidad: ${uiState.emocionesSeleccionadas[tipo] ?: 0}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Campo de Notas
            Text(
                text = "Cuenta un poco más de cómo te sentiste hoy...",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = uiState.nota,
                onValueChange = { viewModel.actualizarNota(it) },
                modifier = Modifier.fillMaxWidth().height(140.dp),
                shape = RoundedCornerShape(16.dp),
                placeholder = { Text("Escribe aquí tus pensamientos...", fontSize = 14.sp) }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Botón Actualizar
            Button(
                onClick = { viewModel.guardarCheckin() },
                modifier = Modifier
                    .width(200.dp)
                    .height(50.dp),
                shape = RoundedCornerShape(25.dp),
                enabled = uiState.emocionesSeleccionadas.isNotEmpty()
            ) {
                Text("Actualizar", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }

            if (uiState.guardadoExitoso) {
                Spacer(modifier = Modifier.height(12.dp))
                Text("¡Check-in guardado!", color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
                LaunchedEffect(uiState.guardadoExitoso) {
                    if (uiState.guardadoExitoso) {
                        delay(1500)
                        onNavigateBack()
                    }
                }
            }

            uiState.error?.let { error ->
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Error: $error",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            // Espacio extra para que el contenido no quede debajo del botón fijo de cerrar
            Spacer(modifier = Modifier.height(120.dp))
        }

        // Botón Cerrar (X) FIJO en la parte inferior
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
        ) {
            IconButton(
                onClick = { onNavigateBack() },
                modifier = Modifier
                    .size(60.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.9f))
                    .border(2.dp, MaterialTheme.colorScheme.error, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Cerrar",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}
