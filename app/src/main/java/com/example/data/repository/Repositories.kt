package com.example.data.repository

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.Location
import android.os.BatteryManager
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

    init {
        refreshActualDeviceLocation()
    }

    fun updateCurrentLocation(location: Location) {
        _currentLocation.value = location
    }

    fun refreshActualDeviceLocation() {
        try {
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? android.location.LocationManager
            if (locationManager != null) {
                val hasFine = context.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
                val hasCoarse = context.checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED

                if (hasFine || hasCoarse) {
                    val gpsLoc = try { locationManager.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER) } catch (e: Exception) { null }
                    val netLoc = try { locationManager.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER) } catch (e: Exception) { null }
                    val passLoc = try { locationManager.getLastKnownLocation(android.location.LocationManager.PASSIVE_PROVIDER) } catch (e: Exception) { null }

                    val validLocations = listOfNotNull(gpsLoc, netLoc, passLoc)
                    val bestLoc = validLocations.maxByOrNull { it.time }
                    if (bestLoc != null) {
                        _currentLocation.value = bestLoc
                    }

                    // Register listener for continuous high-precision updates
                    val listener = object : android.location.LocationListener {
                        override fun onLocationChanged(location: Location) {
                            val current = _currentLocation.value
                            if (current == null || location.accuracy <= current.accuracy || (location.time - current.time) > 10000L) {
                                _currentLocation.value = location
                            }
                        }
                        @Deprecated("Deprecated in Java")
                        override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
                        override fun onProviderEnabled(provider: String) {}
                        override fun onProviderDisabled(provider: String) {}
                    }

                    if (locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)) {
                        locationManager.requestLocationUpdates(android.location.LocationManager.GPS_PROVIDER, 2000L, 0f, listener)
                    }
                    if (locationManager.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER)) {
                        locationManager.requestLocationUpdates(android.location.LocationManager.NETWORK_PROVIDER, 2000L, 0f, listener)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getBatteryLevel(): Int {
        val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { filter ->
            context.registerReceiver(null, filter)
        }
        val level: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        return if (level >= 0 && scale > 0) {
            (level * 100 / scale.toFloat()).toInt()
        } else {
            85 // Fallback default
        }
    }

    suspend fun recordBreadcrumb(isEmergency: Boolean = false, statusType: String = "Vigilancia"): BreadcrumbLocation {
        val loc = _currentLocation.value
        val lat = loc?.latitude ?: 4.6097 // Colombia Default fallback
        val lng = loc?.longitude ?: -74.0817
        val acc = loc?.accuracy ?: 8.0f
        val battery = getBatteryLevel()

        val breadcrumb = BreadcrumbLocation(
            latitude = lat,
            longitude = lng,
            accuracy = acc,
            batteryLevel = battery,
            timestamp = System.currentTimeMillis(),
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
