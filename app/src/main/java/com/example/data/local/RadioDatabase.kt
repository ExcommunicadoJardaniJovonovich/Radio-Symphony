package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        FavouriteStationEntity::class,
        CachedStationEntity::class,
        PlaybackStateEntity::class,
        CustomStationEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class RadioDatabase : RoomDatabase() {
    abstract fun radioDao(): RadioDao

    companion object {
        @Volatile
        private var INSTANCE: RadioDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `cached_stations` (
                        `stationId` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `frequency` TEXT NOT NULL,
                        `band` TEXT NOT NULL,
                        `district` TEXT NOT NULL,
                        `language` TEXT NOT NULL,
                        `genre` TEXT NOT NULL,
                        `streamUrl` TEXT NOT NULL,
                        `fallbackStreamUrl` TEXT NOT NULL,
                        `accentColorHex` INTEGER NOT NULL,
                        `logoCode` TEXT NOT NULL,
                        `tagline` TEXT NOT NULL,
                        `rankOrder` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`stationId`)
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `custom_stations` (
                        `stationId` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `frequency` TEXT NOT NULL,
                        `band` TEXT NOT NULL,
                        `district` TEXT NOT NULL,
                        `language` TEXT NOT NULL,
                        `genre` TEXT NOT NULL,
                        `streamUrl` TEXT NOT NULL,
                        `accentColorHex` INTEGER NOT NULL,
                        `logoCode` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`stationId`)
                    )
                    """.trimIndent()
                )
            }
        }

        fun getInstance(context: Context): RadioDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    RadioDatabase::class.java,
                    "radio_symphony.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
