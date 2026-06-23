package com.efrix.aurorago.ui.screens.mapa

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class MapaViewModelFactory() : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MapaViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MapaViewModel() as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}