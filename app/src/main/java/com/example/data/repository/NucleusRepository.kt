package com.example.data.repository

import android.content.Context
import com.example.data.model.NucleusGroup
import com.example.data.model.NucleusMember
import com.example.data.model.PairingCodeUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

class NucleusRepository(
    private val context: Context,
    private val firestoreSyncRepo: FirestoreSyncRepository
) {
    private val prefs = context.getSharedPreferences("sismo_nucleus_prefs", Context.MODE_PRIVATE)

    private val _userSharingCode = MutableStateFlow(
        prefs.getString("user_sharing_code", null) ?: run {
            val code = PairingCodeUtils.generateUserSharingCode()
            prefs.edit().putString("user_sharing_code", code).apply()
            code
        }
    )
    val userSharingCode: StateFlow<String> = _userSharingCode.asStateFlow()

    fun setUserSharingCode(code: String) {
        if (code.isNotBlank()) {
            prefs.edit().putString("user_sharing_code", code).apply()
            _userSharingCode.value = code
        }
    }

    private val _joinedNuclei = MutableStateFlow<List<NucleusGroup>>(emptyList())
    val joinedNuclei: StateFlow<List<NucleusGroup>> = _joinedNuclei.asStateFlow()

    private val _isLiveLocationSharingActive = MutableStateFlow(
        prefs.getBoolean("is_live_location_sharing_active", true)
    )
    val isLiveLocationSharingActive: StateFlow<Boolean> = _isLiveLocationSharingActive.asStateFlow()

    val nucleusMembersMap: StateFlow<Map<String, List<NucleusMember>>> = firestoreSyncRepo.nucleusMembersMap

    init {
        loadOrInitializeNuclei()
    }

    private fun loadOrInitializeNuclei() {
        val storedCodesStr = prefs.getString("joined_nuclei_codes", null)
        val defaultCategories = listOf("Familia", "Amigos", "Trabajo", "Vecinos")

        val nucleiList = mutableListOf<NucleusGroup>()

        if (storedCodesStr.isNullOrBlank()) {
            // Initialize default category codes for the user
            defaultCategories.forEach { category ->
                val codeKey = "code_category_$category"
                var code = prefs.getString(codeKey, null)
                if (code == null) {
                    code = PairingCodeUtils.generateNucleusCode(category)
                    prefs.edit().putString(codeKey, code).apply()
                }
                nucleiList.add(NucleusGroup(code = code, name = category))
            }
            saveJoinedCodesToPrefs(nucleiList.map { it.code })
        } else {
            val codes = storedCodesStr.split(",").map { it.trim() }.filter { it.isNotBlank() }
            codes.forEach { code ->
                val name = when {
                    code.startsWith("FAM") -> "Familia"
                    code.startsWith("AMG") -> "Amigos"
                    code.startsWith("TRB") -> "Trabajo"
                    code.startsWith("VEC") -> "Vecinos"
                    else -> "Núcleo $code"
                }
                nucleiList.add(NucleusGroup(code = code, name = name))
            }
        }

        _joinedNuclei.value = nucleiList

        // Subscribe Firestore listener to all active nucleus codes
        nucleiList.forEach { group ->
            firestoreSyncRepo.startListeningToNucleusMembers(group.code)
        }
    }

    private fun saveJoinedCodesToPrefs(codes: List<String>) {
        prefs.edit().putString("joined_nuclei_codes", codes.joinToString(",")).apply()
    }

    fun joinNucleus(
        code: String,
        userName: String,
        latitude: Double = 0.0,
        longitude: Double = 0.0,
        batteryLevel: Int = 0
    ): NucleusGroup {
        val cleanCode = code.trim().uppercase(Locale.ROOT)
        val currentList = _joinedNuclei.value.toMutableList()

        var existing = currentList.find { it.code == cleanCode }
        if (existing == null) {
            val name = when {
                cleanCode.startsWith("FAM") -> "Familia ($cleanCode)"
                cleanCode.startsWith("AMG") -> "Amigos ($cleanCode)"
                cleanCode.startsWith("TRB") -> "Trabajo ($cleanCode)"
                cleanCode.startsWith("VEC") -> "Vecinos ($cleanCode)"
                else -> "Núcleo $cleanCode"
            }
            existing = NucleusGroup(code = cleanCode, name = name, isDefault = false)
            currentList.add(existing)
            _joinedNuclei.value = currentList
            saveJoinedCodesToPrefs(currentList.map { it.code })
        }

        // Register user in Firestore for this nucleus with actual location if available
        val currentUserId = firestoreSyncRepo.deviceId
        firestoreSyncRepo.joinNucleusInFirestore(
            nucleusCode = cleanCode,
            userId = currentUserId,
            userName = userName,
            relationship = existing.name,
            latitude = latitude,
            longitude = longitude,
            batteryLevel = batteryLevel
        )

        return existing
    }

    private var lastPublishedLat = 0.0
    private var lastPublishedLng = 0.0
    private var lastPublishedStatus = ""
    private var lastPublishedBattery = -1
    private var lastPublishedTime = 0L

    fun setLiveLocationSharingActive(active: Boolean) {
        prefs.edit().putBoolean("is_live_location_sharing_active", active).apply()
        _isLiveLocationSharingActive.value = active
    }

    fun publishLocationToAllNuclei(
        userName: String,
        latitude: Double,
        longitude: Double,
        batteryLevel: Int,
        status: String,
        forceImmediate: Boolean = false
    ) {
        if (!_isLiveLocationSharingActive.value && !forceImmediate) return

        val now = System.currentTimeMillis()
        val elapsed = now - lastPublishedTime

        // Calculate distance moved in meters
        val results = FloatArray(1)
        if (lastPublishedLat != 0.0 && lastPublishedLng != 0.0) {
            android.location.Location.distanceBetween(
                lastPublishedLat, lastPublishedLng,
                latitude, longitude,
                results
            )
        } else {
            results[0] = 999f
        }

        val distanceMovedMeters = results[0]
        val statusChanged = status != lastPublishedStatus
        val batteryChanged = Math.abs(batteryLevel - lastPublishedBattery) >= 1

        // Estrategia eficiente de Firestore: Publicar si:
        // 1. Es inmediato (forceImmediate: tap de usuario, arranque, SOS, pull-to-refresh)
        // 2. Se movió más de 10 metros
        // 3. Cambió el estado de auxilio
        // 4. Cambió la batería en al menos 1%
        // 5. Han pasado más de 60 segundos
        if (!forceImmediate && distanceMovedMeters < 10f && !statusChanged && !batteryChanged && elapsed < 60000L) {
            return
        }

        lastPublishedLat = latitude
        lastPublishedLng = longitude
        lastPublishedStatus = status
        lastPublishedBattery = batteryLevel
        lastPublishedTime = now

        val currentUserId = firestoreSyncRepo.deviceId
        _joinedNuclei.value.forEach { group ->
            firestoreSyncRepo.publishMemberLocationToNucleus(
                nucleusCode = group.code,
                userId = currentUserId,
                userName = userName,
                phone = "",
                relationship = group.name,
                latitude = latitude,
                longitude = longitude,
                batteryLevel = batteryLevel,
                status = status,
                isLiveSharing = _isLiveLocationSharingActive.value
            )
        }
    }

    /**
     * Actualiza el nombre del usuario en todos los círculos y núcleos activos en Firestore
     */
    fun updateMemberNameInJoinedNuclei(
        userName: String,
        latitude: Double = 0.0,
        longitude: Double = 0.0,
        batteryLevel: Int = 0
    ) {
        val currentUserId = firestoreSyncRepo.deviceId
        val formatted = formatFirstAndLastName(userName)
        _joinedNuclei.value.forEach { group ->
            firestoreSyncRepo.joinNucleusInFirestore(
                nucleusCode = group.code,
                userId = currentUserId,
                userName = formatted,
                relationship = group.name,
                latitude = latitude,
                longitude = longitude,
                batteryLevel = batteryLevel
            )
        }
    }

    companion object {
        fun formatFirstAndLastName(fullName: String): String {
            val clean = fullName.trim()
            if (clean.isBlank()) return "Usuario"
            val parts = clean.split("\\s+".toRegex()).filter { it.isNotBlank() }
            return when {
                parts.size == 1 -> parts[0]
                parts.size >= 2 -> "${parts[0]} ${parts[1]}"
                else -> clean
            }
        }
    }
}
