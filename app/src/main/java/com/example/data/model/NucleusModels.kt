package com.example.data.model

import java.util.Locale
import java.util.UUID

data class NucleusGroup(
    val code: String,
    val name: String,
    val ownerId: String = "",
    val ownerName: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val isDefault: Boolean = true
)

data class NucleusMember(
    val userId: String,
    val name: String,
    val phone: String = "",
    val relationship: String = "Miembro",
    val latitude: Double = 4.6097,
    val longitude: Double = -74.0817,
    val batteryLevel: Int = 85,
    val status: String = "SANO Y SALVO",
    val isLiveSharing: Boolean = true,
    val lastUpdated: Long = System.currentTimeMillis()
)

object PairingCodeUtils {
    fun generateUserSharingCode(): String {
        val hash = UUID.randomUUID().toString().replace("-", "").take(4).uppercase(Locale.ROOT)
        return "SISMO-$hash"
    }

    fun generateNucleusCode(category: String): String {
        val prefix = when (category.lowercase(Locale.ROOT)) {
            "familia" -> "FAM"
            "amigos" -> "AMG"
            "trabajo" -> "TRB"
            "vecinos" -> "VEC"
            else -> "NUC"
        }
        val num = (1000..9999).random()
        return "$prefix-$num"
    }
}
