package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.playback.RadioPlaybackContracts
import com.example.ui.RadioSymphonyRoot
import com.example.ui.theme.RadioSymphonyTheme
import com.example.ui.viewmodel.RadioViewModel

class MainActivity : ComponentActivity() {

    private var openFullPlayerFromNotification by mutableStateOf(false)

    private val radioViewModel: RadioViewModel by viewModels {
        val app = application as RadioSymphonyApp
        RadioViewModel.factory(
            repository = app.repository,
            playbackManager = app.playbackManager,
            preferences = app.preferences
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        handleLaunchIntent(intent)

        setContent {
            val themeMode by radioViewModel.themeMode.collectAsStateWithLifecycle()
            RadioSymphonyTheme(themeMode = themeMode) {
                RadioSymphonyRoot(
                    viewModel = radioViewModel,
                    openFullPlayerOnLaunch = openFullPlayerFromNotification
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleLaunchIntent(intent)
    }

    private fun handleLaunchIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(RadioPlaybackContracts.EXTRA_OPEN_FULL_PLAYER, false) == true) {
            openFullPlayerFromNotification = true
            radioViewModel.expandPlayer()
        }
    }
}
