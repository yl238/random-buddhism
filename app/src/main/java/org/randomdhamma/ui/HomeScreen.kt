package org.randomdhamma.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.randomdhamma.appContainer
import org.randomdhamma.data.ContentType
import org.randomdhamma.data.Pick
import org.randomdhamma.data.PickSource
import org.randomdhamma.data.local.HistoryEntity
import org.randomdhamma.util.suspendRunCatching
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun HomeScreen(onOpen: (Pick) -> Unit, snackbar: SnackbarHostState) {
    val container = LocalContext.current.appContainer
    val latest by container.content.latestDaily.collectAsState(initial = null)
    val settings by container.settings.settings.collectAsState(initial = null)
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(false) }

    fun pickNow(type: ContentType?) {
        if (loading) return
        loading = true
        scope.launch {
            suspendRunCatching { container.content.pick(PickSource.MANUAL, type) }
                .onSuccess(onOpen)
                .onFailure { snackbar.showSnackbar("Couldn't reach dhammatalks.org. Check your connection.") }
            loading = false
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Random Dhamma", style = MaterialTheme.typography.headlineMedium)

        Text("Today's teaching", style = MaterialTheme.typography.titleMedium)
        val today = latest?.takeIf { isToday(it.createdAt) }
        if (today != null) {
            PickCard(today, onClick = { onOpen(today.toPick()) })
        } else {
            Text(
                "Your daily teaching hasn't arrived yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // The time of the next teaching is deliberately never shown: it should be a surprise.
        if (settings?.notificationsEnabled == false) {
            Text(
                "Daily notifications are off.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(8.dp))
        Button(onClick = { pickNow(null) }, enabled = !loading, modifier = Modifier.fillMaxWidth().height(56.dp)) {
            if (loading) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.Filled.Shuffle, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Give me one now")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = { pickNow(ContentType.SUTTA) }, enabled = !loading, modifier = Modifier.weight(1f)) {
                Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Sutta")
            }
            OutlinedButton(onClick = { pickNow(ContentType.TALK) }, enabled = !loading, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.Headphones, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Talk")
            }
        }
    }
}

@Composable
fun PickCard(entry: HistoryEntity, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val isSutta = entry.type == ContentType.SUTTA.name
    Card(modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (isSutta) Icons.AutoMirrored.Filled.MenuBook else Icons.Filled.Headphones,
                contentDescription = if (isSutta) "Sutta" else "Talk",
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(entry.title, style = MaterialTheme.typography.titleMedium)
                val label = if (entry.source == PickSource.DAILY.name) "Daily" else "On demand"
                Text(
                    "$label · ${formatDateTime(entry.createdAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

fun HistoryEntity.toPick() = Pick(ContentType.valueOf(type), url, title)

private fun isToday(millis: Long): Boolean =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate() == LocalDate.now()
