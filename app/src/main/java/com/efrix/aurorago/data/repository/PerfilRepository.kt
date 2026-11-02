package com.efrix.aurorago.data.repository

import android.content.Context
import com.efrix.aurorago.data.model.Checkin
import com.efrix.aurorago.data.model.PerfilLegacy
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKey
import java.io.File
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PerfilRepository(private val context: Context) {
    private val gson = Gson()
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val perfilFile: File
        get() = File(context.filesDir, "perfil.json")

    private val checkinFile: File
        get() = File(context.filesDir, "checkins.json")

    private fun getEncryptedFile(file: File): EncryptedFile {
        return EncryptedFile.Builder(
            context,
            file,
            masterKey,
            EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
        ).build()
    }

    fun guardarPerfil(perfil: PerfilLegacy) {
        try {
            val json = gson.toJson(perfil)
            val encryptedFile = getEncryptedFile(perfilFile)
            encryptedFile.openFileOutput().use { output ->
                output.write(json.toByteArray(Charsets.UTF_8))
            }
        } catch (_: Exception) {
            // Si falla el cifrado, intentar con archivo normal como fallback
            try {
                perfilFile.writeText(gson.toJson(perfil))
            } catch (_: Exception) {
            }
        }
    }

    fun cargarPerfil(): PerfilLegacy? {
        val json = leerContenido(perfilFile) ?: return null
        return try {
            val perfil = gson.fromJson(json, PerfilLegacy::class.java) ?: return null
            @Suppress("SENSELESS_COMPARISON")
            if (perfil.progreso == null) {
                perfil.progreso = mutableMapOf()
            }
            perfil
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun guardarProgreso(miedoId: String, nuevoHp: Int) {
        val perfil = cargarPerfil() ?: return
        perfil.progreso[miedoId] = nuevoHp
        guardarPerfil(perfil)
    }

    fun cargarProgreso(miedoId: String): Int {
        return cargarPerfil()?.progreso?.get(miedoId) ?: 100
    }

    fun existePerfil(): Boolean = perfilFile.exists()

    fun guardarCheckin(checkin: Checkin) {
        try {
            val checkins = cargarCheckins().toMutableList()
            val indice = checkins.indexOfFirst { it.fecha == checkin.fecha }
            if (indice >= 0) checkins[indice] = checkin
            else checkins.add(checkin)
            val json = gson.toJson(checkins)
            val encryptedFile = getEncryptedFile(checkinFile)
            encryptedFile.openFileOutput().use { output ->
                output.write(json.toByteArray(Charsets.UTF_8))
            }
        } catch (_: Exception) {
            // Fallback sin cifrar
            try {
                val checkins = cargarCheckins().toMutableList()
                val indice = checkins.indexOfFirst { it.fecha == checkin.fecha }
                if (indice >= 0) checkins[indice] = checkin
                else checkins.add(checkin)
                checkinFile.writeText(gson.toJson(checkins))
            } catch (_: Exception) {
            }
        }
    }

    fun cargarCheckinHoy(): Checkin? {
        val hoy = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        return cargarCheckins().find { it.fecha == hoy }
    }

    private fun cargarCheckins(): List<Checkin> {
        val json = leerContenido(checkinFile) ?: return emptyList()
        return try {
            val tipo = object : TypeToken<List<Checkin>>() {}.type
            gson.fromJson(json, tipo) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Lee el contenido de un archivo intentando primero con EncryptedFile,
     * y si falla (migración desde archivo sin cifrar), lee con File normal
     * y migra automáticamente al formato cifrado.
     */
    private fun leerContenido(file: File): String? {
        if (!file.exists()) return null

        // Intentar leer con cifrado
        try {
            val encryptedFile = getEncryptedFile(file)
            val input = encryptedFile.openFileInput()
            val bytes = input.readBytes()
            input.close()
            val content = String(bytes, Charsets.UTF_8)
            if (content.isNotBlank()) return content
        } catch (_: Exception) {
            // Archivo no estaba cifrado, leer normal y migrar
        }

        // Fallback: leer sin cifrar y migrar
        return try {
            val content = file.readText()
            if (content.isNotBlank()) {
                // Migrar a cifrado
                try {
                    val encryptedFile = getEncryptedFile(file)
                    encryptedFile.openFileOutput().use { output ->
                        output.write(content.toByteArray(Charsets.UTF_8))
                    }
                    // Eliminar archivo sin cifrar después de migrar
                    file.delete()
                } catch (_: Exception) {
                }
                content
            } else null
        } catch (_: Exception) {
            null
        }
    }

    fun obtenerFactorSpawn(tipoMiedo: String): Float {
        val checkin = cargarCheckinHoy() ?: return 1.0f
        val intensidad = checkin.respuestas[tipoMiedo]
        return if (intensidad != null) {
            1.0f + (intensidad - 3) * 0.3f
        } else 1.0f
    }
}
