package org.randomdhamma.schedule

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import org.randomdhamma.data.SettingsRepository
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class DailyScheduler(
    private val context: Context,
    private val settings: SettingsRepository,
) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    /** Re-arms the stored time if it is still ahead (e.g. after a reboot); otherwise picks a new one. */
    suspend fun ensureScheduled() {
        val s = settings.current()
        if (!s.notificationsEnabled) return cancel()
        if (s.nextFireAt > System.currentTimeMillis()) setAlarm(s.nextFireAt, s.useExactAlarms)
        else reschedule()
    }

    /** Picks a fresh random time, e.g. after the window changed. */
    suspend fun reschedule() {
        val s = settings.current()
        if (!s.notificationsEnabled) return cancel()
        val exact = s.useExactAlarms && canScheduleExact()
        val next = nextFireTime(
            now = ZonedDateTime.now(),
            startMinute = s.windowStartMinute,
            endMinute = s.windowEndMinute,
            lastFiredEpochDay = s.lastFiredEpochDay,
            deliverySlackMinutes = if (exact) 0 else INEXACT_SLACK_MINUTES,
        ).toInstant().toEpochMilli()
        settings.setNextFireAt(next)
        setAlarm(next, s.useExactAlarms)
    }

    /** Called when today's alarm fires: mark today as done and schedule tomorrow. */
    suspend fun onAlarmFired() {
        settings.setLastFiredEpochDay(LocalDate.now().toEpochDay())
        reschedule()
    }

    /** Arms the daily alarm at an explicit time. Used by the debug-only trigger to test the real alarm path. */
    suspend fun scheduleAt(triggerAt: Long) {
        settings.setNextFireAt(triggerAt)
        setAlarm(triggerAt, exact = true)
    }

    suspend fun cancel() {
        alarmManager.cancel(alarmIntent())
        settings.setNextFireAt(0L)
    }

    fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    private fun setAlarm(triggerAt: Long, exact: Boolean) {
        val pi = alarmIntent()
        if (exact && canScheduleExact()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        } else {
            // Inexact: may be a few minutes late in Doze, which is fine for a random time.
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        }
    }

    private fun alarmIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        Intent(context, AlarmReceiver::class.java).setAction(ACTION_DAILY),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    companion object {
        const val ACTION_DAILY = "org.randomdhamma.action.DAILY"
        private const val REQUEST_CODE = 1

        /** Inexact alarms get a delivery window of up to an hour (seen as `window=+1h` in dumpsys alarm). */
        private const val INEXACT_SLACK_MINUTES = 60

        fun describe(millis: Long): ZonedDateTime =
            Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
    }
}
