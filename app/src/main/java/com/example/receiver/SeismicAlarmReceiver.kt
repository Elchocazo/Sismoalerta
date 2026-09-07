package com.example.receiver

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import com.example.SismoAlertaApp
import com.example.service.SeismicMonitoringService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Receptor de Alarma 24/7 para comprobación periódica y resiliente de sismos en segundo plano.
 * Funciona de forma inmune a la suspensión del sistema operativo y al cierre de la aplicación.
 */
class SeismicAlarmReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_CHECK_SEISMIC_FEEDS = "com.example.ACTION_CHECK_SEISMIC_FEEDS"
        private const val ALARM_REQUEST_CODE = 4001
        private const val CHECK_INTERVAL_MS = 15 * 60 * 1000L // 15 minutos (amigable con la batería y Doze Mode)

        /**
         * Programa verificación periódica de cortesía de bajo impacto energético.
         */
        fun schedulePeriodicCheck(context: Context, delayMs: Long = CHECK_INTERVAL_MS) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val intent = Intent(context, SeismicAlarmReceiver::class.java).apply {
                action = ACTION_CHECK_SEISMIC_FEEDS
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            val pendingIntent = PendingIntent.getBroadcast(context, ALARM_REQUEST_CODE, intent, flags)

            val triggerAtMillis = SystemClock.elapsedRealtime() + delayMs

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.ELAPSED_REALTIME_WAKEUP,
                        triggerAtMillis,
                        pendingIntent
                    )
                } else {
                    alarmManager.set(
                        AlarmManager.ELAPSED_REALTIME_WAKEUP,
                        triggerAtMillis,
                        pendingIntent
                    )
                }
            } catch (e: Exception) {
                Log.w("SeismicAlarmReceiver", "Error en schedulePeriodicCheck: ${e.message}")
            }
        }

        fun cancelPeriodicCheck(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val intent = Intent(context, SeismicAlarmReceiver::class.java).apply {
                action = ACTION_CHECK_SEISMIC_FEEDS
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context, ALARM_REQUEST_CODE, intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent)
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val wakeLock = powerManager?.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "SismoAlerta:SeismicAlarmWakeLock"
        )
        wakeLock?.acquire(10000L) // Mantener la CPU despierta máximo 10s para completar la consulta de red

        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val app = context.applicationContext as? SismoAlertaApp
                if (app != null) {
                    val currentLoc = app.locationRepository.currentLocation.value
                    val events = app.seismicOnlineFeedRepository.fetchLiveSeismicReports(currentLoc)

                    val prefs = context.getSharedPreferences("sismo_notified_events_cache", Context.MODE_PRIVATE)
                    val notifiedIds = prefs.getStringSet("notified_ids", emptySet())?.toMutableSet() ?: mutableSetOf()

                    val now = System.currentTimeMillis()
                    var newlyNotified = false

                    for (event in events) {
                        val timeDiff = now - event.timestamp
                        // Permitir un margen de -5 min a 6 horas para tolerar desfases de reloj
                        if (!notifiedIds.contains(event.eventId) && timeDiff in -300000L..(6 * 3600 * 1000L)) {
                            val isNearbyOrFelt = event.distanceKm <= 150.0 && event.magnitude >= 2.5
                            val isSignificantRegional = event.distanceKm <= 350.0 && event.magnitude >= 3.5
                            val isNationalStrong = event.magnitude >= 4.0

                            if (isNearbyOrFelt || isSignificantRegional || isNationalStrong) {
                                SeismicMonitoringService.sendStrongSeismicPushNotification(
                                    context = context,
                                    title = "¡SISMO CONFIRMADO: M ${event.magnitude}!",
                                    message = "${event.epicenter} (a ${event.distanceKm.toInt()} km). Profundidad: ${event.depthKm.toInt()} km. ¡Atención!"
                                )
                                newlyNotified = true
                            }

                            notifiedIds.add(event.eventId)
                        }
                    }

                    if (newlyNotified) {
                        prefs.edit().putStringSet("notified_ids", notifiedIds).apply()
                    }
                }
            } catch (e: Exception) {
                Log.w("SeismicAlarmReceiver", "Error en verificación de sismos en background: ${e.message}")
            } finally {
                // Reprogramar la siguiente alarma para mantener el ciclo perpetuo
                schedulePeriodicCheck(context, CHECK_INTERVAL_MS)

                try {
                    if (wakeLock?.isHeld == true) {
                        wakeLock.release()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                pendingResult.finish()
            }
        }
    }
}
