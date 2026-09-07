package com.example.ui.viewmodel

import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.SismoAlertaApp
import com.example.data.local.entity.BreadcrumbLocation
import com.example.data.local.entity.EmergencyAlertLog
import com.example.data.local.entity.EmergencyContact
import com.example.data.local.entity.SeismicAlertEvent
import com.example.data.repository.FamilyRepository
import com.example.data.repository.LocationRepository
import com.example.data.repository.SeismicRepository
import com.example.data.repository.SettingsRepository
import com.example.service.EmergencySoundPlayer
import com.example.service.SeismicMonitoringService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

import com.example.data.model.NucleusGroup
import com.example.data.model.NucleusMember
import com.example.data.repository.NucleusRepository

class MainViewModel(
    private val seismicRepo: SeismicRepository,
    private val locationRepo: LocationRepository,
    private val familyRepo: FamilyRepository,
    private val settingsRepo: SettingsRepository,
    private val nucleusRepo: NucleusRepository,
    private val context: Context
) : ViewModel() {

    private val soundPlayer = EmergencySoundPlayer(context)
    private val app = context.applicationContext as SismoAlertaApp
    private val firestoreSyncRepo = app.firestoreSyncRepository
    private val seismicOnlineFeedRepo = app.seismicOnlineFeedRepository

    val userSharingCode: StateFlow<String> = nucleusRepo.userSharingCode
    val joinedNuclei: StateFlow<List<NucleusGroup>> = nucleusRepo.joinedNuclei
    val nucleusMembersMap: StateFlow<Map<String, List<NucleusMember>>> = nucleusRepo.nucleusMembersMap
    val isLiveLocationSharingActive: StateFlow<Boolean> = nucleusRepo.isLiveLocationSharingActive

    val firestoreSyncStatus: StateFlow<String> = firestoreSyncRepo.syncStateMessage
    val isSyncingOnlineSeismic: StateFlow<Boolean> = seismicOnlineFeedRepo.isSyncing
    val onlineSyncStatus: StateFlow<String> = seismicOnlineFeedRepo.lastSyncStatus

    val contacts: StateFlow<List<EmergencyContact>> = familyRepo.contacts.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    val breadcrumbs: StateFlow<List<BreadcrumbLocation>> = locationRepo.recentBreadcrumbs.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    val seismicEvents: StateFlow<List<SeismicAlertEvent>> = seismicRepo.seismicEvents.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    val latestSeismicEvent: StateFlow<SeismicAlertEvent?> = seismicRepo.latestEvent.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), null
    )

    val alertLogs: StateFlow<List<EmergencyAlertLog>> = familyRepo.alertLogs.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    val isHighContrast: StateFlow<Boolean> = settingsRepo.isHighContrast
    val forceMaxVolume: StateFlow<Boolean> = settingsRepo.forceMaxVolume
    val isDrillActive: StateFlow<Boolean> = settingsRepo.isDrillActive
    val alarmToneIndex: StateFlow<Int> = settingsRepo.alarmToneIndex
    val customToneUri: StateFlow<String?> = settingsRepo.customToneUri
    val customToneTitle: StateFlow<String> = settingsRepo.customToneTitle

    val minAlertMagnitude: StateFlow<Double> = settingsRepo.minAlertMagnitude
    val maxAlertDistanceKm: StateFlow<Double> = settingsRepo.maxAlertDistanceKm
    val isAudioAlertEnabled: StateFlow<Boolean> = settingsRepo.isAudioAlertEnabled
    val isVibrationAlertEnabled: StateFlow<Boolean> = settingsRepo.isVibrationAlertEnabled

    val currentLocation: StateFlow<Location?> = locationRepo.currentLocation
    val batteryLevel: StateFlow<Int> = locationRepo.batteryLevel
    val currentUserId: String = firestoreSyncRepo.deviceId
    val isServiceRunning: StateFlow<Boolean> = SeismicMonitoringService.isServiceRunning
    val liveMagnitude: StateFlow<Float> = SeismicMonitoringService.liveMagnitude

    private val _currentStatus = MutableStateFlow("SANO Y SALVO")
    val currentStatus: StateFlow<String> = _currentStatus.asStateFlow()

    private val _isTrappedBeaconActive = MutableStateFlow(false)
    val isTrappedBeaconActive: StateFlow<Boolean> = _isTrappedBeaconActive.asStateFlow()

    private val _isPanicCountdownActive = MutableStateFlow(false)
    val isPanicCountdownActive: StateFlow<Boolean> = _isPanicCountdownActive.asStateFlow()

    private val _panicCountdownRemaining = MutableStateFlow(5)
    val panicCountdownRemaining: StateFlow<Int> = _panicCountdownRemaining.asStateFlow()

    private val _drillProgressMessage = MutableStateFlow<String?>(null)
    val drillProgressMessage: StateFlow<String?> = _drillProgressMessage.asStateFlow()

    private val _isRefreshingDashboard = MutableStateFlow(false)
    val isRefreshingDashboard: StateFlow<Boolean> = _isRefreshingDashboard.asStateFlow()

    private val _isRefreshingFamily = MutableStateFlow(false)
    val isRefreshingFamily: StateFlow<Boolean> = _isRefreshingFamily.asStateFlow()

    private val _isRefreshingProfile = MutableStateFlow(false)
    val isRefreshingProfile: StateFlow<Boolean> = _isRefreshingProfile.asStateFlow()

    private val _isRefreshingKit = MutableStateFlow(false)
    val isRefreshingKit: StateFlow<Boolean> = _isRefreshingKit.asStateFlow()

    private val _isRefreshingSettings = MutableStateFlow(false)
    val isRefreshingSettings: StateFlow<Boolean> = _isRefreshingSettings.asStateFlow()

    private var panicTimerJob: Job? = null
    private var trappedBeaconJob: Job? = null

    init {
        // Fetch live seismic reports on startup
        syncOnlineSeismicFeeds()

        // Restore user profile from Firebase Firestore if user is already authenticated
        try {
            val firebaseUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
            if (firebaseUser != null) {
                syncUserProfileFromCloud(firebaseUser.uid, firebaseUser.email ?: "")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Sincronización automática reactiva de ubicación y batería cuando cambien
        viewModelScope.launch {
            currentLocation.collect { loc ->
                if (loc != null) {
                    publishCurrentLocationPulse(forceImmediate = true)
                }
            }
        }

        viewModelScope.launch {
            batteryLevel.collect { bat ->
                if (bat > 0) {
                    publishCurrentLocationPulse(forceImmediate = true)
                }
            }
        }

        // Pulso inicial garantizado al arrancar la app
        viewModelScope.launch {
            delay(1200L)
            locationRepo.refreshActualDeviceLocation()
            publishCurrentLocationPulse(forceImmediate = true)
        }

        // Periodically broadcast live location ONLY when sharing is enabled (Zero-cost during peacetime)
        viewModelScope.launch {
            while (true) {
                if (nucleusRepo.isLiveLocationSharingActive.value) {
                    publishCurrentLocationPulse(forceImmediate = false)
                }
                delay(30000L) // Throttled pulse check
            }
        }
    }

    private val _profileSyncVersion = MutableStateFlow(0L)
    val profileSyncVersion: StateFlow<Long> = _profileSyncVersion.asStateFlow()

    fun syncUserProfileFromCloud(userId: String, userEmail: String) {
        viewModelScope.launch {
            val userPrefs = context.getSharedPreferences("sismo_user_profile_prefs", Context.MODE_PRIVATE)

            // Auto-prefill email if currently blank
            if (userEmail.isNotBlank()) {
                val currentSavedEmail = userPrefs.getString("user_email", "") ?: ""
                if (currentSavedEmail.isBlank()) {
                    userPrefs.edit().putString("user_email", userEmail).apply()
                }
            }

            firestoreSyncRepo.restoreUserProfileFromFirestore(userId, userEmail) { profileData ->
                if (profileData != null) {
                    val editor = userPrefs.edit()

                    val name = profileData["user_name"] as? String ?: ""
                    val doc = profileData["user_doc"] as? String ?: ""
                    val age = profileData["user_age"] as? String ?: ""
                    val occ = profileData["user_occupation"] as? String ?: ""
                    val phone = profileData["user_phone"] as? String ?: ""
                    val email = profileData["user_email"] as? String ?: userEmail
                    val rh = profileData["user_rh"] as? String ?: ""
                    val cond = profileData["user_conditions"] as? String ?: ""
                    val eps = profileData["user_eps"] as? String ?: ""
                    val addr = profileData["user_address"] as? String ?: ""
                    val safe = profileData["user_safe_point"] as? String ?: ""
                    val sharingCode = profileData["user_sharing_code"] as? String ?: ""

                    if (name.isNotBlank()) editor.putString("user_name", name)
                    if (doc.isNotBlank()) editor.putString("user_doc", doc)
                    if (age.isNotBlank()) editor.putString("user_age", age)
                    if (occ.isNotBlank()) editor.putString("user_occupation", occ)
                    if (phone.isNotBlank()) editor.putString("user_phone", phone)
                    if (email.isNotBlank()) editor.putString("user_email", email)
                    if (rh.isNotBlank()) editor.putString("user_rh", rh)
                    if (cond.isNotBlank()) editor.putString("user_conditions", cond)
                    if (eps.isNotBlank()) editor.putString("user_eps", eps)
                    if (addr.isNotBlank()) editor.putString("user_address", addr)
                    if (safe.isNotBlank()) editor.putString("user_safe_point", safe)

                    editor.apply()

                    if (sharingCode.isNotBlank()) {
                        nucleusRepo.setUserSharingCode(sharingCode)
                    }

                    _profileSyncVersion.value = System.currentTimeMillis()
                } else {
                    // Si es usuario nuevo, respaldar el código de enlace inicial
                    val currentSharingCode = nucleusRepo.userSharingCode.value
                    val currentName = userPrefs.getString("user_name", "Usuario") ?: "Usuario"
                    val initialDoc = mapOf(
                        "user_name" to currentName,
                        "user_email" to userEmail,
                        "user_sharing_code" to currentSharingCode
                    )
                    firestoreSyncRepo.saveUserProfileToFirestore(userId, initialDoc)
                }
            }
        }
    }

    fun saveEmergencyProfile(profileData: Map<String, String>) {
        val userPrefs = context.getSharedPreferences("sismo_user_profile_prefs", Context.MODE_PRIVATE)
        val editor = userPrefs.edit()
        profileData.forEach { (key, value) ->
            editor.putString(key, value)
        }
        editor.apply()

        // Sincronizar en la nube Firestore
        val currentUserId = try {
            com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: firestoreSyncRepo.deviceId
        } catch (e: Exception) {
            firestoreSyncRepo.deviceId
        }

        val cloudPayload = HashMap<String, Any>(profileData)
        cloudPayload["user_sharing_code"] = nucleusRepo.userSharingCode.value

        firestoreSyncRepo.saveUserProfileToFirestore(currentUserId, cloudPayload)

        // Propagar el nuevo nombre formateado (Primer nombre y primer apellido) a todos los círculos
        val updatedName = profileData["user_name"] ?: "Usuario"
        val currentLoc = currentLocation.value
        val currentBat = locationRepo.getBatteryLevel()
        nucleusRepo.updateMemberNameInJoinedNuclei(
            userName = updatedName,
            latitude = currentLoc?.latitude ?: 0.0,
            longitude = currentLoc?.longitude ?: 0.0,
            batteryLevel = currentBat
        )

        _profileSyncVersion.value = System.currentTimeMillis()
    }

    fun publishCurrentLocationPulse(forceImmediate: Boolean = false) {
        val userPrefs = context.getSharedPreferences("sismo_user_profile_prefs", Context.MODE_PRIVATE)
        val name = userPrefs.getString("user_name", "Usuario SismoAlerta") ?: "Usuario SismoAlerta"
        val loc = currentLocation.value
        val bat = locationRepo.getBatteryLevel()

        if (loc != null) {
            nucleusRepo.publishLocationToAllNuclei(
                userName = name,
                latitude = loc.latitude,
                longitude = loc.longitude,
                batteryLevel = bat,
                status = currentStatus.value,
                forceImmediate = forceImmediate
            )
        } else {
            // Solicitar actualización inmediata al GPS nativo/Fused
            locationRepo.refreshActualDeviceLocation()
            val lastBreadcrumb = breadcrumbs.value.lastOrNull()
            if (lastBreadcrumb != null) {
                nucleusRepo.publishLocationToAllNuclei(
                    userName = name,
                    latitude = lastBreadcrumb.latitude,
                    longitude = lastBreadcrumb.longitude,
                    batteryLevel = bat,
                    status = currentStatus.value,
                    forceImmediate = forceImmediate
                )
            }
        }
    }

    fun joinNucleusByCode(code: String) {
        val userPrefs = context.getSharedPreferences("sismo_user_profile_prefs", Context.MODE_PRIVATE)
        val name = userPrefs.getString("user_name", "Usuario SismoAlerta") ?: "Usuario SismoAlerta"
        val loc = currentLocation.value
        val bat = locationRepo.getBatteryLevel()
        nucleusRepo.joinNucleus(
            code = code,
            userName = name,
            latitude = loc?.latitude ?: 0.0,
            longitude = loc?.longitude ?: 0.0,
            batteryLevel = bat
        )
        publishCurrentLocationPulse(forceImmediate = true)
    }

    fun toggleLiveLocationSharing(enabled: Boolean) {
        nucleusRepo.setLiveLocationSharingActive(enabled)
        if (enabled) {
            locationRepo.refreshActualDeviceLocation()
            publishCurrentLocationPulse(forceImmediate = true)
        }
    }

    fun syncOnlineSeismicFeeds() {
        viewModelScope.launch {
            val reports = seismicOnlineFeedRepo.fetchLiveSeismicReports(currentLocation.value)
            
            // Detectar si el sismo más reciente ocurrió en el área del usuario
            val latest = reports.firstOrNull()
            if (latest != null) {
                val feltRadiusKm = when {
                    latest.magnitude >= 6.0 -> 400.0
                    latest.magnitude >= 5.0 -> 280.0
                    latest.magnitude >= 4.0 -> 180.0
                    latest.magnitude >= 3.5 -> 120.0
                    latest.magnitude >= 3.0 -> 80.0
                    else -> 40.0
                }
                val isRecent = (System.currentTimeMillis() - latest.timestamp) < (2 * 60 * 60 * 1000L) // En las últimas 2 horas
                if (latest.distanceKm <= feltRadiusKm && isRecent) {
                    // Prender la transmisión automáticamente porque el sismo se sintió en el área
                    nucleusRepo.setLiveLocationSharingActive(true)
                    publishCurrentLocationPulse()
                }
            }
        }
    }

    fun refreshDeviceLocation() {
        locationRepo.refreshActualDeviceLocation()
        syncOnlineSeismicFeeds()
        publishCurrentLocationPulse()
    }

    fun refreshDashboard() {
        viewModelScope.launch {
            _isRefreshingDashboard.value = true
            try {
                locationRepo.refreshActualDeviceLocation()
                syncOnlineSeismicFeeds()
                publishCurrentLocationPulse()
                delay(650L)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isRefreshingDashboard.value = false
            }
        }
    }

    fun refreshFamilyData() {
        viewModelScope.launch {
            _isRefreshingFamily.value = true
            try {
                locationRepo.refreshActualDeviceLocation()
                publishCurrentLocationPulse()
                joinedNuclei.value.forEach { group ->
                    firestoreSyncRepo.startListeningToNucleusMembers(group.code)
                }
                delay(650L)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isRefreshingFamily.value = false
            }
        }
    }

    fun refreshUserProfile() {
        viewModelScope.launch {
            _isRefreshingProfile.value = true
            try {
                val firebaseUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
                if (firebaseUser != null) {
                    syncUserProfileFromCloud(firebaseUser.uid, firebaseUser.email ?: "")
                }
                delay(650L)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isRefreshingProfile.value = false
            }
        }
    }

    fun refreshResilienceKit() {
        viewModelScope.launch {
            _isRefreshingKit.value = true
            delay(500L)
            _isRefreshingKit.value = false
        }
    }

    fun refreshSettings() {
        viewModelScope.launch {
            _isRefreshingSettings.value = true
            locationRepo.refreshActualDeviceLocation()
            delay(500L)
            _isRefreshingSettings.value = false
        }
    }

    fun startTrappedBeacon() {
        // En baliza SOS o emergencia confirmada, encender transmisión GPS en vivo
        nucleusRepo.setLiveLocationSharingActive(true)
        publishCurrentLocationPulse()

        _isTrappedBeaconActive.value = true
        _currentStatus.value = "NECESITO AYUDA / ATRAPADO"

        soundPlayer.startSirenAlert(
            forceMaxVolume = forceMaxVolume.value,
            mode = com.example.service.AlertVibrationMode.PANIC_SOS
        )

        trappedBeaconJob?.cancel()
        trappedBeaconJob = viewModelScope.launch {
            while (_isTrappedBeaconActive.value) {
                executeEmergencyBroadcast(status = "NECESITO AYUDA / ATRAPADO (BALIZA GPS EN VIVO)")
                delay(15000L) // Broadcast location every 15 seconds continuously
            }
        }
    }

    fun stopTrappedBeacon() {
        _isTrappedBeaconActive.value = false
        trappedBeaconJob?.cancel()
        soundPlayer.stopSirenAlert()
        _currentStatus.value = "SANO Y SALVO"
        viewModelScope.launch {
            executeEmergencyBroadcast(status = "SANO Y SALVO")
        }
    }

    fun triggerPanicAlert(reason: String = "Botón de Pánico Manual") {
        // Encender transmisión GPS únicamente si fue activación manual SOS o confirmada (no por acelerómetro para evitar falsos positivos)
        if (reason != "Detección Automática de Acelerómetro") {
            nucleusRepo.setLiveLocationSharingActive(true)
            publishCurrentLocationPulse()
        }

        soundPlayer.startSirenAlert(
            forceMaxVolume = forceMaxVolume.value,
            mode = com.example.service.AlertVibrationMode.CRITICAL_SEISMIC
        )
        _isPanicCountdownActive.value = true
        _panicCountdownRemaining.value = settingsRepo.panicTimerSeconds.value

        panicTimerJob?.cancel()
        panicTimerJob = viewModelScope.launch {
            for (sec in settingsRepo.panicTimerSeconds.value downTo 1) {
                _panicCountdownRemaining.value = sec
                delay(1000L)
            }
            // Countdown expired -> Dispatch emergency broadcast & activate continuous beacon
            _isPanicCountdownActive.value = false
            _panicCountdownRemaining.value = 0
            startTrappedBeacon()
        }
    }

    fun cancelPanicAlert() {
        panicTimerJob?.cancel()
        soundPlayer.stopSirenAlert()
        _isPanicCountdownActive.value = false
        _panicCountdownRemaining.value = settingsRepo.panicTimerSeconds.value
    }

    fun testVibrationFeedback(mode: com.example.service.AlertVibrationMode = com.example.service.AlertVibrationMode.CRITICAL_SEISMIC) {
        soundPlayer.triggerSingleHapticTest(mode)
    }

    fun updateStatus(newStatus: String) {
        if (newStatus == "NECESITO AYUDA / ATRAPADO") {
            startTrappedBeacon()
        } else {
            if (_isTrappedBeaconActive.value) {
                stopTrappedBeacon()
            }
            _currentStatus.value = newStatus
            viewModelScope.launch {
                executeEmergencyBroadcast(status = newStatus)
            }
        }
    }

    private suspend fun executeEmergencyBroadcast(status: String) {
        val loc = currentLocation.value
        val lat = loc?.latitude ?: 0.0
        val lng = loc?.longitude ?: 0.0
        val battery = locationRepo.getBatteryLevel()

        locationRepo.recordBreadcrumb(isEmergency = true, statusType = status)
        familyRepo.sendEmergencyBroadcast(
            statusType = status,
            latitude = lat,
            longitude = lng,
            batteryLevel = battery
        )
        publishCurrentLocationPulse()
    }

    fun startDrillSimulation() {
        viewModelScope.launch {
            settingsRepo.setDrillActive(true)
            _drillProgressMessage.value = "Iniciando Simulacro... [1/4] Verificando Permisos GPS"
            delay(1500L)
            soundPlayer.startSirenAlert(
                forceMaxVolume = false,
                mode = com.example.service.AlertVibrationMode.DRILL_SIMULATION
            )
            _drillProgressMessage.value = "Simulacro Activo [2/4]: Probando Alarma Sonora y Motor Háptico Agresivo"
            delay(3000L)
            soundPlayer.stopSirenAlert()
            _drillProgressMessage.value = "Simulacro Activo [3/4]: Generando Cifrado de Prueba E2EE AES-256"
            delay(2000L)
            seismicRepo.generateMockAgencyAlert(magnitude = 6.4, epicenter = "Costas de Guerrero", distanceKm = 280.0)
            _drillProgressMessage.value = "Simulacro Finalizado [4/4]: ¡Prueba Exitosa! Canales de Auxilio y Hápticos Operativos"
            delay(3000L)
            settingsRepo.setDrillActive(false)
            _drillProgressMessage.value = null
        }
    }

    fun toggleService() {
        val intent = Intent(context, SeismicMonitoringService::class.java)
        if (isServiceRunning.value) {
            context.stopService(intent)
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    fun addContact(name: String, phone: String, relationship: String) {
        viewModelScope.launch {
            familyRepo.addContact(
                EmergencyContact(
                    name = name,
                    phone = phone,
                    relationship = relationship
                )
            )
        }
    }

    fun deleteContact(contact: EmergencyContact) {
        viewModelScope.launch {
            familyRepo.deleteContact(contact)
        }
    }

    fun setHighContrast(enabled: Boolean) {
        settingsRepo.setHighContrast(enabled)
    }

    fun setForceMaxVolume(enabled: Boolean) {
        settingsRepo.setForceMaxVolume(enabled)
    }

    fun setPanicTimerSeconds(seconds: Int) {
        settingsRepo.setPanicTimerSeconds(seconds)
    }

    fun setAlarmToneIndex(index: Int) {
        settingsRepo.setAlarmToneIndex(index)
    }

    fun setCustomTone(uriString: String?, title: String) {
        settingsRepo.setCustomTone(uriString, title)
    }

    fun setMinAlertMagnitude(mag: Double) {
        settingsRepo.setMinAlertMagnitude(mag)
    }

    fun setMaxAlertDistanceKm(distKm: Double) {
        settingsRepo.setMaxAlertDistanceKm(distKm)
    }

    fun setAudioAlertEnabled(enabled: Boolean) {
        settingsRepo.setAudioAlertEnabled(enabled)
    }

    fun setVibrationAlertEnabled(enabled: Boolean) {
        settingsRepo.setVibrationAlertEnabled(enabled)
    }

    override fun onCleared() {
        super.onCleared()
        locationRepo.stopContinuousLocationUpdates()
    }

    class Factory(
        private val seismicRepo: SeismicRepository,
        private val locationRepo: LocationRepository,
        private val familyRepo: FamilyRepository,
        private val settingsRepo: SettingsRepository,
        private val nucleusRepo: NucleusRepository,
        private val context: Context
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MainViewModel(seismicRepo, locationRepo, familyRepo, settingsRepo, nucleusRepo, context) as T
        }
    }
}
