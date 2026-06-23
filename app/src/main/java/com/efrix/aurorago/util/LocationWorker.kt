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
import com.efrix.aurorago.data.repository.AuthRepository
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * LocationWorker: Actualiza la ubicación del usuario en segundo plano.
 * Se ejecuta periódicamente para mantener el estado del juego y regenerar monstruos.
 *
 * Mejoras aplicadas:
 * - Uso de getCurrentLocation para mayor fiabilidad en background.
 * - Restricción de red para evitar fallos de conexión.
 * - Manejo de errores mejorado (SecurityException).
 * - Uso de applicationContext para evitar leaks.
 */
class LocationWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    private val authRepository = AuthRepository
    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(applicationContext)

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Iniciando actualización de ubicación en background")

            // getCurrentLocation es más fiable que lastLocation para procesos en background
            val location = fusedLocationClient.getCurrentLocation(
                Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                CancellationTokenSource().token
            ).await()

            if (location != null) {
                Log.d(TAG, "Ubicación obtenida: ${location.latitude}, ${location.longitude}")
                actualizarMiedosEnBackground(location.latitude, location.longitude)
                Result.success()
            } else {
                Log.w(TAG, "No se pudo obtener la ubicación actual")
                // Si falla, WorkManager reintentará según la política de backoff
                Result.retry()
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Permisos de ubicación revocados o no otorgados", e)
            // No tiene sentido reintentar si no hay permisos
            Result.failure()
        } catch (e: Exception) {
            Log.e(TAG, "Error inesperado en LocationWorker", e)
            Result.retry()
        }
    }

    private suspend fun actualizarMiedosEnBackground(lat: Double, lon: Double) {
        try {
            Log.d(TAG, "Actualizando miedos en: $lat, $lon")
            authRepository.actualizarUbicacionBackground(lat, lon)
        } catch (e: Exception) {
            Log.e(TAG, "Error actualizando miedos", e)
        }
    }

    companion object {
        private const val TAG = "LocationWorker"
        private const val WORK_NAME = "location_updates_background"
        private const val INTERVAL_MINUTES = 15L

        /**
         * Programa actualizaciones de ubicación periódicas.
         * Se recomienda llamar esto después del login exitoso en MainActivity.
         */
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

            Log.d(TAG, "Trabajo periódico programado cada $INTERVAL_MINUTES min con conexión a internet")
        }

        /**
         * Cancela las actualizaciones periódicas.
         * Llamar esto al cerrar sesión.
         */
        fun cancelLocationUpdates(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            Log.d(TAG, "Trabajo de ubicación cancelado")
        }
    }
}
