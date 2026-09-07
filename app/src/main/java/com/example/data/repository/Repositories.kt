package com.example.data.repository

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.Location
import android.os.BatteryManager
import android.os.Build
import android.os.Looper
import android.util.Log
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.example.data.crypto.CryptoUtils
import com.example.data.local.AppDatabase
import com.example.data.local.entity.BreadcrumbLocation
import com.example.data.local.entity.EmergencyAlertLog
import com.example.data.local.entity.EmergencyContact
import com.example.data.local.entity.SeismicAlertEvent
import com.example.sensor.AccelerometerFilter
import com.example.sensor.SeismicDetectionResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SeismicRepository(private val db: AppDatabase) {
    private val seismicDao = db.seismicDao()
    private val filter = AccelerometerFilter()

    val seismicEvents: Flow<List<SeismicAlertEvent>> = seismicDao.getAllSeismicEvents()
    val latestEvent: Flow<SeismicAlertEvent?> = seismicDao.getLatestSeismicEvent()

    private val _currentSensorReading = MutableStateFlow(
        SeismicDetectionResult(0f, 0f, 0f, isSeismicTrigger = false, isDropOrFalsePositive = false, estimatedRichter = 0.0)
    )
    val currentSensorReading: StateFlow<SeismicDetectionResult> = _currentSensorReading.asStateFlow()

    fun processAccelerometerData(x: Float, y: Float, z: Float): SeismicDetectionResult {
        val result = filter.processSample(x, y, z)
        _currentSensorReading.value = result
        return result
    }

    suspend fun saveSeismicEvent(event: SeismicAlertEvent) {
        seismicDao.insertSeismicEvent(event)
    }

    suspend fun purgeMockSeismicEvents() {
        seismicDao.purgeMockSeismicEvents()
    }

    suspend fun generateMockAgencyAlert(magnitude: Double, epicenter: String, distanceKm: Double): SeismicAlertEvent {
        val event = SeismicAlertEvent(
            eventId = "SASMEX-${System.currentTimeMillis()}",
            magnitude = magnitude,
            epicenter = epicenter,
            depthKm = 12.5,
            distanceKm = distanceKm,
            intensityPga = (magnitude * 0.08).toFloat(),
            status = "CONFIRMED",
            timestamp = System.currentTimeMillis(),
            agencySource = "SASMEX / SSN Red de Alerta Sísmica"
        )
        seismicDao.insertSeismicEvent(event)
        return event
    }
}

class LocationRepository(
    private val context: Context,
    private val db: AppDatabase,
    private val firestoreSyncRepo: FirestoreSyncRepository? = null
) {
    private val breadcrumbDao = db.breadcrumbDao()

    val recentBreadcrumbs: Flow<List<BreadcrumbLocation>> = breadcrumbDao.getRecentBreadcrumbs()

    private val _currentLocation = MutableStateFlow<Location?>(null)
    val currentLocation: StateFlow<Location?> = _currentLocation.asStateFlow()

    private val _batteryLevel = MutableStateFlow(getBatteryLevel())
    val batteryLevel: StateFlow<Int> = _batteryLevel.asStateFlow()

    private val fusedClient: FusedLocationProviderClient? by lazy {
        try {
            LocationServices.getFusedLocationProviderClient(context)
        } catch (e: Exception) {
            null
        }
    }

    init {
        registerBatteryMonitor()
        refreshActualDeviceLocation()
    }

    private fun registerBatteryMonitor() {
        try {
            val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val receiver = object : android.content.BroadcastReceiver() {
                override fun onReceive(c: Context?, intent: Intent?) {
                    val updated = getBatteryLevel()
                    if (updated != _batteryLevel.value) {
                        _batteryLevel.value = updated
                    }
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                context.registerReceiver(receiver, filter)
            }
        } catch (e: Exception) {
            Log.w("LocationRepo", "Error al registrar monitor de batería: ${e.message}")
        }
    }

    private var activeLocationCallback: LocationCallback? = null
    private var lastRecordedBreadcrumbLat = 0.0
    private var lastRecordedBreadcrumbLng = 0.0
    private var lastRecordedBreadcrumbTime = 0L

    fun updateCurrentLocation(location: Location) {
        _currentLocation.value = location
    }

    /**
     * Obtiene la ubicación del dispositivo con impacto casi nulo en batería:
     * 1. Consulta lastLocation en caché de Google Play Services (0% consumo de batería).
     * 2. Si es necesario, pide una única lectura balanceada (Wifi/Torres) que apaga el radio de inmediato.
     */
    fun refreshActualDeviceLocation() {
        try {
            val hasFine = context.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
            val hasCoarse = context.checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED

            if (!hasFine && !hasCoarse) return

            // 1. Google Play Services Fused Location (0% consumo de batería)
            fusedClient?.let { client ->
                try {
                    client.lastLocation.addOnSuccessListener { loc: Location? ->
                        if (loc != null) {
                            val curr = _currentLocation.value
                            if (curr == null || loc.time >= curr.time) {
                                _currentLocation.value = loc
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w("LocationRepo", "Error lastLocation: ${e.message}")
                }

                // 2. Consulta puntual de baja potencia que apaga el chip de inmediato al terminar
                try {
                    client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
                        .addOnSuccessListener { freshLoc: Location? ->
                            if (freshLoc != null) {
                                _currentLocation.value = freshLoc
                            }
                        }
                } catch (e: Exception) {
                    Log.w("LocationRepo", "Error getCurrentLocation: ${e.message}")
                }
            }

            // 3. LocationManager Nativo de Respaldo solo si aún no hay coordenadas
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? android.location.LocationManager
            if (locationManager != null && _currentLocation.value == null) {
                val gpsLoc = try { locationManager.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER) } catch (e: Exception) { null }
                val netLoc = try { locationManager.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER) } catch (e: Exception) { null }
                val passLoc = try { locationManager.getLastKnownLocation(android.location.LocationManager.PASSIVE_PROVIDER) } catch (e: Exception) { null }

                val validLocations = listOfNotNull(gpsLoc, netLoc, passLoc)
                val bestLoc = validLocations.maxByOrNull { it.time }
                if (bestLoc != null && _currentLocation.value == null) {
                    _currentLocation.value = bestLoc
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Inicia actualizaciones continuas controladas y seguras sin fugas de hardware.
     */
    fun startContinuousLocationUpdates(highAccuracy: Boolean = false) {
        try {
            val hasFine = context.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
            val hasCoarse = context.checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!hasFine && !hasCoarse) return

            stopContinuousLocationUpdates()

            fusedClient?.let { client ->
                val priority = if (highAccuracy) Priority.PRIORITY_HIGH_ACCURACY else Priority.PRIORITY_BALANCED_POWER_ACCURACY
                val interval = if (highAccuracy) 10000L else 60000L
                val minDistance = if (highAccuracy) 5f else 30f

                val req = LocationRequest.Builder(priority, interval)
                    .setMinUpdateDistanceMeters(minDistance)
                    .build()

                val callback = object : LocationCallback() {
                    override fun onLocationResult(result: LocationResult) {
                        result.lastLocation?.let { loc ->
                            _currentLocation.value = loc
                        }
                    }
                }
                activeLocationCallback = callback
                client.requestLocationUpdates(req, callback, Looper.getMainLooper())
            }
        } catch (e: Exception) {
            Log.w("LocationRepo", "Error en startContinuousLocationUpdates: ${e.message}")
        }
    }

    fun stopContinuousLocationUpdates() {
        try {
            activeLocationCallback?.let { callback ->
                fusedClient?.removeLocationUpdates(callback)
                activeLocationCallback = null
            }
        } catch (e: Exception) {
            Log.w("LocationRepo", "Error al remover LocationCallback: ${e.message}")
        }
    }

    fun getBatteryLevel(): Int {
        // Prioridad 1: Lectura directa del medidor de hardware de la batería (Kernel/Driver)
        try {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            if (bm != null) {
                val capacity = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                if (capacity in 1..100) {
                    return capacity
                }
            }
        } catch (e: Exception) {
            Log.w("LocationRepo", "Error al leer BATTERY_PROPERTY_CAPACITY: ${e.message}")
        }

        // Prioridad 2: Broadcast Intent Sticky del Sistema Operativo
        try {
            val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val batteryStatus: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(null, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                context.registerReceiver(null, filter)
            }
            val level: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            if (level >= 0 && scale > 0) {
                return (level * 100 / scale.toFloat()).toInt()
            }
        } catch (e: Exception) {
            Log.w("LocationRepo", "Error al leer ACTION_BATTERY_CHANGED: ${e.message}")
        }

        return 50 // Valor medio seguro solo en caso de falla extrema
    }

    suspend fun recordBreadcrumb(isEmergency: Boolean = false, statusType: String = "Vigilancia"): BreadcrumbLocation? {
        val loc = _currentLocation.value
        val lat = loc?.latitude ?: 0.0
        val lng = loc?.longitude ?: 0.0
        val acc = loc?.accuracy ?: 8.0f
        val battery = getBatteryLevel()
        val now = System.currentTimeMillis()

        // En condiciones normales de paz, evitar escrituras y tráfico de red si el usuario está inmóvil
        if (!isEmergency && lastRecordedBreadcrumbLat != 0.0 && lastRecordedBreadcrumbLng != 0.0 && lat != 0.0 && lng != 0.0) {
            val results = FloatArray(1)
            Location.distanceBetween(lastRecordedBreadcrumbLat, lastRecordedBreadcrumbLng, lat, lng, results)
            val distanceMovedMeters = results[0]
            val elapsed = now - lastRecordedBreadcrumbTime
            if (distanceMovedMeters < 50f && elapsed < 15 * 60 * 1000L) {
                return null // Inmóvil y menos de 15 min: ahorrar base de datos, radio y batería
            }
        }

        lastRecordedBreadcrumbLat = lat
        lastRecordedBreadcrumbLng = lng
        lastRecordedBreadcrumbTime = now

        val breadcrumb = BreadcrumbLocation(
            latitude = lat,
            longitude = lng,
            accuracy = acc,
            batteryLevel = battery,
            timestamp = now,
            isEmergencyPoint = isEmergency
        )
        breadcrumbDao.insertBreadcrumb(breadcrumb)

        // Encrypt and persist to Firebase Firestore with offline cache support
        firestoreSyncRepo?.syncEncryptedLocation(
            latitude = lat,
            longitude = lng,
            batteryLevel = battery,
            isEmergency = isEmergency,
            statusType = statusType
        )

        return breadcrumb
    }

    suspend fun getLastThreeBreadcrumbs(): List<BreadcrumbLocation> {
        return breadcrumbDao.getLastThreeBreadcrumbs()
    }
}

class FamilyRepository(private val context: Context, private val db: AppDatabase) {
    private val contactDao = db.contactDao()
    private val emergencyAlertDao = db.emergencyAlertDao()

    val contacts: Flow<List<EmergencyContact>> = contactDao.getAllContacts()
    val alertLogs: Flow<List<EmergencyAlertLog>> = emergencyAlertDao.getAllAlertLogs()

    suspend fun addContact(contact: EmergencyContact) {
        contactDao.insertContact(contact)
    }

    suspend fun updateContact(contact: EmergencyContact) {
        contactDao.updateContact(contact)
    }

    suspend fun deleteContact(contact: EmergencyContact) {
        contactDao.deleteContact(contact)
    }

    suspend fun sendEmergencyBroadcast(
        statusType: String,
        latitude: Double,
        longitude: Double,
        batteryLevel: Int
    ): EmergencyAlertLog {
        val rawJsonPayload = """{"status":"$statusType","lat":$latitude,"lng":$longitude,"bat":$batteryLevel,"ts":${System.currentTimeMillis()}}"""
        val encryptedPayload = CryptoUtils.encryptAES256GCM(rawJsonPayload)
        val payloadSizeBytes = encryptedPayload.toByteArray().size

        val log = EmergencyAlertLog(
            timestamp = System.currentTimeMillis(),
            statusType = statusType,
            latitude = latitude,
            longitude = longitude,
            batteryLevel = batteryLevel,
            encryptedPayload = encryptedPayload,
            payloadSizeBytes = payloadSizeBytes,
            smsSent = true,
            networkSent = true
        )

        emergencyAlertDao.insertAlertLog(log)
        return log
    }
}

class SettingsRepository(private val context: Context) {
    private val prefs = context.getSharedPreferences("sismo_alerta_prefs", Context.MODE_PRIVATE)

    private val _isHighContrast = MutableStateFlow(prefs.getBoolean("high_contrast", false))
    val isHighContrast: StateFlow<Boolean> = _isHighContrast.asStateFlow()

    private val _forceMaxVolume = MutableStateFlow(prefs.getBoolean("force_volume", true))
    val forceMaxVolume: StateFlow<Boolean> = _forceMaxVolume.asStateFlow()

    private val _panicTimerSeconds = MutableStateFlow(prefs.getInt("panic_timer", 5))
    val panicTimerSeconds: StateFlow<Int> = _panicTimerSeconds.asStateFlow()

    private val _isDrillActive = MutableStateFlow(prefs.getBoolean("drill_active", false))
    val isDrillActive: StateFlow<Boolean> = _isDrillActive.asStateFlow()

    private val _alarmToneIndex = MutableStateFlow(prefs.getInt("alarm_tone_index", 0))
    val alarmToneIndex: StateFlow<Int> = _alarmToneIndex.asStateFlow()

    private val _customToneUri = MutableStateFlow(prefs.getString("custom_tone_uri", null))
    val customToneUri: StateFlow<String?> = _customToneUri.asStateFlow()

    private val _customToneTitle = MutableStateFlow(prefs.getString("custom_tone_title", "Tono Nativo del Dispositivo") ?: "Tono Nativo del Dispositivo")
    val customToneTitle: StateFlow<String> = _customToneTitle.asStateFlow()

    // Configuración de Umbrales de Alertas Sísmicas (FCM / Segundo Plano)
    private val _minAlertMagnitude = MutableStateFlow(prefs.getFloat("min_alert_magnitude", 3.5f).toDouble())
    val minAlertMagnitude: StateFlow<Double> = _minAlertMagnitude.asStateFlow()

    private val _maxAlertDistanceKm = MutableStateFlow(prefs.getFloat("max_alert_distance_km", 350.0f).toDouble())
    val maxAlertDistanceKm: StateFlow<Double> = _maxAlertDistanceKm.asStateFlow()

    private val _isAudioAlertEnabled = MutableStateFlow(prefs.getBoolean("is_audio_alert_enabled", true))
    val isAudioAlertEnabled: StateFlow<Boolean> = _isAudioAlertEnabled.asStateFlow()

    private val _isVibrationAlertEnabled = MutableStateFlow(prefs.getBoolean("is_vibration_alert_enabled", true))
    val isVibrationAlertEnabled: StateFlow<Boolean> = _isVibrationAlertEnabled.asStateFlow()

    fun setMinAlertMagnitude(mag: Double) {
        prefs.edit().putFloat("min_alert_magnitude", mag.toFloat()).apply()
        _minAlertMagnitude.value = mag
    }

    fun setMaxAlertDistanceKm(distKm: Double) {
        prefs.edit().putFloat("max_alert_distance_km", distKm.toFloat()).apply()
        _maxAlertDistanceKm.value = distKm
    }

    fun setAudioAlertEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("is_audio_alert_enabled", enabled).apply()
        _isAudioAlertEnabled.value = enabled
    }

    fun setVibrationAlertEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("is_vibration_alert_enabled", enabled).apply()
        _isVibrationAlertEnabled.value = enabled
    }

    fun getSeismicAlertFilter(): com.example.data.model.SeismicAlertFilter {
        return com.example.data.model.SeismicAlertFilter(
            minMagnitude = _minAlertMagnitude.value,
            maxDistanceKm = _maxAlertDistanceKm.value,
            isAudioEnabled = _isAudioAlertEnabled.value,
            isVibrationEnabled = _isVibrationAlertEnabled.value
        )
    }

    fun setHighContrast(enabled: Boolean) {
        prefs.edit().putBoolean("high_contrast", enabled).apply()
        _isHighContrast.value = enabled
    }

    fun setForceMaxVolume(enabled: Boolean) {
        prefs.edit().putBoolean("force_volume", enabled).apply()
        _forceMaxVolume.value = enabled
    }

    fun setPanicTimerSeconds(seconds: Int) {
        prefs.edit().putInt("panic_timer", seconds).apply()
        _panicTimerSeconds.value = seconds
    }

    fun setDrillActive(active: Boolean) {
        prefs.edit().putBoolean("drill_active", active).apply()
        _isDrillActive.value = active
    }

    fun setAlarmToneIndex(index: Int) {
        prefs.edit().putInt("alarm_tone_index", index).apply()
        _alarmToneIndex.value = index
    }

    fun setCustomTone(uriString: String?, title: String) {
        prefs.edit().putString("custom_tone_uri", uriString).putString("custom_tone_title", title).apply()
        _customToneUri.value = uriString
        _customToneTitle.value = title
    }
}

