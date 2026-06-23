package com.efrix.aurorago.ui.screens.checkin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.efrix.aurorago.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CheckinViewModel : ViewModel() {
    private val authRepo = AuthRepository
    private val _uiState = MutableStateFlow(CheckinUiState())
    val uiState: StateFlow<CheckinUiState> = _uiState

    data class CheckinUiState(
        val emocionesSeleccionadas: Map<String, Int> = emptyMap(), // tipo -> intensidad 1..5
        val nota: String = "",
        val guardadoExitoso: Boolean = false,
        val isLoading: Boolean = false,
        val error: String? = null
    )

    fun actualizarEmocion(tipo: String, intensidad: Int) {
        val mapa = _uiState.value.emocionesSeleccionadas.toMutableMap()
        mapa[tipo] = intensidad
        _uiState.value = _uiState.value.copy(emocionesSeleccionadas = mapa)
    }

    fun actualizarNota(nota: String) {
        _uiState.value = _uiState.value.copy(nota = nota)
    }

    fun guardarCheckin() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val hoy = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                authRepo.guardarCheckin(
                    fecha = hoy,
                    respuestas = _uiState.value.emocionesSeleccionadas,
                    nota = _uiState.value.nota
                )
                _uiState.value = _uiState.value.copy(guardadoExitoso = true, isLoading = false)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message, isLoading = false)
            }
        }
    }
}
