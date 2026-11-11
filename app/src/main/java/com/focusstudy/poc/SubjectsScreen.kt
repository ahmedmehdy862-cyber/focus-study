package com.focusstudy.poc

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
internal fun SubjectsScreen(onBack: () -> Unit) {
    val viewModel: SubjectListViewModel = viewModel()
    val state by viewModel.state.collectAsState()
    var showAddDialog by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("رجوع") }
            Spacer(Modifier.width(8.dp))
            Text(
                text = "موادي الدراسية",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = FocusColors.TextPrimary
            )
            Button(onClick = { showAddDialog = true }, shape = RoundedCornerShape(14.dp)) {
                Text("مادة +")
            }
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = FocusColors.SurfaceHigh)
        ) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Text("موادك في مكان واحد", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = FocusColors.TextPrimary)
                Spacer(Modifier.height(4.dp))
                Text(
                    "تابع أهداف المذاكرة ومواعيد الامتحانات، ثم اربط مهامك بكل مادة.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = FocusColors.TextSecondary
                )
            }
        }
        Spacer(Modifier.height(16.dp))

        when {
            state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("جارٍ تحميل المواد…", color = FocusColors.TextSecondary)
            }
            state.subjects.isEmpty() -> Card(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = FocusColors.Surface)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 30.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("📚", style = MaterialTheme.typography.headlineSmall)
                    Text("ابدأ بتنظيم موادك", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = FocusColors.TextPrimary, textAlign = TextAlign.Center)
                    Text(
                        "أضف أول مادة لتسجيل هدفك الأسبوعي وموعد امتحانك، وبعدها اربط بها مهام المذاكرة.",
                        color = FocusColors.TextSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                    Button(onClick = { showAddDialog = true }, shape = RoundedCornerShape(14.dp)) { Text("أضف أول مادة") }
                }
            }
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(state.subjects, key = { it.id }) { subject ->
                    SubjectCard(subject)
                }
            }
        }
    }

    if (showAddDialog) {
        AddSubjectDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { name, instructor, goalMinutes, examAt ->
                viewModel.add(name, instructor, goalMinutes, examAt)
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun SubjectCard(subject: StudySubject) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = FocusColors.Surface)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(
                Modifier.size(12.dp).clip(CircleShape).background(Color(subject.color))
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(subject.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = FocusColors.TextPrimary)
                if (subject.instructor.isNotBlank()) {
                    Text(subject.instructor, style = MaterialTheme.typography.bodySmall, color = FocusColors.TextSecondary)
                }
                Text(
                    "هدف المذاكرة: ${subject.goalMinutes} دقيقة",
                    style = MaterialTheme.typography.labelMedium,
                    color = FocusColors.TextSecondary
                )
                subject.nextExamAt?.let { examAt ->
                    Text(
                        "الامتحان القادم: ${formatDueDate(examAt)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = FocusColors.onTint(FocusColors.Amber)
                    )
                }
            }
        }
    }
}

@Composable
private fun AddSubjectDialog(
    onDismiss: () -> Unit,
    onAdd: (String, String, Int, Long?) -> Unit
) {
    var name by rememberSaveable { mutableStateOf("") }
    var instructor by rememberSaveable { mutableStateOf("") }
    var goalMinutes by rememberSaveable { mutableStateOf("180") }
    var examDate by rememberSaveable { mutableStateOf("") }
    val parsedExamAt = parseDueDateMillis(examDate)
    val examDateValid = examDate.isBlank() || parsedExamAt != null

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        containerColor = FocusColors.SurfaceHigh,
        title = { Text("إضافة مادة") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("اسم المادة") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = instructor,
                    onValueChange = { instructor = it },
                    label = { Text("اسم المدرّس (اختياري)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = goalMinutes,
                    onValueChange = { value -> if (value.all(Char::isDigit)) goalMinutes = value },
                    label = { Text("هدف المذاكرة بالدقائق أسبوعيًا") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = examDate,
                    onValueChange = { examDate = it },
                    label = { Text("موعد الامتحان (yyyy-mm-dd، اختياري)") },
                    singleLine = true,
                    isError = !examDateValid,
                    supportingText = if (!examDateValid) {{ Text("اكتب التاريخ بصيغة سنة-شهر-يوم") }} else null,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank()) {
                        onAdd(name.trim(), instructor.trim(), goalMinutes.toIntOrNull() ?: 180, parsedExamAt)
                    }
                },
                enabled = name.isNotBlank() && examDateValid
            ) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}
