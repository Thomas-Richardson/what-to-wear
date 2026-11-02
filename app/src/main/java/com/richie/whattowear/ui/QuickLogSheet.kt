package com.richie.whattowear.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.richie.whattowear.MainViewModel
import com.richie.whattowear.model.Wardrobe

/**
 * The window that opens from the home-screen widget or the reminder: how it feels, the clothes
 * (already ticked), and Save. It sits over whatever was on screen and closes once saved.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuickLogSheet(vm: MainViewModel, onClose: () -> Unit) {
    val d = vm.draft
    val context = LocalContext.current
    val clothes = remember(vm.customClothes) { Wardrobe.items }
    // The clothes it started with stay on show even if unticked, so a mis-tap can be undone.
    val startedWith = remember { d.itemIds }
    var showAll by remember { mutableStateOf(false) }
    val noTap = remember { MutableInteractionSource() }

    LaunchedEffect(vm.savedCount) { if (vm.savedCount > 0) onClose() }
    LaunchedEffect(vm.message) {
        vm.message?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            vm.message = null
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.4f))
            .clickable(interactionSource = noTap, indication = null, onClick = onClose),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Card(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(12.dp)
                // Taps on the card itself mustn't close the window.
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        ) {
            Column(
                Modifier
                    .heightIn(max = 640.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text("Log now", style = MaterialTheme.typography.titleLarge)
                d.place?.let {
                    Text(
                        "At ${it.name}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FeelingRow(d.feeling) { f -> vm.editDraft { it.copy(feeling = f) } }
                AiFill(vm)

                val toggle = { id: String ->
                    vm.editDraft { it.copy(itemIds = if (id in it.itemIds) it.itemIds - id else it.itemIds + id) }
                }
                if (showAll) {
                    ClothesPicker(clothes = clothes, selected = d.itemIds, onToggle = toggle)
                } else {
                    Section("Wearing") {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            clothes.filter { it.id in startedWith || it.id in d.itemIds }.forEach { item ->
                                FilterChip(
                                    selected = item.id in d.itemIds,
                                    onClick = { toggle(item.id) },
                                    label = { Text("${item.emoji} ${item.name}") },
                                )
                            }
                            AssistChip(onClick = { showAll = true }, label = { Text("Other clothes") })
                        }
                    }
                }

                Button(
                    onClick = vm::saveDraft,
                    enabled = !vm.saving && d.feeling != null && d.itemIds.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (vm.saving) "Saving…" else "Save") }
            }
        }
    }
}
