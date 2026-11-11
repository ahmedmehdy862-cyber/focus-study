package com.focusstudy.poc

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class WeeklyAnalytics(
    val dailyMinutes: Map<LocalDate, Int> = emptyMap(),
    val completedTasks: Int = 0,
    val overdueTasks: Int = 0,
    val totalMinutes: Int = 0,
    val sessionCount: Int = 0
) {
    val activeDays: Int get() = dailyMinutes.values.count { it > 0 }
}

class StudyAnalyticsRepository private constructor(private val db: StudyDb) {
    suspend fun week(start: LocalDate, endExclusive: LocalDate): WeeklyAnalytics = withContext(Dispatchers.IO) {
        val zone = ZoneId.systemDefault()
        val startAt = start.atStartOfDay(zone).toInstant().toEpochMilli()
        val endAt = endExclusive.atStartOfDay(zone).toInstant().toEpochMilli()
        val sessions = db.finishedSessionsBetween(startAt, endAt)
        val daily = sessions.groupBy { Instant.ofEpochMilli(it.startedAt).atZone(zone).toLocalDate() }
            .mapValues { (_, rows) -> rows.sumOf { ((it.endsAt - it.startedAt) / 60_000L).toInt().coerceAtLeast(0) } }
        WeeklyAnalytics(
            dailyMinutes = daily,
            completedTasks = db.completedTasksBetween(startAt, endAt),
            overdueTasks = db.overdueTaskCount(System.currentTimeMillis()),
            totalMinutes = daily.values.sum(),
            sessionCount = sessions.size
        )
    }

    companion object {
        @Volatile private var instance: StudyAnalyticsRepository? = null
        fun get(context: android.content.Context): StudyAnalyticsRepository = instance ?: synchronized(this) {
            instance ?: StudyAnalyticsRepository(StudyDb.get(context)).also { instance = it }
        }
    }
}

class StudyAnalyticsViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = StudyAnalyticsRepository.get(application)
    private val _state = MutableStateFlow(WeeklyAnalytics())
    val state = _state.asStateFlow()
    fun load(start: LocalDate, endExclusive: LocalDate) {
        viewModelScope.launch { _state.value = repo.week(start, endExclusive) }
    }
}
