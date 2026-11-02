package com.richie.whattowear.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.richie.whattowear.model.ClothingItem
import com.richie.whattowear.model.Slot

/**
 * The clothes, grouped Top, Layer, Jacket, Bottoms, Extras, as chips you tick.
 * [onAdd] shows an "Add your own" chip in each group; leave it out to hide them.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ClothesPicker(
    clothes: List<ClothingItem>,
    selected: Set<String>,
    onToggle: (String) -> Unit,
    onAdd: ((Slot) -> Unit)? = null,
) {
    // Its own column, so the groups stack whatever they're placed in.
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Slot.entries.forEach { slot ->
            Section(slot.label) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    clothes.filter { it.slot == slot }.forEach { item ->
                        FilterChip(
                            selected = item.id in selected,
                            onClick = { onToggle(item.id) },
                            label = { Text("${item.emoji} ${item.name}") },
                        )
                    }
                    if (onAdd != null) {
                        AssistChip(
                            onClick = { onAdd(slot) },
                            label = { Text("Add your own") },
                            leadingIcon = { Icon(Icons.Default.Add, contentDescription = null, Modifier.size(AssistChipDefaults.IconSize)) },
                        )
                    }
                }
            }
        }
    }
}

