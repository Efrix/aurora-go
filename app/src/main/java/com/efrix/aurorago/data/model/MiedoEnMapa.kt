package com.efrix.aurorago.data.model

data class MiedoEnMapa(
    val id: String,
    override val tipo: String,
    override val intensidad: Int,
    override val contexto: String = "",
    val latitud: Double,
    val longitud: Double,
    val hp: Int = 100
) : Miedo(tipo, intensidad, contexto)
