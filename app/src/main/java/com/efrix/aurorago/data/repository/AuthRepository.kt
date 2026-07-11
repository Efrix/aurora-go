package com.efrix.aurorago.data.repository

import android.util.Log
import com.efrix.aurorago.BuildConfig
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
import java.util.concurrent.CopyOnWriteArrayList

object AuthRepository {
    private val supabase = SupabaseClient.instance
    private val _currentUser = MutableStateFlow(supabase.auth.currentSessionOrNull())
    val currentUser: StateFlow<io.github.jan.supabase.gotrue.user.UserSession?> = _currentUser

    private val _progressListeners = CopyOnWriteArrayList<(Map<String, Int>) -> Unit>()

    private fun logD(tag: String, message: String) {
        if (BuildConfig.DEBUG) Log.d(tag, message)
    }

    private fun logE(tag: String, message: String, e: Exception? = null) {
        if (BuildConfig.DEBUG) Log.e(tag, message, e)
    }

    private fun logW(tag: String, message: String) {
        if (BuildConfig.DEBUG) Log.w(tag, message)
    }

    suspend fun login(email: String, password: String): Result<Unit> {
        return try {
            supabase.auth.signInWith(Email) {
                this.email = email
                this.password = password
            }
            _currentUser.value = supabase.auth.currentSessionOrNull()
            logD(TAG, "Login exitoso")
            Result.success(Unit)
        } catch (e: Exception) {
            logE(TAG, "Error en login", e)
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
            if (e.message?.contains("session_id claim", ignoreCase = true) == true) {
                logW(TAG, "La sesion ya habia expirado o no existia en el servidor")
            } else {
                logE(TAG, "Error durante signOut", e)
            }
        } finally {
            try {
                supabase.auth.clearSession()
            } catch (_: Exception) {
            }
            _currentUser.value = null
        }
    }

    suspend fun getPerfil(): Perfil? {
        val session = supabase.auth.currentSessionOrNull()
        val userId = session?.user?.id ?: run {
            logE(TAG, "No hay sesion activa para getPerfil")
            return null
        }

        logD(TAG, "Buscando perfil para userId")

        return try {
            val response = supabase.postgrest["perfiles"]
                .select {
                    filter {
                        eq("id", userId)
                    }
                }

            val perfil = response.decodeSingle<Perfil>()
            logD(TAG, "Perfil decodificado exitosamente")
            perfil
        } catch (e: Exception) {
            logE(TAG, "Error al obtener/decodificar perfil", e)
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
            logE(TAG, "Error actualizando nombre de usuario", e)
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
            logD(TAG, "Progreso obtenido de DB: ${progresos.size} registros")
            progresos.associate { it.miedo_id to it.hp_restante }
        } catch (e: Exception) {
            logE(TAG, "Error en getProgresoMiedos", e)
            emptyMap()
        }
    }

    suspend fun actualizarProgreso(miedoId: String, hpRestante: Int, onProgressUpdated: (() -> Unit)? = null): Boolean {
        val userId = supabase.auth.currentSessionOrNull()?.user?.id ?: return false
        logD(TAG, "Intentando actualizar progreso: miedo=$miedoId, hp=$hpRestante")
        try {
            val updatePayload = com.efrix.aurorago.data.model.ProgresoMiedoUpdate(
                usuario_id = userId,
                miedo_id = miedoId,
                hp_restante = hpRestante,
                updated_at = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.getDefault()).format(Date())
            )

            supabase.postgrest["progreso_miedos"].upsert(
                value = updatePayload,
                onConflict = "usuario_id,miedo_id"
            )

            logD(TAG, "Upsert exitoso en Supabase para $miedoId")

            onProgressUpdated?.invoke()

            val nuevoProgreso = getProgresoMiedos()
            notifyProgressListeners(nuevoProgreso)

            return hpRestante <= 0
        } catch (e: Exception) {
            logE(TAG, "Error al actualizar progreso en Supabase", e)
            return false
        }
    }

    suspend fun guardarCheckin(fecha: String, respuestas: Map<String, Int>, nota: String) {
        val userId = supabase.auth.currentSessionOrNull()?.user?.id
            ?: throw Exception("No hay sesion activa")
        supabase.postgrest["checkins"].upsert(
            mapOf(
                "usuario_id" to userId,
                "fecha" to fecha,
                "respuestas" to respuestas,
                "nota" to nota
            )
        )
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
        try {
            val session = supabase.auth.currentSessionOrNull()
            if (session != null) {
                logD(TAG, "Sesion encontrada, validando con servidor")
                try {
                    val userId = session.user?.id ?: return
                    supabase.postgrest["perfiles"]
                        .select { filter { eq("id", userId) } }
                    _currentUser.value = supabase.auth.currentSessionOrNull()
                } catch (e: Exception) {
                    logW(TAG, "Sesion invalida o expirada: ${e.message}")
                    supabase.auth.clearSession()
                    _currentUser.value = null
                }
            } else {
                logD(TAG, "No hay sesion guardada")
                _currentUser.value = null
            }
        } catch (e: Exception) {
            logE(TAG, "Error restaurando sesion", e)
            _currentUser.value = null
        }
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
            logD(TAG, "Ubicacion actualizada en Supabase")
        } catch (e: Exception) {
            logE(TAG, "Error actualizando ubicacion en background", e)
        }
    }

    suspend fun importarLegacyASupabase(
        nombreUsuario: String,
        edad: Int?,
        miedos: List<com.efrix.aurorago.data.model.MiedoLegacy>,
        progresoLocal: Map<String, Int>
    ): Result<Unit> {
        val userId = supabase.auth.currentSessionOrNull()?.user?.id
            ?: return Result.failure(Exception("No hay sesion activa"))
        return try {
            val miedosData = miedos.map { m ->
                com.efrix.aurorago.data.model.MiedoData(
                    tipo = m.tipo,
                    intensidad = m.intensidad,
                    contexto = m.contexto
                )
            }
            supabase.postgrest["perfiles"].upsert(
                mapOf(
                    "id" to userId.toString(),
                    "nombre_usuario" to nombreUsuario,
                    "edad" to edad,
                    "miedos" to miedosData
                ),
                onConflict = "id"
            )
            for ((miedoId, hp) in progresoLocal) {
                supabase.postgrest["progreso_miedos"].upsert(
                    mapOf(
                        "usuario_id" to userId.toString(),
                        "miedo_id" to miedoId,
                        "hp_restante" to hp,
                        "updated_at" to SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.getDefault()).format(Date())
                    ),
                    onConflict = "usuario_id,miedo_id"
                )
            }
            logD(TAG, "Perfil legacy importado a Supabase exitosamente")
            Result.success(Unit)
        } catch (e: Exception) {
            logE(TAG, "Error importando perfil legacy a Supabase", e)
            Result.failure(e)
        }
    }

    private const val TAG = "AuthRepository"
}
