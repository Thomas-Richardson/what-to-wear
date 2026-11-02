package com.richie.whattowear.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.richie.whattowear.MainViewModel
import com.richie.whattowear.model.Feeling
import com.richie.whattowear.model.Slot
import com.richie.whattowear.model.TimeOfDay
import com.richie.whattowear.model.Wardrobe
import com.richie.whattowear.model.WarmthLevel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Open-Meteo keeps about three months of past weather. */
private const val DAYS_BACK = 90L

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun LogScreen(vm: MainViewModel, onUseMyLocation: () -> Unit) {
    val d = vm.draft
    val today = LocalDate.now()
    val yesterday = today.minusDays(1)
    var choosingPlace by remember { mutableStateOf(false) }
    var pickingDate by remember { mutableStateOf(false) }
    var addingTo by remember { mutableStateOf<Slot?>(null) }
    // Reading customClothes here makes the chips redraw when you add or remove your own clothes.
    val clothes = remember(vm.customClothes) { Wardrobe.items }

    Column(
        Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Section("When") {
            val now = d.timeOfDay == TimeOfDay.NOW
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    d.date == today && !now,
                    { vm.editDraft { it.copy(date = today, timeOfDay = it.timeOfDay.notNow()) } },
                    label = { Text("Today") },
                )
                FilterChip(
                    d.date == yesterday,
                    { vm.editDraft { it.copy(date = yesterday, timeOfDay = it.timeOfDay.notNow()) } },
                    label = { Text("Yesterday") },
                )
                val earlier = d.date.isBefore(yesterday)
                FilterChip(
                    earlier,
                    { pickingDate = true },
                    label = { Text(if (earlier) d.date.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.UK)) else "Earlier…") },
                )
                FilterChip(now, { vm.editDraft { it.copy(date = today, timeOfDay = TimeOfDay.NOW) } }, label = { Text("Now") })
            }
            // "Now" already says when, so the parts of the day only show for the other choices.
            if (!now) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TimeOfDay.entries.filter { it != TimeOfDay.NOW }.forEach { part ->
                        FilterChip(
                            d.timeOfDay == part,
                            { vm.editDraft { it.copy(timeOfDay = part) } },
                            label = { Text(part.label) },
                        )
                    }
                }
            }
        }

        Section("Where") {
            PlaceRow(name = d.place?.name ?: "Choose a place", usingGps = false, onChange = { choosingPlace = true })
        }

        ClothesPicker(
            clothes = clothes,
            selected = d.itemIds,
            onToggle = { id -> vm.editDraft { it.copy(itemIds = if (id in it.itemIds) it.itemIds - id else it.itemIds + id) } },
            onAdd = { addingTo = it },
        )

        Section("How did it feel? (1–5)") {
            FeelingRow(d.feeling) { f -> vm.editDraft { it.copy(feeling = f) } }
        }

        OutlinedTextField(
            value = d.note,
            onValueChange = { v -> vm.editDraft { it.copy(note = v) } },
            label = { Text("Note (optional)") },
            modifier = Modifier.fillMaxWidth(),
        )

        Button(onClick = vm::saveDraft, enabled = !vm.saving, modifier = Modifier.fillMaxWidth()) {
            Text(if (vm.saving) "Saving…" else "Save")
        }
    }

    addingTo?.let { slot ->
        AddClothingDialog(
            startSlot = slot,
            taken = { s, name -> clothes.any { it.slot == s && it.name.equals(name, ignoreCase = true) } },
            onAdd = { name, s, level, keepsRainOff ->
                vm.addCustomClothing(name, s, level, keepsRainOff)
                addingTo = null
            },
            onDismiss = { addingTo = null },
        )
    }

    if (choosingPlace) {
        PlaceDialog(
            search = vm::searchPlaces,
            onUseMyLocation = {
                choosingPlace = false
                onUseMyLocation()
            },
            onPick = { p ->
                choosingPlace = false
                vm.editDraft { it.copy(place = p) }
            },
            onDismiss = { choosingPlace = false },
        )
    }

    if (pickingDate) {
        val earliest = today.minusDays(DAYS_BACK)
        val state = rememberDatePickerState(
            initialSelectedDateMillis = d.date.toUtcMillis(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                    val day = utcMillisToDate(utcTimeMillis)
                    return !day.isAfter(today) && !day.isBefore(earliest)
                }

                override fun isSelectableYear(year: Int) = year in earliest.year..today.year
            },
        )
        DatePickerDialog(
            onDismissRequest = { pickingDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { ms ->
                        val picked = utcMillisToDate(ms)
                        vm.editDraft { it.copy(date = picked, timeOfDay = if (picked == today) it.timeOfDay else it.timeOfDay.notNow()) }
                    }
                    pickingDate = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickingDate = false }) { Text("Cancel") } },
        ) {
            DatePicker(state)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddClothingDialog(
    startSlot: Slot,
    taken: (Slot, String) -> Boolean,
    onAdd: (String, Slot, WarmthLevel, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var slot by remember { mutableStateOf(startSlot) }
    var level by remember { mutableStateOf(WarmthLevel.defaultFor(startSlot)) }
    var keepsRainOff by remember { mutableStateOf(false) }
    val clash = name.isNotBlank() && taken(slot, name.trim())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add your own") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("What is it?") },
                    placeholder = { Text("e.g. Navy pea coat") },
                    singleLine = true,
                    isError = clash,
                    supportingText = if (clash) {
                        { Text("That's already in ${slot.label}.") }
                    } else {
                        null
                    },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Goes under", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Slot.entries.forEach { s -> FilterChip(slot == s, { slot = s }, label = { Text(s.label) }) }
                }
                Text("How warm is it?", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WarmthLevel.entries.forEach { w -> FilterChip(level == w, { level = w }, label = { Text(w.label) }) }
                }
                Text(
                    "${level.label}: ${level.example}.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { keepsRainOff = !keepsRainOff },
                ) {
                    Checkbox(checked = keepsRainOff, onCheckedChange = { keepsRainOff = it })
                    Text("Keeps the rain off")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onAdd(name.trim(), slot, level, keepsRainOff) }, enabled = name.isNotBlank() && !clash) {
                Text("Add")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// The date picker works in UTC milliseconds.
private fun LocalDate.toUtcMillis() = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun utcMillisToDate(ms: Long) = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()

/** Switching to an earlier day turns "Now" into "All day". */
private fun TimeOfDay.notNow() = if (this == TimeOfDay.NOW) TimeOfDay.ALL_DAY else this

/** The five buttons for Richie's 1–5 scale: number, emoji and his words. */
@Composable
fun FeelingRow(selected: Feeling?, onPick: (Feeling) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Feeling.entries.forEach { f ->
            val on = selected == f
            Surface(
                onClick = { onPick(f) },
                shape = MaterialTheme.shapes.medium,
                color = if (on) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                border = if (on) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                modifier = Modifier.weight(1f),
            ) {
                Column(
                    Modifier.padding(vertical = 10.dp, horizontal = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("${f.score}", style = MaterialTheme.typography.titleLarge)
                    Text(f.emoji, fontSize = 22.sp)
                    Text(f.label, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
                }
            }
        }
    }
}
