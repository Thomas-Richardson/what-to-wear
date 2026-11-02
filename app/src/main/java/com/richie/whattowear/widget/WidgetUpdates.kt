package com.richie.whattowear.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/** Keeps the widget's suggestion fresh: every couple of hours, and straight after a log is saved. */
object WidgetUpdates {
    private val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    fun schedule(context: Context) {
        val every2Hours = PeriodicWorkRequestBuilder<WidgetRefreshWorker>(2, TimeUnit.HOURS).setConstraints(online).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork("widget-refresh", ExistingPeriodicWorkPolicy.KEEP, every2Hours)
    }

    fun refreshNow(context: Context) {
        val once = OneTimeWorkRequestBuilder<WidgetRefreshWorker>().setConstraints(online).build()
        WorkManager.getInstance(context).enqueueUniqueWork("widget-refresh-now", ExistingWorkPolicy.REPLACE, once)
    }
}

class WidgetRefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        // If the weather can't be fetched, the widget keeps showing what it had.
        runCatching { WidgetData.refresh(applicationContext) }
        WearWidget().updateAll(applicationContext)
        return Result.success()
    }
}
