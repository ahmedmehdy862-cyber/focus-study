package com.focusstudy.poc

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SubjectListViewModel(application: Application) : AndroidViewModel(application) {
    data class UiState(
        val subjects: List<StudySubject> = emptyList(),
        val loading: Boolean = true
    )

    private val repository = SubjectRepository.get(application)
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true)
            _state.value = UiState(subjects = repository.subjects(), loading = false)
        }
    }

    fun add(name: String, instructor: String, goalMinutes: Int, examAt: Long?) {
        viewModelScope.launch {
            repository.add(name, instructor, goalMinutes, examAt)
            refresh()
        }
    }
}
