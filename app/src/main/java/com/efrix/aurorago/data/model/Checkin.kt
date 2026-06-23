package com.efrix.aurorago.data.model

// Renombrado para evitar conflictos con SupabaseModels.kt
data class CheckinLegacy(
    val fecha: String,            // YYYY-MM-DD
    val emociones: List<EmocionCheckinLegacy> = emptyList(),
    val nota: String = ""
)

data class EmocionCheckinLegacy(
    val tipo: String,             // "sombra_social", "nube_tormenta", ...
    val intensidadSentida: Int    // 1..5
)
