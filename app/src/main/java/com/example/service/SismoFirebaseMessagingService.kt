package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.location.Location
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.SismoAlertaApp
import com.example.data.local.AppDatabase
import com.example.data.local.entity.SeismicAlertEvent
import com.example.data.repository.SettingsRepository
import com.google.android.gms.location.LocationServices
import com.google.android.gms.tasks.Tasks
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Servicio de Firebase Cloud Messaging (FCM) de Alta Prioridad.
 * Se activa automáticamente por el sistema operativo incluso cuando la aplicación
 * está cerrada, en segundo plano, la pantalla apagada o el teléfono bloqueado.
 */
class SismoFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "SismoFCMService"
        const val SEISMIC_TOPIC = "sismos_colombia"
        const val EMERGENCY_CHANNEL_ID = "sismo_emergency_alert_channel"

        /**
         * Suscribe el dispositivo al tópico de sismos de Colombia de forma idempotente.
         */
        fun subscribeToTopic() {
            try {
                FirebaseMessaging.getInstance().subscribeToTopic(SEISMIC_TOPIC)
                    .addOnSuccessListener {
                        Log.i(TAG, "Suscripción exitosa al tópico FCM: $SEISMIC_TOPIC ✓")
                    }
                    .addOnFailureListener { e ->
                        Log.w(TAG, "Error al suscribirse al tópico FCM $SEISMIC_TOPIC: ${e.message}")
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Excepción en subscribeToTopic: ${e.message}")
            }
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.i(TAG, "Nuevo FCM Token recibido: $token")
        subscribeToTopic()

        // Persistir token localmente y sincronizar con Firestore si hay usuario
        try {
            val prefs = getSharedPreferences("sismo_fcm_prefs", Context.MODE_PRIVATE)
            prefs.edit().putString("fcm_token", token).apply()

            val app = applicationContext as? SismoAlertaApp
            val deviceId = app?.firestoreSyncRepository?.deviceId ?: "DEV_UNKNOWN"
            val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()
            firestore.collection("device_fcm_tokens").document(deviceId).set(
                mapOf(
                    "token" to token,
                    "updatedAt" to System.currentTimeMillis(),
                    "platform" to "android"
                )
            )
        } catch (e: Exception) {
            Log.w(TAG, "Error al registrar nuevo token en Firestore: ${e.message}")
        }
    }

    /**
     * Se ejecuta de forma instantánea al recibir un mensaje FCM data-only de alta prioridad.
     * Este método se invoca incluso con el proceso de la aplicación detenido por Android.
     */
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.i(TAG, "Mensaje FCM recibido de: ${remoteMessage.from}, data payload: ${remoteMessage.data}")

        val data = remoteMessage.data
        if (data.isEmpty()) {
            Log.w(TAG, "Payload vacío en mensaje FCM; ignorando.")
            return
        }

        // Extraer campos del sismo
        val eventId = data["id"] ?: data["eventId"] ?: "SGC-${System.currentTimeMillis()}"
        val magnitude = data["magnitude"]?.toDoubleOrNull() ?: 0.0
        val epicenter = data["epicenter"] ?: "Colombia"
        val latitude = data["latitude"]?.toDoubleOrNull() ?: 4.6097
        val longitude = data["longitude"]?.toDoubleOrNull() ?: -74.0817
        val depthKm = data["depthKm"]?.toDoubleOrNull() ?: 10.0
        val timestamp = data["timestamp"]?.toLongOrNull() ?: System.currentTimeMillis()
        val agencySource = data["source"] ?: "Servicio Geológico Colombiano (SGC)"
        val status = data["status"] ?: "CONFIRMED"

        if (magnitude <= 0.0) {
            Log.w(TAG, "Magnitud inválida ($magnitude); descartando alerta.")
            return
        }

        val context = applicationContext
        val settingsRepo = SettingsRepository(context)
        val alertFilter = settingsRepo.getSeismicAlertFilter()

        // 1. Deduplicación estricta
        val prefs = context.getSharedPreferences("sismo_notified_events_cache", Context.MODE_PRIVATE)
        val notifiedIds = prefs.getStringSet("notified_ids", emptySet())?.toMutableSet() ?: mutableSetOf()
        if (notifiedIds.contains(eventId)) {
            Log.d(TAG, "Evento $eventId ya fue notificado previamente. Descartando duplicado.")
            return
        }

        // 2. Obtener última ubicación conocida de forma sincrónica con timeout seguro
        val userLocation = getLastKnownLocationSafely(context)
        val userLat = userLocation?.latitude ?: 4.6097
        val userLng = userLocation?.longitude ?: -74.0817

        val distanceKm = calculateDistanceKm(userLat, userLng, latitude, longitude)

        // 3. Evaluar umbrales configurados por el usuario
        if (!alertFilter.shouldAlert(magnitude, distanceKm)) {
            Log.i(TAG, "Sismo M $magnitude a $distanceKm km no cumple los filtros del usuario (Min: ${alertFilter.minMagnitude}, MaxDist: ${alertFilter.maxDistanceKm} km).")
            // Aún así lo guardamos en la base de datos local para que aparezca en el historial
            saveEventLocally(eventId, magnitude, epicenter, depthKm, distanceKm, timestamp, agencySource, status)
            return
        }

        // 4. Guardar en Base de Datos local Room
        saveEventLocally(eventId, magnitude, epicenter, depthKm, distanceKm, timestamp, agencySource, status)

        // Marcar evento como notificado para idempotencia
        notifiedIds.add(eventId)
        prefs.edit().putStringSet("notified_ids", notifiedIds).apply()

        // 5. Disparar Alarma Sonora forzada y Vibración Sísmica si están habilitadas
        if (alertFilter.isAudioEnabled || alertFilter.isVibrationEnabled) {
            try {
                val soundPlayer = EmergencySoundPlayer(context)
                val forceVolume = settingsRepo.forceMaxVolume.value
                val toneIndex = settingsRepo.alarmToneIndex.value
                val customToneUri = settingsRepo.customToneUri.value

                soundPlayer.startSirenAlert(
                    forceMaxVolume = forceVolume && alertFilter.isAudioEnabled,
                    mode = AlertVibrationMode.CRITICAL_SEISMIC,
                    toneIndex = toneIndex,
                    customUriString = customToneUri
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error al disparar alerta sonora/háptica: ${e.message}")
            }
        }

        // 6. Generar Notificación Crítica de Máxima Prioridad (Heads-Up)
        showEmergencyNotification(
            context = context,
            eventId = eventId,
            magnitude = magnitude,
            epicenter = epicenter,
            distanceKm = distanceKm,
            depthKm = depthKm,
            agencySource = agencySource,
            latitude = latitude,
            longitude = longitude
        )
    }

    private fun saveEventLocally(
        eventId: String,
        magnitude: Double,
        epicenter: String,
        depthKm: Double,
        distanceKm: Double,
        timestamp: Long,
        agencySource: String,
        status: String
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getDatabase(applicationContext)
                db.seismicDao().insertSeismicEvent(
                    SeismicAlertEvent(
                        eventId = eventId,
                        magnitude = magnitude,
                        epicenter = epicenter,
                        depthKm = depthKm,
                        distanceKm = distanceKm,
                        intensityPga = (magnitude * 0.05).toFloat(),
                        status = status,
                        timestamp = timestamp,
                        agencySource = agencySource
                    )
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error guardando evento en Room: ${e.message}")
            }
        }
    }

    private fun showEmergencyNotification(
        context: Context,
        eventId: String,
        magnitude: Double,
        epicenter: String,
        distanceKm: Double,
        depthKm: Double,
        agencySource: String,
        latitude: Double,
        longitude: Double
    ) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Crear canal prioritario con bypass de No Molestar (DND)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                EMERGENCY_CHANNEL_ID,
                "ALERTAS SÍSMICAS CRÍTICAS SGC",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alertas audibles prioritarias ante sismos confirmados en tiempo real."
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500, 200, 1000)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setBypassDnd(true)
                val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                setSound(alarmUri, AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                )
            }
            manager.createNotificationChannel(channel)
        }

        // PendingIntent para abrir MainActivity directamente en el Dashboard y centrar el mapa
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("NAVIGATE_TO", "dashboard")
            putExtra("EVENT_ID", eventId)
            putExtra("LATITUDE", latitude)
            putExtra("LONGITUDE", longitude)
            putExtra("MAGNITUDE", magnitude)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            (System.currentTimeMillis() % 10000).toInt(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val title = "🚨 ¡SISMO DETECTADO: M ${String.format(Locale.US, "%.1f", magnitude)}!"
        val bigMessage = "📍 Epicentro: $epicenter\n" +
                "📏 Distancia: ${distanceKm.toInt()} km de tu ubicación\n" +
                "⬇️ Profundidad: ${depthKm.toInt()} km\n" +
                "🏛️ Fuente: $agencySource"

        val notification = NotificationCompat.Builder(context, EMERGENCY_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText("A ${distanceKm.toInt()} km en $epicenter • M ${String.format(Locale.US, "%.1f", magnitude)}")
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigMessage))
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setFullScreenIntent(pendingIntent, true)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setVibrate(longArrayOf(0, 500, 200, 500, 200, 1000))
            .build()

        manager.notify((System.currentTimeMillis() % 20000).toInt(), notification)
    }

    private fun getLastKnownLocationSafely(context: Context): Location? {
        return try {
            val fusedClient = LocationServices.getFusedLocationProviderClient(context)
            val task = fusedClient.lastLocation
            Tasks.await(task, 2, TimeUnit.SECONDS)
        } catch (e: Exception) {
            null
        }
    }

    private fun calculateDistanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return (r * c * 10).toInt() / 10.0
    }
}
