package com.efrix.aurorago.ui.screens.importarperfil

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun ImportPerfilScreen(
    onNavigateToMap: () -> Unit,
    viewModel: ImportPerfilViewModel = viewModel(factory = ImportPerfilViewModelFactory(LocalContext.current))
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Lanzador para seleccionar archivo
    val fileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                val inputStream = context.contentResolver.openInputStream(it) ?: return@let
                val json = inputStream.use { stream ->
                    java.io.BufferedReader(java.io.InputStreamReader(stream)).use { reader ->
                        reader.readText()
                    }
                }
                viewModel.procesarTextoJson(json)
            } catch (_: Exception) {
                viewModel.procesarTextoJson("")
            }
        }
    }

    // Texto pegado manualmente
    var textoJson by remember { mutableStateOf("") }

    // Si el perfil se cargó con éxito, navegamos automáticamente al mapa
    LaunchedEffect(uiState.perfilCargado) {
        if (uiState.perfilCargado != null) {
            onNavigateToMap()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Importa tu Perfil",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Carga el archivo JSON que generaste o pégalo manualmente.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(32.dp))

        // Botón para seleccionar archivo
        Button(
            onClick = { fileLauncher.launch("application/json") },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("📁 Seleccionar archivo JSON")
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = textoJson,
            onValueChange = { textoJson = it },
            label = { Text("Pega aquí el contenido JSON") },
            modifier = Modifier.fillMaxWidth().height(120.dp),
            maxLines = 5
        )

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = { viewModel.procesarTextoJson(textoJson) },
            enabled = textoJson.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("📋 Usar texto pegado")
        }

        // Mensajes de error o éxito
        if (uiState.error != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = uiState.error!!,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        if (uiState.mensajeExito != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = uiState.mensajeExito!!,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        if (uiState.isLoading) {
            Spacer(modifier = Modifier.height(16.dp))
            CircularProgressIndicator()
        }
    }
}