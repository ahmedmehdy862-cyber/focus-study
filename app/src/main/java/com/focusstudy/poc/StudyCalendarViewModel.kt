package com.focusstudy.poc

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class CalendarUiState(val events: List<StudyEvent> = emptyList(), val loading: Boolean = false)

class StudyCalendarViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = StudyEventRepository.get(application)
    private val _state = MutableStateFlow(CalendarUiState())
    val state = _state.asStateFlow()

    fun load(start: Long, end: Long) {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true)
            _state.value = CalendarUiState(events = repository.eventsBetween(start, end))
        }
    }

    suspend fun hasConflict(start: Long, end: Long) = repository.hasConflict(start, end)
    suspend fun nextAvailableStart(start: Long, duration: Long, dayEnd: Long) = repository.nextAvailableStart(start, duration, dayEnd)
    suspend fun add(title: String, start: Long, end: Long, kind: String) {
        repository.add(title, start, end, kind)
    }
    fun delete(id: Long, start: Long, end: Long) {
        viewModelScope.launch { repository.delete(id); load(start, end) }
    }
}
