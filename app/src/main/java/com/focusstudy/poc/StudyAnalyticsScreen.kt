package com.focusstudy.poc

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale

@Composable
internal fun StudyAnalyticsScreen(onBack: () -> Unit) {
    val vm: StudyAnalyticsViewModel = viewModel()
    val state by vm.state.collectAsState()
    val today = LocalDate.now()
    val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
    val weekEnd = weekStart.plusDays(7)
    LaunchedEffect(weekStart) { vm.load(weekStart, weekEnd) }
    val locale = remember { Locale.forLanguageTag("ar-EG") }
    val weekDates = remember(weekStart) { (0L..6L).map(weekStart::plusDays) }
    val hasEnoughData = state.activeDays >= 3 && state.completedTasks >= 5
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("رجوع") }
            Spacer(Modifier.weight(1f))
            Text("إحصاءاتك", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricCard("تركيز الأسبوع", "${state.totalMinutes} د", Modifier.weight(1f))
            MetricCard("جلسات مكتملة", state.sessionCount.toString(), Modifier.weight(1f))
            MetricCard("مهام مكتملة", state.completedTasks.toString(), Modifier.weight(1f))
        }
        Spacer(Modifier.height(14.dp))
        Card(colors = CardDefaults.cardColors(containerColor = FocusColors.Surface), shape = RoundedCornerShape(22.dp)) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Text("وقت التركيز خلال الأسبوع", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(14.dp))
                Canvas(Modifier.fillMaxWidth().height(150.dp)) {
                    val maxMinutes = (weekDates.maxOfOrNull { state.dailyMinutes[it] ?: 0 } ?: 0).coerceAtLeast(1)
                    val slot = size.width / 7f
                    val barWidth = slot * 0.52f
                    weekDates.forEachIndexed { index, day ->
                        val minutes = state.dailyMinutes[day] ?: 0
                        val barHeight = size.height * (minutes.toFloat() / maxMinutes)
                        val x = index * slot + (slot - barWidth) / 2f
                        drawRoundRect(
                            color = if (minutes == 0) FocusColors.Outline else FocusColors.Blue,
                            topLeft = androidx.compose.ui.geometry.Offset(x, size.height - barHeight),
                            size = androidx.compose.ui.geometry.Size(barWidth, barHeight.coerceAtLeast(3f)),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(12f, 12f)
                        )
                    }
                }
                Row(Modifier.fillMaxWidth()) {
                    weekDates.forEach { day ->
                        Text(day.dayOfWeek.getDisplayName(TextStyle.SHORT, locale), modifier = Modifier.weight(1f), textAlign = TextAlign.Center, color = FocusColors.TextSecondary, style = MaterialTheme.typography.labelSmall)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    if (!hasEnoughData) "البيانات لسه قليلة لعرض نسبة أو اتجاه أسبوعي موثوق. هنحتاج ٣ أيام فيها جلسات و٥ مهام مكتملة على الأقل."
                    else "عندك ${state.activeDays} أيام مذاكرة هذا الأسبوع، وفيه ${state.overdueTasks} مهام متأخرة تحتاج مراجعة.",
                    color = FocusColors.TextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Text("الأرقام مبنية على جلسات التركيز المكتملة ومواعيد المهام المسجلة على جهازك.", color = FocusColors.TextMuted, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun MetricCard(title: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = FocusColors.Surface), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(title, color = FocusColors.TextSecondary, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
        }
    }
}
