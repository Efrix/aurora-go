package com.efrix.aurorago.ui.screens.mapa

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.efrix.aurorago.data.model.MiedoEnMapa
import com.efrix.aurorago.data.model.Perfil
import com.efrix.aurorago.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.*
import kotlin.random.Random

data class MapaUiState(
    val perfil: Perfil? = null,
    val emailUsuario: String = "",
    val progresoMiedos: Map<String, Int> = emptyMap(),
    val miedosEnMapa: List<MiedoEnMapa> = emptyList(),
    val ubicacionActual: Pair<Double, Double>? = null,
    val isLoading: Boolean = true,
    val errorUbicacion: String? = null
)

class MapaViewModel : ViewModel() {
    private val authRepo = AuthRepository
    private val _uiState = MutableStateFlow(MapaUiState())
    val uiState: StateFlow<MapaUiState> = _uiState

    private var ultimaUbicacionGeneracion: Pair<Double, Double>? = null
    private var respuestasCheckin: Map<String, Int> = emptyMap()
    private var isInitialLoadDone = false

    init {
        refrescarDatos()
    }

    fun refrescarDatos() {
        viewModelScope.launch {
            if (!isInitialLoadDone) {
                _uiState.value = _uiState.value.copy(isLoading = true)
            }
            isInitialLoadDone = true

            val perfil = authRepo.getPerfil()
            val email = authRepo.getEmail()
            if (perfil != null) {
                val progreso = authRepo.getProgresoMiedos()
                val hoy = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                respuestasCheckin = authRepo.obtenerCheckinHoy(hoy) ?: emptyMap()

                _uiState.value = _uiState.value.copy(
                    perfil = perfil, 
                    emailUsuario = email,
                    progresoMiedos = progreso,
                    isLoading = false
                )
                
                // Si ya tenemos ubicación, regeneramos para aplicar posibles cambios
                _uiState.value.ubicacionActual?.let { (lat, lon) ->
                    val miedos = generarMiedos(perfil, progreso, lat, lon)
                    _uiState.value = _uiState.value.copy(miedosEnMapa = miedos)
                }
            } else {
                _uiState.value = _uiState.value.copy(isLoading = false, perfil = null, emailUsuario = email)
            }
        }
    }

    fun actualizarNombreUsuario(nuevoNombre: String) {
        viewModelScope.launch {
            val result = authRepo.actualizarNombreUsuario(nuevoNombre)
            if (result.isSuccess) {
                refrescarDatos()
            }
        }
    }

    fun actualizarUbicacion(lat: Double, lng: Double) {
        val perfil = _uiState.value.perfil ?: return
        val progreso = _uiState.value.progresoMiedos
        
        val distanciaDesdeUltimaGeneracion = ultimaUbicacionGeneracion?.let { (uLat, uLng) ->
            calcularDistanciaMetros(uLat, uLng, lat, lng)
        } ?: Double.MAX_VALUE

        val miedos = if (distanciaDesdeUltimaGeneracion > 50.0) {
            ultimaUbicacionGeneracion = Pair(lat, lng)
            generarMiedos(perfil, progreso, lat, lng)
        } else {
            _uiState.value.miedosEnMapa
        }

        _uiState.value = _uiState.value.copy(
            ubicacionActual = Pair(lat, lng),
            miedosEnMapa = miedos,
            errorUbicacion = null
        )
    }
    
    fun onProgresoMiedosActualizado(nuevoProgreso: Map<String, Int>) {
        val nuevosMiedos = _uiState.value.miedosEnMapa.mapNotNull { miedo ->
            val nuevoHp = nuevoProgreso[miedo.tipo] ?: 100
            if (nuevoHp <= 0) {
                null // El monstruo desaparece si ha sido derrotado
            } else if (miedo.hp != nuevoHp) {
                miedo.copy(hp = nuevoHp)
            } else {
                miedo
            }
        }
        _uiState.value = _uiState.value.copy(
            progresoMiedos = nuevoProgreso,
            miedosEnMapa = nuevosMiedos
        )
    }

    fun setErrorUbicacion(mensaje: String) {
        _uiState.value = _uiState.value.copy(errorUbicacion = mensaje)
    }

    private fun generarMiedos(perfil: Perfil, progreso: Map<String, Int>, lat: Double, lon: Double): List<MiedoEnMapa> {
        val lista = mutableListOf<MiedoEnMapa>()
        val random = Random(System.currentTimeMillis())
        val radioMin = 5.0
        val radioMax = 300.0

        for (miedo in perfil.miedos) {
            val factor = obtenerFactorSpawn(miedo.tipo)
            val cantidadBase = when {
                miedo.intensidad >= 9 -> 10
                miedo.intensidad >= 7 -> 7
                miedo.intensidad >= 5 -> 5
                miedo.intensidad >= 3 -> 3
                else -> 2
            }
            val cantidad = max(1, (cantidadBase * factor).toInt())

            repeat(cantidad) { index ->
                val id = "${perfil.nombre_usuario}_${miedo.tipo}_${System.currentTimeMillis()}_$index"
                val hpActual = progreso[miedo.tipo] ?: 100
                
                if (hpActual > 0) {
                    val angulo = random.nextDouble() * 2 * PI
                    // Usamos pow(1.5) para sesgar la distribución hacia valores menores
                    // Esto hace que aparezcan más monstruos cerca del jugador (clustering)
                    val distancia = radioMin + (random.nextDouble().pow(1.5) * (radioMax - radioMin))
                    
                    val offsetLat = (distancia / 111320.0) * cos(angulo)
                    val offsetLon = (distancia / (111320.0 * cos(Math.toRadians(lat)))) * sin(angulo)

                    lista.add(
                        MiedoEnMapa(
                            id = id,
                            tipo = miedo.tipo,
                            latitud = lat + offsetLat,
                            longitud = lon + offsetLon,
                            intensidad = miedo.intensidad,
                            hp = hpActual
                        )
                    )
                }
            }
        }
        return lista
    }
    
    fun actualizarHpMiedoEnMapa(tipoMiedo: String, nuevoHp: Int) {
        val nuevosMiedos = _uiState.value.miedosEnMapa.map { miedo ->
            if (miedo.tipo == tipoMiedo) {
                miedo.copy(hp = nuevoHp)
            } else {
                miedo
            }
        }
        _uiState.value = _uiState.value.copy(miedosEnMapa = nuevosMiedos)
    }

    private fun obtenerFactorSpawn(tipoMiedo: String): Float {
        val intensidad = respuestasCheckin[tipoMiedo] ?: return 1.0f
        return 1.0f + (intensidad - 3) * 0.3f
    }

    fun nombreAmigable(tipo: String): String = when (tipo) {
        "sombra_social" -> "Sombra del Juicio"
        "nube_tormenta" -> "Nube de Tormenta"
        "niebla_gris" -> "Niebla Gris"
        "reloj_tembloroso" -> "Reloj Tembloroso"
        else -> tipo.replaceFirstChar { it.uppercase() }
    }

    private fun calcularDistanciaMetros(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371e3
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val deltaPhi = Math.toRadians(lat2 - lat1)
        val deltaLambda = Math.toRadians(lon2 - lon1)
        val a = sin(deltaPhi / 2) * sin(deltaPhi / 2) +
                cos(phi1) * cos(phi2) *
                sin(deltaLambda / 2) * sin(deltaLambda / 2)
        return r * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    fun logout(onLogout: () -> Unit) {
        viewModelScope.launch {
            authRepo.logout()
            onLogout()
        }
    }
}
