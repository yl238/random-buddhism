package org.randomdhamma.schedule

/** What to do with the stored alarm time when (re)starting, see [rearmAction]. */
enum class RearmAction { CANCEL, REARM, RESCHEDULE, LEAVE }

/**
 * An inexact alarm can be delivered up to an hour after its time; after this long it can be
 * assumed lost rather than still on its way.
 */
const val PENDING_GRACE_MS = 2L * 60 * 60 * 1000

/**
 * Decides what to do with the stored next alarm time. AlarmManager can't be asked whether an
 * alarm is still pending, so a time that passed only recently is left alone: it may still be
 * delivered (inexact alarms run late), or it may be firing right now, in which case
 * AlarmReceiver schedules tomorrow. Rescheduling it here would move or skip today's teaching.
 *
 * @param alarmsCleared true after a reboot, which removes every alarm, so a past time is certainly lost.
 */
fun rearmAction(enabled: Boolean, nextFireAt: Long, now: Long, alarmsCleared: Boolean): RearmAction = when {
    !enabled -> RearmAction.CANCEL
    nextFireAt > now -> RearmAction.REARM
    nextFireAt == 0L -> RearmAction.RESCHEDULE
    alarmsCleared -> RearmAction.RESCHEDULE
    now - nextFireAt < PENDING_GRACE_MS -> RearmAction.LEAVE
    else -> RearmAction.RESCHEDULE // lost, e.g. the app was force-stopped
}
