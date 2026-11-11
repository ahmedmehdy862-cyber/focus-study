package com.focusstudy.poc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class StudyPlannerTest {
    private fun task(
        id: Long,
        minutes: Int,
        priority: TaskPriority = TaskPriority.MEDIUM,
        dueAt: Long? = null,
        status: TaskStatus = TaskStatus.TODO
    ) = StudyTask(
        id = id, title = "Task $id", plannedMinutes = minutes, doneMinutes = 0,
        isDone = status == TaskStatus.DONE, blockedPackages = emptySet(), createdAt = id,
        priority = priority, status = status, dueAt = dueAt
    )

    @Test fun planningNeverExceedsDailyGoal() {
        val week = StudyPlanner.createWeek(listOf(task(1, 400), task(2, 300)), LocalDate.of(2026, 11, 10), 120)
        assertTrue(week.groupBy { it.date }.values.all { day -> day.sumOf { it.minutes } <= 120 })
    }

    @Test fun planningSkipsFinishedAndCancelledTasks() {
        val week = StudyPlanner.createWeek(
            listOf(task(1, 60, status = TaskStatus.DONE), task(2, 30, status = TaskStatus.CANCELLED)),
            LocalDate.of(2026, 11, 10), 120
        )
        assertTrue(week.isEmpty())
    }

    @Test fun eachStudyBlockIsAtMostFiftyMinutes() {
        val week = StudyPlanner.createWeek(listOf(task(1, 180)), LocalDate.of(2026, 11, 10), 120)
        assertTrue(week.all { it.minutes <= 50 })
        assertEquals(180, week.sumOf { it.minutes })
    }
}
