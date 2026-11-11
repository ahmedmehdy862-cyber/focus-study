package com.focusstudy.poc

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class StudyEventRepository private constructor(private val db: StudyDb) {
    suspend fun eventsBetween(start: Long, end: Long) = withContext(Dispatchers.IO) { db.eventsBetween(start, end) }
    suspend fun hasConflict(start: Long, end: Long) = withContext(Dispatchers.IO) { db.hasEventConflict(start, end) }
    suspend fun nextAvailableStart(start: Long, duration: Long, dayEnd: Long) =
        withContext(Dispatchers.IO) { db.nextAvailableEventStart(start, duration, dayEnd) }
    suspend fun add(title: String, startsAt: Long, endsAt: Long, kind: String) = withContext(Dispatchers.IO) {
        db.insertEvent(title, kind, startsAt, endsAt)
    }
    suspend fun delete(id: Long) = withContext(Dispatchers.IO) { db.deleteEvent(id) }

    companion object {
        @Volatile private var instance: StudyEventRepository? = null
        fun get(context: Context): StudyEventRepository = instance ?: synchronized(this) {
            instance ?: StudyEventRepository(StudyDb.get(context)).also { instance = it }
        }
    }
}
