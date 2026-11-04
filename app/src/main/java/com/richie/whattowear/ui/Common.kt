package com.richie.whattowear.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.richie.whattowear.model.Place
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel(title)
        content()
    }
}

@Composable
fun PlaceRow(name: String, usingGps: Boolean, onChange: () -> Unit, trailing: @Composable () -> Unit = {}) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(if (usingGps) Icons.Default.LocationOn else Icons.Default.Place, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text(name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        TextButton(onClick = onChange) { Text("Change") }
        trailing()
    }
}

@Composable
fun PlaceDialog(
    search: suspend (String) -> List<Place>,
    onUseMyLocation: () -> Unit,
    onPick: (Place) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Place>>(emptyList()) }
    var status by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val runSearch = {
        if (query.isNotBlank()) {
            status = "Searching…"
            scope.launch {
                status = try {
                    results = search(query)
                    if (results.isEmpty()) "No places found." else null
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    "Couldn't search. Check your internet."
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Where?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(shape = MaterialTheme.shapes.small, onClick = onUseMyLocation, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.LocationOn, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Use my location")
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search for a town or city") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { runSearch() }),
                    trailingIcon = {
                        IconButton(onClick = { runSearch() }) { Icon(Icons.Default.Search, contentDescription = "Search") }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                status?.let { Text(it) }
                Column(Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState())) {
                    results.forEach { p ->
                        ListItem(
                            headlineContent = { Text(p.name) },
                            supportingContent = p.detail?.let { { Text(it) } },
                            modifier = Modifier.clickable { onPick(p) },
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
