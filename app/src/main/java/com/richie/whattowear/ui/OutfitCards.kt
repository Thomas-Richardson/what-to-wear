package com.richie.whattowear.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.richie.whattowear.model.ClothingItem
import com.richie.whattowear.model.Recommender

/**
 * The day's outfits as cards you swipe between, with dots underneath, then the day's notes and
 * "Log what I wore", which starts from whichever card is showing. The edge of the next card shows on the
 * right, so it's clear there are more. With only one outfit it's a single card, as before 0.12.
 */
@Composable
internal fun OutfitCards(cards: List<Recommender.Suggestion>, onLog: (List<ClothingItem>) -> Unit) {
    val pager = rememberPagerState { cards.size }
    // New weather, or switching between "Rest of day" and "Next 3 hours", means new cards: start from the first.
    LaunchedEffect(cards) { pager.scrollToPage(0) }
    val peek = if (cards.size > 1) PEEK else 0.dp

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box {
            // Invisible copies of every card, stacked on top of each other, make the row as tall as the tallest
            // card, so the page doesn't jump up and down as you swipe. Screen readers skip them.
            Box(Modifier.fillMaxWidth().padding(end = peek).alpha(0f).clearAndSetSemantics {}) {
                cards.forEachIndexed { i, s -> OutfitCard(s, i, cards.size) }
            }
            HorizontalPager(
                state = pager,
                contentPadding = PaddingValues(end = peek),
                pageSpacing = 12.dp,
                verticalAlignment = Alignment.Top,
                modifier = Modifier.matchParentSize().testTag("outfits"),
            ) { page -> OutfitCard(cards[page], page, cards.size, Modifier.fillMaxHeight()) }
        }
        if (cards.size > 1) PageDots(cards.size, pager.currentPage)
        cards.first().notes.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
        Button(
            shape = MaterialTheme.shapes.small,
            onClick = { onLog(cards[pager.currentPage.coerceIn(cards.indices)].items) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Log what I wore") }
        Text(
            if (cards.size > 1) "Starts from the outfit showing. Change anything you didn't wear."
            else "Starts from this suggestion. Change anything you didn't wear.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** How much of the next card shows on the right. */
private val PEEK = 28.dp

@Composable
private fun OutfitCard(s: Recommender.Suggestion, index: Int, count: Int, modifier: Modifier = Modifier) {
    Card(modifier.fillMaxWidth(), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionLabel(if (index == 0) "Wear" else "Or wear", Modifier.weight(1f))
                if (count > 1) {
                    Text(
                        "${index + 1} of $count",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            s.items.forEach { item ->
                Text("${item.emoji}  ${item.name}", style = MaterialTheme.typography.titleLarge)
            }
            Spacer(Modifier.height(4.dp))
            Text(s.reason, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Small Art Deco diamonds, one per card: the one showing is bigger and in the text colour, the rest gold. */
@Composable
private fun PageDots(count: Int, current: Int) {
    Row(
        Modifier.fillMaxWidth().clearAndSetSemantics {},
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { i ->
            val showing = i == current
            Box(
                Modifier
                    .padding(horizontal = 5.dp)
                    .size(if (showing) 9.dp else 6.dp)
                    .rotate(45f)
                    .background(if (showing) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline),
            )
        }
    }
}
