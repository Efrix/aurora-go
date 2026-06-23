package com.efrix.aurorago.ui.screens.checkin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class CheckinViewModelFactory : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CheckinViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return CheckinViewModel() as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
