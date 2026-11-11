package com.focusstudy.poc

import android.app.TimePickerDialog
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun FirstLaunchSetupScreen(
    current: FocusPreferences,
    onComplete: (FocusPreferences) -> Unit,
    onRequestNotifications: () -> Unit
) {
    val context = LocalContext.current
    var page by rememberSaveable { mutableStateOf(0) }
    var name by rememberSaveable { mutableStateOf(current.studentName) }
    var goal by rememberSaveable { mutableStateOf(current.dailyGoalMinutes.toFloat()) }
    var avatar by rememberSaveable { mutableStateOf(current.avatarPath) }
    var style by rememberSaveable { mutableStateOf(current.companionStyle) }
    var general by rememberSaveable { mutableStateOf(current.generalNotificationsEnabled) }
    var morning by rememberSaveable { mutableStateOf(current.morningMessageEnabled) }
    var evening by rememberSaveable { mutableStateOf(current.eveningMessageEnabled) }
    var morningHour by rememberSaveable { mutableStateOf(current.morningMessageHour) }
    var eveningHour by rememberSaveable { mutableStateOf(current.eveningMessageHour) }

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp)) {
        Text("Focus Study", style = MaterialTheme.typography.labelLarge, color = FocusColors.onTint(FocusColors.Blue))
        Text(if (page == 0) "نجهّز مساحتك الدراسية" else "نضبط طريقة التذكير", style = MaterialTheme.typography.headlineSmall, color = FocusColors.TextPrimary, fontWeight = FontWeight.Bold)
        Text("الخطوة ${page + 1} من 2", style = MaterialTheme.typography.bodySmall, color = FocusColors.TextSecondary)
        Spacer(Modifier.height(12.dp))
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (page == 0) {
                item {
                    SetupCard("ملفك الدراسي", "اسمك هيظهر في الترحيب والرسائل التشجيعية.") {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it.take(32) },
                            label = { Text("الاسم الذي تحب أن نناديك به") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp)
                        )
                        if (name.trim().length < 2) Text("اكتب اسمًا من حرفين على الأقل للمتابعة.", color = FocusColors.Amber)
                    }
                }
                item {
                    SetupCard("رفيقك الجانبي", "اختار صورتك ونمطًا بصريًا ليظهر رفيقك ويتفاعل مع تقدمك.") {
                        AvatarChooser(avatar, name, style, onAvatarChanged = { avatar = it })
                        Spacer(Modifier.height(12.dp))
                        CompanionStylePicker(style, onSelect = { style = it })
                    }
                }
                item {
                    SetupCard("هدفك اليومي", "نبدأ بهدف واقعي ويمكنك تغييره في أي وقت.") {
                        Text("${goal.toInt()} دقيقة مذاكرة", color = FocusColors.TextSecondary)
                        Slider(value = goal, onValueChange = { goal = it }, valueRange = 30f..360f, steps = 10)
                    }
                }
            } else {
                item {
                    SetupCard("تذكيرات المهام", "متابعة أساسية لا يمكن إيقافها من إعدادات التطبيق.") {
                        Text("🔔 كل ساعتين من 9 صباحًا إلى 9 مساءً طالما عندك مهام غير مكتملة.", color = FocusColors.TextPrimary)
                        Spacer(Modifier.height(6.dp))
                        Text("تتوقف أثناء جلسة التركيز، وتظهر المهمة ذات الأولوية أولًا.", color = FocusColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                    }
                }
                item {
                    SetupCard("رسائل التشجيع العامة", "اختيارية ويمكنك التحكم فيها أو إيقافها لاحقًا.") {
                        PreferenceSwitchRow("رسائل عامة", "تشجيع صباحي ومسائي باسمك.", general) { general = it }
                        if (general) {
                            PreferenceSwitchRow("رسالة الصباح", "رسالة بداية اليوم.", morning) { morning = it }
                            if (morning) TimeChoice(context, "موعد رسالة الصباح", morningHour, 6, 11) { morningHour = it }
                            PreferenceSwitchRow("رسالة المساء", "رسالة مراجعة خفيفة في نهاية اليوم.", evening) { evening = it }
                            if (evening) TimeChoice(context, "موعد رسالة المساء", eveningHour, 17, 23) { eveningHour = it }
                        }
                    }
                }
                item {
                    Text("بعد الحفظ سنطلب إذن الإشعارات من أندرويد. تسليم الإشعارات يظل خاضعًا لإذن النظام.", color = FocusColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (page == 1) TextButton(onClick = { page = 0 }, modifier = Modifier.weight(1f)) { Text("رجوع") }
            Button(
                onClick = {
                    if (page == 0) page = 1
                    else {
                        val updated = current.copy(
                            studentName = name.trim(),
                            dailyGoalMinutes = goal.toInt(),
                            avatarPath = avatar,
                            companionStyle = style,
                            generalNotificationsEnabled = general,
                            morningMessageEnabled = morning,
                            eveningMessageEnabled = evening,
                            morningMessageHour = morningHour,
                            eveningMessageHour = eveningHour,
                            onboardingCompleted = true
                        )
                        onComplete(updated)
                        onRequestNotifications()
                    }
                },
                enabled = page == 1 || name.trim().length >= 2,
                modifier = Modifier.weight(2f),
                shape = RoundedCornerShape(16.dp)
            ) { Text(if (page == 0) "متابعة" else "ابدأ رحلتك") }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun CompanionStylePicker(selected: Int, onSelect: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("اختار شخصية رفيقك", color = FocusColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            CompanionStyle.all.forEachIndexed { index, style ->
                FilterChip(
                    selected = selected == index,
                    onClick = { onSelect(index) },
                    label = { Text("${style.mark} ${style.title}") }
                )
            }
        }
    }
}

@Composable
internal fun PreferenceSwitchRow(title: String, description: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = FocusColors.TextPrimary)
            Text(description, color = FocusColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
internal fun TimeChoice(context: Context, label: String, hour: Int, minHour: Int, maxHour: Int, onSelected: (Int) -> Unit) {
    TextButton(onClick = {
        TimePickerDialog(context, { _, chosen, _ -> onSelected(chosen.coerceIn(minHour, maxHour)) }, hour, 0, false).show()
    }) { Text("$label · ${String.format(java.util.Locale.US, "%02d:00", hour)}") }
}

@Composable
private fun SetupCard(title: String, description: String, content: @Composable () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = FocusColors.Surface), shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = FocusColors.TextPrimary, fontWeight = FontWeight.SemiBold)
            Text(description, style = MaterialTheme.typography.bodySmall, color = FocusColors.TextSecondary)
            content()
        }
    }
}
