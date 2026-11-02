package com.richie.whattowear

import android.content.Context
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.hasText
import androidx.glance.testing.unit.hasTextEqualTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.richie.whattowear.widget.WidgetContent
import com.richie.whattowear.widget.WidgetState
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/** What the home-screen widget shows, drawn without a home screen. */
@RunWith(AndroidJUnit4::class)
class WidgetTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val today = LocalDate.now().toString()

    @Test
    fun showsTodaysSuggestionAndTheFiveButtons() = runGlanceAppWidgetUnitTest {
        val state = WidgetState(listOf("shirt", "blazer", "trousers"), fromTodaysLog = false, "⛅ feels like 11°C · Manchester", today)
        provideComposable { GlanceTheme { WidgetContent(context, state) } }
        onNode(hasText("Wear today")).assertExists()
        onNode(hasText("Shirt · Blazer · Trousers / jeans")).assertExists()
        onNode(hasText("⛅ feels like 11°C · Manchester")).assertExists()
        // Exact text: "1" on its own is a button, the 1 in "11°C" isn't.
        for (n in 1..5) onNode(hasTextEqualTo("$n")).assertExists()
    }

    @Test
    fun saysWearingOnceYouveLoggedToday() = runGlanceAppWidgetUnitTest {
        provideComposable { GlanceTheme { WidgetContent(context, WidgetState(listOf("gilet"), true, "", today)) } }
        onNode(hasText("Wearing today")).assertExists()
    }

    @Test
    fun anOldSuggestionIsntShownAsToday() = runGlanceAppWidgetUnitTest {
        provideComposable { GlanceTheme { WidgetContent(context, WidgetState(listOf("gilet"), false, "", "2026-01-01")) } }
        onNode(hasText("Open the app to get today's suggestion")).assertExists()
    }
}
