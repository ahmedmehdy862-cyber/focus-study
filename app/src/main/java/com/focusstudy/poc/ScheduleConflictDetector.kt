package com.focusstudy.poc

/** Uses half-open ranges [start, end), so back-to-back events are allowed. */
object ScheduleConflictDetector {
    fun overlaps(startAt: Long, endAt: Long, events: List<StudyEvent>, ignoreId: Long? = null): Boolean {
        if (endAt <= startAt) return true
        return events.any { it.id != ignoreId && startAt < it.endsAt && endAt > it.startsAt }
    }

    fun nextAvailableStart(startAt: Long, duration: Long, dayEnd: Long, events: List<StudyEvent>): Long? {
        if (duration <= 0) return null
        var candidate = startAt
        for (event in events.asSequence().filter { it.endsAt > startAt && it.startsAt < dayEnd }.sortedBy { it.startsAt }) {
            if (event.startsAt - candidate >= duration) return candidate
            candidate = maxOf(candidate, event.endsAt)
        }
        return candidate.takeIf { dayEnd - it >= duration }
    }
}
