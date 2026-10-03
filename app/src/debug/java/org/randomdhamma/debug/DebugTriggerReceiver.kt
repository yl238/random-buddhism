package org.randomdhamma.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.randomdhamma.appContainer

/**
 * Debug builds only.
 *
 * Arm the real daily alarm N seconds from now:
 *   adb shell am broadcast -a org.randomdhamma.debug.FIRE_IN --ei seconds 20 -n org.randomdhamma/.debug.DebugTriggerReceiver
 *
 * Pick a fresh random time (without revealing it):
 *   adb shell am broadcast -a org.randomdhamma.debug.REROLL -n org.randomdhamma/.debug.DebugTriggerReceiver
 */
class DebugTriggerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val scheduler = context.appContainer.scheduler
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                when (intent.action) {
                    ACTION_REROLL -> scheduler.reschedule()
                    else -> {
                        val seconds = intent.getIntExtra("seconds", 20).coerceAtLeast(1)
                        scheduler.scheduleAt(System.currentTimeMillis() + seconds * 1000L)
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_REROLL = "org.randomdhamma.debug.REROLL"
    }
}
