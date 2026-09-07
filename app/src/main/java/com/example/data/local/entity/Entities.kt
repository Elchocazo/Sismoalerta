package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "emergency_contacts")
data class EmergencyContact(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val phone: String,
    val relationship: String,
    val publicKeyHash: String = "E2EE-256-AES-DEFAULT",
    val notifyViaSms: Boolean = true,
    val notifyViaApp: Boolean = true
)

@Entity(tableName = "breadcrumb_locations")
data class BreadcrumbLocation(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float,
    val batteryLevel: Int,
    val speed: Float = 0f,
    val timestamp: Long = System.currentTimeMillis(),
    val isEmergencyPoint: Boolean = false
)

@Entity(tableName = "seismic_alert_events")
data class SeismicAlertEvent(
    @PrimaryKey val eventId: String,
    val magnitude: Double,
    val epicenter: String,
    val depthKm: Double,
    val distanceKm: Double,
    val intensityPga: Float,
    val status: String, // "CONFIRMED", "CANCELLED", "DRILL"
    val timestamp: Long,
    val agencySource: String = "SSN / SASMEX Official Feed"
)

@Entity(tableName = "emergency_alert_logs")
data class EmergencyAlertLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val statusType: String, // "SANO Y SALVO", "NECESITO AYUDA / ATRAPADO", "LESIONADO", "EVACUANDO"
    val latitude: Double,
    val longitude: Double,
    val batteryLevel: Int,
    val encryptedPayload: String,
    val payloadSizeBytes: Int,
    val smsSent: Boolean = false,
    val networkSent: Boolean = true
)
