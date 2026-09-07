package com.example.service

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.SismoAlertaApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SeismicMonitoringService : Service(), LocationListener {

    private lateinit var locationManager: LocationManager

    private var wakeLock: PowerManager.WakeLock? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private var breadcrumbJob: Job? = null
    private var onlineMonitoringJob: Job? = null

    companion object {
        const val CHANNEL_ID = "sismo_vigilance_channel"
        const val EMERGENCY_CHANNEL_ID = "sismo_emergency_alert_channel"
        const val NOTIF_ID = 1001
        const val EMERGENCY_NOTIF_ID = 1002

        private val _isServiceRunning = MutableStateFlow(false)
        val isServiceRunning = _isServiceRunning.asStateFlow()

        private val _liveMagnitude = MutableStateFlow(0f)
        val liveMagnitude = _liveMagnitude.asStateFlow()

        fun sendStrongSeismicPushNotification(context: Context, title: String, message: String) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Ensure High Priority Notification Channel is created before posting notification
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val emergencyChannel = NotificationChannel(
                    EMERGENCY_CHANNEL_ID,
                    "ALERTAS SÍSMICAS CRÍTICAS SGC",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Alertas audibles prioritarias ante sismos confirmados."
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 600, 200, 600, 200, 1000)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                    setBypassDnd(true)
                }
                manager.createNotificationChannel(emergencyChannel)
            }

            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("NAVIGATE_TO", "dashboard")
            }
            val pendingIntent = PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val emergencyNotif = NotificationCompat.Builder(context, EMERGENCY_CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setFullScreenIntent(pendingIntent, true)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setVibrate(longArrayOf(0, 600, 200, 600, 200, 1000))
                .build()

            manager.notify((System.currentTimeMillis() % 10000).toInt(), emergencyNotif)
        }
    }

    override fun onCreate() {
        super.onCreate()
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

        createNotificationChannel()

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "SismoAlerta:SeismicMonitoringWakeLock"
        ).apply {
            setReferenceCounted(false)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            if (wakeLock?.isHeld == false) {
                wakeLock?.acquire()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val notification = buildForegroundNotification("Vigilancia SGC en Vivo (24/7)")
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIF_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
            } else {
                startForeground(NOTIF_ID, notification)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            try {
                startForeground(NOTIF_ID, notification)
            } catch (e2: Exception) {
                e2.printStackTrace()
            }
        }

        _isServiceRunning.value = true

        // Armar el receptor de alarma de respaldo
        com.example.receiver.SeismicAlarmReceiver.schedulePeriodicCheck(applicationContext)

        // Register location listener
        try {
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2000L, 0f, this)
            }
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 2000L, 0f, this)
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
        }

        // Start periodic breadcrumb trail recorder (every 60s)
        breadcrumbJob?.cancel()
        breadcrumbJob = serviceScope.launch {
            while (_isServiceRunning.value) {
                delay(60000L)
                val app = application as? SismoAlertaApp
                app?.locationRepository?.recordBreadcrumb(isEmergency = false)
            }
        }

        // Start 24/7 background monitor for official seismic reports (every 25 seconds)
        onlineMonitoringJob?.cancel()
        onlineMonitoringJob = serviceScope.launch {
            val prefs = getSharedPreferences("sismo_notified_events_cache", Context.MODE_PRIVATE)
            var notifiedIds = prefs.getStringSet("notified_ids", emptySet())?.toMutableSet() ?: mutableSetOf()

            while (_isServiceRunning.value) {
                try {
                    val app = application as? SismoAlertaApp
                    if (app != null) {
                        val currentLoc = app.locationRepository.currentLocation.value
                        val events = app.seismicOnlineFeedRepository.fetchLiveSeismicReports(currentLoc)

                        val now = System.currentTimeMillis()
                        var newlyNotified = false

                        for (event in events) {
                            val timeDiff = now - event.timestamp
                            if (!notifiedIds.contains(event.eventId) && timeDiff in -300000L..(6 * 3600 * 1000L)) {
                                val isNearbyOrFelt = event.distanceKm <= 150.0 && event.magnitude >= 2.5
                                val isSignificantRegional = event.distanceKm <= 350.0 && event.magnitude >= 3.5
                                val isNationalStrong = event.magnitude >= 4.0

                                if (isNearbyOrFelt || isSignificantRegional || isNationalStrong) {
                                    sendStrongSeismicPushNotification(
                                        context = applicationContext,
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
                    Log.w("SeismicService", "Error polling seismic feed: ${e.message}")
                }
                delay(25000L) // Polling interval
            }
        }

        return START_STICKY
    }

    override fun onLocationChanged(location: Location) {
        val app = application as? SismoAlertaApp
        app?.locationRepository?.updateCurrentLocation(location)
    }

    @Deprecated("Deprecated in Java")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}

    override fun onProviderEnabled(provider: String) {}

    override fun onProviderDisabled(provider: String) {}

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        // Programar el AlarmReceiver para garantizar vigilancia ininterrumpida sin violar restricciones de Android 12+
        com.example.receiver.SeismicAlarmReceiver.schedulePeriodicCheck(applicationContext, 1000L)
    }

    override fun onDestroy() {
        super.onDestroy()
        _isServiceRunning.value = false
        try {
            locationManager.removeUpdates(this)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        breadcrumbJob?.cancel()
        onlineMonitoringJob?.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)

            val channel = NotificationChannel(
                CHANNEL_ID,
                "SismoAlerta Servicio de Vigilancia",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Monitoreo continuo de acelerómetro y GPS para emergencias sísmicas."
            }
            manager?.createNotificationChannel(channel)

            val emergencyChannel = NotificationChannel(
                EMERGENCY_CHANNEL_ID,
                "ALERTAS SÍSMICAS CRÍTICAS",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alertas audibles prioritarias ante sismos y emergencias."
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500, 200, 800)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            manager?.createNotificationChannel(emergencyChannel)
        }
    }

    private fun buildForegroundNotification(contentText: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SismoAlerta Resiliencia")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
