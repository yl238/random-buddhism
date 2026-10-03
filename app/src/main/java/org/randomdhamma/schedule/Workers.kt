package org.randomdhamma.schedule

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CancellationException
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import org.randomdhamma.appContainer
import org.randomdhamma.data.ContentType
import org.randomdhamma.data.PickSource
import org.randomdhamma.notify.Notifications
import java.util.concurrent.TimeUnit

/** Resolves today's random teaching and posts the notification. */
class DailyPickWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = applicationContext.appContainer
        val isTest = inputData.getBoolean(KEY_TEST, false)
        if (!isTest && !container.settings.current().notificationsEnabled) return Result.success()
        return try {
            val type = inputData.getString(KEY_TYPE)?.let(ContentType::valueOf)
            val pick = container.content.pick(if (isTest) PickSource.MANUAL else PickSource.DAILY, type)
            Notifications.showPick(applicationContext, pick)
            Result.success()
        } catch (e: CancellationException) {
            throw e // Work was stopped or replaced; let WorkManager handle it rather than retrying.
        } catch (e: Exception) {
            Log.w(TAG, "Daily pick failed (attempt ${runAttemptCount + 1})", e)
            if (runAttemptCount < MAX_RETRIES) {
                Result.retry()
            } else {
                // Offline or the site is down: still nudge the user; the pick is resolved when tapped.
                Notifications.showFallback(applicationContext)
                Result.success()
            }
        }
    }

    // Only used for expedited work on Android 11 and below.
    override suspend fun getForegroundInfo(): ForegroundInfo =
        ForegroundInfo(Notifications.WORKING_ID, Notifications.workingNotification(applicationContext))

    companion object {
        private const val TAG = "DailyPickWorker"
        private const val KEY_TEST = "test"
        private const val KEY_TYPE = "type"
        private const val MAX_RETRIES = 3

        /** [type] forces a sutta or a talk (test notifications only); null means the usual coin flip. */
        fun enqueue(context: Context, test: Boolean = false, type: ContentType? = null) {
            val request = OneTimeWorkRequestBuilder<DailyPickWorker>()
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .setInputData(workDataOf(KEY_TEST to test, KEY_TYPE to type?.name))
                .build()
            val name = if (test) "test-pick" else "daily-pick"
            WorkManager.getInstance(context).enqueueUniqueWork(name, ExistingWorkPolicy.REPLACE, request)
        }
    }
}

/** Downloads and caches every year's talk list so talks can be picked uniformly. */
class TalkCacheWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = try {
        applicationContext.appContainer.content.refreshTalkCache()
        Result.success()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "Talk cache refresh failed (attempt ${runAttemptCount + 1})", e)
        if (runAttemptCount < 5) Result.retry() else Result.failure()
    }

    companion object {
        private const val TAG = "TalkCacheWorker"

        /**
         * Runs once straight away, then weekly to pick up new talks. KEEP means calling this on
         * every app start doesn't re-run it or refetch the index.
         */
        fun enqueue(context: Context) {
            val request = PeriodicWorkRequestBuilder<TalkCacheWorker>(7, TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        // A new name, so the one-time "talk-cache" work from earlier builds doesn't clash.
        private const val UNIQUE_NAME = "talk-cache-weekly"
    }
}
