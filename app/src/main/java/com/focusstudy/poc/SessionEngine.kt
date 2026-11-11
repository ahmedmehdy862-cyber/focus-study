package com.focusstudy.poc

import android.content.Context
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Single source of truth for the running focus session.
 *
 * A session is always attached to a task and to an explicit list of apps. Once started it is
 * locked: there is no user-facing cancel path, and it only ends when the chosen duration is over
 * (or when the process is restored after that time). While it runs:
 *  - the selected apps cannot be opened (AppBlockAccessibilityService hides them and returns home);
 *  - their network traffic is dropped by BlockVpnService;
 *  - in the optional Device Owner mode they are also suspended by the system, which disables their
 *    icons and removes them from Recents.
 */
object SessionEngine {

    data class Active(
        val sessionId: Long,
        val taskId: Long,
        val taskTitle: String,
        val startsAt: Long,
        val endsAt: Long,
        val blockedPackages: Set<String>
    ) {
        val totalSeconds: Long get() = ((endsAt - startsAt) / 1000L).coerceAtLeast(1L)
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val restoreLock = Mutex()

    private val _active = MutableStateFlow<Active?>(null)
    val active: StateFlow<Active?> = _active.asStateFlow()

    private val _remainingSeconds = MutableStateFlow(0L)
    val remainingSeconds: StateFlow<Long> = _remainingSeconds.asStateFlow()

    /** True when the session is running but the VPN tunnel was revoked, so network is not filtered. */
    private val _vpnLost = MutableStateFlow(false)
    val vpnLost: StateFlow<Boolean> = _vpnLost.asStateFlow()

    /** Bumped whenever tasks change, so screens know to reload them. */
    private val _tasksVersion = MutableStateFlow(0)
    val tasksVersion: StateFlow<Int> = _tasksVersion.asStateFlow()

    private var ticker: Job? = null
    private var appContext: Context? = null
    private var database: StudyDb? = null
    private var initialized = false

    fun init(context: Context) {
        val app = context.applicationContext
        if (initialized) return
        initialized = true
        appContext = app
        database = StudyDb.get(app)
        scope.launch { restore() }
    }

    /** Re-reads persisted state; used by the end-of-session alarm. */
    fun refresh(context: Context) {
        init(context)
        scope.launch { restore() }
    }

    private suspend fun restore() = restoreLock.withLock {
        val db = database ?: return
        val context = appContext ?: return
        val row = withContext(Dispatchers.IO) { db.activeSession() }

        if (row == null) {
            // Self-heal: apps suspended by a session that already ended must be released.
            withContext(Dispatchers.IO) { DeviceOwnerController.unsuspendAll(context) }
            return
        }

        val now = System.currentTimeMillis()
        if (row.endsAt <= now) {
            withContext(Dispatchers.IO) {
                db.finishSession(row.id)
                val minutes = ((row.endsAt - row.startedAt) / 60_000L).toInt()
                if (minutes > 0) db.addFocusMinutes(row.taskId, minutes)
                DeviceOwnerController.unsuspendAll(context)
            }
            SessionAlarm.cancel(context)
            _tasksVersion.value++
            return
        }

        _active.value = Active(
            sessionId = row.id,
            taskId = row.taskId,
            taskTitle = row.taskTitle,
            startsAt = row.startedAt,
            endsAt = row.endsAt,
            blockedPackages = row.blockedPackages
        )
        BlockGate.setBlocked(row.blockedPackages)
        if (row.blockedPackages.isNotEmpty()) {
            startVpn(context, row.blockedPackages)
            withContext(Dispatchers.IO) { DeviceOwnerController.suspend(context, row.blockedPackages) }
        }
        SessionAlarm.schedule(context, row.endsAt)
        startTicker()
    }

    /**
     * Starts a locked session. [minutes] and [packages] come from the setup screen, so the learner
     * always decides both before the clock starts.
     */
    fun start(context: Context, taskId: Long, taskTitle: String, minutes: Int, packages: Set<String>) {
        if (_active.value != null) return
        val now = System.currentTimeMillis()
        val endsAt = now + minutes * 60_000L
        val app = context.applicationContext

        scope.launch {
            val sessionId = withContext(Dispatchers.IO) {
                database?.startSession(taskId, now, endsAt, packages) ?: -1L
            }
            _active.value = Active(sessionId, taskId, taskTitle, now, endsAt, packages)
            BlockGate.setBlocked(packages)
            _vpnLost.value = false
            if (packages.isNotEmpty()) {
                startVpn(app, packages)
                withContext(Dispatchers.IO) { DeviceOwnerController.suspend(app, packages) }
            }
            SessionAlarm.schedule(app, endsAt)
            startTicker()
        }
    }

    /** Re-creates the tunnel after the user re-grants VPN consent; never unlocks the session. */
    fun restartVpn(context: Context) {
        val session = _active.value ?: return
        if (session.blockedPackages.isEmpty()) return
        startVpn(context.applicationContext, session.blockedPackages)
        _vpnLost.value = false
    }

    fun onVpnLost() {
        if (_active.value != null) _vpnLost.value = true
    }

    private fun startVpn(context: Context, packages: Set<String>) {
        runCatching {
            ContextCompat.startForegroundService(
                context,
                BlockVpnService.startIntent(context, packages.toList())
            )
        }
    }

    /**
     * The only way a session can end: its own end time. Kept private on purpose, so no UI action
     * can release the blocked apps early.
     */
    private fun finish(context: Context) {
        val session = _active.value ?: return
        ticker?.cancel()
        ticker = null
        _active.value = null
        _remainingSeconds.value = 0L
        _vpnLost.value = false
        BlockGate.clear()
        AppBlockAccessibilityService.hideOverlayIfShown()
        SessionAlarm.cancel(context)
        runCatching { context.startService(BlockVpnService.stopIntent(context)) }

        scope.launch {
            val db = database
            withContext(Dispatchers.IO) { DeviceOwnerController.unsuspendAll(context) }
            if (db != null) {
                withContext(Dispatchers.IO) {
                    val minutes = ((session.endsAt - session.startsAt) / 60_000L).toInt()
                    db.finishSession(session.sessionId)
                    if (minutes > 0) db.addFocusMinutes(session.taskId, minutes)
                }
            }
            _tasksVersion.value++
        }
    }

    /** Lets other writers (view models, repositories) tell the UI that tasks changed. */
    fun notifyTasksChanged() {
        _tasksVersion.value++
    }

    fun updateTaskBlocked(taskId: Long, packages: Set<String>) {
        scope.launch {
            withContext(Dispatchers.IO) { database?.updateTaskBlocked(taskId, packages) }
            _tasksVersion.value++
        }
    }

    fun addTask(title: String, plannedMinutes: Int, blockedPackages: Set<String>) {
        scope.launch {
            withContext(Dispatchers.IO) {
                database?.insertTask(title, plannedMinutes, blockedPackages)
            }
            _tasksVersion.value++
        }
    }

    fun deleteTask(taskId: Long) {
        scope.launch {
            withContext(Dispatchers.IO) { database?.deleteTask(taskId) }
            _tasksVersion.value++
        }
    }

    private fun startTicker() {
        ticker?.cancel()
        ticker = scope.launch {
            while (true) {
                val session = _active.value ?: break
                val left = (session.endsAt - System.currentTimeMillis()) / 1000L
                if (left <= 0L) {
                    _remainingSeconds.value = 0L
                    appContext?.let { finish(it) }
                    break
                }
                _remainingSeconds.value = left
                delay(500)
            }
        }
    }
}
