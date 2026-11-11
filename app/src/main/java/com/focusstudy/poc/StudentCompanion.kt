package com.focusstudy.poc

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class CompanionStyle(val title: String, val mark: String, val motto: String, val tint: Color) {
    companion object {
        val all = listOf(
            CompanionStyle("المستكشف", "🧭", "خطوة ورا خطوة", FocusColors.Blue),
            CompanionStyle("العالِم", "🔭", "فضولك يقودك", FocusColors.Teal),
            CompanionStyle("البطل", "⚡", "أنت قدّها", FocusColors.Rose),
            CompanionStyle("الفنان", "🎨", "اصنع يومك بطريقتك", FocusColors.Amber)
        )
    }
}

internal fun copyAvatarToAppFiles(context: Context, uri: Uri): String? = runCatching {
    val resolver = context.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (bounds.outWidth / sample > 1024 || bounds.outHeight / sample > 1024) sample *= 2
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    val bitmap = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        ?: return@runCatching null
    val scale = minOf(1f, 512f / maxOf(bitmap.width, bitmap.height))
    val prepared = if (scale < 1f) Bitmap.createScaledBitmap(
        bitmap,
        (bitmap.width * scale).toInt().coerceAtLeast(1),
        (bitmap.height * scale).toInt().coerceAtLeast(1),
        true
    ) else bitmap
    val output = File(context.filesDir, "student_companion_avatar.jpg")
    output.outputStream().use { prepared.compress(Bitmap.CompressFormat.JPEG, 88, it) }
    if (prepared !== bitmap) prepared.recycle()
    bitmap.recycle()
    output.absolutePath
}.getOrNull()

@Composable
internal fun AvatarChooser(
    avatarPath: String,
    name: String,
    styleIndex: Int,
    onAvatarChanged: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch {
            val saved = withContext(Dispatchers.IO) { copyAvatarToAppFiles(context, uri) }
            saved?.let(onAvatarChanged)
        }
    }
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        AvatarPortrait(avatarPath, name, styleIndex, 76.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("رفيقك الدراسي", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text("اختار صورة، ونحوّلها لإطار شخصية ${CompanionStyle.all[styleIndex.coerceIn(0, 3)].title} داخل التطبيق.", style = MaterialTheme.typography.bodySmall, color = FocusColors.TextSecondary)
            Button(onClick = { picker.launch("image/*") }) { Text(if (avatarPath.isBlank()) "اختيار صورة" else "تغيير الصورة") }
        }
    }
}

@Composable
internal fun AvatarPortrait(
    avatarPath: String,
    name: String,
    styleIndex: Int,
    size: Dp,
    modifier: Modifier = Modifier
) {
    val bitmap = remember(avatarPath) {
        if (avatarPath.isNotBlank()) runCatching { BitmapFactory.decodeFile(avatarPath)?.asImageBitmap() }.getOrNull() else null
    }
    val style = CompanionStyle.all[styleIndex.coerceIn(0, CompanionStyle.all.lastIndex)]
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Box(
            Modifier.fillMaxSize().padding(3.dp)
                .border(3.dp, Brush.linearGradient(listOf(style.tint, FocusColors.Violet, style.tint)), CircleShape)
                .padding(4.dp).clip(CircleShape).background(FocusColors.SurfaceHigh),
            contentAlignment = Alignment.Center
        ) {
            if (bitmap != null) {
                Image(bitmap, contentDescription = "صورة رفيق المذاكرة", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                Text(name.trim().take(1).ifBlank { "✦" }, color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            }
        }
        Box(
            Modifier.align(Alignment.BottomEnd).size(size * 0.38f).clip(CircleShape)
                .background(FocusColors.Surface).border(2.dp, FocusColors.Surface, CircleShape),
            contentAlignment = Alignment.Center
        ) { Text(style.mark, style = MaterialTheme.typography.bodyMedium) }
    }
}

@Composable
internal fun StudyCompanionCard(preferences: FocusPreferences, tasks: List<StudyTask>) {
    val session by SessionEngine.active.collectAsState()
    val today = LocalDate.now()
    val todayStart = today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val tomorrowStart = today.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val completedToday = tasks.count { it.completedAt?.let { at -> at in todayStart until tomorrowStart } == true }
    val pending = tasks.filter { !it.isDone && it.status != TaskStatus.DONE && it.status != TaskStatus.CANCELLED }
    val urgent = pending.firstOrNull { it.priority == TaskPriority.URGENT }
    val style = CompanionStyle.all[preferences.companionStyle.coerceIn(0, CompanionStyle.all.lastIndex)]
    val message = when {
        session != null -> "جلسة رائعة! أنا جنبك لحد ما تخلص."
        completedToday > 0 -> "أنجزت $completedToday اليوم. كمّل بنفس الهدوء!"
        urgent != null -> "نبدأ بـ ${urgent.title}؟ خطوة واحدة تكفي للبداية."
        pending.isNotEmpty() -> "اختار مهمة واحدة، وأنا هفضل أشجعك."
        else -> "يوم جديد يعني فرصة جديدة. أضف أول خطوة!"
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = FocusColors.Surface),
        shape = RoundedCornerShape(22.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            AvatarPortrait(preferences.avatarPath, preferences.studentName, preferences.companionStyle, 62.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("${style.mark} رفيقك ${style.title}", style = MaterialTheme.typography.labelLarge, color = FocusColors.TextPrimary, fontWeight = FontWeight.Bold)
                Text(message, style = MaterialTheme.typography.bodySmall, color = FocusColors.TextSecondary)
                Text(style.motto, style = MaterialTheme.typography.labelSmall, color = FocusColors.onTint(FocusColors.Blue))
            }
            Text("✦", color = FocusColors.Amber, style = MaterialTheme.typography.titleLarge)
        }
    }
}
