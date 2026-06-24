package com.efrix.aurorago.ui.screens.reto

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import kotlinx.coroutines.delay
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RetoScreen(
    navController: NavController,
    tipoMiedo: String,
    miedoId: String,
    viewModel: RetoViewModel = viewModel(
        factory = RetoViewModelFactory(tipoMiedo, miedoId)
    )
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    if (uiState.isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else if (uiState.miedoDerrotado) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("🏆 ${uiState.recompensa}", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(16.dp))
                Button(onClick = { navController.popBackStack() }) {
                    Text("Volver al mapa")
                }
            }
        }
    } else {
        Scaffold(
            topBar = {
                TopAppBar(title = { Text("Enfrentando a ${viewModel.nombreAmigable(tipoMiedo)}") })
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // HP Visual Bar
                val hpProgress = uiState.hpActual / 100f
                val hpColor = when {
                    uiState.hpActual > 50 -> Color(0xFF4CAF50) // Verde
                    uiState.hpActual > 25 -> Color(0xFFFFEB3B) // Amarillo
                    else -> Color(0xFFF44336) // Rojo
                }

                Text(
                    "HP de la Emoción: ${uiState.hpActual}/100",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(Modifier.height(8.dp))
                
                LinearProgressIndicator(
                    progress = { hpProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(12.dp)
                        .clip(RoundedCornerShape(6.dp)),
                    color = hpColor,
                    trackColor = hpColor.copy(alpha = 0.2f)
                )

                Spacer(Modifier.height(24.dp))
                Text(uiState.retoTexto, style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(24.dp))

                when (tipoMiedo) {
                    "sombra_social" -> QuizReto(uiState) { idx: Int ->
                        if (idx == uiState.respuestaCorrectaIdx) viewModel.completarReto()
                        else Toast.makeText(context, "Intenta con otra opción", Toast.LENGTH_SHORT).show()
                    }
                    "nube_tormenta" -> RespiracionReto(uiState.duracionRespiracion) {
                        viewModel.completarReto()
                    }
                    "niebla_gris" -> GratitudFotoReto(context) { _ ->
                        viewModel.completarReto()
                    }
                    "reloj_tembloroso" -> TextoReflexionReto { _ ->
                        viewModel.completarReto()
                    }
                    else -> {
                        Button(onClick = { viewModel.completarReto() }) {
                            Text("Superar reto")
                        }
                    }
                }
                
                // Notificar al mapa cuando el reto se completa
                LaunchedEffect(uiState.retoCompletado) {
                    if (uiState.retoCompletado) {
                        // This would require a shared ViewModel or communication mechanism
                        // For now, we'll rely on the periodic refresh to sync the data
                    }
                }

                if (uiState.retoCompletado) {
                    Spacer(Modifier.height(16.dp))
                    Text("¡Reto superado! -25 HP", color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { navController.popBackStack() }) {
                        Text("Seguir explorando")
                    }
                }
            }
        }
    }
}

@Composable
fun QuizReto(uiState: RetoUiState, onAnswer: (Int) -> Unit) {
    Column {
        uiState.opcionesQuiz.forEachIndexed { index: Int, opcion: String ->
            Button(
                onClick = { onAnswer(index) },
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) {
                Text(opcion)
            }
        }
    }
}

@Composable
fun RespiracionReto(duracion: Int, onCompleted: () -> Unit) {
    var tiempoRestante by remember { mutableStateOf(duracion) }
    var corriendo by remember { mutableStateOf(false) }

    LaunchedEffect(corriendo) {
        if (corriendo) {
            while (tiempoRestante > 0) {
                delay(1000L)
                tiempoRestante--
            }
            onCompleted()
        }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Tiempo restante: ${tiempoRestante}s", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        if (!corriendo) {
            Button(onClick = { corriendo = true }) {
                Text("Iniciar respiración")
            }
        }
    }
}

@Composable
fun GratitudFotoReto(context: android.content.Context, onPhotoTaken: (File) -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var imageCapture: ImageCapture? by remember { mutableStateOf(null) }
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    if (!hasCameraPermission) {
        Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
            Text("Permitir cámara")
        }
    } else {
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).also { previewView ->
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                    imageCapture = ImageCapture.Builder().build()
                    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                    try {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            preview,
                            imageCapture
                        )
                    } catch (_: Exception) {}
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
        )

        Spacer(Modifier.height(8.dp))
        Button(onClick = {
            val photoFile = File(context.externalMediaDirs.first(), "${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.jpg")
            val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
            imageCapture?.takePicture(
                outputOptions,
                ContextCompat.getMainExecutor(context),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                        onPhotoTaken(photoFile)
                    }
                    override fun onError(exception: ImageCaptureException) {
                        Toast.makeText(context, "Error al capturar foto", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }) {
            Text("Tomar foto")
        }
    }
}

@Composable
fun TextoReflexionReto(onReflexion: (String) -> Unit) {
    var texto by remember { mutableStateOf("") }
    Column {
        OutlinedTextField(
            value = texto,
            onValueChange = { texto = it },
            label = { Text("Escribe aquí") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        Button(onClick = { onReflexion(texto) }, enabled = texto.isNotBlank()) {
            Text("Compartir logro")
        }
    }
}