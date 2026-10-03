package org.randomdhamma

import android.content.Context
import org.randomdhamma.data.ContentRepository
import org.randomdhamma.data.SettingsRepository
import org.randomdhamma.data.local.AppDatabase
import org.randomdhamma.data.remote.DhammaTalksClient
import org.randomdhamma.schedule.DailyScheduler

/** Manual dependency container, one per process. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    val settings = SettingsRepository(appContext)
    private val database = AppDatabase.create(appContext)
    private val client = DhammaTalksClient()
    val content = ContentRepository(client, database.historyDao(), database.talkDao(), settings)
    val scheduler = DailyScheduler(appContext, settings)
}

val Context.appContainer: AppContainer
    get() = (applicationContext as RandomDhammaApp).container
