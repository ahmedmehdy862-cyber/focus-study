package com.focusstudy.poc

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.launch

private data class PendingCalendarEvent(val title: String, val startsAt: Long, val endsAt: Long, val kind: String)

@Composable
internal fun StudyCalendarScreen(onBack: () -> Unit, onStartTask: (Long) -> Unit) {
    val vm: TaskListViewModel = viewModel()
    val state by vm.state.collectAsState()
    val version by SessionEngine.tasksVersion.collectAsState()
    LaunchedEffect(version) { vm.refresh() }
    val eventVm: StudyCalendarViewModel = viewModel()
    val eventState by eventVm.state.collectAsState()
    val today = LocalDate.now()
    var monthKey by rememberSaveable { mutableStateOf(YearMonth.from(today).toString()) }
    val month = remember(monthKey) { YearMonth.parse(monthKey) }
    var selectedDate by rememberSaveable { mutableStateOf(today.toString()) }
    var calendarView by rememberSaveable { mutableStateOf("month") }
    val selected = remember(selectedDate) { runCatching { LocalDate.parse(selectedDate) }.getOrDefault(today) }
    val first = month.atDay(1).with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
    val days = remember(monthKey) { (0L..41L).map { first.plusDays(it) } }
    val locale = remember { Locale.forLanguageTag("ar-EG") }
    val monthTitle = remember(monthKey) { month.atDay(1).format(DateTimeFormatter.ofPattern("MMMM yyyy", locale)) }
    val scopeStart = if (calendarView == "week") selected.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY)) else selected
    val scopeEnd = if (calendarView == "week") scopeStart.plusDays(6) else selected
    val scopeTasks = state.tasks.filter { task ->
        val date = task.dueAt?.let { java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).toLocalDate() }
        (date != null && !date.isBefore(scopeStart) && !date.isAfter(scopeEnd)) ||
            (date == null && !today.isBefore(scopeStart) && !today.isAfter(scopeEnd))
    }
    val zone = remember { ZoneId.systemDefault() }
    val eventRangeStart = when (calendarView) {
        "month" -> month.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        "week" -> scopeStart.atStartOfDay(zone).toInstant().toEpochMilli()
        else -> selected.atStartOfDay(zone).toInstant().toEpochMilli()
    }
    val eventRangeEnd = when (calendarView) {
        "month" -> month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        "week" -> scopeEnd.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        else -> selected.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    }
    LaunchedEffect(eventRangeStart, eventRangeEnd) { eventVm.load(eventRangeStart, eventRangeEnd) }
    val selectedEvents = eventState.events.filter {
        it.startsAt >= scopeStart.atStartOfDay(zone).toInstant().toEpochMilli() &&
            it.startsAt < scopeEnd.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    }
    var showAddEvent by rememberSaveable { mutableStateOf(false) }
    var pendingConflict by remember { mutableStateOf<PendingCalendarEvent?>(null) }
    var conflictSuggestion by remember { mutableStateOf<Long?>(null) }
    val coroutineScope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("رجوع") }
            Spacer(Modifier.width(8.dp))
            Text("التقويم الدراسي", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = FocusColors.TextPrimary)
            Button(onClick = { showAddEvent = true }, shape = RoundedCornerShape(14.dp)) { Text("موعد +") }
        }
        Text("خطّط أسبوعك، راجع يومك، وابدأ المهمة من نفس الشاشة.", modifier = Modifier.padding(start = 8.dp, bottom = 8.dp), color = FocusColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            listOf("day" to "يوم", "week" to "أسبوع", "month" to "شهر").forEach { (key, label) ->
                FilterChip(selected = calendarView == key, onClick = { calendarView = key }, label = { Text(label) })
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { monthKey = YearMonth.from(today).toString(); selectedDate = today.toString() }) { Text("اليوم") }
        }
        if (calendarView != "month") {
            val step = if (calendarView == "week") 7L else 1L
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = { selectedDate = selected.minusDays(step).toString() }) { Text("‹ السابق") }
                Text(
                    if (calendarView == "day") selected.format(DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", locale))
                    else "أسبوع ${selected.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY)).format(DateTimeFormatter.ofPattern("d MMM", locale))}",
                    modifier = Modifier.weight(1f), textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold,
                    color = FocusColors.TextPrimary
                )
                TextButton(onClick = { selectedDate = selected.plusDays(step).toString() }) { Text("التالي ›") }
            }
        }
        if (calendarView == "week") {
            val weekStart = selected.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
            Row(Modifier.fillMaxWidth()) {
                (0L..6L).forEach { offset ->
                    val date = weekStart.plusDays(offset)
                    val count = state.tasks.count { it.dueAt.isOnLocalDate(date) }
                    Column(
                        modifier = Modifier.weight(1f).padding(2.dp).clip(RoundedCornerShape(12.dp))
                            .background(if (date == selected) FocusColors.Blue else FocusColors.Surface)
                            .clickable { selectedDate = date.toString() }.padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale), color = if (date == selected) Color.White else FocusColors.TextSecondary, style = MaterialTheme.typography.labelSmall)
                        Text(date.dayOfMonth.toString(), color = if (date == selected) Color.White else FocusColors.TextPrimary)
                        Text(if (count == 0) "·" else count.toString(), color = if (date == selected) Color.White else FocusColors.onTint(FocusColors.Teal), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
        if (calendarView == "month") Card(colors = CardDefaults.cardColors(containerColor = FocusColors.Surface), shape = RoundedCornerShape(22.dp)) {
            Column(Modifier.fillMaxWidth().padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = {
                        val target = month.minusMonths(1)
                        monthKey = target.toString()
                        selectedDate = target.atDay(1).toString()
                    }) { Text("‹ السابق") }
                    Text(monthTitle, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = FocusColors.TextPrimary)
                    TextButton(onClick = {
                        val target = month.plusMonths(1)
                        monthKey = target.toString()
                        selectedDate = target.atDay(1).toString()
                    }) { Text("التالي ›") }
                }
                TextButton(
                    onClick = { monthKey = YearMonth.from(today).toString(); selectedDate = today.toString() },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) { Text("العودة إلى اليوم") }
                Row(Modifier.fillMaxWidth()) {
                    (0..6).forEach { offset ->
                        val dow = DayOfWeek.SUNDAY.plus(offset.toLong())
                        Text(dow.getDisplayName(TextStyle.SHORT, locale), Modifier.weight(1f), textAlign = TextAlign.Center, color = FocusColors.TextSecondary, style = MaterialTheme.typography.labelSmall)
                    }
                }
                days.chunked(7).forEach { week ->
                    Row(Modifier.fillMaxWidth()) {
                        week.forEach { date ->
                            val dayTasks = state.tasks.filter { it.dueAt.isOnLocalDate(date) }
                            val dayEvents = eventState.events.count { java.time.Instant.ofEpochMilli(it.startsAt).atZone(zone).toLocalDate() == date }
                            val isSelected = date == selected
                            val inMonth = YearMonth.from(date) == month
                            Column(
                                modifier = Modifier.weight(1f).padding(2.dp).clip(RoundedCornerShape(12.dp))
                                    .background(if (isSelected) FocusColors.Blue else if (date == today) FocusColors.SurfaceHigh else Color.Transparent)
                                    .clickable { selectedDate = date.toString() }
                                    .padding(vertical = 7.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    date.dayOfMonth.toString(),
                                    color = if (isSelected) Color.White else if (inMonth) FocusColors.TextPrimary else FocusColors.TextMuted,
                                    style = MaterialTheme.typography.labelMedium
                                )
                                if (dayTasks.isNotEmpty() || dayEvents > 0) {
                                    Text((dayTasks.size + dayEvents).toString(), color = if (isSelected) Color.White else FocusColors.onTint(FocusColors.Teal), style = MaterialTheme.typography.labelSmall)
                                } else {
                                    Spacer(Modifier.height(14.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (calendarView == "week") "مهام الأسبوع" else selected.format(DateTimeFormatter.ofPattern("EEEE d MMMM", locale)), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = FocusColors.TextPrimary)
            Spacer(Modifier.weight(1f))
            Text("${scopeTasks.size} مهام · ${selectedEvents.size} مواعيد", color = FocusColors.TextSecondary, style = MaterialTheme.typography.labelMedium)
        }
        Spacer(Modifier.height(6.dp))
        val selectedTasks = scopeTasks
            .sortedWith(compareBy<StudyTask> { it.isDone || it.status == TaskStatus.DONE }.thenByDescending { it.priority.level })
        if (selectedTasks.isEmpty() && selectedEvents.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Card(colors = CardDefaults.cardColors(containerColor = FocusColors.Surface), shape = RoundedCornerShape(20.dp)) {
                    Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("يوم بمساحة لبداية جديدة", color = FocusColors.TextPrimary, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text("أضف موعد مذاكرة أو اختر مهمة من يوم آخر، ثم ابدأ جلستك من هنا.", color = FocusColors.TextSecondary, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall)
                        Button(onClick = { showAddEvent = true }, shape = RoundedCornerShape(14.dp)) { Text("أضف موعدًا") }
                    }
                }
            }
        } else {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (selectedEvents.isNotEmpty()) item { Text("المواعيد", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = FocusColors.TextPrimary) }
                items(selectedEvents, key = { "event-${it.id}" }) { event ->
                    Card(colors = CardDefaults.cardColors(containerColor = FocusColors.Surface), shape = RoundedCornerShape(18.dp)) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(event.title, maxLines = 1, fontWeight = FontWeight.SemiBold, color = FocusColors.TextPrimary)
                                val startTime = java.time.Instant.ofEpochMilli(event.startsAt).atZone(zone).toLocalTime()
                                val endTime = java.time.Instant.ofEpochMilli(event.endsAt).atZone(zone).toLocalTime()
                                Text("${event.kind} · ${startTime.format(DateTimeFormatter.ofPattern("HH:mm"))}–${endTime.format(DateTimeFormatter.ofPattern("HH:mm"))}", color = FocusColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                            }
                            TextButton(onClick = { eventVm.delete(event.id, eventRangeStart, eventRangeEnd) }) { Text("حذف") }
                        }
                    }
                }
                if (selectedTasks.isNotEmpty()) item { Text("المهام", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = FocusColors.TextPrimary) }
                items(selectedTasks, key = { it.id }) { task ->
                    Card(colors = CardDefaults.cardColors(containerColor = FocusColors.Surface), shape = RoundedCornerShape(18.dp)) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(task.title, maxLines = 1, fontWeight = FontWeight.SemiBold, color = FocusColors.TextPrimary)
                                val taskDay = task.dueAt?.let { java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).toLocalDate() }
                                val datePrefix = when {
                                    taskDay == null -> "بدون موعد · "
                                    calendarView == "week" -> taskDay.format(DateTimeFormatter.ofPattern("EEE d MMM", locale)) + " · "
                                    else -> ""
                                }
                                Text("$datePrefix${task.category.label} · ${task.plannedMinutes} دقيقة · ${task.priority.label}", color = FocusColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                            }
                            val completed = task.isDone || task.status == TaskStatus.DONE
                            Checkbox(
                                checked = completed,
                                onCheckedChange = { done -> vm.setStatus(task.id, if (done) TaskStatus.DONE else TaskStatus.TODO) }
                            )
                            if (!completed) {
                                if (task.dueAt == null) TextButton(onClick = {
                                    vm.reschedule(task.id, selected.atStartOfDay(zone).toInstant().toEpochMilli())
                                }) { Text("خطط لليوم") }
                                TextButton(onClick = { onStartTask(task.id) }) { Text("ابدأ") }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddEvent) {
        CalendarEventDialog(
            selectedDate = selected,
            onDismiss = { showAddEvent = false },
            onAdd = { title, startsAt, endsAt, kind ->
                coroutineScope.launch {
                    if (eventVm.hasConflict(startsAt, endsAt)) {
                        pendingConflict = PendingCalendarEvent(title, startsAt, endsAt, kind)
                        val dayEnd = selected.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
                        conflictSuggestion = eventVm.nextAvailableStart(startsAt, endsAt - startsAt, dayEnd) ?: -1L
                        showAddEvent = false
                    } else {
                        eventVm.add(title, startsAt, endsAt, kind)
                        showAddEvent = false
                        eventVm.load(eventRangeStart, eventRangeEnd)
                    }
                }
            }
        )
    }
    conflictSuggestion?.let { suggestion ->
        val draft = pendingConflict
        AlertDialog(
            onDismissRequest = { conflictSuggestion = null; pendingConflict = null; showAddEvent = true },
            title = { Text("الموعد يتعارض مع حدث آخر") },
            text = {
                Text(
                    if (suggestion == -1L) "لا توجد فترة كافية متاحة في هذا اليوم. عدّل وقت البداية والنهاية."
                    else "أقرب وقت متاح اليوم ${java.time.Instant.ofEpochMilli(suggestion).atZone(zone).toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"))}."
                )
            },
            confirmButton = {
                TextButton(enabled = suggestion >= 0L, onClick = {
                    conflictSuggestion = null
                    pendingConflict = null
                    coroutineScope.launch {
                        draft?.let { eventVm.add(it.title, suggestion, suggestion + (it.endsAt - it.startsAt), it.kind) }
                        eventVm.load(eventRangeStart, eventRangeEnd)
                    }
                }) { Text("استخدم الوقت المقترح") }
            },
            dismissButton = { TextButton(onClick = { conflictSuggestion = null; pendingConflict = null; showAddEvent = true }) { Text("تعديل الوقت") } }
        )
    }
}

@Composable
private fun CalendarEventDialog(
    selectedDate: LocalDate,
    onDismiss: () -> Unit,
    onAdd: (String, Long, Long, String) -> Unit
) {
    var title by rememberSaveable(selectedDate.toString()) { mutableStateOf("") }
    var start by rememberSaveable(selectedDate.toString()) { mutableStateOf("09:00") }
    var end by rememberSaveable(selectedDate.toString()) { mutableStateOf("10:00") }
    var kind by rememberSaveable(selectedDate.toString()) { mutableStateOf("دراسة") }
    val zone = ZoneId.systemDefault()
    val startMillis = runCatching { selectedDate.atTime(LocalTime.parse(start)).atZone(zone).toInstant().toEpochMilli() }.getOrNull()
    val endMillis = runCatching { selectedDate.atTime(LocalTime.parse(end)).atZone(zone).toInstant().toEpochMilli() }.getOrNull()
    val valid = title.isNotBlank() && startMillis != null && endMillis != null && endMillis > startMillis
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إضافة موعد") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(selectedDate.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.forLanguageTag("ar-EG"))), color = FocusColors.TextSecondary)
                OutlinedTextField(value = title, onValueChange = { title = it.take(80) }, label = { Text("اسم الموعد") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = start, onValueChange = { start = it }, label = { Text("من (HH:mm)") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = end, onValueChange = { end = it }, label = { Text("إلى (HH:mm)") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    listOf("دراسة", "محاضرة", "امتحان", "استراحة").forEach { option ->
                        FilterChip(selected = kind == option, onClick = { kind = option }, label = { Text(option) })
                    }
                }
                if (startMillis == null || endMillis == null || endMillis <= startMillis) {
                    Text("استخدم وقت 24 ساعة، ويجب أن ينتهي الموعد بعد بدايته.", color = FocusColors.onTint(FocusColors.Rose), style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = { TextButton(enabled = valid, onClick = { onAdd(title.trim(), startMillis!!, endMillis!!, kind) }) { Text("حفظ الموعد") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}
