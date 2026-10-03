package org.randomdhamma.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

data class Settings(
    /** Window start/end as minutes after midnight, end exclusive. */
    val windowStartMinute: Int = 7 * 60,
    val windowEndMinute: Int = 22 * 60,
    /** Chance (0–100) that the daily pick is a sutta rather than a talk. */
    val suttaPercent: Int = 50,
    val defaultSpeed: Float = 0.75f,
    val notificationsEnabled: Boolean = true,
    val useExactAlarms: Boolean = false,
    /** Epoch millis of the next scheduled notification, 0 if none. */
    val nextFireAt: Long = 0L,
    /** Epoch day of the last daily notification, -1 if never. */
    val lastFiredEpochDay: Long = -1L,
    val talkCacheComplete: Boolean = false,
)

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val windowStart = intPreferencesKey("window_start")
        val windowEnd = intPreferencesKey("window_end")
        val suttaPercent = intPreferencesKey("sutta_percent")
        val defaultSpeed = floatPreferencesKey("default_speed")
        val notificationsEnabled = booleanPreferencesKey("notifications_enabled")
        val useExactAlarms = booleanPreferencesKey("use_exact_alarms")
        val nextFireAt = longPreferencesKey("next_fire_at")
        val lastFiredEpochDay = longPreferencesKey("last_fired_epoch_day")
        val talkCacheComplete = booleanPreferencesKey("talk_cache_complete")
    }

    val settings: Flow<Settings> = context.dataStore.data.map { it.toSettings() }

    suspend fun current(): Settings = settings.first()

    private fun Preferences.toSettings(): Settings {
        val d = Settings()
        return Settings(
            windowStartMinute = this[Keys.windowStart] ?: d.windowStartMinute,
            windowEndMinute = this[Keys.windowEnd] ?: d.windowEndMinute,
            suttaPercent = this[Keys.suttaPercent] ?: d.suttaPercent,
            defaultSpeed = this[Keys.defaultSpeed] ?: d.defaultSpeed,
            notificationsEnabled = this[Keys.notificationsEnabled] ?: d.notificationsEnabled,
            useExactAlarms = this[Keys.useExactAlarms] ?: d.useExactAlarms,
            nextFireAt = this[Keys.nextFireAt] ?: d.nextFireAt,
            lastFiredEpochDay = this[Keys.lastFiredEpochDay] ?: d.lastFiredEpochDay,
            talkCacheComplete = this[Keys.talkCacheComplete] ?: d.talkCacheComplete,
        )
    }

    suspend fun setWindow(startMinute: Int, endMinute: Int) {
        require(startMinute in 0 until endMinute && endMinute <= 24 * 60) { "Invalid window" }
        context.dataStore.edit {
            it[Keys.windowStart] = startMinute
            it[Keys.windowEnd] = endMinute
        }
    }

    suspend fun setSuttaPercent(percent: Int) =
        context.dataStore.edit { it[Keys.suttaPercent] = percent.coerceIn(0, 100) }

    suspend fun setDefaultSpeed(speed: Float) =
        context.dataStore.edit { it[Keys.defaultSpeed] = speed.coerceIn(0.25f, 3f) }

    suspend fun setNotificationsEnabled(enabled: Boolean) =
        context.dataStore.edit { it[Keys.notificationsEnabled] = enabled }

    suspend fun setUseExactAlarms(enabled: Boolean) =
        context.dataStore.edit { it[Keys.useExactAlarms] = enabled }

    suspend fun setNextFireAt(millis: Long) =
        context.dataStore.edit { it[Keys.nextFireAt] = millis }

    suspend fun setLastFiredEpochDay(day: Long) =
        context.dataStore.edit { it[Keys.lastFiredEpochDay] = day }

    suspend fun setTalkCacheComplete(complete: Boolean) =
        context.dataStore.edit { it[Keys.talkCacheComplete] = complete }
}
