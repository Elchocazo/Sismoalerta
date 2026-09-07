package com.example

import com.example.data.model.SeismicAlertFilter
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SeismicAlertFilterTest {

    @Test
    fun shouldAlert_triggersWhenCriteriaMet() {
        val filter = SeismicAlertFilter(
            minMagnitude = 3.5,
            maxDistanceKm = 350.0,
            isAudioEnabled = true,
            isVibrationEnabled = true
        )

        // Sismo que cumple ambos criterios
        assertTrue(filter.shouldAlert(magnitude = 4.5, distanceKm = 120.0))
        assertTrue(filter.shouldAlert(magnitude = 3.5, distanceKm = 350.0))
    }

    @Test
    fun shouldAlert_rejectsWhenMagnitudeTooLow() {
        val filter = SeismicAlertFilter(
            minMagnitude = 3.5,
            maxDistanceKm = 350.0,
            isAudioEnabled = true,
            isVibrationEnabled = true
        )

        // Sismo menor al umbral
        assertFalse(filter.shouldAlert(magnitude = 3.2, distanceKm = 50.0))
        assertFalse(filter.shouldAlert(magnitude = 2.0, distanceKm = 10.0))
    }

    @Test
    fun shouldAlert_rejectsWhenDistanceTooFar() {
        val filter = SeismicAlertFilter(
            minMagnitude = 3.5,
            maxDistanceKm = 250.0,
            isAudioEnabled = true,
            isVibrationEnabled = true
        )

        // Sismo fuera del radio de proximidad
        assertFalse(filter.shouldAlert(magnitude = 5.0, distanceKm = 400.0))
    }

    @Test
    fun shouldAlert_supportsNationwideSetting() {
        val filter = SeismicAlertFilter(
            minMagnitude = 4.0,
            maxDistanceKm = 0.0, // 0 = sin límite / Todo el país
            isAudioEnabled = true,
            isVibrationEnabled = true
        )

        // Cualquier distancia dentro de Colombia debe alertar
        assertTrue(filter.shouldAlert(magnitude = 4.2, distanceKm = 950.0))
    }
}
