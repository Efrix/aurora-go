package com.efrix.aurorago.util

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.efrix.aurorago.BuildConfig
import com.efrix.aurorago.data.repository.AuthRepository
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class LocationWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    private val authRepository = AuthRepository
    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(applicationContext)

    private fun logD(tag: String, message: String) {
        if (BuildConfig.DEBUG) Log.d(tag, message)
    }

    private fun logE(tag: String, message: String, e: Exception? = null) {
        if (BuildConfig.DEBUG) Log.e(tag, message, e)
    }

    private fun logW(tag: String, message: String) {
        if (BuildConfig.DEBUG) Log.w(tag, message)
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val cts = CancellationTokenSource()
        try {
            logD(TAG, "Iniciando actualizacion de ubicacion en background")

            val location = fusedLocationClient.getCurrentLocation(
                Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                cts.token
            ).await()

            if (location != null) {
                logD(TAG, "Ubicacion obtenida")
                actualizarMiedosEnBackground(location.latitude, location.longitude)
                Result.success()
            } else {
                logW(TAG, "No se pudo obtener la ubicacion actual")
                Result.retry()
            }
        } catch (e: SecurityException) {
            logE(TAG, "Permisos de ubicacion revocados o no otorgados", e)
            Result.failure()
        } catch (e: Exception) {
            logE(TAG, "Error inesperado en LocationWorker", e)
            Result.retry()
        } finally {
            cts.cancel()
        }
    }

    private suspend fun actualizarMiedosEnBackground(lat: Double, lon: Double) {
        try {
            logD(TAG, "Actualizando miedos en background")
            authRepository.actualizarUbicacionBackground(lat, lon)
        } catch (e: Exception) {
            logE(TAG, "Error actualizando miedos", e)
        }
    }

    companion object {
        private const val TAG = "LocationWorker"
        private const val WORK_NAME = "location_updates_background"
        private const val INTERVAL_MINUTES = 15L

        fun scheduleLocationUpdates(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val locationWork = PeriodicWorkRequestBuilder<LocationWorker>(
                INTERVAL_MINUTES,
                TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setInitialDelay(5, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                locationWork
            )

            if (BuildConfig.DEBUG) Log.d(TAG, "Trabajo periodico programado")
        }

        fun cancelLocationUpdates(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            if (BuildConfig.DEBUG) Log.d(TAG, "Trabajo de ubicacion cancelado")
        }
    }
}
