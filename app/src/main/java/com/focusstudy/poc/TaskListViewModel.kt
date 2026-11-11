package com.focusstudy.poc

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 
 * Holds the task list for the tasks screen. Survives configuration changes, so rotation no longer 
 * drops the list or reloads it from scratch.
 */
class TaskListViewModel(application: Application) : AndroidViewModel(application) {

    data class UiState(
        val tasks: List<StudyTask> = emptyList(),
        val totalMinutes: Int = 0,
        val loading: Boolean = true
    )

    private val repository = TaskRepository.get(application)

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var loadJob: Job? = null

    fun refresh() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val tasks = repository.tasks()
            val total = repository.totalFocusedMinutes()
            _state.value = UiState(tasks = tasks, totalMinutes = total, loading = false)
        }
    }

    fun delete(taskId: Long) {
        viewModelScope.launch {
            repository.delete(taskId)
            SessionEngine.notifyTasksChanged()
            refresh()
        }
    }

    fun add(
        title: String,
        plannedMinutes: Int,
        category: TaskCategory,
        priority: TaskPriority,
        dueAt: Long?,
        subjectId: Long?,
        notes: String,
        recurrence: Recurrence
    ) {
        viewModelScope.launch {
            repository.add(
                title = title,
                plannedMinutes = plannedMinutes,
                blockedPackages = emptySet(),
                description = notes,
                category = category,
                priority = priority,
                dueAt = dueAt,
                subjectId = subjectId,
                notes = notes,
                recurrence = recurrence
            )
            SessionEngine.notifyTasksChanged()
            refresh()
        }
    }

    fun duplicate(taskId: Long) {
        viewModelScope.launch {
            repository.duplicate(taskId)
            SessionEngine.notifyTasksChanged()
            refresh()
        }
    }

    fun reschedule(taskId: Long, dueAt: Long?) {
        viewModelScope.launch {
            repository.reschedule(taskId, dueAt)
            SessionEngine.notifyTasksChanged()
            refresh()
        }
    }

    suspend fun subtasks(taskId: Long): List<Subtask> = repository.subtasks(taskId)

    suspend fun createSubtask(taskId: Long, title: String) {
        if (title.isNotBlank()) repository.addSubtask(taskId, title.trim())
    }

    suspend fun updateSubtask(subtaskId: Long, done: Boolean) = repository.setSubtaskDone(subtaskId, done)

    suspend fun removeSubtask(subtaskId: Long) = repository.deleteSubtask(subtaskId)

    fun addSubtask(taskId: Long, title: String) {
        if (title.isBlank()) return
        viewModelScope.launch { repository.addSubtask(taskId, title.trim()) }
    }

    fun setSubtaskDone(subtaskId: Long, done: Boolean) {
        viewModelScope.launch { repository.setSubtaskDone(subtaskId, done) }
    }

    fun deleteSubtask(subtaskId: Long) {
        viewModelScope.launch { repository.deleteSubtask(subtaskId) }
    }

    fun update(task: StudyTask) {
        viewModelScope.launch {
            repository.update(task)
            SessionEngine.notifyTasksChanged()
            refresh()
        }
    }

    fun setStatus(taskId: Long, status: TaskStatus) {
        viewModelScope.launch {
            repository.setStatus(taskId, status)
            SessionEngine.notifyTasksChanged()
            refresh()
        }
    }
}