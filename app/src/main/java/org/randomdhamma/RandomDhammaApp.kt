package org.randomdhamma

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.randomdhamma.notify.Notifications
import org.randomdhamma.schedule.TalkCacheWorker

class RandomDhammaApp : Application() {
    lateinit var container: AppContainer
        private set

    val appScope = CoroutineScope(SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Notifications.createChannels(this)
        TalkCacheWorker.enqueue(this)
        appScope.launch { container.scheduler.ensureScheduled() }
    }
}
