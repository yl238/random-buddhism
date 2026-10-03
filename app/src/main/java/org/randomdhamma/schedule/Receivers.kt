package org.randomdhamma.schedule

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.randomdhamma.appContainer

private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

private fun BroadcastReceiver.runAsync(block: suspend () -> Unit) {
    val pending = goAsync()
    receiverScope.launch {
        try {
            block()
        } finally {
            pending.finish()
        }
    }
}

/** Fires once a day at the random time: schedule tomorrow, then hand the network work to WorkManager. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != DailyScheduler.ACTION_DAILY) return
        runAsync {
            context.appContainer.scheduler.onAlarmFired()
            DailyPickWorker.enqueue(context)
        }
    }
}

/** Alarms are cleared on reboot / app update, and clock changes shift the window. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val scheduler = context.appContainer.scheduler
        runAsync {
            when (intent.action) {
                Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED -> scheduler.reschedule()
                else -> scheduler.ensureScheduled()
            }
        }
    }
}
