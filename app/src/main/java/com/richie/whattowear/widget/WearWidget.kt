package com.richie.whattowear.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.richie.whattowear.MainActivity
import com.richie.whattowear.QuickLogActivity
import com.richie.whattowear.data.CustomClothesStore
import com.richie.whattowear.model.Feeling
import com.richie.whattowear.model.Wardrobe
import com.richie.whattowear.ui.Brand
import com.richie.whattowear.ui.feelingDayNight
import com.richie.whattowear.ui.onFeelingColor
import androidx.glance.color.ColorProvider

/**
 * The home-screen widget: today's suggestion (or what you logged earlier today) and five buttons
 * for Richie's 1–5 scale. A button opens the quick-log window with that score already picked.
 */
class WearWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        Wardrobe.custom = CustomClothesStore(context).load().map { it.toItem() }
        val state = WidgetData.read(context)
        provideContent { GlanceTheme { WidgetContent(context, state) } }
    }
}

class WearWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = WearWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetUpdates.schedule(context)
        WidgetUpdates.refreshNow(context)
    }
}

/** Richie's branding, light or dark to match the phone (see ui/Theme.kt). */
private object WidgetColors {
    val background = ColorProvider(day = Brand.Ivory, night = Brand.Navy)
    val text = ColorProvider(day = Brand.Navy, night = Brand.Ivory)
    val quiet = ColorProvider(day = Brand.Slate, night = Brand.Mist)
    val label = ColorProvider(day = Brand.Burgundy, night = Brand.LightGold)
    val gold = ColorProvider(day = Brand.Gold, night = Brand.Gold)
}

@Composable
internal fun WidgetContent(context: Context, state: WidgetState) {
    Column(
        GlanceModifier.fillMaxSize().background(WidgetColors.background).cornerRadius(8.dp).padding(12.dp),
    ) {
        // Tapping the suggestion opens the app.
        Column(GlanceModifier.fillMaxWidth().clickable(actionStartActivity<MainActivity>())) {
            val heading = when {
                !state.isToday || state.itemIds.isEmpty() -> "What to Wear"
                state.fromTodaysLog -> "Wearing today"
                else -> "Wear today"
            }
            Text(heading.uppercase(), style = TextStyle(color = WidgetColors.label, fontSize = 11.sp, fontWeight = FontWeight.Medium))
            Text(
                if (state.isToday && state.itemIds.isNotEmpty()) state.clothesLine else "Open the app to get today's suggestion",
                style = TextStyle(color = WidgetColors.text, fontSize = 15.sp, fontWeight = FontWeight.Medium),
                maxLines = 2,
            )
            if (state.isToday && state.weatherLine.isNotBlank()) {
                Text(state.weatherLine, style = TextStyle(color = WidgetColors.quiet, fontSize = 12.sp), maxLines = 1)
            }
        }
        // The suggestion stays at the top and the buttons at the bottom, whatever size the widget is.
        Spacer(GlanceModifier.defaultWeight())
        // A thin gold line, the widget's version of the app's divider.
        Box(GlanceModifier.fillMaxWidth().height(1.dp).background(WidgetColors.gold)) {}
        Spacer(GlanceModifier.height(6.dp))
        Text("How does it feel now?", style = TextStyle(color = WidgetColors.quiet, fontSize = 12.sp))
        Spacer(GlanceModifier.height(4.dp))
        Row(GlanceModifier.fillMaxWidth()) {
            Feeling.entries.forEach { f ->
                val (day, night) = feelingDayNight(f)
                val on = ColorProvider(day = onFeelingColor(f), night = onFeelingColor(f))
                Box(GlanceModifier.defaultWeight().padding(horizontal = 2.dp)) {
                    Column(
                        GlanceModifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .background(ColorProvider(day = day, night = night))
                            .cornerRadius(4.dp)
                            .clickable(actionStartActivity(QuickLogActivity.intent(context, f.score))),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("${f.score}", style = TextStyle(color = on, fontSize = 16.sp, fontWeight = FontWeight.Medium))
                        Text(f.emoji, style = TextStyle(fontSize = 13.sp))
                    }
                }
            }
        }
    }
}
