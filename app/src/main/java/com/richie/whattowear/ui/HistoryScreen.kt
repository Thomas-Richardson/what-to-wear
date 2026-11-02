package com.richie.whattowear.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.richie.whattowear.MainViewModel
import com.richie.whattowear.model.CustomClothing
import com.richie.whattowear.model.Entry
import com.richie.whattowear.model.Recommender
import com.richie.whattowear.model.WarmthLevel
import com.richie.whattowear.model.deg
import com.richie.whattowear.model.plural
import com.richie.whattowear.reminder.Reminders
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun HistoryScreen(vm: MainViewModel, onConnectNote: () -> Unit) {
    val context = LocalContext.current
    val recommender = remember(vm.entries, vm.customClothes) { Recommender(vm.entries) }
    // Dated logs newest first, then the rows from before the app in the note's own order.
    val ordered = remember(vm.entries) {
        vm.entries.filter { it.date != null }.sortedWith(compareByDescending<Entry> { it.date }.thenByDescending { it.whenLabel }) +
            vm.entries.filter { it.date == null }
    }
    var toDelete by remember { mutableStateOf<Entry?>(null) }
    var toRemove by remember { mutableStateOf<CustomClothing?>(null) }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { NoteCard(vm, onConnectNote) }
        item { ReminderCard(vm) }
        item { LearntCard(recommender, vm.entries.size) }
        if (vm.noteName == null && vm.entries.isNotEmpty()) {
            item {
                OutlinedButton(onClick = { share(context, vm.exportJson()) }) {
                    Icon(Icons.Default.Share, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Export my logs")
                }
            }
        }
        if (vm.customClothes.isNotEmpty()) {
            item { YourClothesCard(vm.customClothes, onRemove = { toRemove = it }) }
        }
        items(ordered) { entry -> EntryCard(entry, onDelete = { toDelete = entry }) }
    }

    toDelete?.let { entry ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Delete this log?") },
            text = {
                Text(
                    if (vm.noteName != null) "It's removed from your note too, and the app forgets what it learnt from it."
                    else "The app will forget what it learnt from it.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteEntry(entry)
                    toDelete = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Cancel") } },
        )
    }

    toRemove?.let { clothing ->
        AlertDialog(
            onDismissRequest = { toRemove = null },
            title = { Text("Remove ${clothing.name}?") },
            text = { Text("It disappears from the Log tab. Logs that included it stop counting it.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.removeCustomClothing(clothing)
                    toRemove = null
                }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { toRemove = null }) { Text("Cancel") } },
        )
    }
}

/** Where the logs live: the connected note, a problem reading it, or an invitation to connect it. */
@Composable
fun NoteCard(vm: MainViewModel, onConnectNote: () -> Unit) {
    val problem = vm.noteProblem
    val name = vm.noteName
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when {
                problem != null -> {
                    Text(problem, style = MaterialTheme.typography.bodyMedium)
                    if (vm.noteDamaged) OutlinedButton(onClick = vm::reloadEntries) { Text("Check again") }
                    else OutlinedButton(onClick = onConnectNote) { Text("Connect it again") }
                }
                name != null -> {
                    Text("Your logs live in \"$name\" in Obsidian.", style = MaterialTheme.typography.bodyMedium)
                    if (vm.skippedRows > 0) {
                        Text(
                            "${plural(vm.skippedRows, "row")} there can't be used yet: each needs a Score and clothes the app knows.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                else -> {
                    Text(
                        "Keep your logs in your Obsidian note \"How do I feel at different temperatures\", " +
                            "so you can see them on your Mac. The app learns from the rows already there.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    OutlinedButton(onClick = onConnectNote) { Text("Connect my note") }
                }
            }
        }
    }
}

@Composable
private fun ReminderCard(vm: MainViewModel) {
    val context = LocalContext.current
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Reminder at ${Reminders.HOUR - 12}pm", style = MaterialTheme.typography.titleSmall)
                Text(
                    if (vm.reminderOn && !Reminders.canNotify(context)) "Notifications are off for this app in Android's settings."
                    else "Only on days you haven't logged yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = vm.reminderOn, onCheckedChange = vm::setReminder)
        }
    }
}

@Composable
private fun LearntCard(recommender: Recommender, count: Int) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("What it's learnt", style = MaterialTheme.typography.titleMedium)
            Text(
                if (count == 0) "Nothing yet, so this is the starter guess. Every log teaches it a bit more."
                else "From ${plural(count, "log")}. On a dry day it would now suggest:",
                style = MaterialTheme.typography.bodyMedium,
            )
            listOf(5.0, 12.0, 20.0).forEach { t ->
                Text(
                    "Feels like ${t.toInt()}°C: " + recommender.typicalOutfit(t).joinToString { it.name },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (count > 0) {
                val diff = recommender.differenceFromStarter()
                when {
                    diff >= 0.75 -> "You like to be warmer than the app's generic guess."
                    diff <= -0.75 -> "You like to be cooler than the app's generic guess."
                    else -> null
                }?.let { Text(it, style = MaterialTheme.typography.bodyMedium, fontStyle = FontStyle.Italic) }
            }
        }
    }
}

@Composable
private fun YourClothesCard(clothes: List<CustomClothing>, onRemove: (CustomClothing) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp)) {
            Text("Your own clothes", style = MaterialTheme.typography.titleMedium)
            clothes.forEach { c ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${c.slot.emoji} ${c.name} · ${c.slot.label} · ${WarmthLevel.nearest(c.warmth).label}" +
                            if (c.keepsRainOff) " · keeps rain off" else "",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { onRemove(c) }) { Icon(Icons.Default.Delete, contentDescription = "Remove ${c.name}") }
                }
            }
        }
    }
}

@Composable
private fun EntryCard(entry: Entry, onDelete: () -> Unit) {
    val whenWhere = listOfNotNull(
        entry.date?.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.UK)) ?: "From before the app",
        entry.whenLabel.ifBlank { null },
        entry.place.ifBlank { null },
    ).joinToString(" · ")
    val temperatures = listOfNotNull(
        entry.realTempC?.let { deg(it) },
        "feels like ${deg(entry.dressTempC)}",
        entry.weather.ifBlank { null },
    ).joinToString(" · ")
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "${entry.feeling.emoji} ${entry.feeling.score} ${entry.feeling.label} · $whenWhere",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(temperatures, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                // His own words where he wrote them; otherwise the app's names for the clothes.
                Text(entry.wore.ifBlank { entry.items.joinToString { it.name } }, style = MaterialTheme.typography.bodyMedium)
                if (entry.countedAs.isNotBlank()) {
                    Text(
                        "Counted as: ${entry.countedAs}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (entry.unknownClothes.isNotEmpty()) {
                    Text(
                        "Not counted (the app doesn't know them): ${entry.unknownClothes.joinToString()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                val felt = entry.felt.removePrefix(entry.feeling.label).removePrefix(".").trim()
                if (felt.isNotBlank()) Text(felt, style = MaterialTheme.typography.bodySmall, fontStyle = FontStyle.Italic)
            }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "Delete") }
        }
    }
}

private fun share(context: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "What to Wear logs")
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(send, "Export logs"))
}
