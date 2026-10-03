package org.randomdhamma.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import org.randomdhamma.ui.theme.RandomDhammaTheme

class MainActivity : ComponentActivity() {
    private val pendingIntent = mutableStateOf<Intent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Reopening from Recents redelivers the original (e.g. notification) intent; don't replay it.
        val launchedFromHistory = intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0
        if (savedInstanceState == null && !launchedFromHistory) pendingIntent.value = intent
        setContent {
            RandomDhammaTheme {
                AppNav(
                    pendingIntent = pendingIntent.value,
                    onIntentHandled = { pendingIntent.value = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        pendingIntent.value = intent
    }
}
