package com.focusstudy.poc

/** Task classification used by filters, the dashboard and analytics. */
enum class TaskCategory(val key: String, val label: String) {
    STUDY("study", "دراسة"),
    UNIVERSITY("university", "جامعة"),
    PERSONAL("personal", "شخصي"),
    WORK("work", "عمل"),
    HABITS("habits", "عادات"),
    OTHER("other", "أخرى");

    companion object {
        fun from(key: String?): TaskCategory =
            entries.firstOrNull { it.key == key } ?: STUDY
    }
}

enum class TaskPriority(val level: Int, val label: String) {
    LOW(0, "منخفضة"),
    MEDIUM(1, "متوسطة"),
    HIGH(2, "عالية"),
    URGENT(3, "عاجلة");

    companion object {
        fun from(level: Int): TaskPriority =
            entries.firstOrNull { it.level == level } ?: MEDIUM
    }
}

enum class TaskStatus(val key: String, val label: String) {
    TODO("todo", "لم تبدأ"),
    DOING("doing", "قيد التنفيذ"),
    DONE("done", "مكتملة"),
    POSTPONED("postponed", "مؤجلة"),
    CANCELLED("cancelled", "ملغاة");

    companion object {
        fun from(key: String?): TaskStatus =
            entries.firstOrNull { it.key == key } ?: TODO
    }
}

enum class Recurrence(val key: String, val label: String) {
    NONE("none", "بدون تكرار"),
    DAILY("daily", "يوميًا"),
    WEEKLY("weekly", "أسبوعيًا"),
    MONTHLY("monthly", "شهريًا");

    companion object {
        fun from(key: String?): Recurrence =
            entries.firstOrNull { it.key == key } ?: NONE
    }
}

/**
 * A task as stored in the local database.
 *
 * [isDone] is kept for backward compatibility with the v1 schema and stays in sync with
 * [status] / accumulated focus minutes; [progress] is derived from minutes.
 */
data class StudyTask(
    val id: Long,
    val title: String,
    val plannedMinutes: Int,
    val doneMinutes: Int,
    val isDone: Boolean,
    val blockedPackages: Set<String>,
    val createdAt: Long,
    val description: String = "",
    val category: TaskCategory = TaskCategory.STUDY,
    val priority: TaskPriority = TaskPriority.MEDIUM,
    val status: TaskStatus = TaskStatus.TODO,
    val startAt: Long? = null,
    val dueAt: Long? = null,
    val subjectId: Long? = null,
    val notes: String = "",
    val recurrence: Recurrence = Recurrence.NONE,
    val completedAt: Long? = null,
    val sortOrder: Int = 0,
    val originalDueAt: Long? = null
) {
    val progress: Float
        get() = if (plannedMinutes <= 0) 0f else (doneMinutes.toFloat() / plannedMinutes).coerceIn(0f, 1f)

    val remainingMinutes: Int
        get() = (plannedMinutes - doneMinutes).coerceAtLeast(0)
}

data class Subtask(
    val id: Long,
    val taskId: Long,
    val title: String,
    val isDone: Boolean,
    val sortOrder: Int
)

data class StudySubject(
    val id: Long,
    val semesterId: Long?,
    val name: String,
    val color: Int,
    val icon: String,
    val instructor: String,
    val goalMinutes: Int,
    val createdAt: Long,
    val nextExamAt: Long?
)

data class StudyEvent(
    val id: Long,
    val title: String,
    val kind: String,
    val startsAt: Long,
    val endsAt: Long,
    val notes: String = ""
)

data class FocusSessionMetric(val startedAt: Long, val endsAt: Long, val taskId: Long)
