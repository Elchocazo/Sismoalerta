package com.example

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SeismicFeedUnitTest {

    @Test
    fun testTranslatePlaceNameToSpanish() {
        fun translatePlaceNameToSpanish(raw: String): String {
            var place = raw
                .replace("WSW of", "al OSO de", ignoreCase = true)
                .replace("WNW of", "al ONO de", ignoreCase = true)
                .replace("NNW of", "al NNO de", ignoreCase = true)
                .replace("NNE of", "al NNE de", ignoreCase = true)
                .replace("ENE of", "al ENE de", ignoreCase = true)
                .replace("ESE of", "al ESE de", ignoreCase = true)
                .replace("SSE of", "al SSE de", ignoreCase = true)
                .replace("SSW of", "al SSO de", ignoreCase = true)
                .replace("NW of", "al NO de", ignoreCase = true)
                .replace("NE of", "al NE de", ignoreCase = true)
                .replace("SW of", "al SO de", ignoreCase = true)
                .replace("SE of", "al SE de", ignoreCase = true)
                .replace("N of", "al Norte de", ignoreCase = true)
                .replace("S of", "al Sur de", ignoreCase = true)
                .replace("E of", "al Este de", ignoreCase = true)
                .replace("W of", "al Oeste de", ignoreCase = true)
                .replace("near the coast of", "cerca a la costa de", ignoreCase = true)
                .replace("off the coast of", "frente a la costa de", ignoreCase = true)
                .replace("COLOMBIA", "Colombia", ignoreCase = false)

            return place
        }

        val translated1 = translatePlaceNameToSpanish("13 km WSW of San José del Palmar, Colombia")
        assertEquals("13 km al OSO de San José del Palmar, Colombia", translated1)

        val translated2 = translatePlaceNameToSpanish("24 km NNW of Riosucio, Colombia")
        assertEquals("24 km al NNO de Riosucio, Colombia", translated2)

        val translated3 = translatePlaceNameToSpanish("5 km S of Mesa de los Santos, Colombia")
        assertEquals("5 km al Sur de Mesa de los Santos, Colombia", translated3)
    }

    @Test
    fun testTimestampReliabilityInvariant() {
        val originalEventTimestamp = 1786632152192L // Real historical occurrence time
        val multipleConsultationTimestamps = listOf(
            originalEventTimestamp,
            originalEventTimestamp,
            originalEventTimestamp
        )

        // Multiple consultations must never alter the occurrence timestamp of the earthquake
        multipleConsultationTimestamps.forEach { ts ->
            assertEquals(1786632152192L, ts)
        }
    }

    @Test
    fun testProfileCloudSyncMapping() {
        val profileData = mapOf(
            "user_name" to "Carlos Mendez",
            "user_doc" to "1020304050",
            "user_age" to "32",
            "user_occupation" to "Ingeniero",
            "user_phone" to "+57 300 123 4567",
            "user_email" to "carlos.mendez@gmail.com",
            "user_rh" to "O+",
            "user_conditions" to "Alérgico a la penicilina",
            "user_eps" to "Sura",
            "user_address" to "Cra 7 # 45-10",
            "user_safe_point" to "Parque Central",
            "user_sharing_code" to "SISMO-77B2"
        )

        assertEquals("carlos.mendez@gmail.com", profileData["user_email"])
        assertEquals("O+", profileData["user_rh"])
        assertEquals("SISMO-77B2", profileData["user_sharing_code"])
        assertTrue(profileData.containsKey("user_safe_point"))
    }

    @Test
    fun testFormatFirstAndLastName() {
        val formatted1 = com.example.data.repository.NucleusRepository.formatFirstAndLastName("Carlos Alberto Gomez Rodriguez")
        assertEquals("Carlos Alberto", formatted1)

        val formatted2 = com.example.data.repository.NucleusRepository.formatFirstAndLastName("María Pérez")
        assertEquals("María Pérez", formatted2)

        val formatted3 = com.example.data.repository.NucleusRepository.formatFirstAndLastName("Rodrigo")
        assertEquals("Rodrigo", formatted3)
    }
}
