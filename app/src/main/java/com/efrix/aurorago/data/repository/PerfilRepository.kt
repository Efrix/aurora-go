package com.efrix.aurorago.data.repository

import android.content.Context
import com.efrix.aurorago.data.model.Checkin
import com.efrix.aurorago.data.model.PerfilLegacy
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PerfilRepository(private val context: Context) {
    private val gson = Gson()
    private val perfilFile: File
        get() = File(context.filesDir, "perfil.json")

    fun guardarPerfil(perfil: PerfilLegacy) {
        val json = gson.toJson(perfil)
        perfilFile.writeText(json)
    }

    fun cargarPerfil(): PerfilLegacy? {
        if (!perfilFile.exists()) return null
        return try {
            val json = perfilFile.readText()
            val perfil = gson.fromJson(json, PerfilLegacy::class.java) ?: return null
            
            // Si progreso es null (porque el JSON no lo trae), lo inicializamos
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

    private val checkinFile: File
        get() = File(context.filesDir, "checkins.json")

    fun guardarCheckin(checkin: Checkin) {
        val checkins = cargarCheckins().toMutableList()
        val indice = checkins.indexOfFirst { it.fecha == checkin.fecha }
        if (indice >= 0) checkins[indice] = checkin
        else checkins.add(checkin)
        val json = gson.toJson(checkins)
        checkinFile.writeText(json)
    }

    fun cargarCheckinHoy(): Checkin? {
        val hoy = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        return cargarCheckins().find { it.fecha == hoy }
    }

    private fun cargarCheckins(): List<Checkin> {
        return try {
            val json = checkinFile.readText()
            val tipo = object : TypeToken<List<Checkin>>() {}.type
            gson.fromJson(json, tipo)
        } catch (e: Exception) {
            emptyList()
        }
    }

    // Calcula un factor de spawn para cada tipo de miedo basado en el check-in de hoy
    fun obtenerFactorSpawn(tipoMiedo: String): Float {
        val checkin = cargarCheckinHoy() ?: return 1.0f
        val intensidad = checkin.respuestas[tipoMiedo]
        return if (intensidad != null) {
            1.0f + (intensidad - 3) * 0.3f  // Centrado en 3: sin cambio, máx 1.6, mín 0.4
        } else 1.0f
    }
}
