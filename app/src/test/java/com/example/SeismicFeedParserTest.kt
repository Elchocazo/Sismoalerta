package com.example

import android.location.Location
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.repository.SeismicOnlineFeedRepository
import com.example.data.repository.SeismicRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SeismicFeedParserTest {

    private lateinit var feedRepo: SeismicOnlineFeedRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = AppDatabase.getDatabase(context)
        val seismicRepo = SeismicRepository(db)
        feedRepo = SeismicOnlineFeedRepository(context, seismicRepo)
    }

    @Test
    fun parseSgcJson_parsesTotoroCaucaEarthquakesCorrectly() {
        val sgcJsonSample = """
        [
            {
                "SITIO":"Totoro - Cauca, Colombia",
                "MAGNITUD":3.1,
                "TIME_VALUE":1788040588,
                "ID_SISMO":"OVSPOP_2833552",
                "FECHA":"29/08/2026 - 04:56 PM",
                "PROFUNDIDAD":9,
                "ID":"OVSPOP_2833552",
                "TIENE_INFOGRAFIA":0,
                "LONGITUD":-76.34,
                "INICIADOR":"172.25.2.207-srv-siss7",
                "LATITUD":2.4,
                "I_MAX":4
            },
            {
                "SITIO":"Totoro - Cauca, Colombia",
                "MAGNITUD":3.5,
                "TIME_VALUE":1788038479,
                "ID_SISMO":"SGC2026raesti",
                "FECHA":"29/08/2026 - 04:21 PM",
                "PROFUNDIDAD":3,
                "ID":"SGC2026raesti",
                "TIENE_INFOGRAFIA":0,
                "LONGITUD":-76.37,
                "INICIADOR":"172.25.2.207-srv-siss7",
                "LATITUD":2.45,
                "I_MAX":5
            },
            {
                "SITIO":"Totoro - Cauca, Colombia",
                "MAGNITUD":3.9,
                "TIME_VALUE":1788038477,
                "ID_SISMO":"OVSPOP_2833532",
                "FECHA":"29/08/2026 - 04:21 PM",
                "PROFUNDIDAD":10,
                "ID":"OVSPOP_2833532",
                "TIENE_INFOGRAFIA":0,
                "LONGITUD":-76.33,
                "INICIADOR":"172.25.2.207-srv-siss7",
                "LATITUD":2.39,
                "I_MAX":5
            }
        ]
        """.trimIndent()

        val popayanLocation = Location("gps").apply {
            latitude = 2.4419
            longitude = -76.6063
        }

        val events = feedRepo.parseSgcJson(sgcJsonSample, popayanLocation)

        assertEquals(3, events.size)

        val firstEvent = events[0]
        assertEquals("SGC-OVSPOP_2833552", firstEvent.eventId)
        assertEquals("Totoro - Cauca, Colombia", firstEvent.epicenter)
        assertEquals(3.1, firstEvent.magnitude, 0.01)
        assertEquals(9.0, firstEvent.depthKm, 0.01)
        assertEquals("Servicio Geológico Colombiano (SGC Oficial)", firstEvent.agencySource)
        assertEquals(1788040588000L, firstEvent.timestamp)
        assertTrue(firstEvent.distanceKm < 45.0)

        val strongestEvent = events[2]
        assertEquals("SGC-OVSPOP_2833532", strongestEvent.eventId)
        assertEquals(3.9, strongestEvent.magnitude, 0.01)
        assertEquals(10.0, strongestEvent.depthKm, 0.01)
    }

    @Test
    fun calculateDistanceKm_calculatesAccurateDistance() {
        val dist = feedRepo.calculateDistanceKm(2.4419, -76.6063, 2.39, -76.33)
        assertTrue(dist in 20.0..40.0)
    }
}
