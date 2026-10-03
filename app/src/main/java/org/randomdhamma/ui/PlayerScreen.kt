package org.randomdhamma.ui

import android.content.ComponentName
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import kotlinx.coroutines.delay
import org.randomdhamma.appContainer
import org.randomdhamma.playback.PlaybackService

private val SPEEDS = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f)

/**
 * Plays [url] through [PlaybackService]. With a null [url] it just attaches to whatever is playing
 * (e.g. when opened from the media notification).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(url: String?, title: String?, onBack: () -> Unit) {
    val context = LocalContext.current
    val container = context.appContainer
    var controller by remember { mutableStateOf<MediaController?>(null) }

    DisposableEffect(Unit) {
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener(
            { controller = runCatching { future.get() }.getOrNull() },
            ContextCompat.getMainExecutor(context),
        )
        onDispose {
            controller = null
            MediaController.releaseFuture(future)
        }
    }

    // Start the requested talk at the default speed (0.75× unless changed in Settings).
    LaunchedEffect(controller, url) {
        val c = controller ?: return@LaunchedEffect
        if (url == null || c.currentMediaItem?.mediaId == url) return@LaunchedEffect
        val item = MediaItem.Builder()
            .setMediaId(url)
            .setUri(url)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title ?: "Morning talk")
                    .setArtist("Ṭhānissaro Bhikkhu")
                    .setAlbumTitle("Short morning talks")
                    .build()
            )
            .build()
        c.setMediaItem(item)
        c.setPlaybackSpeed(container.settings.current().defaultSpeed)
        c.prepare()
        c.play()
    }

    var isPlaying by remember { mutableStateOf(false) }
    var buffering by remember { mutableStateOf(false) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var speed by remember { mutableFloatStateOf(1f) }
    var currentTitle by remember { mutableStateOf(title) }
    var error by remember { mutableStateOf<String?>(null) }
    var dragging by remember { mutableStateOf<Float?>(null) }

    LaunchedEffect(controller) {
        val c = controller ?: return@LaunchedEffect
        while (true) {
            isPlaying = c.isPlaying
            buffering = c.playbackState == Player.STATE_BUFFERING
            position = c.currentPosition
            duration = c.duration.coerceAtLeast(0L)
            speed = c.playbackParameters.speed
            currentTitle = c.mediaMetadata.title?.toString() ?: currentTitle
            error = c.playerError?.let { "Playback failed. Check your connection." }
            delay(500)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Morning talk") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.height(24.dp))
            Text(
                currentTitle ?: "Nothing playing",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
            Text("Ṭhānissaro Bhikkhu", color = MaterialTheme.colorScheme.onSurfaceVariant)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            Spacer(Modifier.height(16.dp))
            val c = controller
            Slider(
                value = dragging ?: if (duration > 0) position.toFloat() / duration else 0f,
                onValueChange = { dragging = it },
                onValueChangeFinished = {
                    dragging?.let { frac -> c?.seekTo((frac * duration).toLong()) }
                    dragging = null
                },
                enabled = c != null && duration > 0,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(Modifier.fillMaxWidth()) {
                Text(formatDuration(dragging?.let { (it * duration).toLong() } ?: position), Modifier.weight(1f))
                Text(formatDuration(duration))
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                IconButton(onClick = { c?.seekBack() }, enabled = c != null) {
                    Icon(Icons.Filled.Replay10, contentDescription = "Back 10 seconds")
                }
                FilledIconButton(
                    onClick = { c?.let { if (it.isPlaying) it.pause() else it.play() } },
                    enabled = c != null,
                    modifier = Modifier.size(72.dp),
                ) {
                    when {
                        buffering -> CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
                        isPlaying -> Icon(Icons.Filled.Pause, contentDescription = "Pause", Modifier.size(40.dp))
                        else -> Icon(Icons.Filled.PlayArrow, contentDescription = "Play", Modifier.size(40.dp))
                    }
                }
                IconButton(onClick = { c?.seekForward() }, enabled = c != null) {
                    Icon(Icons.Filled.Forward10, contentDescription = "Forward 10 seconds")
                }
            }

            Text("Speed", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SPEEDS.forEach { s ->
                    FilterChip(
                        selected = kotlin.math.abs(speed - s) < 0.01f,
                        onClick = { c?.setPlaybackSpeed(s); speed = s },
                        label = { Text("${s}×".replace(".0×", "×")) },
                        enabled = c != null,
                    )
                }
            }
        }
    }
}
