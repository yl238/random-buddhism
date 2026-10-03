package org.randomdhamma.ui

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.abs

private val dateTimeFormat = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
private val timeFormat = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

fun formatDateTime(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(dateTimeFormat)

fun formatMinuteOfDay(minute: Int): String =
    LocalTime.of(minute / 60 % 24, minute % 60).format(timeFormat)

fun formatDuration(ms: Long): String {
    val total = (ms.coerceAtLeast(0) / 1000).toInt()
    val h = total / 3600
    val m = total % 3600 / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/** Playback speeds offered in the player and as the default in Settings. */
val TALK_SPEEDS = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f)

/** 0.75 -> "0.75×", 1.0 -> "1×". */
fun formatSpeed(speed: Float): String = "${speed}×".replace(".0×", "×")

/** Speeds are floats, so compare with a tolerance. */
fun isSameSpeed(a: Float, b: Float): Boolean = abs(a - b) < 0.01f
