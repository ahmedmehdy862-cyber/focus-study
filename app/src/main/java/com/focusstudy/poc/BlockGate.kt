package com.focusstudy.poc

/**
 * Shared blocking gate. The focus session writes it; the accessibility service reads it to
 * decide whether the app that just came to the foreground is allowed.
 */
object BlockGate {

    @Volatile
    private var blocked: Set<String> = emptySet()

    @Volatile
    private var sessionActive: Boolean = false

    fun setBlocked(packages: Set<String>) {
        blocked = packages
        sessionActive = true
    }

    fun clear() {
        blocked = emptySet()
        sessionActive = false
    }

    fun isBlocked(packageName: String?): Boolean =
        sessionActive && packageName != null && blocked.contains(packageName)

    val blockedCount: Int
        get() = blocked.size
}
