package com.efrix.aurorago.data.model

open class Miedo(
    open val tipo: String,
    open val intensidad: Int,
    open val contexto: String = ""
)