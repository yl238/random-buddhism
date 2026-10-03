package org.randomdhamma.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.randomdhamma.appContainer

/**
 * Debug builds only. Arms the real daily alarm N seconds from now:
 *   adb shell am broadcast -a org.randomdhamma.debug.FIRE_IN --ei seconds 20 -n org.randomdhamma/.debug.DebugTriggerReceiver
 */
class DebugTriggerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val seconds = intent.getIntExtra("seconds", 20).coerceAtLeast(1)
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                context.appContainer.scheduler.scheduleAt(System.currentTimeMillis() + seconds * 1000L)
            } finally {
                pending.finish()
            }
        }
    }
}
