package org.randomdhamma.playback

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import androidx.core.app.ServiceCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.randomdhamma.appContainer
import org.randomdhamma.notify.Notifications
import org.randomdhamma.ui.NavIntents

/** Background playback with lock-screen / notification controls via a Media3 session. */
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setSeekBackIncrementMs(SEEK_MS)
            .setSeekForwardIncrementMs(SEEK_MS)
            .build()
        val openPlayer = PendingIntent.getActivity(
            this, 0, NavIntents.openPlayer(this),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        session = MediaSession.Builder(this, player).setSessionActivity(openPlayer).build().also {
            // Register the session up front: when playback starts from the lock screen no controller
            // connects, and Media3 only shows its player notification for registered sessions.
            addSession(it)
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    /**
     * Handles the Play button on a talk notification. It starts playback here rather than in an
     * activity, so it works from the lock screen without unlocking.
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_PLAY_TALK) {
            val url = intent.getStringExtra(EXTRA_URL)
            val title = intent.getStringExtra(EXTRA_TITLE)
            Notifications.cancelDaily(this)
            if (url != null) {
                // Android 16+ ignores audio focus requests from background apps ("audio hardening"),
                // so become a foreground service *before* play(). Media3's player notification uses
                // the same id and replaces this placeholder as soon as playback starts.
                ServiceCompat.startForeground(
                    this,
                    DefaultMediaNotificationProvider.DEFAULT_NOTIFICATION_ID,
                    Notifications.startingPlaybackNotification(this, title),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
                )
                scope.launch {
                    val speed = appContainer.settings.current().defaultSpeed
                    session?.player?.playTalk(url, title, speed)
                }
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }

    companion object {
        const val SEEK_MS = 10_000L
        private const val ACTION_PLAY_TALK = "org.randomdhamma.action.PLAY_TALK"
        private const val EXTRA_URL = "url"
        private const val EXTRA_TITLE = "title"

        fun playTalkIntent(context: Context, url: String, title: String?): Intent =
            Intent(context, PlaybackService::class.java)
                .setAction(ACTION_PLAY_TALK)
                .putExtra(EXTRA_URL, url)
                .putExtra(EXTRA_TITLE, title)
    }
}
