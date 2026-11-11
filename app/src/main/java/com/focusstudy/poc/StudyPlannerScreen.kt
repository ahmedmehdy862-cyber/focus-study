package com.focusstudy.poc

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
internal fun StudyPlannerScreen(dailyGoal: Int, onBack: () -> Unit, onStartTask: (Long) -> Unit) {
    val vm: TaskListViewModel = viewModel()
    val state by vm.state.collectAsState()
    val version by SessionEngine.tasksVersion.collectAsState()
    LaunchedEffect(version) { vm.refresh() }
    val today = LocalDate.now()
    val plan = remember(state.tasks, dailyGoal, today) { StudyPlanner.createWeek(state.tasks, today, dailyGoal) }
    val locale = remember { Locale.forLanguageTag("ar-EG") }
    val formatter = remember { DateTimeFormatter.ofPattern("EEEE d MMMM", locale) }
    val totalMinutes = plan.sumOf { it.minutes }
    val openCount = state.tasks.count { !it.isDone && it.status != TaskStatus.CANCELLED }
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("رجوع") }
            Spacer(Modifier.weight(1f))
            Text("الخطة الذكية", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(8.dp))
        Text("خطة عملية من مهامك ومواعيدك الفعلية. خصصنا حدًا يوميًا قدره $dailyGoal دقيقة، مع تقسيم الجلسات إلى فترات قصيرة.", color = FocusColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(8.dp))
        Text("${plan.size} جلسة مقترحة · $totalMinutes دقيقة هذا الأسبوع · $openCount مهام مفتوحة", color = FocusColors.onTint(FocusColors.Teal), style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(10.dp))
        if (plan.isEmpty()) {
            Column(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text("مافيش مهام مفتوحة للخطة حاليًا.", style = MaterialTheme.typography.titleMedium)
                Text("أضف مهامًا مع مدة أو موعد استحقاق عشان نرتبها لك.", color = FocusColors.TextSecondary)
            }
        } else LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            plan.groupBy { it.date }.forEach { (date, blocks) ->
                item(key = "day-$date") {
                    Text(date.format(formatter), modifier = Modifier.padding(top = 8.dp), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                }
                items(blocks, key = { "${date}-${it.task.id}" }) { block ->
                    Card(colors = CardDefaults.cardColors(containerColor = FocusColors.Surface), shape = RoundedCornerShape(18.dp)) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(block.task.title, maxLines = 1, fontWeight = FontWeight.SemiBold)
                                Text("${block.minutes} دقيقة · ${block.task.priority.label}", color = FocusColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                                if (block.minutes >= 45) Text("استراحة 10 دقائق بعد الجلسة", color = FocusColors.onTint(FocusColors.Teal), style = MaterialTheme.typography.labelSmall)
                            }
                            TextButton(onClick = { onStartTask(block.task.id) }) { Text("ابدأ") }
                        }
                    }
                }
            }
        }
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) { Text("رجوع للمهام") }
    }
}
