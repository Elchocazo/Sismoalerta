package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.BreadcrumbLocation
import com.example.data.local.entity.EmergencyAlertLog
import com.example.data.local.entity.EmergencyContact
import com.example.data.local.entity.SeismicAlertEvent
import kotlinx.coroutines.flow.Flow

@Dao
interface ContactDao {
    @Query("SELECT * FROM emergency_contacts ORDER BY name ASC")
    fun getAllContacts(): Flow<List<EmergencyContact>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContact(contact: EmergencyContact): Long

    @Update
    suspend fun updateContact(contact: EmergencyContact)

    @Delete
    suspend fun deleteContact(contact: EmergencyContact)

    @Query("SELECT * FROM emergency_contacts")
    suspend fun getContactsListSync(): List<EmergencyContact>
}

@Dao
interface BreadcrumbDao {
    @Query("SELECT * FROM breadcrumb_locations ORDER BY timestamp DESC LIMIT 100")
    fun getRecentBreadcrumbs(): Flow<List<BreadcrumbLocation>>

    @Query("SELECT * FROM breadcrumb_locations ORDER BY timestamp DESC LIMIT 3")
    suspend fun getLastThreeBreadcrumbs(): List<BreadcrumbLocation>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBreadcrumb(breadcrumb: BreadcrumbLocation): Long

    @Query("DELETE FROM breadcrumb_locations WHERE timestamp < :cutoffTimestamp")
    suspend fun purgeOldBreadcrumbs(cutoffTimestamp: Long)
}

@Dao
interface SeismicDao {
    @Query("SELECT * FROM seismic_alert_events ORDER BY timestamp DESC LIMIT 50")
    fun getAllSeismicEvents(): Flow<List<SeismicAlertEvent>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSeismicEvent(event: SeismicAlertEvent)

    @Query("SELECT * FROM seismic_alert_events ORDER BY timestamp DESC LIMIT 1")
    fun getLatestSeismicEvent(): Flow<SeismicAlertEvent?>

    @Query("DELETE FROM seismic_alert_events WHERE eventId LIKE 'SGC-COL-%' OR eventId LIKE 'ONLINE-EQ-%'")
    suspend fun purgeMockSeismicEvents()
}

@Dao
interface EmergencyAlertDao {
    @Query("SELECT * FROM emergency_alert_logs ORDER BY timestamp DESC")
    fun getAllAlertLogs(): Flow<List<EmergencyAlertLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlertLog(log: EmergencyAlertLog): Long
}
