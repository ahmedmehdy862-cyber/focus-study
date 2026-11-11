package com.focusstudy.poc

import android.content.Context

data class FocusPreferences(
    val studentName: String = "",
    val dailyGoalMinutes: Int = 120,
    val defaultSessionMinutes: Int = 25,
    val accentIndex: Int = 0,
    val textScale: Float = 1f,
    val dailyReminderEnabled: Boolean = true,
    val reminderHour: Int = 20,
    val reminderMinute: Int = 0,
    val remindersPerDay: Int = 1,
    val permanentBlockedPackages: Set<String> = emptySet(),
    val onboardingCompleted: Boolean = false,
    val avatarPath: String = "",
    val companionStyle: Int = 0,
    val generalNotificationsEnabled: Boolean = true,
    val morningMessageEnabled: Boolean = true,
    val eveningMessageEnabled: Boolean = true,
    val morningMessageHour: Int = 8,
    val eveningMessageHour: Int = 20
) {
    companion object {
        private const val FILE = "focus_study_preferences"

        fun load(context: Context): FocusPreferences {
            val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            return FocusPreferences(
                studentName = prefs.getString("student_name", "").orEmpty(),
                dailyGoalMinutes = prefs.getInt("daily_goal_minutes", 120).coerceIn(30, 720),
                defaultSessionMinutes = prefs.getInt("session_minutes", 25).coerceIn(5, 120),
                accentIndex = prefs.getInt("accent_index", 0).coerceIn(0, FocusColors.accents.lastIndex),
                textScale = prefs.getFloat("text_scale", 1f).coerceIn(0.9f, 1.2f),
                dailyReminderEnabled = prefs.getBoolean("daily_reminder_enabled", true),
                reminderHour = prefs.getInt("reminder_hour", 20).coerceIn(0, 23),
                reminderMinute = prefs.getInt("reminder_minute", 0).coerceIn(0, 59),
                remindersPerDay = prefs.getInt("reminders_per_day", 1).coerceIn(1, 3),
                permanentBlockedPackages = prefs.getStringSet("permanent_blocked_packages", emptySet()).orEmpty().toSet(),
                onboardingCompleted = prefs.getBoolean(
                    "onboarding_completed",
                    prefs.getString("student_name", "").orEmpty().isNotBlank()
                ),
                avatarPath = prefs.getString("avatar_path", "").orEmpty(),
                companionStyle = prefs.getInt("companion_style", 0).coerceIn(0, 3),
                generalNotificationsEnabled = prefs.getBoolean("general_notifications_enabled", prefs.getBoolean("daily_reminder_enabled", true)),
                morningMessageEnabled = prefs.getBoolean("morning_message_enabled", true),
                eveningMessageEnabled = prefs.getBoolean("evening_message_enabled", true),
                morningMessageHour = prefs.getInt("morning_message_hour", 8).coerceIn(6, 11),
                eveningMessageHour = prefs.getInt("evening_message_hour", 20).coerceIn(17, 23)
            )
        }

        fun save(context: Context, value: FocusPreferences) {
            context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
                .putString("student_name", value.studentName.trim())
                .putInt("daily_goal_minutes", value.dailyGoalMinutes.coerceIn(30, 720))
                .putInt("session_minutes", value.defaultSessionMinutes.coerceIn(5, 120))
                .putInt("accent_index", value.accentIndex.coerceIn(0, FocusColors.accents.lastIndex))
                .putFloat("text_scale", value.textScale.coerceIn(0.9f, 1.2f))
                .putBoolean("daily_reminder_enabled", value.dailyReminderEnabled)
                .putInt("reminder_hour", value.reminderHour.coerceIn(0, 23))
                .putInt("reminder_minute", value.reminderMinute.coerceIn(0, 59))
                .putInt("reminders_per_day", value.remindersPerDay.coerceIn(1, 3))
                .putStringSet("permanent_blocked_packages", value.permanentBlockedPackages.toSet())
                .putBoolean("onboarding_completed", value.onboardingCompleted)
                .putString("avatar_path", value.avatarPath)
                .putInt("companion_style", value.companionStyle.coerceIn(0, 3))
                .putBoolean("general_notifications_enabled", value.generalNotificationsEnabled)
                .putBoolean("morning_message_enabled", value.morningMessageEnabled)
                .putBoolean("evening_message_enabled", value.eveningMessageEnabled)
                .putInt("morning_message_hour", value.morningMessageHour.coerceIn(6, 11))
                .putInt("evening_message_hour", value.eveningMessageHour.coerceIn(17, 23))
                .apply()
        }
    }
}
