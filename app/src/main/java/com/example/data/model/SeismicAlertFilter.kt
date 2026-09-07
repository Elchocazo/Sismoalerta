package com.example.data.model

/**
 * Model representing the user's custom earthquake alert filters.
 *
 * @property minMagnitude The minimum Richter magnitude required to trigger an alert sound/notification (e.g. 3.5).
 * @property maxDistanceKm The maximum epicentral distance in kilometers to trigger an alert (-1.0 means nationwide / no distance limit).
 * @property isAudioEnabled Whether critical sirens are enabled.
 * @property isVibrationEnabled Whether aggressive haptic vibration is enabled.
 */
data class SeismicAlertFilter(
    val minMagnitude: Double = 3.5,
    val maxDistanceKm: Double = 350.0,
    val isAudioEnabled: Boolean = true,
    val isVibrationEnabled: Boolean = true
) {
    /**
     * Determines whether an earthquake with the given magnitude and distance meets the user's alert criteria.
     */
    fun shouldAlert(magnitude: Double, distanceKm: Double): Boolean {
        if (magnitude < minMagnitude) return false
        if (maxDistanceKm > 0.0 && distanceKm > maxDistanceKm) return false
        return true
    }
}
