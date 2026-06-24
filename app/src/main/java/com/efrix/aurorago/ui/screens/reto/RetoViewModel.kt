package com.efrix.aurorago.ui.screens.reto

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.efrix.aurorago.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

    data class RetoUiState(
        val tipoMiedo: String = "",
        val miedoId: String = "",
        val hpActual: Int = 100,
        val retoCompletado: Boolean = false,
        val miedoDerrotado: Boolean = false,
        val recompensa: String? = null,
        val isLoading: Boolean = true,
        val retoTexto: String = "",
        val opcionesQuiz: List<String> = emptyList(),
        val respuestaCorrectaIdx: Int = 0,
        val duracionRespiracion: Int = 30,
        val contexto: String = "",
        val emocionesDerrotadas: Int = 0
    )

class RetoViewModel(tipoMiedo: String, miedoId: String) : ViewModel() {
    private val authRepo = AuthRepository
    private val _uiState = MutableStateFlow(RetoUiState())
    val uiState: StateFlow<RetoUiState> = _uiState

    init {
        viewModelScope.launch {
            val perfil = authRepo.getPerfil()
            val progreso = authRepo.getProgresoMiedos()
            val hoy = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            val checkinHoy = authRepo.obtenerCheckinHoy(hoy)
            
            val hpGuardado = progreso[tipoMiedo] ?: 100
            val miedoBase = perfil?.miedos?.find { it.tipo == tipoMiedo }
            val contexto = miedoBase?.contexto ?: ""
            val intensidadCheckin = checkinHoy?.get(tipoMiedo) ?: 3

            val retoConfig = generarReto(tipoMiedo, contexto, intensidadCheckin)

            _uiState.value = _uiState.value.copy(
                tipoMiedo = tipoMiedo,
                miedoId = miedoId,
                hpActual = hpGuardado,
                isLoading = false,
                contexto = contexto,
                retoTexto = retoConfig.texto,
                opcionesQuiz = retoConfig.opciones,
                respuestaCorrectaIdx = retoConfig.correctaIdx,
                duracionRespiracion = retoConfig.duracionRespiracion
            )
        }
    }

    fun completarReto(onProgressUpdated: (() -> Unit)? = null) {
        viewModelScope.launch {
            val nuevoHp = (_uiState.value.hpActual - 25).coerceAtLeast(0)
            
            // Actualización local inmediata para respuesta visual rápida
            _uiState.value = _uiState.value.copy(
                hpActual = nuevoHp,
                retoCompletado = true
            )

            // Actualizar en el repositorio (Supabase)
            val fueDerrotado = authRepo.actualizarProgreso(_uiState.value.tipoMiedo, nuevoHp, onProgressUpdated)
            
            if (fueDerrotado) {
                // Pequeña pausa para que el usuario vea el HP en 0 antes del mensaje de victoria
                delay(1000)
                _uiState.value = _uiState.value.copy(
                    miedoDerrotado = true,
                    recompensa = "¡Has derrotado a ${nombreAmigable(_uiState.value.tipoMiedo)}! Ganaste la Medalla de la Valentía."
                )
            }
        }
    }

    data class RetoConfig(
        val texto: String,
        val opciones: List<String> = emptyList(),
        val correctaIdx: Int = 0,
        val duracionRespiracion: Int = 30
    )

    private fun generarReto(tipo: String, contexto: String, intensidadCheckin: Int): RetoConfig {
        val intensidadEfectiva = (intensidadCheckin + 2) / 2
        return when (tipo) {
            "sombra_social" -> {
                val situacion = if (contexto.isNotBlank()) "Cuando $contexto" else "En una situación social"
                val pregunta = "$situacion, ¿qué pensamiento te ayuda más?"
                val opciones = listOf(
                    "Voy a hacerlo bien, soy capaz.",
                    "Seguro que me juzgan, mejor no hablar.",
                    "Si me equivoco no pasa nada, todos cometemos errores."
                )
                RetoConfig(pregunta, opciones, correctaIdx = 0)
            }
            "nube_tormenta" -> {
                val situacion = if (contexto.isNotBlank()) "cuando piensas en '$contexto'" else "en momentos de frustración"
                val texto = "Respira profundamente para calmar la tormenta interior $situacion."
                RetoConfig(texto, duracionRespiracion = 30 + intensidadEfectiva * 10)
            }
            "niebla_gris" -> {
                val texto = if (contexto.isNotBlank())
                    "Aunque te sientas triste por '$contexto', busca algo a tu alrededor que te dé un rayo de gratitud y tómale una foto."
                else
                    "Aunque ahora reine la niebla, toma una foto de algo que te haga sentir agradecido/a."
                RetoConfig(texto)
            }
            "reloj_tembloroso" -> {
                val texto = if (contexto.isNotBlank())
                    "Escribe un pequeño logro que hayas tenido a pesar de sentir '$contexto'."
                else
                    "Escribe un logro reciente que te haga sentir orgulloso/a."
                RetoConfig(texto)
            }
            else -> RetoConfig("Enfrenta este miedo con valentía.")
        }
    }

    fun nombreAmigable(tipo: String): String = when (tipo) {
        "sombra_social" -> "Sombra del Juicio"
        "nube_tormenta" -> "Nube de Tormenta"
        "niebla_gris" -> "Niebla Gris"
        "reloj_tembloroso" -> "Reloj Tembloroso"
        else -> "Miedo Desconocido"
    }
}

