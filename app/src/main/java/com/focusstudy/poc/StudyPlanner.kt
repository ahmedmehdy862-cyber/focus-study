package com.focusstudy.poc

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class PlannedStudyBlock(val date: LocalDate, val task: StudyTask, val minutes: Int)

/** Deterministic planner based only on saved tasks, priorities, estimates and deadlines. */
object StudyPlanner {
    fun createWeek(tasks: List<StudyTask>, start: LocalDate, dailyGoalMinutes: Int): List<PlannedStudyBlock> {
        val zone = ZoneId.systemDefault()
        val active = tasks.filter { !it.isDone && it.status != TaskStatus.DONE && it.status != TaskStatus.CANCELLED }
        val remaining = active.associate { it.id to it.remainingMinutes.coerceAtLeast(1) }.toMutableMap()
        val ordered = active.sortedWith(
            compareBy<StudyTask> { task ->
                task.dueAt?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() } ?: LocalDate.MAX
            }.thenByDescending { it.priority.level }
                .thenByDescending { it.subjectId != null }
                .thenBy { it.createdAt }
        )
        val result = mutableListOf<PlannedStudyBlock>()
        for (offset in 0L..6L) {
            val date = start.plusDays(offset)
            var capacity = dailyGoalMinutes.coerceIn(30, 720)
            for (task in ordered) {
                if (capacity <= 0) break
                val left = remaining[task.id] ?: 0
                if (left <= 0) continue
                val minutes = minOf(left, capacity, 50)
                if (minutes > 0) {
                    result += PlannedStudyBlock(date, task, minutes)
                    remaining[task.id] = left - minutes
                    capacity -= minutes
                }
            }
        }
        return result
    }
}
