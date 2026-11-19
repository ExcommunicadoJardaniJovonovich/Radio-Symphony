package com.example

import android.app.Application
import com.example.data.local.RadioDatabase
import com.example.data.local.RadioPreferences
import com.example.data.repository.RadioRepository
import com.example.playback.RadioPlaybackManager

class RadioSymphonyApp : Application() {

    lateinit var database: RadioDatabase
        private set

    lateinit var preferences: RadioPreferences
        private set

    lateinit var repository: RadioRepository
        private set

    lateinit var playbackManager: RadioPlaybackManager
        private set

    override fun onCreate() {
        super.onCreate()
        database = RadioDatabase.getInstance(this)
        preferences = RadioPreferences(this)
        repository = RadioRepository(database.radioDao())
        playbackManager = RadioPlaybackManager(
            context = this,
            repository = repository,
            preferences = preferences
        )
    }
}
