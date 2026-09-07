package com.example.worker

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.SismoAlertaApp
import com.example.service.SismoFirebaseMessagingService
import java.util.concurrent.TimeUnit

/**
 * Worker periódico de respaldo de WorkManager (Fail-Safe Local).
 * Se ejecuta cada 15-30 minutos únicamente si hay conectividad a Internet,
 * respetando las políticas de batería y Doze Mode de Android.
 */
class SeismicCheckWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val WORK_NAME = "seismic_periodic_failsafe_worker"

        fun schedulePeriodicCheck(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(false)
                .build()

            val workRequest = PeriodicWorkRequestBuilder<SeismicCheckWorker>(
                15, TimeUnit.MINUTES,
                5, TimeUnit.MINUTES // Flex interval
            )
                .setConstraints(constraints)
                .build()

            try {
                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    workRequest
                )
                Log.i("SeismicWorker", "WorkManager de respaldo programado exitosamente cada 15 min ✓")
            } catch (e: Exception) {
                Log.w("SeismicWorker", "No se pudo programar WorkManager (entorno restringido o test runner): ${e.message}")
            }
        }
    }

    override suspend fun doWork(): Result {
        Log.d("SeismicWorker", "Ejecutando verificación periódica de cortesía de sismos...")
        return try {
            val app = applicationContext as? SismoAlertaApp ?: return Result.success()
            val location = app.locationRepository.currentLocation.value

            val prefs = applicationContext.getSharedPreferences("sismo_notified_events_cache", Context.MODE_PRIVATE)
            val notifiedIds = prefs.getStringSet("notified_ids", emptySet()) ?: emptySet()

            // Consultar feeds en segundo plano de forma no invasiva
            val newEvents = app.seismicOnlineFeedRepository.checkForNewSeismicEvents(location, notifiedIds)

            val settingsRepo = app.settingsRepository
            val filter = settingsRepo.getSeismicAlertFilter()

            for (event in newEvents) {
                if (filter.shouldAlert(event.magnitude, event.distanceKm)) {
                    Log.i("SeismicWorker", "Nuevo sismo detectado vía WorkManager: M ${event.magnitude} en ${event.epicenter}")
                    // Invocar notificación y sonido
                    com.example.service.SeismicMonitoringService.sendStrongSeismicPushNotification(
                        context = applicationContext,
                        title = "🚨 ¡SISMO CONFIRMADO: M ${event.magnitude}!",
                        message = "${event.epicenter} (a ${event.distanceKm.toInt()} km). Profundidad: ${event.depthKm.toInt()} km."
                    )
                    val updatedIds = notifiedIds.toMutableSet()
                    updatedIds.add(event.eventId)
                    prefs.edit().putStringSet("notified_ids", updatedIds).apply()
                }
            }

            Result.success()
        } catch (e: Exception) {
            Log.w("SeismicWorker", "Error en SeismicCheckWorker: ${e.message}")
            Result.retry()
        }
    }
}
