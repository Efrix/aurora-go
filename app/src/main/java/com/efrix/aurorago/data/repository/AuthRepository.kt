package com.efrix.aurorago.data.repository

import android.util.Log
import com.efrix.aurorago.BuildConfig
import com.efrix.aurorago.data.model.*
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
                PerfilNombreUpdate(nombre_usuario = nuevoNombre)
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
            val updatePayload = ProgresoMiedoUpdate(
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

        // 1. Guardar o actualizar el checkin de hoy mediante una clase serializable dedicada
        val checkinInsert = CheckinInsert(
            usuario_id = userId,
            fecha = fecha,
            respuestas = respuestas,
            nota = nota
        )
        supabase.postgrest["checkins"].upsert(checkinInsert)

        // 2. Si se ingresó una nota en el cuadro de texto, actualizar el contexto de las emociones en el perfil
        val notaLimpia = nota.trim()
        val perfilActual = getPerfil()
        if (perfilActual != null) {
            val nuevosMiedos = perfilActual.miedos.map { m ->
                val nuevaIntensidad = respuestas[m.tipo] ?: m.intensidad
                m.copy(
                    intensidad = nuevaIntensidad,
                    contexto = if (notaLimpia.isNotEmpty()) notaLimpia else m.contexto
                )
            }.toMutableList()

            // Asegurar registrar emociones en el perfil si no existían previamente
            for ((tipo, intensidad) in respuestas) {
                if (nuevosMiedos.none { it.tipo == tipo }) {
                    nuevosMiedos.add(
                        MiedoData(
                            tipo = tipo,
                            intensidad = intensidad,
                            contexto = notaLimpia
                        )
                    )
                }
            }

            try {
                supabase.postgrest["perfiles"].update(
                    PerfilMiedosUpdate(miedos = nuevosMiedos)
                ) {
                    filter {
                        eq("id", userId)
                    }
                }
            } catch (e: Exception) {
                logE(TAG, "Error actualizando miedos/contexto en perfil al guardar checkin", e)
            }
        }

        // 3. Actualizar HP de cada emoción según la respuesta e intensidad elegida
        val progresoActual = getProgresoMiedos()
        for ((tipo, intensidad) in respuestas) {
            if (intensidad > 0) {
                val hpActual = progresoActual[tipo] ?: 100
                val nuevoHp = if (hpActual <= 0) {
                    // Si estaba derrotado y selecciona una intensidad, "revive" con 100 de vida
                    100
                } else {
                    // Sube 10 HP por cada nivel de intensidad (1 -> +10, 2 -> +20, etc.), máx 100
                    val aumento = intensidad * 10
                    (hpActual + aumento).coerceAtMost(100)
                }
                actualizarProgreso(tipo, nuevoHp)
            }
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

    suspend fun obtenerCheckinObjetoHoy(fecha: String): Checkin? {
        val userId = supabase.auth.currentSessionOrNull()?.user?.id ?: return null
        return try {
            val response = supabase.postgrest["checkins"]
                .select {
                    filter {
                        eq("usuario_id", userId)
                        eq("fecha", fecha)
                    }
                }
            response.decodeSingleOrNull<Checkin>()
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
            val updateData = PerfilUbicacionUpdate(
                latitud = lat,
                longitud = lon,
                updated_at = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.getDefault()).format(Date())
            )
            supabase.postgrest["perfiles"].update(updateData) {
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
        miedos: List<MiedoLegacy>,
        progresoLocal: Map<String, Int>
    ): Result<Unit> {
        val userId = supabase.auth.currentSessionOrNull()?.user?.id
            ?: return Result.failure(Exception("No hay sesion activa"))
        return try {
            val miedosData = miedos.map { m ->
                MiedoData(
                    tipo = m.tipo,
                    intensidad = m.intensidad,
                    contexto = m.contexto
                )
            }
            val perfilImport = PerfilLegacyImport(
                id = userId,
                nombre_usuario = nombreUsuario,
                edad = edad,
                miedos = miedosData
            )
            supabase.postgrest["perfiles"].upsert(
                value = perfilImport,
                onConflict = "id"
            )
            for ((miedoId, hp) in progresoLocal) {
                val progresoUpdate = ProgresoMiedoUpdate(
                    usuario_id = userId,
                    miedo_id = miedoId,
                    hp_restante = hp,
                    updated_at = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.getDefault()).format(Date())
                )
                supabase.postgrest["progreso_miedos"].upsert(
                    value = progresoUpdate,
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
