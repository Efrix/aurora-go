package com.efrix.aurorago.ui.screens.importarperfil

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.efrix.aurorago.data.model.PerfilLegacy
import com.efrix.aurorago.data.repository.AuthRepository
import com.efrix.aurorago.data.repository.PerfilRepository
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class ImportUiState(
    val perfilCargado: PerfilLegacy? = null,
    val error: String? = null,
    val mensajeExito: String? = null,
    val isLoading: Boolean = false
)

class ImportPerfilViewModel(context: Context) : ViewModel() {
    private val repository = PerfilRepository(context)
    private val authRepo = AuthRepository
    private val gson = Gson()

    private val _uiState = MutableStateFlow(ImportUiState())
    val uiState: StateFlow<ImportUiState> = _uiState

    fun procesarTextoJson(json: String) {
        viewModelScope.launch {
            _uiState.value = ImportUiState(isLoading = true)
            try {
                val perfil = gson.fromJson(json, PerfilLegacy::class.java)
                if (perfil == null || perfil.nombreUsuario.isBlank() || perfil.miedos.isEmpty()) {
                    _uiState.value = ImportUiState(error = "El JSON no tiene un perfil valido.")
                    return@launch
                }
                repository.guardarPerfil(perfil)

                val syncResult = authRepo.importarLegacyASupabase(
                    nombreUsuario = perfil.nombreUsuario,
                    edad = perfil.edad,
                    miedos = perfil.miedos,
                    progresoLocal = perfil.progreso
                )

                val mensaje = if (syncResult.isSuccess) {
                    "Perfil cargado y sincronizado. ¡Bienvenido/a, ${perfil.nombreUsuario}!"
                } else {
                    "Perfil guardado localmente (sync falló: ${syncResult.exceptionOrNull()?.message})."
                }

                _uiState.value = ImportUiState(
                    perfilCargado = perfil,
                    mensajeExito = mensaje
                )
            } catch (e: JsonSyntaxException) {
                _uiState.value = ImportUiState(error = "El texto no es un JSON válido. Revisa el formato.")
            } catch (e: Exception) {
                _uiState.value = ImportUiState(error = "Error inesperado: ${e.message}")
            }
        }
    }

    fun limpiarEstado() {
        _uiState.value = ImportUiState()
    }
}