package com.richie.whattowear.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.updateAll
import com.richie.whattowear.reminder.Reminders
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * After the app is updated, Android shows the widget's loading spinner until something redraws it.
 * This redraws it at once from the saved suggestion (no internet needed), then fetches a fresh one.
 */
class AppUpdatedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                WearWidget().updateAll(context)
            } finally {
                pending.finish()
            }
        }
        WidgetUpdates.schedule(context)
        WidgetUpdates.refreshNow(context)
        Reminders.schedule(context)
    }
}
