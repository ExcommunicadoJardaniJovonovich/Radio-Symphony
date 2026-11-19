package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.AppThemeMode
import com.example.data.model.EqualizerCatalog
import com.example.data.model.EqualizerSettings
import com.example.data.model.FavouriteSortOrder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.radioSettingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "radio_symphony_prefs"
)

class RadioPreferences(private val context: Context) {

    private object Keys {
        val AUTO_RECONNECT = booleanPreferencesKey("auto_reconnect")
        val RESTORE_LAST_STATION = booleanPreferencesKey("restore_last_station")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val FAVOURITE_SORT = stringPreferencesKey("favourite_sort")

        // Equalizer & DSP settings
        val EQ_ENABLED = booleanPreferencesKey("eq_enabled")
        val EQ_PRESET_ID = stringPreferencesKey("eq_preset_id")
        val EQ_BANDS_CSV = stringPreferencesKey("eq_bands_csv")
        val EQ_PREAMP_DB = floatPreferencesKey("eq_preamp_db")
        val EQ_BASS_BOOST = intPreferencesKey("eq_bass_boost")
        val EQ_VIRTUALIZER = intPreferencesKey("eq_virtualizer")
        val EQ_LOUDNESS_MB = intPreferencesKey("eq_loudness_mb")
        val EQ_LIMITER_ENABLED = booleanPreferencesKey("eq_limiter_enabled")
    }

    val autoReconnectEnabled: Flow<Boolean> = context.radioSettingsDataStore.data.map { prefs ->
        prefs[Keys.AUTO_RECONNECT] ?: true
    }

    val restoreLastStationEnabled: Flow<Boolean> = context.radioSettingsDataStore.data.map { prefs ->
        prefs[Keys.RESTORE_LAST_STATION] ?: true
    }

    val themeMode: Flow<AppThemeMode> = context.radioSettingsDataStore.data.map { prefs ->
        AppThemeMode.fromString(prefs[Keys.THEME_MODE])
    }

    val favouriteSortOrder: Flow<FavouriteSortOrder> = context.radioSettingsDataStore.data.map { prefs ->
        when (prefs[Keys.FAVOURITE_SORT]) {
            FavouriteSortOrder.ALPHABETICAL.name -> FavouriteSortOrder.ALPHABETICAL
            else -> FavouriteSortOrder.RECENTLY_PLAYED
        }
    }

    val equalizerSettings: Flow<EqualizerSettings> = context.radioSettingsDataStore.data.map { prefs ->
        val defaultPreset = EqualizerCatalog.findPreset(EqualizerCatalog.PRESET_HIFI_STUDIO)
        val parsedBands = prefs[Keys.EQ_BANDS_CSV]
            ?.split(",")
            ?.mapNotNull { it.trim().toFloatOrNull() }
            ?.takeIf { it.size == EqualizerCatalog.BAND_COUNT }
            ?: defaultPreset.bandGainsDb

        EqualizerSettings(
            enabled = prefs[Keys.EQ_ENABLED] ?: false,
            presetId = prefs[Keys.EQ_PRESET_ID] ?: EqualizerCatalog.PRESET_HIFI_STUDIO,
            bandGainsDb = parsedBands,
            preampDb = prefs[Keys.EQ_PREAMP_DB] ?: 0f,
            bassBoostStrength = prefs[Keys.EQ_BASS_BOOST] ?: defaultPreset.bassBoostStrength,
            virtualizerStrength = prefs[Keys.EQ_VIRTUALIZER] ?: defaultPreset.virtualizerStrength,
            loudnessGainMb = prefs[Keys.EQ_LOUDNESS_MB] ?: defaultPreset.loudnessGainMb,
            limiterEnabled = prefs[Keys.EQ_LIMITER_ENABLED] ?: true
        ).normalized()
    }

    suspend fun setAutoReconnect(enabled: Boolean) {
        context.radioSettingsDataStore.edit { it[Keys.AUTO_RECONNECT] = enabled }
    }

    suspend fun setRestoreLastStation(enabled: Boolean) {
        context.radioSettingsDataStore.edit { it[Keys.RESTORE_LAST_STATION] = enabled }
    }

    suspend fun setThemeMode(mode: AppThemeMode) {
        context.radioSettingsDataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setFavouriteSortOrder(order: FavouriteSortOrder) {
        context.radioSettingsDataStore.edit { it[Keys.FAVOURITE_SORT] = order.name }
    }

    suspend fun saveEqualizerSettings(settings: EqualizerSettings) {
        val safe = settings.normalized()
        context.radioSettingsDataStore.edit { prefs ->
            prefs[Keys.EQ_ENABLED] = safe.enabled
            prefs[Keys.EQ_PRESET_ID] = safe.presetId
            prefs[Keys.EQ_BANDS_CSV] = safe.bandGainsDb.joinToString(",")
            prefs[Keys.EQ_PREAMP_DB] = safe.preampDb
            prefs[Keys.EQ_BASS_BOOST] = safe.bassBoostStrength
            prefs[Keys.EQ_VIRTUALIZER] = safe.virtualizerStrength
            prefs[Keys.EQ_LOUDNESS_MB] = safe.loudnessGainMb
            prefs[Keys.EQ_LIMITER_ENABLED] = safe.limiterEnabled
        }
    }
}
