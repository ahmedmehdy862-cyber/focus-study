package com.focusstudy.poc

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

internal fun parseDueDateMillis(value: String?): Long? = value
    ?.trim()
    ?.takeIf(String::isNotEmpty)
    ?.let { runCatching { LocalDate.parse(it).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() }.getOrNull() }

internal fun formatDueDate(value: Long?): String = value
    ?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate().toString() }
    .orEmpty()

private fun Long?.asLocalDate(): LocalDate? = this
    ?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate() }

internal fun Long?.isOnLocalDate(date: LocalDate): Boolean = asLocalDate() == date
internal fun Long?.isAfterLocalDate(date: LocalDate): Boolean = asLocalDate()?.isAfter(date) == true
internal fun Long?.isBeforeLocalDate(date: LocalDate): Boolean = asLocalDate()?.isBefore(date) == true

internal enum class TaskListFilter(val label: String) {
    ALL("الكل"),
    TODAY("اليوم"),
    UPCOMING("القادمة"),
    OVERDUE("المتأخرة"),
    OPEN("غير مكتملة"),
    COMPLETED("مكتملة"),
    URGENT("عاجلة")
}

internal enum class TaskListSort(val label: String) {
    DEADLINE("موعد التسليم"),
    PRIORITY("الأولوية"),
    NEWEST("الأحدث"),
    TITLE("الاسم")
}

@Composable
internal fun TaskListControls(
    query: String,
    onQueryChange: (String) -> Unit,
    filter: TaskListFilter,
    onFilterChange: (TaskListFilter) -> Unit,
    sort: TaskListSort,
    onSortChange: (TaskListSort) -> Unit
) {
    var sortMenuExpanded by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("بحث في المهام") },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    TextButton(onClick = { onQueryChange("") }) { Text("مسح") }
                }
            },
            textStyle = MaterialTheme.typography.bodyMedium
        )

        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Row(
                modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                TaskListFilter.entries.forEach { option ->
                    FilterChip(
                        selected = filter == option,
                        onClick = { onFilterChange(option) },
                        label = { Text(option.label) },
                        modifier = Modifier.semantics {
                            contentDescription = "تصفية المهام: ${option.label}"
                        }
                    )
                }
            }

            androidx.compose.foundation.layout.Box {
                TextButton(onClick = { sortMenuExpanded = true }) {
                    Text("ترتيب")
                }
                DropdownMenu(
                    expanded = sortMenuExpanded,
                    onDismissRequest = { sortMenuExpanded = false }
                ) {
                    TaskListSort.entries.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.label) },
                            onClick = {
                                onSortChange(option)
                                sortMenuExpanded = false
                            },
                            trailingIcon = if (sort == option) {
                                { Text("✓", color = MaterialTheme.colorScheme.primary) }
                            } else null
                        )
                    }
                }
            }
        }
    }
}
