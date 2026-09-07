package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.local.dao.BreadcrumbDao
import com.example.data.local.dao.ContactDao
import com.example.data.local.dao.EmergencyAlertDao
import com.example.data.local.dao.SeismicDao
import com.example.data.local.entity.BreadcrumbLocation
import com.example.data.local.entity.EmergencyAlertLog
import com.example.data.local.entity.EmergencyContact
import com.example.data.local.entity.SeismicAlertEvent

@Database(
    entities = [
        EmergencyContact::class,
        BreadcrumbLocation::class,
        SeismicAlertEvent::class,
        EmergencyAlertLog::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun contactDao(): ContactDao
    abstract fun breadcrumbDao(): BreadcrumbDao
    abstract fun seismicDao(): SeismicDao
    abstract fun emergencyAlertDao(): EmergencyAlertDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "sismo_alerta_db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
