package com.example

import android.app.Application
import com.example.data.local.AppDatabase
import com.example.data.local.entity.EmergencyContact
import com.example.data.repository.FamilyRepository
import com.example.data.repository.FirestoreSyncRepository
import com.example.data.repository.LocationRepository
import com.example.data.repository.SeismicOnlineFeedRepository
import com.example.data.repository.SeismicRepository
import com.example.data.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

import com.example.data.repository.NucleusRepository

class SismoAlertaApp : Application() {

    lateinit var database: AppDatabase
        private set

    lateinit var seismicRepository: SeismicRepository
        private set

    lateinit var seismicOnlineFeedRepository: SeismicOnlineFeedRepository
        private set

    lateinit var locationRepository: LocationRepository
        private set

    lateinit var familyRepository: FamilyRepository
        private set

    lateinit var settingsRepository: SettingsRepository
        private set

    lateinit var firestoreSyncRepository: FirestoreSyncRepository
        private set

    lateinit var nucleusRepository: NucleusRepository
        private set

    override fun onCreate() {
        super.onCreate()

        database = AppDatabase.getDatabase(this)
        firestoreSyncRepository = FirestoreSyncRepository(this)
        nucleusRepository = NucleusRepository(this, firestoreSyncRepository)
        seismicRepository = SeismicRepository(database)
        seismicOnlineFeedRepository = SeismicOnlineFeedRepository(this, seismicRepository)
        locationRepository = LocationRepository(this, database, firestoreSyncRepository)
        familyRepository = FamilyRepository(this, database)
        settingsRepository = SettingsRepository(this)

        // Iniciar inmediatamente la vigilancia resiliente 24/7 en segundo plano
        com.example.receiver.SeismicAlarmReceiver.schedulePeriodicCheck(this)

        // Purge legacy demo contacts and mock seismic events for production readiness
        CoroutineScope(Dispatchers.IO).launch {
            try {
                seismicRepository.purgeMockSeismicEvents()
            } catch (e: Exception) {
                e.printStackTrace()
            }

            val existingContacts = familyRepository.contacts.first()
            for (contact in existingContacts) {
                if (contact.name.contains("Ana María", ignoreCase = true) ||
                    contact.name.contains("Carlos (Hermano)", ignoreCase = true) ||
                    contact.name.contains("Roberto (Papá)", ignoreCase = true)
                ) {
                    familyRepository.deleteContact(contact)
                }
            }
        }
    }
}
