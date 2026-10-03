package org.randomdhamma.notify

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import org.randomdhamma.R
import org.randomdhamma.data.ContentType
import org.randomdhamma.data.Pick
import org.randomdhamma.ui.NavIntents

object Notifications {
    private const val CHANNEL_DAILY = "daily"
    private const val CHANNEL_BACKGROUND = "background"
    private const val DAILY_ID = 100
    const val WORKING_ID = 101

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_DAILY,
                context.getString(R.string.channel_daily_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = context.getString(R.string.channel_daily_description) }
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_BACKGROUND, "Background work", NotificationManager.IMPORTANCE_MIN)
        )
    }

    fun canPost(context: Context): Boolean =
        (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()

    fun showPick(context: Context, pick: Pick) {
        val heading = when (pick.type) {
            ContentType.SUTTA -> "Today's sutta"
            ContentType.TALK -> "Today's Dhamma talk"
        }
        post(context, heading, pick.title, NavIntents.forPick(context, pick))
    }

    fun showFallback(context: Context) {
        post(context, "Today's teaching", "Tap for today's random teaching", NavIntents.resolve(context))
    }

    private fun post(context: Context, title: String, text: String, intent: android.content.Intent) {
        if (!canPost(context)) return
        val pi = PendingIntent.getActivity(
            context, DAILY_ID, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_DAILY)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(DAILY_ID, notification)
        } catch (_: SecurityException) {
            // Permission revoked between the check and the post.
        }
    }

    fun workingNotification(context: Context): Notification =
        NotificationCompat.Builder(context, CHANNEL_BACKGROUND)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Finding today's teaching…")
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
}
