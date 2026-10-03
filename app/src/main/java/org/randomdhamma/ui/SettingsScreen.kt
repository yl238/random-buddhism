package org.randomdhamma.ui

import android.content.Intent
import android.os.Build
import android.provider.Settings as AndroidSettings
import android.text.format.DateFormat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.coroutines.launch
import org.randomdhamma.appContainer
import org.randomdhamma.notify.Notifications
import org.randomdhamma.schedule.DailyPickWorker

private val SPEED_OPTIONS = listOf(0.5f, 0.75f, 1.0f, 1.25f)

@Composable
fun SettingsScreen(snackbar: SnackbarHostState) {
    val context = LocalContext.current
    val container = context.appContainer
    val settings by container.settings.settings.collectAsState(initial = null)
    val scope = rememberCoroutineScope()
    val s = settings ?: return

    // Re-check system permissions whenever we come back from system settings.
    var exactGranted by remember { mutableIntStateOf(0) }
    var notificationsAllowed by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        exactGranted = if (container.scheduler.canScheduleExact()) 1 else 0
        notificationsAllowed = if (Notifications.canPost(context)) 1 else 0
        if (s.notificationsEnabled) scope.launch { container.scheduler.ensureScheduled() }
        onPauseOrDispose { }
    }

    fun setWindow(start: Int, end: Int) {
        if (start >= end) {
            scope.launch { snackbar.showSnackbar("The earliest time must be before the latest time.") }
            return
        }
        scope.launch {
            container.settings.setWindow(start, end)
            container.scheduler.reschedule()
        }
    }

    // Which window edge is being edited, if any.
    var editing by remember { mutableStateOf<WindowEdge?>(null) }
    editing?.let { edge ->
        val initial = if (edge == WindowEdge.START) s.windowStartMinute else s.windowEndMinute
        TimePickerDialog(
            title = if (edge == WindowEdge.START) "Earliest time" else "Latest time",
            initialMinute = initial,
            is24Hour = DateFormat.is24HourFormat(context),
            onDismiss = { editing = null },
            onConfirm = { minute ->
                editing = null
                if (edge == WindowEdge.START) setWindow(minute, s.windowEndMinute)
                else setWindow(s.windowStartMinute, minute)
            },
        )
    }

    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)

        SwitchRow("Daily notification", "One random teaching a day at a random time", s.notificationsEnabled) { on ->
            scope.launch {
                container.settings.setNotificationsEnabled(on)
                if (on) container.scheduler.reschedule() else container.scheduler.cancel()
            }
        }
        if (notificationsAllowed == 0) {
            Text(
                "Notifications are blocked for this app.",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedButton(onClick = {
                context.startActivity(
                    Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(AndroidSettings.EXTRA_APP_PACKAGE, context.packageName)
                )
            }) { Text("Open notification settings") }
        }
        if (s.notificationsEnabled && s.nextFireAt > 0) {
            Text(
                "Next: ${formatDateTime(s.nextFireAt)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        HorizontalDivider()
        Text("Time window", style = MaterialTheme.typography.titleMedium)
        ValueRow("Earliest", formatMinuteOfDay(s.windowStartMinute)) { editing = WindowEdge.START }
        ValueRow("Latest", formatMinuteOfDay(s.windowEndMinute)) { editing = WindowEdge.END }

        HorizontalDivider()
        Text("Mix", style = MaterialTheme.typography.titleMedium)
        var percent by remember(s.suttaPercent) { mutableFloatStateOf(s.suttaPercent.toFloat()) }
        Text("Suttas ${percent.toInt()}% · Talks ${100 - percent.toInt()}%")
        Slider(
            value = percent,
            onValueChange = { percent = it },
            onValueChangeFinished = { scope.launch { container.settings.setSuttaPercent(percent.toInt()) } },
            valueRange = 0f..100f,
            steps = 9,
        )

        HorizontalDivider()
        Text("Default talk speed", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SPEED_OPTIONS.forEach { option ->
                FilterChip(
                    selected = kotlin.math.abs(s.defaultSpeed - option) < 0.01f,
                    onClick = { scope.launch { container.settings.setDefaultSpeed(option) } },
                    label = { Text("${option}×".replace(".0×", "×")) },
                )
            }
        }

        HorizontalDivider()
        SwitchRow(
            "Exact time",
            "Fire at the exact minute. Otherwise Android may deliver it up to an hour late, so the time is picked early enough to stay in your window. Needs the Alarms & reminders permission.",
            s.useExactAlarms,
        ) { on ->
            scope.launch {
                container.settings.setUseExactAlarms(on)
                container.scheduler.reschedule()
            }
            if (on && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !container.scheduler.canScheduleExact()) {
                context.startActivity(
                    Intent(AndroidSettings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, "package:${context.packageName}".toUri())
                )
            }
        }
        if (s.useExactAlarms && exactGranted == 0) {
            Text(
                "Permission not granted, so the notification may arrive up to an hour after the scheduled time.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        HorizontalDivider()
        OutlinedButton(onClick = {
            DailyPickWorker.enqueue(context, test = true)
            scope.launch { snackbar.showSnackbar("Test notification on its way…") }
        }) { Text("Send test notification") }
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 16.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun ValueRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
    }
}

private enum class WindowEdge { START, END }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(
    title: String,
    initialMinute: Int,
    is24Hour: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    val state = rememberTimePickerState(
        initialHour = initialMinute / 60 % 24,
        initialMinute = initialMinute % 60,
        is24Hour = is24Hour,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { TimePicker(state = state) },
        confirmButton = { TextButton(onClick = { onConfirm(state.hour * 60 + state.minute) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
