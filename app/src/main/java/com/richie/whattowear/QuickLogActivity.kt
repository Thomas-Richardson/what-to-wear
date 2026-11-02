package com.richie.whattowear

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.richie.whattowear.data.LocationHelper
import com.richie.whattowear.model.Feeling
import com.richie.whattowear.ui.QuickLogSheet
import com.richie.whattowear.ui.WhatToWearTheme
import com.richie.whattowear.widget.WidgetData

/**
 * Logging without opening the app: started by a 1–5 button on the home-screen widget or by the
 * reminder. It shows over the home screen and closes as soon as the log is saved.
 */
class QuickLogActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) {
            val score = intent.getIntExtra(EXTRA_SCORE, 0)
            vm.startQuickLog(Feeling.entries.firstOrNull { it.score == score }, WidgetData.read(this).itemIds)
            // Log where you are now if the app may use your location; otherwise the last place it knew.
            if (LocationHelper.hasPermission(this)) vm.useMyLocationForDraft()
        }
        setContent { WhatToWearTheme { QuickLogSheet(vm, onClose = ::finish) } }
    }

    companion object {
        private const val EXTRA_SCORE = "score"

        /** [score] picks a 1–5 button in advance; leave it out to let you choose in the window. */
        fun intent(context: Context, score: Int? = null): Intent =
            Intent(context, QuickLogActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                .apply { if (score != null) putExtra(EXTRA_SCORE, score) }
    }
}
