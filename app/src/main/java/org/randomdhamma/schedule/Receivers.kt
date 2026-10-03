package org.randomdhamma.schedule

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.randomdhamma.appContainer

private const val TAG = "Receivers"

private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

/** Runs [block] off the main thread while keeping the broadcast alive (goAsync). */
internal fun BroadcastReceiver.runAsync(block: suspend () -> Unit) {
    val pending = goAsync()
    receiverScope.launch {
        try {
            block()
        } finally {
            pending.finish()
        }
    }
}

/** Fires once a day at the random time: hand today's pick to WorkManager, then schedule tomorrow. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != DailyScheduler.ACTION_DAILY) return
        // Queue today's teaching first, so a failure while rescheduling can't lose it.
        DailyPickWorker.enqueue(context)
        runAsync {
            try {
                context.appContainer.scheduler.onAlarmFired()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Couldn't schedule tomorrow's alarm", e)
            }
        }
    }
}

/** Alarms are cleared on reboot, and clock changes shift the window. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val scheduler = context.appContainer.scheduler
        runAsync {
            when (intent.action) {
                Intent.ACTION_BOOT_COMPLETED -> scheduler.onBoot()
                Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED -> scheduler.reschedule()
                // App updates keep alarms, so just make sure one is armed.
                else -> scheduler.ensureScheduled()
            }
        }
    }
}
