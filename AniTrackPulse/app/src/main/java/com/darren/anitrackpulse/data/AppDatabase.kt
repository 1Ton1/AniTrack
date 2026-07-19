package com.darren.anitrackpulse.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [AnimeEntry::class], version = 3, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun animeDao(): AnimeDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE anime_entries ADD COLUMN isPinned INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE anime_entries ADD COLUMN isRewatching INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "anitrackpulse.db"
                ).addMigrations(MIGRATION_2_3)
                    // Safety net: if a device's local DB is on a version older than 2 (e.g. a very
                    // early test install) there is no explicit migration path recorded for it, and
                    // Room throws IllegalStateException on startup instead of crashing the app.
                    // Rebuilding the local tables in that rare case is an acceptable trade-off for a
                    // solo dev/test app.
                    .fallbackToDestructiveMigration()
                    .build().also { INSTANCE = it }
            }
        }
    }
}
