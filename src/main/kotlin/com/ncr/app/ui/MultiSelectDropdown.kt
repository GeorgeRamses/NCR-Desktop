package com.ncr.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Dropdown with checkboxes. The menu stays open while ticking items.
 * Empty selection = "no filter" (shows [allLabel]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> MultiSelectDropdown(
    label: String,
    allLabel: String,
    options: List<Pair<T, String>>,
    selected: Set<T>,
    onSelectedChange: (Set<T>) -> Unit,
    modifier: Modifier = Modifier,
    searchable: Boolean = false
) {
    var expanded by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }

    val visible = remember(options, query) {
        if (query.isBlank()) options
        else options.filter { it.second.contains(query.trim(), ignoreCase = true) }
    }

    val displayText = when {
        selected.isEmpty() -> allLabel
        selected.size == 1 -> options.firstOrNull { it.first == selected.first() }?.second ?: allLabel
        else -> "${selected.size} selected"
    }

    Box(modifier) {
        OutlinedTextField(
            value = displayText,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth()
        )
        // Transparent overlay so the whole field opens the menu
        Box(Modifier.matchParentSize().clickable { expanded = true })

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false; query = "" },
            modifier = Modifier.widthIn(min = 280.dp)
        ) {
            if (searchable) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear search")
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp).fillMaxWidth()
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(onClick = { onSelectedChange(selected + visible.map { it.first }) }) {
                    Text(if (query.isBlank()) "Select all" else "Select shown")
                }
                TextButton(onClick = { onSelectedChange(emptySet()) }) { Text("Clear") }
            }
            HorizontalDivider()

            if (visible.isEmpty()) {
                DropdownMenuItem(text = { Text("No matches") }, enabled = false, onClick = {})
            }
            visible.forEach { (value, name) ->
                val checked = value in selected
                DropdownMenuItem(
                    text = { Text(name) },
                    leadingIcon = { Checkbox(checked = checked, onCheckedChange = null) },
                    onClick = {
                        onSelectedChange(if (checked) selected - value else selected + value)
                    }
                )
            }
        }
    }
}

data class FilterChipItem(val label: String, val onRemove: () -> Unit)

/** Row of removable chips summarising the active filters. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SelectedFilterChips(chips: List<FilterChipItem>, modifier: Modifier = Modifier) {
    if (chips.isEmpty()) return
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        chips.forEach { chip ->
            InputChip(
                selected = true,
                onClick = chip.onRemove,
                label = { Text(chip.label, fontSize = 12.sp) },
                trailingIcon = {
                    Icon(
                        Icons.Default.Clear,
                        contentDescription = "Remove ${chip.label}",
                        modifier = Modifier.size(16.dp)
                    )
                }
            )
        }
    }
}
