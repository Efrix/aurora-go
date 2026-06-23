package com.efrix.aurorago.ui.screens.reto

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class RetoViewModelFactory(
    private val tipoMiedo: String,
    private val miedoId: String
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(RetoViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return RetoViewModel(tipoMiedo, miedoId) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
