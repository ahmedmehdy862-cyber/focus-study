package com.focusstudy.poc

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SubjectRepository private constructor(private val db: StudyDb) {
    suspend fun subjects(): List<StudySubject> = withContext(Dispatchers.IO) { db.subjects() }

    suspend fun add(
        name: String,
        instructor: String,
        goalMinutes: Int,
        examAt: Long?
    ): Long = withContext(Dispatchers.IO) {
        db.insertSubject(name, instructor, goalMinutes, examAt)
    }

    companion object {
        @Volatile
        private var instance: SubjectRepository? = null

        fun get(context: Context): SubjectRepository =
            instance ?: synchronized(this) {
                instance ?: SubjectRepository(StudyDb.get(context)).also { instance = it }
            }
    }
}
