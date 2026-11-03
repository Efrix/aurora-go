package com.efrix.aurorago.ui.screens.mapa

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.efrix.aurorago.BuildConfig
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.efrix.aurorago.data.repository.AuthRepository
import com.efrix.aurorago.util.LocationWorker
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.efrix.aurorago.util.MapStateHolder
import com.google.android.gms.location.*
import org.mapsforge.core.model.LatLong
import org.mapsforge.map.android.view.MapView

interface ProgressUpdateListener {
    fun onProgressUpdated(nuevoProgreso: Map<String, Int>)
}

class SafeMapsforgeView(context: Context) : MapView(context) {
    var isFullyReady = false
        private set

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        isFullyReady = true
    }

    override fun onDetachedFromWindow() {
        isFullyReady = false
        super.onDetachedFromWindow()
    }
}

@Composable
fun MapaScreen(
    onMiedoClick: (tipoMiedo: String, miedoId: String) -> Unit,
    onCheckinClick: () -> Unit,
    onLogout: () -> Unit,
    viewModel: MapaViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    var mostrandoPerfil by remember { mutableStateOf(false) }

    // Pantalla de carga: solo se muestra la primera vez que se abre el mapa
    // en esta sesión. Al volver desde un reto o el check-in ya no aparece.
    var mostrarCarga by remember { mutableStateOf(!MapStateHolder.mapaInicializado) }
    var mapaPreparado by remember { mutableStateOf(false) }
    var showCheckinRealizadoDialog by remember { mutableStateOf(false) }

    // Consentimiento de ubicacion en background
    val prefs = remember {
        context.getSharedPreferences("location_consent", Context.MODE_PRIVATE)
    }
    var showLocationConsent by remember {
        mutableStateOf(!prefs.getBoolean("consentido", false))
    }

    if (showLocationConsent) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Ubicación en segundo plano") },
            text = {
                Text(
                    "Aurora GO necesita acceder a tu ubicación en segundo plano " +
                    "para actualizar las emociones en el mapa cada 15 minutos, " +
                    "incluso cuando la app no esté abierta.\n\n" +
                    "Tu ubicación solo se usa dentro de la app y se almacena " +
                    "de forma segura en tu perfil."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    prefs.edit().putBoolean("consentido", true).apply()
                    LocationWorker.scheduleLocationUpdates(context)
                    showLocationConsent = false
                }) {
                    Text("Aceptar")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    prefs.edit().putBoolean("consentido", false).apply()
                    showLocationConsent = false
                }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Estado del GPS
    val locationManager = remember { context.getSystemService(Context.LOCATION_SERVICE) as LocationManager }
    var gpsActivo by remember {
        mutableStateOf(locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER))
    }

    // Obtener componentes del mapa del Singleton
    val mapView = remember { MapStateHolder.getMapView(context) }
    val downloadLayer = MapStateHolder.getDownloadLayer()
    val userLocationCircle = MapStateHolder.getUserLocationCircle()
    val monstruosLayer = remember { MonstruosLayer(context, mapView) }

    // --- Referencias "vivas" para que el LaunchedEffect lea siempre
    //     el valor más reciente, sin depender de que Compose recomponga ---
    val miedosEnMapaRef = remember { mutableStateOf(uiState.miedosEnMapa) }
    val ubicacionRef = remember { mutableStateOf(uiState.ubicacionActual) }

    // Flag para centrar el mapa una sola vez
    val centerInitialized = remember { mutableStateOf(false) }

    LaunchedEffect(uiState.miedosEnMapa) {
        miedosEnMapaRef.value = uiState.miedosEnMapa
    }

    LaunchedEffect(uiState.ubicacionActual) {
        ubicacionRef.value = uiState.ubicacionActual
    }

    // Ocultamos la pantalla de carga cuando el mapa ya está montado y
    // tenemos ubicación. El pequeño margen extra permite que se dibujen
    // los primeros tiles y los monstruos.
    val datosListosParaMapa = mapaPreparado && uiState.ubicacionActual != null
    LaunchedEffect(datosListosParaMapa) {
        if (datosListosParaMapa) {
            delay(1_500L)
            mostrarCarga = false
            MapStateHolder.marcarMapaInicializado()
        }
    }

    // Si el GPS tarda demasiado, no dejamos al usuario atrapado en la carga.
    LaunchedEffect(uiState.isLoading) {
        if (!uiState.isLoading) {
            delay(12_000L)
            if (mostrarCarga) {
                mostrarCarga = false
                MapStateHolder.marcarMapaInicializado()
            }
        }
    }

    LaunchedEffect(Unit) {
        monstruosLayer.onMonstruoTap = { miedoId, tipo ->
            val miedo = miedosEnMapaRef.value.find { it.id == miedoId }
            if (miedo != null) {
                val hp = uiState.progresoMiedos[tipo] ?: 100
                val nombreMiedo = viewModel.nombreAmigable(tipo)
                if (hp <= 0) {
                    Toast.makeText(context, "$nombreMiedo ya fue derrotado", Toast.LENGTH_SHORT).show()
                } else {
                    if (gpsActivo) {
                        ubicacionRef.value?.let { (uLat, uLon) ->
                            val dist = calcularDistancia(uLat, uLon, miedo.latitud, miedo.longitud)
                            if (dist <= 50.0) {
                                onMiedoClick(tipo, miedoId)
                            } else {
                                Toast.makeText(
                                    context,
                                    "Demasiado lejos (${dist.toInt()}m).\n$nombreMiedo: $hp HP",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    } else {
                        Toast.makeText(context, "Activa el GPS", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        while (true) {
            try {
                val safeMap = mapView as? SafeMapsforgeView
                if (safeMap != null && safeMap.isFullyReady) {
                    if (!mapaPreparado) mapaPreparado = true
                    var changed = false
                    val layers = safeMap.layerManager.layers

                    downloadLayer?.let { layer ->
                        if (!layers.contains(layer)) {
                            layers.add(0, layer)
                            changed = true
                        }
                    }

                    if (!layers.contains(monstruosLayer)) {
                        val index = if (downloadLayer != null && layers.contains(downloadLayer)) 1 else 0
                        layers.add(index, monstruosLayer)
                        changed = true
                    }

                    userLocationCircle?.let { circle ->
                        if (!layers.contains(circle)) {
                            layers.add(circle)
                            changed = true
                        }
                        ubicacionRef.value?.let { (lat, lon) ->
                            circle.setLatLong(LatLong(lat, lon))
                            changed = true
                        }
                    }

                    monstruosLayer.sync(miedosEnMapaRef.value)
                    monstruosLayer.advanceFrame()

                    if (!centerInitialized.value) {
                        ubicacionRef.value?.let { (lat, lon) ->
                            safeMap.model.mapViewPosition.setCenter(LatLong(lat, lon))
                            centerInitialized.value = true
                            changed = true
                        }
                    }

                    if (changed) {
                        safeMap.invalidate()
                    }
                } else {
                    mapView.postInvalidate()
                }
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.e("MapaScreen", "Error en el ciclo de animacion", e)
            }
            delay(100)
        }
    }

    // --- Ciclo de vida (pausa/reanuda capa de descarga) ---
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    if (BuildConfig.DEBUG) Log.d("MapaScreen-Mapsforge", "ON_RESUME")
                    viewModel.refrescarDatos()
                    gpsActivo = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                    downloadLayer?.onResume()
                }
                Lifecycle.Event.ON_PAUSE -> {
                    if (BuildConfig.DEBUG) Log.d("MapaScreen-Mapsforge", "ON_PAUSE")
                    downloadLayer?.onPause()
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            if (BuildConfig.DEBUG) Log.d("MapaScreen-Mapsforge", "DISPOSE")
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    
    // Asegurar desvinculación de parent anterior y actualización de datos al entrar/salir
    DisposableEffect(mapView) {
        viewModel.refrescarDatos()
        downloadLayer?.onResume()
        onDispose {
            (mapView.parent as? android.view.ViewGroup)?.removeView(mapView)
        }
    }

    // Observar actualizaciones de progreso de miedos
    DisposableEffect(Unit) {
        val listener: (Map<String, Int>) -> Unit = { nuevoProgreso ->
            viewModel.onProgresoMiedosActualizado(nuevoProgreso)
        }
        AuthRepository.addProgressListener(listener)
        onDispose {
            AuthRepository.removeProgressListener(listener)
        }
    }

    // --- Ubicación (seguimiento activo) ---
    DisposableEffect(context) {
        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    viewModel.actualizarUbicacion(location.latitude, location.longitude)
                }
            }
        }
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000L)
            .setMinUpdateIntervalMillis(2000L)
            .build()

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED
        ) {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
        }
        onDispose { fusedLocationClient.removeLocationUpdates(locationCallback) }
    }

    // --- Broadcast para cambios en el sensor GPS ---
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent?.action == LocationManager.PROVIDERS_CHANGED_ACTION) {
                    val ahoraActivo = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                    if (ahoraActivo != gpsActivo) {
                        gpsActivo = ahoraActivo
                    }
                }
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION), Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION))
        }
        onDispose { context.unregisterReceiver(receiver) }
    }

    // --- Gestión de Permisos ---
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) viewModel.setErrorUbicacion("Se necesita permiso de ubicación.")
    }
    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    // --- Interfaz de Usuario ---
    Box(modifier = Modifier.fillMaxSize()) {
        when {
            uiState.isLoading -> {
                PantallaDeCarga(mensajeEstado = "Cargando tu perfil...")
            }
            uiState.perfil == null -> {
                Text(
                    "No hay perfil. Inicia sesión nuevamente.",
                    modifier = Modifier.align(Alignment.Center).padding(16.dp)
                )
            }
            uiState.errorUbicacion != null -> {
                Column(
                    modifier = Modifier.align(Alignment.Center).padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Error: ${uiState.errorUbicacion}")
                    Button(onClick = { viewModel.refrescarDatos() }) { Text("Reintentar") }
                }
            }
            else -> {
                AndroidView(
                    factory = { _ ->
                        (mapView.parent as? android.view.ViewGroup)?.removeView(mapView)
                        mapView.apply {
                            setZoomLevel(18)
                            isClickable = true
                            isFocusable = true
                            setZoomLevelMin(18)
                            setZoomLevelMax(19)

                            // Asegurar presencia de las capas base
                            downloadLayer?.let { layer ->
                                if (!layerManager.layers.contains(layer)) {
                                    layerManager.layers.add(0, layer)
                                }
                                layer.onResume() // Forzar inicio de descarga
                            }
                            userLocationCircle?.let { circle ->
                                if (!layerManager.layers.contains(circle)) {
                                    layerManager.layers.add(circle)
                                }
                            }

                            uiState.ubicacionActual?.let { (lat, lon) ->
                                model.mapViewPosition.setCenter(LatLong(lat, lon))
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                    update = { map ->
                        val safeMap = map as? SafeMapsforgeView ?: return@AndroidView
                        if (!safeMap.isFullyReady) return@AndroidView

                        val layers = safeMap.layerManager.layers

                        downloadLayer?.let {
                            if (!layers.contains(it)) layers.add(0, it)
                            it.onResume()
                        }
                        userLocationCircle?.let {
                            if (!layers.contains(it)) {
                                val index = if (layers.contains(downloadLayer)) 1 else 0
                                layers.add(index, it)
                            }
                        }

                        safeMap.invalidate()
                    }
                )

                // Header de Perfil
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 40.dp, start = 16.dp, end = 16.dp)
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                    tonalElevation = 4.dp,
                    shadowElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(60.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                .clickable { mostrandoPerfil = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccountCircle,
                                contentDescription = "Avatar",
                                modifier = Modifier.size(50.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = uiState.perfil?.nombre_usuario ?: "Usuario",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(onClick = {
                                    MapStateHolder.clear() // Limpiar recursos del mapa al cerrar sesión
                                    viewModel.logout(onLogout)
                                }) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                                        contentDescription = "Cerrar sesión",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }

                            val miedosDerrotados = uiState.progresoMiedos.values.count { it <= 0 }
                            val totalMiedos = uiState.perfil?.miedos?.size ?: 0

                            Text(
                                text = "Emociones derrotadas $miedosDerrotados/$totalMiedos",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Banner GPS desactivado
                AnimatedVisibility(
                    visible = !gpsActivo,
                    enter = expandVertically(),
                    exit = shrinkVertically(),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 130.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 32.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.Red.copy(alpha = 0.8f))
                            .padding(vertical = 4.dp, horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationOff,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "GPS desactivado",
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }

                // Botón centrar
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(end = 16.dp),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        FloatingActionButton(
                            onClick = {
                                if (gpsActivo) {
                                    uiState.ubicacionActual?.let { (lat, lon) ->
                                        mapView.model.mapViewPosition.setCenter(LatLong(lat, lon))
                                    }
                                } else {
                                    Toast.makeText(context, "Activa el GPS", Toast.LENGTH_SHORT).show()
                                }
                            },
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.size(56.dp)
                        ) {
                            Icon(Icons.Default.MyLocation, contentDescription = "Centrar")
                        }
                        Text(
                            "Centrar",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(top = 4.dp),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }

                // Botón Check-in
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box {
                        FloatingActionButton(
                            onClick = {
                                if (!gpsActivo) {
                                    Toast.makeText(context, "GPS requerido", Toast.LENGTH_SHORT).show()
                                } else if (uiState.checkinHoyRealizado) {
                                    showCheckinRealizadoDialog = true
                                } else {
                                    onCheckinClick()
                                }
                            },
                            containerColor = if (uiState.checkinHoyRealizado)
                                MaterialTheme.colorScheme.tertiaryContainer
                            else MaterialTheme.colorScheme.primaryContainer,
                            shape = CircleShape,
                            modifier = Modifier.size(80.dp)
                        ) {
                            Icon(
                                Icons.Default.EmojiEmotions,
                                contentDescription = "Check-in",
                                modifier = Modifier.size(40.dp)
                            )
                        }
                        if (uiState.checkinHoyRealizado) {
                            Surface(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(24.dp),
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.tertiary
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Completado",
                                    modifier = Modifier.padding(4.dp),
                                    tint = MaterialTheme.colorScheme.onTertiary
                                )
                            }
                        }
                    }
                    Text(
                        if (uiState.checkinHoyRealizado) "Check-in completado"
                        else "Check-in diario",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(top = 8.dp),
                        fontWeight = FontWeight.Bold
                    )
                }

                // Diálogo emergente personalizado si el Check-in ya fue realizado hoy
                if (showCheckinRealizadoDialog) {
                    AlertDialog(
                        onDismissRequest = { showCheckinRealizadoDialog = false },
                        icon = {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        title = {
                            Text(
                                text = "¡Check-in ya realizado!",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        },
                        text = {
                            Text(
                                text = "Ya has completado tu Check-in diario de hoy. Vuelve mañana para registrar cómo te sientes y continuar tu progreso.",
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        },
                        confirmButton = {
                            TextButton(
                                onClick = { showCheckinRealizadoDialog = false }
                            ) {
                                Text("Entendido", fontWeight = FontWeight.Bold)
                            }
                        },
                        shape = RoundedCornerShape(24.dp),
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 6.dp
                    )
                }

                // Overlay de Perfil
                AnimatedVisibility(
                    visible = mostrandoPerfil,
                    enter = fadeIn() + slideInVertically { it },
                    exit = fadeOut() + slideOutVertically { it }
                ) {
                    ProfileOverlay(
                        uiState = uiState,
                        viewModel = viewModel,
                        onClose = { mostrandoPerfil = false },
                        onUpdateName = { viewModel.actualizarNombreUsuario(it) }
                    )
                }

                // Pantalla de carga inicial del mapa (tiles + ubicación + monstruos)
                if (mostrarCarga) {
                    PantallaDeCarga(mensajeEstado = "Preparando el mapa y tus emociones...")
                }
            }
        }
    }
}

@Composable
fun ProfileOverlay(
    uiState: MapaUiState,
    viewModel: MapaViewModel,
    onClose: () -> Unit,
    onUpdateName: (String) -> Unit
) {
    var editandoNombre by remember { mutableStateOf(false) }
    var nuevoNombre by remember { mutableStateOf(uiState.perfil?.nombre_usuario ?: "") }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(40.dp))

            // Avatar y Nombre
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.AccountCircle,
                    contentDescription = null,
                    modifier = Modifier.size(80.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (editandoNombre) {
                OutlinedTextField(
                    value = nuevoNombre,
                    onValueChange = { nuevoNombre = it },
                    label = { Text("Nombre de usuario") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        IconButton(onClick = {
                            onUpdateName(nuevoNombre)
                            editandoNombre = false
                        }) {
                            Icon(Icons.Default.Check, contentDescription = "Guardar")
                        }
                    }
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = uiState.perfil?.nombre_usuario ?: "Usuario",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = { editandoNombre = true }) {
                        Icon(Icons.Default.Edit, contentDescription = "Editar nombre", modifier = Modifier.size(20.dp))
                    }
                }
            }

            Text(
                text = uiState.emailUsuario,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "Tus Emociones",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Start)
            )

            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                val miedos = uiState.perfil?.miedos ?: emptyList()
                items(miedos) { miedo ->
                    MiedoProgressItem(
                        tipo = miedo.tipo,
                        nombre = viewModel.nombreAmigable(miedo.tipo),
                        progreso = uiState.progresoMiedos[miedo.tipo] ?: 100
                    )
                }
            }

            // Botón de Cerrar (X)
            Box(
                modifier = Modifier
                    .padding(bottom = 16.dp)
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .clickable { onClose() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Cerrar",
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}

@Composable
fun MiedoProgressItem(tipo: String, nombre: String, progreso: Int) {
    val color = when (tipo.lowercase()) {
        "sombra_social" -> Color(0xFF9C27B0)
        "nube_tormenta" -> Color(0xFF2196F3)
        "niebla_gris" -> Color(0xFF607D8B)
        "reloj_tembloroso" -> Color(0xFFFF9800)
        else -> MaterialTheme.colorScheme.primary
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = nombre, fontWeight = FontWeight.Medium)
            Text(text = "$progreso HP", style = MaterialTheme.typography.bodySmall)
        }
        Spacer(modifier = Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { progreso / 100f },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = color,
            trackColor = color.copy(alpha = 0.2f)
        )
    }
}

private fun calcularDistancia(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6371e3
    val phi1 = Math.toRadians(lat1)
    val phi2 = Math.toRadians(lat2)
    val deltaPhi = Math.toRadians(lat2 - lat1)
    val deltaLambda = Math.toRadians(lon2 - lon1)
    val a = (Math.sin(deltaPhi / 2) * Math.sin(deltaPhi / 2) +
            Math.cos(phi1) * Math.cos(phi2) *
            Math.sin(deltaLambda / 2) * Math.sin(deltaLambda / 2)).coerceIn(0.0, 1.0)
    return r * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
}
