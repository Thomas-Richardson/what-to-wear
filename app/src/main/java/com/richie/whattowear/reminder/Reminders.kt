package com.richie.whattowear.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.richie.whattowear.QuickLogActivity
import com.richie.whattowear.R
import com.richie.whattowear.data.Logs
import com.richie.whattowear.data.Settings
import java.time.Duration
import java.time.LocalDate
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/** A daily nudge at 5pm, only on days with no log yet, so remembering to log takes no effort. */
object Reminders {
    const val HOUR = 17
    private const val WORK = "daily-reminder"
    private const val CHANNEL = "reminders"

    /**
     * How long until the next [hour]:00 on the phone's clock. Worked out with the time zone, so the
     * night the clocks change (a 23- or 25-hour day) still lands on 5pm.
     */
    fun delayUntil(now: ZonedDateTime, hour: Int): Duration {
        val todayAt = now.toLocalDate().atTime(hour, 0).atZone(now.zone)
        val next = if (now.isBefore(todayAt)) todayAt else now.toLocalDate().plusDays(1).atTime(hour, 0).atZone(now.zone)
        return Duration.between(now, next)
    }

    /** True if the next reminder is due within ten minutes of [HOUR]:00 local time. A 24-hour repeat drifts an hour when the clocks change. */
    fun onTime(nextRunMillis: Long, zone: ZoneId = ZoneId.systemDefault()): Boolean {
        val local = Instant.ofEpochMilli(nextRunMillis).atZone(zone)
        val minutesFrom5pm = local.hour * 60 + local.minute - HOUR * 60
        return minutesFrom5pm in -10..10
    }

    /**
     * [restart] re-times it (after switching it on). Otherwise an existing schedule is left alone,
     * unless it has drifted off 5pm after the clocks changed. Reads WorkManager's database, so call
     * it off the main thread.
     */
    fun schedule(context: Context, restart: Boolean = false) {
        val work = WorkManager.getInstance(context)
        if (!Settings(context).reminderOn) {
            work.cancelUniqueWork(WORK)
            return
        }
        val existing = work.getWorkInfosForUniqueWork(WORK).get().firstOrNull { !it.state.isFinished }
        val drifted = existing != null && !onTime(existing.nextScheduleTimeMillis)
        val daily = PeriodicWorkRequestBuilder<ReminderWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(delayUntil(ZonedDateTime.now(), HOUR).toMillis(), TimeUnit.MILLISECONDS)
            .build()
        val policy = if (restart || drifted) ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE else ExistingPeriodicWorkPolicy.KEEP
        work.enqueueUniquePeriodicWork(WORK, policy, daily)
    }

    fun canNotify(context: Context) = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun show(context: Context) {
        if (!canNotify(context)) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Reminders to log", NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(
            context, 0, QuickLogActivity.intent(context), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("How does your outfit feel?")
            .setContentText("Tap to log it in two taps.")
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        @Suppress("MissingPermission") // checked by canNotify above
        NotificationManagerCompat.from(context).notify(1, notification)
    }
}

class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (!Settings(applicationContext).reminderOn) return Result.success()
        // Only nudge on days with no log yet. If the note can't be read, nudge anyway.
        val loggedToday = runCatching { Logs.load(applicationContext).entries.any { it.date == LocalDate.now() } }
            .getOrDefault(false)
        if (!loggedToday) Reminders.show(applicationContext)
        return Result.success()
    }
}
