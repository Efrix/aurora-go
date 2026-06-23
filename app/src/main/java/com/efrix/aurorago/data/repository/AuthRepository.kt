package com.efrix.aurorago.data.repository

import android.util.Log
import com.efrix.aurorago.data.model.Checkin
import com.efrix.aurorago.data.model.Perfil
import com.efrix.aurorago.data.model.ProgresoMiedo
import com.efrix.aurorago.data.supabase.SupabaseClient
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.gotrue.providers.builtin.Email
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AuthRepository {
    private val supabase = SupabaseClient.instance
    private val _currentUser = MutableStateFlow(supabase.auth.currentSessionOrNull())
    val currentUser: StateFlow<io.github.jan.supabase.gotrue.user.UserSession?> = _currentUser
    
    private val _progressListeners = mutableListOf<(Map<String, Int>) -> Unit>()

    suspend fun login(email: String, password: String): Result<Unit> {
        return try {
            supabase.auth.signInWith(Email) {
                this.email = email
                this.password = password
            }
            _currentUser.value = supabase.auth.currentSessionOrNull()
            Log.d("AuthRepository", "Login exitoso: ${_currentUser.value?.user?.id}")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("AuthRepository", "Error en login", e)
            Result.failure(e)
        }
    }
    
    fun addProgressListener(listener: (Map<String, Int>) -> Unit) {
        _progressListeners.add(listener)
    }
    
    fun removeProgressListener(listener: (Map<String, Int>) -> Unit) {
        _progressListeners.remove(listener)
    }
    
    private fun notifyProgressListeners(nuevoProgreso: Map<String, Int>) {
        _progressListeners.forEach { listener ->
            listener(nuevoProgreso)
        }
    }

    suspend fun logout() {
        try {
            supabase.auth.signOut()
        } catch (e: Exception) {
            // Manejar específicamente el caso donde la sesión ya no existe en el servidor
            if (e.message?.contains("session_id claim", ignoreCase = true) == true) {
                Log.w("AuthRepository", "La sesión ya había expirado o no existía en el servidor, limpiando localmente.")
            } else {
                Log.e("AuthRepository", "Error durante signOut (posiblemente sesión ya expirada)", e)
            }
        } finally {
            // Asegurarnos de limpiar el estado local
            try {
                supabase.auth.clearSession()
            } catch (e: Exception) {
                // Ignorar si falla clearSession
            }
            _currentUser.value = null
        }
    }

    suspend fun getPerfil(): Perfil? {
        val session = supabase.auth.currentSessionOrNull()
        val userId = session?.user?.id ?: run {
            Log.e("AuthRepository", "No hay sesión activa para getPerfil")
            return null
        }
        
        Log.d("AuthRepository", "Buscando perfil para userId: $userId")
        
        return try {
            val response = supabase.postgrest["perfiles"]
                .select {
                    filter {
                        eq("id", userId)
                    }
                }
            
            val perfil = response.decodeSingle<Perfil>()
            Log.d("AuthRepository", "Perfil decodificado exitosamente: ${perfil.nombre_usuario}")
            perfil
        } catch (e: Exception) {
            Log.e("AuthRepository", "Error al obtener/decodificar perfil", e)
            null
        }
    }

    suspend fun actualizarNombreUsuario(nuevoNombre: String): Result<Unit> {
        val userId = supabase.auth.currentSessionOrNull()?.user?.id ?: return Result.failure(Exception("No session"))
        return try {
            supabase.postgrest["perfiles"].update(
                mapOf("nombre_usuario" to nuevoNombre)
            ) {
                filter {
                    eq("id", userId)
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("AuthRepository", "Error actualizando nombre de usuario", e)
            Result.failure(e)
        }
    }

    fun getEmail(): String {
        return supabase.auth.currentSessionOrNull()?.user?.email ?: ""
    }

    suspend fun getProgresoMiedos(): Map<String, Int> {
        val userId = supabase.auth.currentSessionOrNull()?.user?.id ?: return emptyMap()
        return try {
            val response = supabase.postgrest["progreso_miedos"]
                .select {
                    filter {
                        eq("usuario_id", userId)
                    }
                }
            val progresos = response.decodeList<ProgresoMiedo>()
            progresos.associate { it.miedo_id to it.hp_restante }
        } catch (e: Exception) {
            Log.e("AuthRepository", "Error en getProgresoMiedos", e)
            emptyMap()
        }
    }

    suspend fun actualizarProgreso(miedoId: String, hpRestante: Int, onProgressUpdated: (() -> Unit)? = null) {
        val userId = supabase.auth.currentSessionOrNull()?.user?.id ?: return
        try {
            // Usamos upsert para que si no existe el registro de progreso para ese miedo lo cree
            supabase.postgrest["progreso_miedos"].upsert(
                mapOf(
                    "usuario_id" to userId,
                    "miedo_id" to miedoId,
                    "hp_restante" to hpRestante,
                    "updated_at" to SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.getDefault()).format(Date())
                )
            )
            // Notificar a los listeners que el progreso ha sido actualizado
            onProgressUpdated?.invoke()
            
            // Obtener el progreso actualizado y notificar a los listeners registrados
            val nuevoProgreso = getProgresoMiedos()
            notifyProgressListeners(nuevoProgreso)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun guardarCheckin(fecha: String, respuestas: Map<String, Int>, nota: String) {
        val userId = supabase.auth.currentSessionOrNull()?.user?.id ?: return
        try {
            // Usamos upsert por si el usuario actualiza su checkin del mismo día
            supabase.postgrest["checkins"].upsert(
                mapOf(
                    "usuario_id" to userId,
                    "fecha" to fecha,
                    "respuestas" to respuestas,
                    "nota" to nota
                )
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun obtenerCheckinHoy(fecha: String): Map<String, Int>? {
        val userId = supabase.auth.currentSessionOrNull()?.user?.id ?: return null
        return try {
            val response = supabase.postgrest["checkins"]
                .select {
                    filter {
                        eq("usuario_id", userId)
                        eq("fecha", fecha)
                    }
                }
            val checkin = response.decodeSingleOrNull<Checkin>()
            checkin?.respuestas
        } catch (e: Exception) {
            null
        }
    }

    suspend fun restoreSession() {
        _currentUser.value = supabase.auth.currentSessionOrNull()
    }

    suspend fun actualizarUbicacionBackground(lat: Double, lon: Double) {
        val userId = supabase.auth.currentSessionOrNull()?.user?.id ?: return
        try {
            supabase.postgrest["perfiles"].update(
                mapOf(
                    "latitud" to lat,
                    "longitud" to lon,
                    "updated_at" to SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.getDefault()).format(Date())
                )
            ) {
                filter {
                    eq("id", userId)
                }
            }
            Log.d("AuthRepository", "Ubicación actualizada en Supabase: $lat, $lon")
        } catch (e: Exception) {
            Log.e("AuthRepository", "Error actualizando ubicación en background", e)
        }
    }
}
