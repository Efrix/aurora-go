package com.efrix.aurorago.data.model

// Renombrado para evitar conflictos con SupabaseModels.kt
data class PerfilLegacy(
    val nombreUsuario: String,
    val edad: Int?,
    val miedos: List<MiedoLegacy>,
    val consentimiento: Boolean,
    val fechaCreacion: String,
    var progreso: MutableMap<String, Int> = mutableMapOf()
)

data class MiedoLegacy(
    val tipo: String,
    val intensidad: Int,
    val contexto: String = ""
)
