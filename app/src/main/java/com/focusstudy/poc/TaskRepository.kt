package com.focusstudy.poc

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Single entry point for task persistence. Screens and view models go through it instead of
 * touching [StudyDb] directly, so the storage engine can change later without touching the UI.
 */
class TaskRepository private constructor(private val db: StudyDb) {

    suspend fun tasks(): List<StudyTask> = io { db.tasks() }

    suspend fun task(taskId: Long): StudyTask? = io { db.task(taskId) }

    suspend fun totalFocusedMinutes(): Int = io { db.totalFocusedMinutes() }

    suspend fun add(
        title: String,
        plannedMinutes: Int,
        blockedPackages: Set<String>,
        description: String = "",
        category: TaskCategory = TaskCategory.STUDY,
        priority: TaskPriority = TaskPriority.MEDIUM,
        dueAt: Long? = null,
        subjectId: Long? = null,
        notes: String = "",
        recurrence: Recurrence = Recurrence.NONE
    ): Long = io {
        db.insertTask(
            title = title,
            plannedMinutes = plannedMinutes,
            blockedPackages = blockedPackages,
            description = description,
            category = category,
            priority = priority,
            dueAt = dueAt,
            subjectId = subjectId,
            notes = notes,
            recurrence = recurrence
        )
    }

    suspend fun update(task: StudyTask) = io { db.updateTask(task) }

    suspend fun delete(taskId: Long) = io { db.deleteTask(taskId) }

    suspend fun duplicate(taskId: Long): Long = io { db.duplicateTask(taskId) }

    suspend fun reschedule(taskId: Long, dueAt: Long?) = io { db.rescheduleTask(taskId, dueAt) }

    suspend fun setStatus(taskId: Long, status: TaskStatus) = io { db.setTaskStatus(taskId, status) }

    suspend fun updateBlocked(taskId: Long, packages: Set<String>) =
        io { db.updateTaskBlocked(taskId, packages) }

    suspend fun subtasks(taskId: Long): List<Subtask> = io { db.subtasks(taskId) }

    suspend fun addSubtask(taskId: Long, title: String): Long = io { db.addSubtask(taskId, title) }

    suspend fun setSubtaskDone(subtaskId: Long, done: Boolean) =
        io { db.setSubtaskDone(subtaskId, done) }

    suspend fun deleteSubtask(subtaskId: Long) = io { db.deleteSubtask(subtaskId) }

    private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) { block() }

    companion object {
        @Volatile
        private var instance: TaskRepository? = null

        fun get(context: Context): TaskRepository =
            instance ?: synchronized(this) {
                instance ?: TaskRepository(StudyDb.get(context)).also { instance = it }
            }
    }
}
