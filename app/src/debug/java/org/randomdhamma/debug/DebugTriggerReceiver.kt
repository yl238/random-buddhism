package org.randomdhamma.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.randomdhamma.appContainer
import org.randomdhamma.data.ContentType
import org.randomdhamma.schedule.DailyPickWorker
import org.randomdhamma.schedule.runAsync

/**
 * Debug builds only.
 *
 * Arm the real daily alarm N seconds from now:
 *   adb shell am broadcast -a org.randomdhamma.debug.FIRE_IN --ei seconds 20 -n org.randomdhamma/.debug.DebugTriggerReceiver
 *
 * Send a test notification now (same as Settings → Send test notification; today's time is untouched):
 *   adb shell am broadcast -a org.randomdhamma.debug.TEST_NOTIFY -n org.randomdhamma/.debug.DebugTriggerReceiver
 * Add `--es type TALK` or `--es type SUTTA` to force one kind.
 *
 * Pick a fresh random time (without revealing it):
 *   adb shell am broadcast -a org.randomdhamma.debug.REROLL -n org.randomdhamma/.debug.DebugTriggerReceiver
 */
class DebugTriggerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_TEST_NOTIFY) {
            val raw = intent.getStringExtra("type")
            val type = ContentType.entries.firstOrNull { it.name.equals(raw, ignoreCase = true) }
            DailyPickWorker.enqueue(context, test = true, type = type)
            return
        }
        val scheduler = context.appContainer.scheduler
        runAsync {
            when (intent.action) {
                ACTION_REROLL -> scheduler.reschedule()
                else -> {
                    val seconds = intent.getIntExtra("seconds", 20).coerceAtLeast(1)
                    scheduler.scheduleAt(System.currentTimeMillis() + seconds * 1000L)
                }
            }
        }
    }

    companion object {
        const val ACTION_REROLL = "org.randomdhamma.debug.REROLL"
        const val ACTION_TEST_NOTIFY = "org.randomdhamma.debug.TEST_NOTIFY"
    }
}
