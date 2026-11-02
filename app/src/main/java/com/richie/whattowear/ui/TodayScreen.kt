package com.richie.whattowear.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.richie.whattowear.Ahead
import com.richie.whattowear.MainViewModel
import com.richie.whattowear.model.Recommender
import com.richie.whattowear.model.WeatherCodes
import com.richie.whattowear.model.WeatherSummary
import com.richie.whattowear.model.degRange

@Composable
fun TodayScreen(vm: MainViewModel, onUseMyLocation: () -> Unit, onLogOutfit: () -> Unit, onConnectNote: () -> Unit) {
    val recommender = remember(vm.entries, vm.customClothes) { Recommender(vm.entries) }
    val summary = vm.todaySummary()
    val suggestion = remember(summary, recommender) { summary?.let(recommender::suggest) }
    var choosingPlace by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        PlaceRow(
            name = vm.place?.name ?: "Choose a place",
            usingGps = vm.usingGps,
            onChange = { choosingPlace = true },
            trailing = {
                IconButton(onClick = { vm.refresh() }, enabled = !vm.loading) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                }
            },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Ahead.entries.forEach { a ->
                FilterChip(selected = vm.ahead == a, onClick = { vm.ahead = a }, label = { Text(a.label) })
            }
        }

        // Until the note is connected (or if it can't be read), say so here too: it's where the learning comes from.
        if (vm.noteName == null || vm.noteProblem != null) NoteCard(vm, onConnectNote)

        when {
            summary != null && suggestion != null -> {
                WeatherCard(summary)
                SuggestionCard(suggestion)
                Button(
                    onClick = {
                        vm.startLogFromSuggestion(suggestion.items)
                        onLogOutfit()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Log what I wore") }
                Text(
                    "Starts from this suggestion. Change anything you didn't wear.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            vm.loading -> Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            else -> Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(vm.error ?: "No weather yet.")
                    vm.locationDetail?.let {
                        Text(
                            "Details: $it",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        when {
                            vm.locationBlocked -> OutlinedButton(onClick = { openAppSettings(context) }) { Text("Open settings") }
                            vm.needsLocationPermission -> OutlinedButton(onClick = onUseMyLocation) { Text("Allow location") }
                        }
                        OutlinedButton(onClick = { vm.refresh() }) { Text("Try again") }
                    }
                }
            }
        }
    }

    if (choosingPlace) {
        PlaceDialog(
            search = vm::searchPlaces,
            onUseMyLocation = {
                choosingPlace = false
                onUseMyLocation()
            },
            onPick = {
                choosingPlace = false
                vm.choosePlace(it)
            },
            onDismiss = { choosingPlace = false },
        )
    }
}

/** Android's page for this app, where location permission can be switched on after "Don't allow". */
private fun openAppSettings(context: Context) = context.startActivity(
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
)

@Composable
private fun WeatherCard(w: WeatherSummary) {
    val (emoji, words) = WeatherCodes.describe(w.weatherCode, night = w.dayShare < 0.5)
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(emoji, fontSize = 40.sp)
            Spacer(Modifier.width(16.dp))
            Column {
                Text("Feels like ${degRange(w.feelsMinC, w.feelsMaxC)}", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "$words · rain ${w.rainChanceMax}% · wind ${w.windMaxKmh.toInt()} km/h",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SuggestionCard(s: Recommender.Suggestion) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Wear", style = MaterialTheme.typography.titleMedium)
            s.items.forEach { item ->
                Text("${item.emoji}  ${item.name}", style = MaterialTheme.typography.titleLarge)
            }
            Spacer(Modifier.width(4.dp))
            Text(s.reason, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            s.notes.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
        }
    }
}
