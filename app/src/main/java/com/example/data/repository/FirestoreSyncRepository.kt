package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.data.crypto.CryptoUtils
import com.example.data.model.NucleusGroup
import com.example.data.model.NucleusMember
import com.example.data.model.PairingCodeUtils
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

class FirestoreSyncRepository(private val context: Context) {

    private val firestore: FirebaseFirestore? by lazy {
        try {
            val instance = FirebaseFirestore.getInstance()
            try {
                val settings = FirebaseFirestoreSettings.Builder()
                    .setPersistenceEnabled(true)
                    .build()
                instance.firestoreSettings = settings
            } catch (e: Exception) {
                Log.e("FirestoreSyncRepo", "Firestore persistence config: ${e.message}")
            }
            instance
        } catch (e: Exception) {
            Log.w("FirestoreSyncRepo", "Firebase not initialized: ${e.message}")
            null
        }
    }

    val deviceId: String by lazy {
        val prefs = context.getSharedPreferences("sismo_device_prefs", Context.MODE_PRIVATE)
        var id = prefs.getString("device_id", null)
        if (id == null) {
            id = "DEV-" + UUID.randomUUID().toString().take(8)
            prefs.edit().putString("device_id", id).apply()
        }
        id
    }

    private val _syncStateMessage = MutableStateFlow("Persistencia Local Offline Firestore Activa")
    val syncStateMessage: StateFlow<String> = _syncStateMessage.asStateFlow()

    private val _isOnlineSynced = MutableStateFlow(true)
    val isOnlineSynced: StateFlow<Boolean> = _isOnlineSynced.asStateFlow()

    private val _nucleusMembersMap = MutableStateFlow<Map<String, List<NucleusMember>>>(emptyMap())
    val nucleusMembersMap: StateFlow<Map<String, List<NucleusMember>>> = _nucleusMembersMap.asStateFlow()

    private val activeListeners = mutableMapOf<String, ListenerRegistration>()

    /**
     * Encrypts location using AES-256-GCM and persists it to Firebase Firestore.
     * When network is unavailable, Firestore stores the data in local persistent cache
     * and automatically synchronizes with cloud Firestore once connection is restored.
     */
    fun syncEncryptedLocation(
        latitude: Double,
        longitude: Double,
        batteryLevel: Int,
        isEmergency: Boolean,
        statusType: String
    ) {
        val timestamp = System.currentTimeMillis()
        val rawPayloadJson = """{"lat":$latitude,"lng":$longitude,"bat":$batteryLevel,"status":"$statusType","emergency":$isEmergency,"ts":$timestamp}"""

        // Encrypt with AES-256-GCM
        val encryptedPayload = CryptoUtils.encryptAES256GCM(rawPayloadJson)

        val locationDoc = hashMapOf(
            "deviceId" to deviceId,
            "encryptedData" to encryptedPayload,
            "timestamp" to timestamp,
            "isEmergency" to isEmergency,
            "status" to statusType,
            "encryptionAlgorithm" to "AES-256-GCM"
        )

        _syncStateMessage.value = "Almacenando ubicación cifrada offline..."

        firestore?.collection("encrypted_user_locations")
            ?.document(deviceId)
            ?.set(locationDoc)
            ?.addOnSuccessListener {
                _syncStateMessage.value = "Sincronizado con la Nube Firestore ✓"
                _isOnlineSynced.value = true
            }
            ?.addOnFailureListener { e ->
                _syncStateMessage.value = "Guardado en Caché Offline (Auto-Sincronización pendiente) 📶"
                _isOnlineSynced.value = false
            }
    }

    /**
     * Publishes live location to a specific nucleus group in Firestore (Find My Kids feature).
     */
    fun publishMemberLocationToNucleus(
        nucleusCode: String,
        userId: String,
        userName: String,
        phone: String,
        relationship: String,
        latitude: Double,
        longitude: Double,
        batteryLevel: Int,
        status: String,
        isLiveSharing: Boolean
    ) {
        val memberDoc = hashMapOf(
            "userId" to userId,
            "name" to userName,
            "phone" to phone,
            "relationship" to relationship,
            "latitude" to latitude,
            "longitude" to longitude,
            "batteryLevel" to batteryLevel,
            "status" to status,
            "isLiveSharing" to isLiveSharing,
            "lastUpdated" to System.currentTimeMillis()
        )

        firestore?.collection("nuclei")
            ?.document(nucleusCode)
            ?.collection("members")
            ?.document(userId)
            ?.set(memberDoc)
            ?.addOnSuccessListener {
                _syncStateMessage.value = "Ubicación en Vivo transmitida a $nucleusCode ✓"
                _isOnlineSynced.value = true
            }
            ?.addOnFailureListener {
                _syncStateMessage.value = "Ubicación en Caché para $nucleusCode (Auto-Sync) 📶"
                _isOnlineSynced.value = false
            }
    }

    /**
     * Listen in real-time to group members of a given nucleus code.
     */
    fun startListeningToNucleusMembers(nucleusCode: String) {
        if (activeListeners.containsKey(nucleusCode)) return

        val listener = firestore?.collection("nuclei")
            ?.document(nucleusCode)
            ?.collection("members")
            ?.addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("FirestoreSyncRepo", "Listen error for $nucleusCode: ${error.message}")
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val members = snapshot.documents.mapNotNull { doc ->
                        val uId = doc.getString("userId") ?: doc.id
                        val name = doc.getString("name") ?: "Miembro"
                        val phone = doc.getString("phone") ?: ""
                        val rel = doc.getString("relationship") ?: "Miembro"
                        val lat = doc.getDouble("latitude") ?: 4.6097
                        val lng = doc.getDouble("longitude") ?: -74.0817
                        val bat = doc.getLong("batteryLevel")?.toInt() ?: 85
                        val st = doc.getString("status") ?: "SANO Y SALVO"
                        val isSharing = doc.getBoolean("isLiveSharing") ?: true
                        val ts = doc.getLong("lastUpdated") ?: System.currentTimeMillis()

                        NucleusMember(
                            userId = uId,
                            name = name,
                            phone = phone,
                            relationship = rel,
                            latitude = lat,
                            longitude = lng,
                            batteryLevel = bat,
                            status = st,
                            isLiveSharing = isSharing,
                            lastUpdated = ts
                        )
                    }

                    val currentMap = _nucleusMembersMap.value.toMutableMap()
                    currentMap[nucleusCode] = members
                    _nucleusMembersMap.value = currentMap
                }
            }

        if (listener != null) {
            activeListeners[nucleusCode] = listener
        }
    }

    /**
     * Register user in a nucleus group in Firestore.
     */
    fun joinNucleusInFirestore(
        nucleusCode: String,
        userId: String,
        userName: String,
        relationship: String = "Miembro"
    ) {
        val nucleusDoc = hashMapOf(
            "code" to nucleusCode,
            "lastJoinedAt" to System.currentTimeMillis()
        )
        firestore?.collection("nuclei")?.document(nucleusCode)?.set(nucleusDoc)

        // Add member document
        publishMemberLocationToNucleus(
            nucleusCode = nucleusCode,
            userId = userId,
            userName = userName,
            phone = "",
            relationship = relationship,
            latitude = 4.6097,
            longitude = -74.0817,
            batteryLevel = 85,
            status = "SANO Y SALVO",
            isLiveSharing = true
        )

        // Start listening to real-time updates for this nucleus
        startListeningToNucleusMembers(nucleusCode)
    }

    fun stopListening(nucleusCode: String) {
        activeListeners[nucleusCode]?.remove()
        activeListeners.remove(nucleusCode)
    }

    /**
     * Guarda la ficha médica y de emergencia del usuario en la nube (Firestore) para persistencia total.
     */
    fun saveUserProfileToFirestore(
        userId: String,
        profileData: Map<String, Any>
    ) {
        if (userId.isBlank()) return
        val docData = HashMap(profileData)
        docData["lastSavedAt"] = System.currentTimeMillis()

        firestore?.collection("user_profiles")
            ?.document(userId)
            ?.set(docData)
            ?.addOnSuccessListener {
                Log.d("FirestoreSyncRepo", "Ficha médica y de emergencia sincronizada con Firestore exitosamente ✓")
            }
            ?.addOnFailureListener { e ->
                Log.w("FirestoreSyncRepo", "Error al sincronizar ficha médica en Firestore: ${e.message}")
            }
    }

    /**
     * Recupera la ficha médica y de emergencia desde Firestore tras una reinstalación o inicio de sesión.
     */
    fun restoreUserProfileFromFirestore(
        userId: String,
        userEmail: String = "",
        onRestored: (Map<String, Any>?) -> Unit
    ) {
        if (userId.isBlank()) {
            onRestored(null)
            return
        }

        firestore?.collection("user_profiles")
            ?.document(userId)
            ?.get()
            ?.addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val data = document.data
                    onRestored(data)
                } else if (userEmail.isNotBlank()) {
                    // Intento de búsqueda secundaria por correo si el userId cambió
                    firestore?.collection("user_profiles")
                        ?.whereEqualTo("user_email", userEmail)
                        ?.limit(1)
                        ?.get()
                        ?.addOnSuccessListener { querySnap ->
                            if (querySnap != null && !querySnap.isEmpty) {
                                val data = querySnap.documents.first().data
                                onRestored(data)
                            } else {
                                onRestored(null)
                            }
                        }
                        ?.addOnFailureListener {
                            onRestored(null)
                        }
                } else {
                    onRestored(null)
                }
            }
            ?.addOnFailureListener { e ->
                Log.w("FirestoreSyncRepo", "Error al recuperar ficha médica: ${e.message}")
                onRestored(null)
            }
    }
}

