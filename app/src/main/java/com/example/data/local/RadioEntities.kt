package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.data.model.FavouriteStationItem
import com.example.data.model.RadioBand
import com.example.data.model.RadioStation

@Entity(tableName = "favourite_stations")
data class FavouriteStationEntity(
    @PrimaryKey val stationId: String,
    val name: String,
    val frequency: String,
    val band: String,
    val district: String,
    val language: String,
    val genre: String,
    val streamUrl: String,
    val fallbackStreamUrl: String,
    val accentColorHex: Long,
    val logoCode: String,
    val tagline: String,
    val addedAt: Long,
    val lastPlayedAt: Long
) {
    fun toDomainItem(): FavouriteStationItem = FavouriteStationItem(
        station = RadioStation(
            id = stationId,
            name = name,
            frequency = frequency,
            band = RadioBand.fromString(band),
            district = district,
            language = language,
            genre = genre,
            streamUrl = streamUrl,
            fallbackStreamUrl = fallbackStreamUrl,
            accentColorHex = accentColorHex,
            logoCode = logoCode,
            tagline = tagline
        ),
        addedAt = addedAt,
        lastPlayedAt = lastPlayedAt
    )

    companion object {
        fun fromStation(
            station: RadioStation,
            addedAt: Long = System.currentTimeMillis(),
            lastPlayedAt: Long = 0L
        ): FavouriteStationEntity = FavouriteStationEntity(
            stationId = station.id,
            name = station.name,
            frequency = station.frequency,
            band = station.band.name,
            district = station.district,
            language = station.language,
            genre = station.genre,
            streamUrl = station.streamUrl,
            fallbackStreamUrl = station.fallbackStreamUrl,
            accentColorHex = station.accentColorHex,
            logoCode = station.logoCode,
            tagline = station.tagline,
            addedAt = addedAt,
            lastPlayedAt = lastPlayedAt
        )
    }
}

@Entity(tableName = "cached_stations")
data class CachedStationEntity(
    @PrimaryKey val stationId: String,
    val name: String,
    val frequency: String,
    val band: String,
    val district: String,
    val language: String,
    val genre: String,
    val streamUrl: String,
    val fallbackStreamUrl: String,
    val accentColorHex: Long,
    val logoCode: String,
    val tagline: String,
    val rankOrder: Int,
    val updatedAt: Long
) {
    fun toStation(): RadioStation = RadioStation(
        id = stationId,
        name = name,
        frequency = frequency,
        band = RadioBand.fromString(band),
        district = district,
        language = language,
        genre = genre,
        streamUrl = streamUrl,
        fallbackStreamUrl = fallbackStreamUrl,
        accentColorHex = accentColorHex,
        logoCode = logoCode,
        tagline = tagline,
        isLiveDirectory = true
    )

    companion object {
        fun fromStation(
            station: RadioStation,
            rankOrder: Int,
            updatedAt: Long = System.currentTimeMillis()
        ): CachedStationEntity = CachedStationEntity(
            stationId = station.id,
            name = station.name,
            frequency = station.frequency,
            band = station.band.name,
            district = station.district,
            language = station.language,
            genre = station.genre,
            streamUrl = station.streamUrl,
            fallbackStreamUrl = station.fallbackStreamUrl,
            accentColorHex = station.accentColorHex,
            logoCode = station.logoCode,
            tagline = station.tagline,
            rankOrder = rankOrder,
            updatedAt = updatedAt
        )
    }
}

@Entity(tableName = "last_played_state")
data class PlaybackStateEntity(
    @PrimaryKey val id: Int = SINGLETON_ID,
    val stationId: String,
    val name: String,
    val frequency: String,
    val band: String,
    val district: String,
    val language: String,
    val genre: String,
    val streamUrl: String,
    val fallbackStreamUrl: String,
    val accentColorHex: Long,
    val logoCode: String,
    val tagline: String,
    val playedAt: Long
) {
    fun toStation(): RadioStation = RadioStation(
        id = stationId,
        name = name,
        frequency = frequency,
        band = RadioBand.fromString(band),
        district = district,
        language = language,
        genre = genre,
        streamUrl = streamUrl,
        fallbackStreamUrl = fallbackStreamUrl,
        accentColorHex = accentColorHex,
        logoCode = logoCode,
        tagline = tagline
    )

    companion object {
        const val SINGLETON_ID = 1

        fun fromStation(
            station: RadioStation,
            playedAt: Long = System.currentTimeMillis()
        ): PlaybackStateEntity = PlaybackStateEntity(
            id = SINGLETON_ID,
            stationId = station.id,
            name = station.name,
            frequency = station.frequency,
            band = station.band.name,
            district = station.district,
            language = station.language,
            genre = station.genre,
            streamUrl = station.streamUrl,
            fallbackStreamUrl = station.fallbackStreamUrl,
            accentColorHex = station.accentColorHex,
            logoCode = station.logoCode,
            tagline = station.tagline,
            playedAt = playedAt
        )
    }
}

@Entity(tableName = "custom_stations")
data class CustomStationEntity(
    @PrimaryKey val stationId: String,
    val name: String,
    val frequency: String,
    val band: String,
    val district: String,
    val language: String,
    val genre: String,
    val streamUrl: String,
    val accentColorHex: Long,
    val logoCode: String,
    val createdAt: Long
) {
    fun toStation(): RadioStation = RadioStation(
        id = stationId,
        name = name,
        frequency = frequency,
        band = RadioBand.fromString(band),
        district = district,
        language = language,
        genre = genre,
        streamUrl = streamUrl,
        accentColorHex = accentColorHex,
        logoCode = logoCode,
        tagline = "Custom tuned station • $district"
    )

    companion object {
        fun fromStation(
            station: RadioStation,
            createdAt: Long = System.currentTimeMillis()
        ): CustomStationEntity = CustomStationEntity(
            stationId = station.id,
            name = station.name,
            frequency = station.frequency,
            band = station.band.name,
            district = station.district,
            language = station.language,
            genre = station.genre,
            streamUrl = station.streamUrl,
            accentColorHex = station.accentColorHex,
            logoCode = station.logoCode,
            createdAt = createdAt
        )
    }
}

