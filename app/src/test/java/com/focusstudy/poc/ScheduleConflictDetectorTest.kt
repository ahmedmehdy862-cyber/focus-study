package com.focusstudy.poc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleConflictDetectorTest {
    private val event = StudyEvent(1, "Lecture", "lecture", 100, 200)

    @Test fun adjacentEventsDoNotConflict() {
        assertFalse(ScheduleConflictDetector.overlaps(200, 260, listOf(event)))
    }

    @Test fun intersectingEventsConflict() {
        assertTrue(ScheduleConflictDetector.overlaps(199, 260, listOf(event)))
    }

    @Test fun findsNextOpenSlotAfterConflict() {
        assertEquals(200L, ScheduleConflictDetector.nextAvailableStart(150, 40, 400, listOf(event)))
    }
}
