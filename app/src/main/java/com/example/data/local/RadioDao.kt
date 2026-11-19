package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface RadioDao {

    @Query("SELECT * FROM favourite_stations ORDER BY name COLLATE NOCASE ASC")
    fun observeFavouritesAlphabetical(): Flow<List<FavouriteStationEntity>>

    @Query("SELECT * FROM favourite_stations ORDER BY CASE WHEN lastPlayedAt > 0 THEN lastPlayedAt ELSE addedAt END DESC, name COLLATE NOCASE ASC")
    fun observeFavouritesRecentlyPlayed(): Flow<List<FavouriteStationEntity>>

    @Query("SELECT stationId FROM favourite_stations")
    fun observeFavouriteIds(): Flow<List<String>>

    @Query("SELECT * FROM favourite_stations ORDER BY name COLLATE NOCASE ASC")
    suspend fun getAllFavouritesOnce(): List<FavouriteStationEntity>

    @Query("SELECT * FROM favourite_stations WHERE stationId = :stationId LIMIT 1")
    suspend fun getFavouriteById(stationId: String): FavouriteStationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFavourite(entity: FavouriteStationEntity)

    @Query("DELETE FROM favourite_stations WHERE stationId = :stationId")
    suspend fun deleteFavouriteById(stationId: String)

    @Query("UPDATE favourite_stations SET lastPlayedAt = :playedAt WHERE stationId = :stationId")
    suspend fun updateFavouriteLastPlayed(stationId: String, playedAt: Long)

    @Query("SELECT * FROM cached_stations ORDER BY rankOrder ASC")
    fun observeCachedStations(): Flow<List<CachedStationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCachedStations(entities: List<CachedStationEntity>)

    @Query("DELETE FROM cached_stations")
    suspend fun clearCachedStations()

    @Transaction
    suspend fun replaceCachedStations(entities: List<CachedStationEntity>) {
        clearCachedStations()
        upsertCachedStations(entities)
    }

    @Query("SELECT * FROM last_played_state WHERE id = 1 LIMIT 1")
    fun observeLastPlayedState(): Flow<PlaybackStateEntity?>

    @Query("SELECT * FROM last_played_state WHERE id = 1 LIMIT 1")
    suspend fun getLastPlayedStateOnce(): PlaybackStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLastPlayedState(state: PlaybackStateEntity)

    @Query("SELECT * FROM custom_stations ORDER BY createdAt DESC")
    fun observeCustomStations(): Flow<List<CustomStationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCustomStation(entity: CustomStationEntity)

    @Query("DELETE FROM custom_stations WHERE stationId = :stationId")
    suspend fun deleteCustomStationById(stationId: String)
}
