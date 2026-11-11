package com.focusstudy.poc

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.ZonedDateTime

internal object StudyNotificationScheduler {
    const val ACTION_TASK_NUDGE = "com.focusstudy.poc.TASK_NUDGE"
    const val ACTION_MORNING = "com.focusstudy.poc.MORNING_MESSAGE"
    const val ACTION_EVENING = "com.focusstudy.poc.EVENING_MESSAGE"

    private const val TASK_CHANNEL = "task_reminders_mandatory"
    private const val GENERAL_CHANNEL = "daily_encouragement"
    private const val TASK_NOTIFICATION_ID = 5101
    private const val MORNING_NOTIFICATION_ID = 5102
    private const val EVENING_NOTIFICATION_ID = 5103
    private const val TASK_REQUEST = 5111
    private const val MORNING_REQUEST = 5112
    private const val EVENING_REQUEST = 5113
    private val taskHours = listOf(9, 11, 13, 15, 17, 19, 21)

    fun update(context: Context, preferences: FocusPreferences) {
        cancelLegacyReminder(context)
        scheduleNextTaskNudge(context)
        scheduleDaily(
            context,
            ACTION_MORNING,
            MORNING_REQUEST,
            preferences.morningMessageHour,
            preferences.generalNotificationsEnabled && preferences.morningMessageEnabled
        )
        scheduleDaily(
            context,
            ACTION_EVENING,
            EVENING_REQUEST,
            preferences.eveningMessageHour,
            preferences.generalNotificationsEnabled && preferences.eveningMessageEnabled
        )
    }

    private fun cancelLegacyReminder(context: Context) {
        val legacy = PendingIntent.getBroadcast(
            context,
            402,
            Intent(context, DailyReminderReceiver::class.java).setAction("com.focusstudy.poc.DAILY_REMINDER"),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        ) ?: return
        (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(legacy)
        legacy.cancel()
    }

    private fun scheduleNextTaskNudge(context: Context) {
        val now = ZonedDateTime.now()
        val next = taskHours.map { hour -> now.withHour(hour).withMinute(0).withSecond(0).withNano(0) }
            .firstOrNull { it.isAfter(now) }
            ?: now.plusDays(1).withHour(taskHours.first()).withMinute(0).withSecond(0).withNano(0)
        schedule(context, ACTION_TASK_NUDGE, TASK_REQUEST, next.toInstant().toEpochMilli())
    }

    private fun scheduleDaily(context: Context, action: String, requestCode: Int, hour: Int, enabled: Boolean) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = pendingIntent(context, action, requestCode)
        if (!enabled) {
            alarmManager.cancel(pendingIntent)
            return
        }
        val now = ZonedDateTime.now()
        var next = now.withHour(hour.coerceIn(0, 23)).withMinute(0).withSecond(0).withNano(0)
        if (!next.isAfter(now)) next = next.plusDays(1)
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.toInstant().toEpochMilli(), pendingIntent)
    }

    private fun schedule(context: Context, action: String, requestCode: Int, atMillis: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            atMillis,
            pendingIntent(context, action, requestCode)
        )
    }

    private fun pendingIntent(context: Context, action: String, requestCode: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(context, DailyReminderReceiver::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    suspend fun dispatch(context: Context, action: String?) {
        val appContext = context.applicationContext
        when (action) {
            ACTION_TASK_NUDGE -> showTaskNudge(appContext)
            ACTION_MORNING -> showEncouragement(appContext, morning = true)
            ACTION_EVENING -> showEncouragement(appContext, morning = false)
        }
        update(appContext, FocusPreferences.load(appContext))
    }

    private fun showTaskNudge(context: Context) {
        if (!notificationsAllowed(context)) return
        val database = StudyDb.get(context)
        val activeSession = database.activeSession()?.takeIf { it.endsAt > System.currentTimeMillis() }
        if (activeSession != null) return

        val pending = database.tasks().filter {
            !it.isDone && it.status != TaskStatus.DONE && it.status != TaskStatus.CANCELLED
        }
        if (pending.isEmpty()) {
            notificationManager(context).cancel(TASK_NOTIFICATION_ID)
            return
        }
        val nextTask = pending.sortedWith(
            compareBy<StudyTask> {
                when {
                    it.dueAt?.let { due ->
                        java.time.Instant.ofEpochMilli(due).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                            .isBefore(java.time.LocalDate.now())
                    } == true -> 0
                    it.priority == TaskPriority.URGENT -> 1
                    else -> 2
                }
            }.thenByDescending { it.priority.level }.thenBy { it.dueAt ?: Long.MAX_VALUE }
        ).first()
        val name = FocusPreferences.load(context).studentName.trim()
        val greeting = if (name.isBlank()) "ما زالت عندك مهام مفتوحة" else "يا $name، ما زالت عندك مهام مفتوحة"
        val message = "$greeting (${pending.size}). خُد خطوة الآن: ${nextTask.title}"
        post(
            context,
            channelId = TASK_CHANNEL,
            channelName = "تذكيرات المهام الأساسية",
            description = "تذكير متكرر بالمهام غير المكتملة، ويتوقف تلقائيًا أثناء جلسة التركيز.",
            notificationId = TASK_NOTIFICATION_ID,
            title = "متابعة مهامك",
            message = message,
            highPriority = true
        )
    }

    private fun showEncouragement(context: Context, morning: Boolean) {
        val prefs = FocusPreferences.load(context)
        val enabled = prefs.generalNotificationsEnabled &&
            if (morning) prefs.morningMessageEnabled else prefs.eveningMessageEnabled
        if (!enabled || !notificationsAllowed(context)) return
        val name = prefs.studentName.trim().ifBlank { "بطل Focus Study" }
        val messages = if (morning) listOf(
            "صباحك فرصة جديدة، ابدأ بأصغر خطوة وهتلاقي يومك بيتحرك.",
            "كل إنجاز كبير بدأ بتركيز بسيط. اختار أول مهمة وابدأ.",
            "خلي هدفك واضح وخطوتك صغيرة. يوم مذاكرة موفق!"
        ) else listOf(
            "اقفل يومك بفخر على اللي أنجزته، وجهّز خطوة بكرة.",
            "حتى لو أنجزت جزءًا صغيرًا، ده تقدم يستحق التقدير. تصبح على خير.",
            "راجع تقدمك بهدوء وخد راحتك. بكرة فرصة جديدة تكمل فيها."
        )
        val message = "$name، ${messages[java.time.LocalDate.now().dayOfYear % messages.size]}"
        post(
            context,
            channelId = GENERAL_CHANNEL,
            channelName = "رسائل التشجيع",
            description = "رسائل صباحية ومسائية اختيارية بالاسم الذي خصصته.",
            notificationId = if (morning) MORNING_NOTIFICATION_ID else EVENING_NOTIFICATION_ID,
            title = if (morning) "صباح الخير ☀️" else "مساء التقدم 🌙",
            message = message,
            highPriority = false,
            avatarPath = prefs.avatarPath
        )
    }

    private fun notificationsAllowed(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun notificationManager(context: Context) =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private fun post(
        context: Context,
        channelId: String,
        channelName: String,
        description: String,
        notificationId: Int,
        title: String,
        message: String,
        highPriority: Boolean,
        avatarPath: String = ""
    ) {
        val manager = notificationManager(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    channelId,
                    channelName,
                    if (highPriority) NotificationManager.IMPORTANCE_HIGH else NotificationManager.IMPORTANCE_DEFAULT
                ).apply { this.description = description }
            )
        }
        val openApp = PendingIntent.getActivity(
            context,
            notificationId,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_stat_focus)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .setPriority(if (highPriority) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
        if (highPriority) builder.setCategory(NotificationCompat.CATEGORY_REMINDER).setDefaults(NotificationCompat.DEFAULT_ALL)
        if (avatarPath.isNotBlank()) {
            BitmapFactory.decodeFile(avatarPath)?.let(builder::setLargeIcon)
        }
        manager.notify(notificationId, builder.build())
    }
}

class DailyReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val result = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                StudyNotificationScheduler.dispatch(context.applicationContext, intent?.action)
            } finally {
                result.finish()
            }
        }
    }
}

class FocusBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            StudyNotificationScheduler.update(context, FocusPreferences.load(context))
            AppUpdateManager.schedule(context)
        }
    }
}
