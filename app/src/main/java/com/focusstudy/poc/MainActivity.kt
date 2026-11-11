package com.focusstudy.poc

import android.Manifest

import android.app.Activity

import android.content.ClipData

import android.content.ClipboardManager

import android.content.Context

import android.content.Intent

import android.content.pm.PackageManager

import android.net.Uri

import android.net.VpnService

import android.os.Build

import android.os.Bundle

import android.os.PowerManager

import android.provider.Settings

import android.widget.Toast

import androidx.activity.ComponentActivity

import androidx.activity.compose.setContent

import androidx.activity.result.contract.ActivityResultContracts

import androidx.compose.animation.Crossfade

import androidx.compose.animation.core.LinearEasing

import androidx.compose.animation.core.RepeatMode

import androidx.compose.animation.core.animateFloat

import androidx.compose.animation.core.animateFloatAsState

import androidx.compose.animation.core.infiniteRepeatable

import androidx.compose.animation.core.rememberInfiniteTransition

import androidx.compose.animation.core.tween

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image

import androidx.compose.foundation.background

import androidx.compose.foundation.clickable

import androidx.compose.foundation.layout.Arrangement

import androidx.compose.foundation.layout.Box

import androidx.compose.foundation.layout.Column

import androidx.compose.foundation.layout.Row

import androidx.compose.foundation.layout.Spacer

import androidx.compose.foundation.layout.fillMaxSize

import androidx.compose.foundation.layout.fillMaxWidth

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding

import androidx.compose.foundation.layout.size

import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll

import androidx.compose.foundation.lazy.LazyColumn

import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed

import androidx.compose.foundation.shape.CircleShape

import androidx.compose.foundation.shape.RoundedCornerShape

import androidx.compose.material3.AlertDialog

import androidx.compose.material3.Button

import androidx.compose.material3.ButtonDefaults

import androidx.compose.material3.Card

import androidx.compose.material3.CardDefaults

import androidx.compose.material3.Checkbox

import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem

import androidx.compose.material3.HorizontalDivider

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.FilterChip
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LocalContentColor

import androidx.compose.material3.OutlinedButton

import androidx.compose.material3.OutlinedTextField

import androidx.compose.material3.Slider

import androidx.compose.material3.SliderDefaults

import androidx.compose.material3.Text

import androidx.compose.material3.TextButton
import androidx.compose.material3.ProvideTextStyle

import androidx.compose.runtime.Composable

import androidx.compose.runtime.CompositionLocalProvider

import androidx.compose.runtime.DisposableEffect

import androidx.compose.runtime.LaunchedEffect

import androidx.compose.runtime.collectAsState

import androidx.compose.runtime.getValue

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope

import androidx.compose.runtime.remember

import androidx.compose.runtime.rememberUpdatedState

import androidx.compose.runtime.saveable.listSaver

import androidx.compose.runtime.saveable.rememberSaveable

import androidx.compose.runtime.setValue

import androidx.compose.ui.Alignment

import androidx.compose.ui.Modifier

import androidx.compose.ui.draw.clip

import androidx.compose.ui.geometry.Offset

import androidx.compose.ui.geometry.Size

import androidx.compose.ui.graphics.Brush

import androidx.compose.ui.graphics.Color

import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale

import androidx.compose.ui.graphics.drawscope.Stroke

import androidx.compose.ui.platform.LocalContext

import androidx.compose.ui.platform.LocalLayoutDirection

import androidx.compose.ui.text.font.FontWeight

import androidx.compose.ui.text.style.TextAlign

import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.text.input.KeyboardType

import androidx.compose.ui.unit.dp

import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap

import androidx.core.content.ContextCompat

import androidx.lifecycle.Lifecycle

import androidx.lifecycle.LifecycleEventObserver

import androidx.lifecycle.compose.LocalLifecycleOwner

import androidx.lifecycle.viewmodel.compose.viewModel

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

import java.util.Locale

class MainActivity : ComponentActivity() {

    private data class PendingStart(

        val taskId: Long,

        val title: String,

        val minutes: Int,

        val packages: Set<String>

    )

    private var pendingStart: PendingStart? = null

    private var resumeVpnRequested = false

    private val vpnPermission = registerForActivityResult(

        ActivityResultContracts.StartActivityForResult()

    ) { result ->

        val granted = result.resultCode == Activity.RESULT_OK

        if (resumeVpnRequested) {

            resumeVpnRequested = false

            if (granted) {

                SessionEngine.restartVpn(this)

            } else {

                toast("لم يُمنح إذن VPN، لذلك لن تُحجب الشبكة.")

            }

            return@registerForActivityResult

        }

        val pending = pendingStart

        pendingStart = null

        if (pending == null) return@registerForActivityResult

        if (!granted) {

            toast("لن تبدأ الجلسة بدون إذن VPN.")

            return@registerForActivityResult

        }

        SessionEngine.start(this, pending.taskId, pending.title, pending.minutes, pending.packages)

    }

    private val notificationPermission = registerForActivityResult(

        ActivityResultContracts.RequestPermission()

    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        SessionEngine.init(this)
        AppUpdateManager.schedule(this)
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            AppUpdateManager.check(applicationContext, manual = false)
        }

        setContent {

            val preferences = remember { mutableStateOf(FocusPreferences.load(this@MainActivity)) }
            FocusTheme(
                accent = FocusColors.accents[preferences.value.accentIndex],
                textScale = preferences.value.textScale
            ) {

                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {

                    AppRoot(

                        preferences = preferences.value,
                        onPreferencesChanged = { updated ->
                            FocusPreferences.save(this@MainActivity, updated)
                            preferences.value = updated
                        },

                        onStartSession = ::startFocusSession,

                        onResumeVpn = ::resumeVpnBlocking,

                        onRequestNotifications = ::requestNotificationPermissionIfNeeded,
                        openUpdatesInitially = intent?.getBooleanExtra(AppUpdateManager.EXTRA_OPEN_UPDATES, false) == true

                    )

                }

            }

        }

    }

    private fun requestNotificationPermissionIfNeeded() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&

            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=

            PackageManager.PERMISSION_GRANTED

        ) {

            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)

        }

    }

    /** Only reachable from the setup screen, after duration, apps and permissions are in place. */

    private fun startFocusSession(taskId: Long, title: String, minutes: Int, packages: Set<String>) {

        SessionEngine.updateTaskBlocked(taskId, packages)

        if (packages.isEmpty()) {

            toast("اختر تطبيقًا واحدًا على الأقل قبل بدء الجلسة.")

            return

        }

        val consent = VpnService.prepare(this)

        if (consent != null) {

            pendingStart = PendingStart(taskId, title, minutes, packages)

            vpnPermission.launch(consent)

        } else {

            SessionEngine.start(this, taskId, title, minutes, packages)

        }

    }

    /** Re-creates network filtering during a running session; the session itself stays locked. */

    private fun resumeVpnBlocking() {

        val consent = VpnService.prepare(this)

        if (consent != null) {

            resumeVpnRequested = true

            vpnPermission.launch(consent)

        } else {

            SessionEngine.restartVpn(this)

        }

    }

    private fun toast(message: String) {

        Toast.makeText(this, message, Toast.LENGTH_LONG).show()

    }

}

@Composable

private fun AppRoot(

    preferences: FocusPreferences,
    onPreferencesChanged: (FocusPreferences) -> Unit,

    onStartSession: (Long, String, Int, Set<String>) -> Unit,

    onResumeVpn: () -> Unit,

    onRequestNotifications: () -> Unit,
    openUpdatesInitially: Boolean

) {

    val context = LocalContext.current
    LaunchedEffect(
        preferences.generalNotificationsEnabled,
        preferences.morningMessageEnabled,
        preferences.eveningMessageEnabled,
        preferences.morningMessageHour,
        preferences.eveningMessageHour
    ) {
        StudyNotificationScheduler.update(context, preferences)
    }
    LaunchedEffect(Unit) { PrayerReminderScheduler.schedule(context) }

    val session by SessionEngine.active.collectAsState()

    val remaining by SessionEngine.remainingSeconds.collectAsState()

    val vpnLost by SessionEngine.vpnLost.collectAsState()

    var setupTaskId by remember { mutableStateOf<Long?>(null) }

    var pickerTaskId by remember { mutableStateOf<Long?>(null) }

    var rootTab by rememberSaveable { mutableStateOf(if (openUpdatesInitially) "updates" else "today") }
    var previousRootTab by rememberSaveable { mutableStateOf(if (openUpdatesInitially) "more" else "today") }
    fun openSecondary(tab: String) {
        previousRootTab = rootTab
        rootTab = tab
    }
    fun returnToPreviousTab() { rootTab = previousRootTab }

    val screen = when {

        session != null -> "session"

        setupTaskId != null -> "setup"

        pickerTaskId != null -> "picker"

        !preferences.onboardingCompleted -> "onboarding"

        else -> rootTab

    }

    BrandBackground {

        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        Crossfade(
            targetState = screen,
            animationSpec = tween(300),
            label = "screen",
            modifier = Modifier.weight(1f).fillMaxWidth()
        ) { current ->

            when (current) {

                "onboarding" -> FirstLaunchSetupScreen(
                    current = preferences,
                    onComplete = onPreferencesChanged,
                    onRequestNotifications = onRequestNotifications
                )

                "session" -> session?.let { active ->

                    SessionScreen(

                        session = active,

                        remainingSeconds = remaining,

                        vpnLost = vpnLost,

                        onResumeVpn = onResumeVpn

                    )

                }

                "setup" -> setupTaskId?.let { id ->

                    SessionSetupScreen(

                        taskId = id,
                        defaultMinutes = preferences.defaultSessionMinutes,

                        onBack = { setupTaskId = null },

                        onRequestNotifications = onRequestNotifications,

                        onStart = { taskId, title, minutes, packages ->

                            setupTaskId = null

                            onStartSession(taskId, title, minutes, packages)

                        }

                    )

                }

                "picker" -> pickerTaskId?.let { id ->

                    BlockedAppsScreen(taskId = id, onDone = { pickerTaskId = null })

                }

                "subjects" -> SubjectsScreen(onBack = { returnToPreviousTab() })
                "calendar" -> StudyCalendarScreen(
                    onBack = { returnToPreviousTab() },
                    onStartTask = { taskId -> setupTaskId = taskId }
                )
                "more" -> MoreSectionsScreen(
                    onPlanner = { openSecondary("planner") },
                    onAnalytics = { openSecondary("analytics") },
                    onSettings = { openSecondary("settings") },
                    onSupport = { openSecondary("support") },
                    onUpdates = { openSecondary("updates") },
                    onPrayerTimes = { openSecondary("prayer_times") }
                )
                "prayer_times" -> PrayerTimesScreen(onBack = { returnToPreviousTab() })
                "updates" -> AppUpdatesScreen(onBack = { returnToPreviousTab() })
                "settings" -> PersonalizationScreen(
                    current = preferences,
                    onBack = { returnToPreviousTab() },
                    onSave = onPreferencesChanged,
                    onRequestNotifications = onRequestNotifications,
                    onManagePermanentApps = { openSecondary("permanent_apps") }
                )
                "permanent_apps" -> PermanentAppsScreen(
                    selectedPackages = preferences.permanentBlockedPackages,
                    onBack = { rootTab = "settings" },
                    onSave = { packages -> onPreferencesChanged(preferences.copy(permanentBlockedPackages = packages)) }
                )
                "support" -> SupportScreen(onBack = { returnToPreviousTab() })
                "planner" -> StudyPlannerScreen(
                    dailyGoal = preferences.dailyGoalMinutes,
                    onBack = { returnToPreviousTab() },
                    onStartTask = { taskId -> setupTaskId = taskId }
                )
                "analytics" -> StudyAnalyticsScreen(onBack = { returnToPreviousTab() })

                "today", "tasks" -> TasksScreen(

                    onOpenSetup = { setupTaskId = it },

                    onEditBlocked = { pickerTaskId = it },

                    onOpenSubjects = { openSecondary("subjects") },
                    onOpenCalendar = { openSecondary("calendar") },
                    onOpenSettings = { openSecondary("settings") },
                    onOpenSupport = { openSecondary("support") },
                    onOpenPlanner = { openSecondary("planner") },
                    onOpenAnalytics = { openSecondary("analytics") },
                    dashboard = current == "today",
                    onOpenAllTasks = { rootTab = "tasks" },
                    preferences = preferences

                )

            }

        }

        if (screen in setOf("today", "tasks", "calendar", "subjects", "more")) {
            NavigationBar(containerColor = FocusColors.Surface) {
                listOf("today" to "اليوم", "tasks" to "مهامي", "calendar" to "التقويم", "subjects" to "موادي", "more" to "المزيد")
                    .forEach { (tab, label) ->
                        NavigationBarItem(
                            selected = rootTab == tab,
                            onClick = { rootTab = tab },
                            icon = { Text(when (tab) { "today" -> "◷"; "tasks" -> "✓"; "calendar" -> "▦"; "subjects" -> "▤"; else -> "•••" }) },
                            label = { Text(label) },
                            alwaysShowLabel = true
                        )
                    }
            }
        }
        }

    }

}

@Composable

private fun TasksScreen(

    onOpenSetup: (Long) -> Unit,

    onEditBlocked: (Long) -> Unit,

    onOpenSubjects: () -> Unit,
    onOpenCalendar: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSupport: () -> Unit,
    onOpenPlanner: () -> Unit,
    onOpenAnalytics: () -> Unit,
    dashboard: Boolean,
    onOpenAllTasks: () -> Unit,
    preferences: FocusPreferences

) {

    val context = LocalContext.current

    val version by SessionEngine.tasksVersion.collectAsState()

    val viewModel: TaskListViewModel = viewModel()

    val ui by viewModel.state.collectAsState()
    val subjectViewModel: SubjectListViewModel = viewModel()
    val subjectState by subjectViewModel.state.collectAsState()
    val subjectNames = remember(subjectState.subjects) { subjectState.subjects.associate { it.id to it.name } }

    val tasks = ui.tasks

    val totalMinutes = ui.totalMinutes

    var showAddDialog by remember { mutableStateOf(false) }

    var showEditDialog by remember { mutableStateOf(false) }

    var deleteTarget by remember { mutableStateOf<StudyTask?>(null) }
    var subtasksTarget by remember { mutableStateOf<StudyTask?>(null) }

    var editTarget by remember { mutableStateOf<StudyTask?>(null) }
    var taskQuery by rememberSaveable { mutableStateOf("") }
    var taskFilter by rememberSaveable { mutableStateOf(TaskListFilter.ALL) }
    var taskSort by rememberSaveable { mutableStateOf(TaskListSort.DEADLINE) }
    val visibleTasks = remember(tasks, taskQuery, taskFilter, taskSort) {
        val matchingTasks = tasks.filter { task ->
            val matchesFilter = when (taskFilter) {
                TaskListFilter.ALL -> true
                TaskListFilter.TODAY -> task.dueAt.isOnLocalDate(java.time.LocalDate.now()) && !task.isDone
                TaskListFilter.UPCOMING -> task.dueAt.isAfterLocalDate(java.time.LocalDate.now()) && !task.isDone
                TaskListFilter.OVERDUE -> task.dueAt.isBeforeLocalDate(java.time.LocalDate.now()) && !task.isDone
                TaskListFilter.OPEN -> !task.isDone && task.status != TaskStatus.CANCELLED
                TaskListFilter.COMPLETED -> task.isDone || task.status == TaskStatus.DONE
                TaskListFilter.URGENT -> !task.isDone && task.status != TaskStatus.CANCELLED && task.priority == TaskPriority.URGENT
            }
            val query = taskQuery.trim()
            val matchesQuery = query.isEmpty() || task.title.contains(query, ignoreCase = true) ||
                task.description.contains(query, ignoreCase = true) ||
                task.notes.contains(query, ignoreCase = true) ||
                task.category.label.contains(query, ignoreCase = true) ||
                task.subjectId?.let { subjectNames[it]?.contains(query, ignoreCase = true) } == true
            matchesFilter && matchesQuery
        }
        when (taskSort) {
            TaskListSort.DEADLINE -> matchingTasks.sortedWith(
                compareBy<StudyTask> { it.dueAt ?: Long.MAX_VALUE }
                    .thenByDescending { it.priority.level }
            )
            TaskListSort.PRIORITY -> matchingTasks.sortedWith(
                compareByDescending<StudyTask> { it.priority.level }
                    .thenBy { it.dueAt ?: Long.MAX_VALUE }
            )
            TaskListSort.NEWEST -> matchingTasks.sortedByDescending { it.createdAt }
            TaskListSort.TITLE -> matchingTasks.sortedBy { it.title.lowercase(Locale.getDefault()) }
        }
    }
    val nextTask = remember(tasks) {
        val today = java.time.LocalDate.now()
        tasks.filter { !it.isDone && it.status != TaskStatus.CANCELLED }
            .minWithOrNull(
                compareBy<StudyTask> { task ->
                    when {
                        task.dueAt.isBeforeLocalDate(today) -> 0
                        task.priority == TaskPriority.URGENT -> 1
                        task.dueAt.isOnLocalDate(today) -> 2
                        task.dueAt != null -> 3
                        else -> 4
                    }
                }.thenByDescending { it.priority.level }.thenBy { it.dueAt ?: Long.MAX_VALUE }
            )
    }

    var accessibilityOn by remember {

        mutableStateOf(AppBlockAccessibilityService.isEnabled(context))

    }

    LaunchedEffect(version) { viewModel.refresh() }

    RefreshOnResume {

        accessibilityOn = AppBlockAccessibilityService.isEnabled(context)

    }

    Column(

        modifier = Modifier

            .fillMaxSize()

            .padding(horizontal = 18.dp)

            .padding(top = 22.dp, bottom = 10.dp)

    ) {

        Row(verticalAlignment = Alignment.CenterVertically) {

            FocusLogo(size = 46.dp)

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {

                Text(

                    text = if (!dashboard) "مهامي" else if (preferences.studentName.isBlank()) "Focus Study" else "أهلًا، ${preferences.studentName}",

                    style = MaterialTheme.typography.titleMedium,

                    fontWeight = FontWeight.Bold,
                    color = FocusColors.TextPrimary

                )

                Text(

                    text = java.time.LocalDate.now().format(
                        java.time.format.DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.forLanguageTag("ar-EG"))
                    ) + if (dashboard) " · رتّب يومك وابدأ بخطوة صغيرة" else " · أنشئ مهامك ونظّم أولوياتك",

                    style = MaterialTheme.typography.bodySmall,

                    color = FocusColors.TextSecondary

                )

            }

            Button(

                onClick = { showAddDialog = true },

                shape = RoundedCornerShape(16.dp),

                contentPadding = ButtonDefaults.ButtonWithIconContentPadding

            ) { Text("مهمة +") }

        }

        if (dashboard) {
            Spacer(Modifier.height(12.dp))
            StudyCompanionCard(preferences, tasks)
            Spacer(Modifier.height(12.dp))
            HeroCard(totalMinutes = totalMinutes, tasks = tasks, preferences = preferences)
            Spacer(Modifier.height(14.dp))
            if (nextTask != null) {
                NextStepCard(task = nextTask, onStart = { onOpenSetup(nextTask.id) })
            } else {
                DashboardEmptyStep(onAddTask = { showAddDialog = true })
            }
            if (!accessibilityOn) {
                Spacer(Modifier.height(10.dp))
                AccessibilityWarningCard(onEnable = { openAccessibilitySettings(context) })
            }
            Spacer(Modifier.height(12.dp))
            Card(colors = CardDefaults.cardColors(containerColor = FocusColors.Surface), shape = RoundedCornerShape(20.dp)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("قائمة مهامك", style = MaterialTheme.typography.titleSmall, color = FocusColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                        Text("${tasks.count { !it.isDone && it.status != TaskStatus.CANCELLED }} مهمة قيد الإنجاز", style = MaterialTheme.typography.bodySmall, color = FocusColors.TextSecondary)
                    }
                    TextButton(onClick = onOpenAllTasks) { Text("إدارة المهام") }
                }
            }
            Spacer(Modifier.weight(1f))
        } else {
        Spacer(Modifier.height(16.dp))
        if (!accessibilityOn) {
            AccessibilityWarningCard(onEnable = { openAccessibilitySettings(context) })
            Spacer(Modifier.height(10.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {

            Text(

                text = "مهامي",

                style = MaterialTheme.typography.titleSmall,

                fontWeight = FontWeight.SemiBold,
                color = FocusColors.TextPrimary

            )

            Spacer(Modifier.weight(1f))

            Text(

                text = "${tasks.count { !it.isDone && it.status != TaskStatus.CANCELLED }} قيد الإنجاز",

                style = MaterialTheme.typography.labelMedium,

                color = FocusColors.TextSecondary

            )

        }

        Spacer(Modifier.height(10.dp))
        TaskListControls(
            query = taskQuery,
            onQueryChange = { taskQuery = it },
            filter = taskFilter,
            onFilterChange = { taskFilter = it },
            sort = taskSort,
            onSortChange = { taskSort = it }
        )

        Spacer(Modifier.height(8.dp))

        LazyColumn(

            modifier = Modifier.fillMaxWidth().weight(1f),

            verticalArrangement = Arrangement.spacedBy(10.dp)

        ) {

            itemsIndexed(visibleTasks, key = { _, task -> task.id }) { index, task ->

                TaskCard(

                    task = task,

                    subjectName = task.subjectId?.let { subjectNames[it] },

                    accent = FocusColors.accents[index % FocusColors.accents.size],

                    onStart = { onOpenSetup(task.id) },

                    onEditBlocked = { onEditBlocked(task.id) },

                    onDelete = { deleteTarget = task },
                    onEdit = { editTarget = task; showEditDialog = true },
                    onDuplicate = { viewModel.duplicate(task.id) },
                    onReschedule = {
                        val today = java.time.LocalDate.now()
                        val currentDue = task.dueAt?.let {
                            java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                        } ?: today
                        val newDate = if (currentDue.isBefore(today)) today.plusDays(1) else currentDue.plusDays(1)
                        viewModel.reschedule(task.id, parseDueDateMillis(newDate.toString()))
                    },
                    onSubtasks = { subtasksTarget = task },
                    onToggleDone = {
                        viewModel.setStatus(
                            task.id,
                            if (task.isDone) TaskStatus.TODO else TaskStatus.DONE
                        )
                    }

                )

            }

            if (visibleTasks.isEmpty()) {

                item {
                    if (tasks.isEmpty()) EmptyTasks()
                    else Text(
                        text = if (taskQuery.isBlank()) "لا توجد مهام ضمن هذا التصفية." else "لا توجد مهام تطابق بحثك.",
                        modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp),
                        color = FocusColors.TextSecondary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

            }

            item { Spacer(Modifier.height(8.dp)) }

        }
        }

    }

    if (showAddDialog) {

        AddTaskDialog(

            onDismiss = { showAddDialog = false },

            subjects = subjectState.subjects,
            onAdd = { title, minutes, category, priority, dueAtString, recurrence, notes, subjectId ->

                viewModel.add(title, minutes, category, priority, parseDueDateMillis(dueAtString), subjectId, notes.orEmpty(), recurrence)

                showAddDialog = false

            },

        )

    }

    subtasksTarget?.let { target ->
        SubtasksDialog(task = target, viewModel = viewModel, onDismiss = { subtasksTarget = null })
    }

    val target = deleteTarget

    if (target != null) {

        AlertDialog(

            onDismissRequest = { deleteTarget = null },

            shape = RoundedCornerShape(26.dp),

            containerColor = FocusColors.SurfaceHigh,

            title = { Text("حذف المهمة؟") },

            text = {

                Text(

                    "«${target.title}» ستُحذف مع ${target.doneMinutes} دقيقة تركيز مسجّلة. لا يمكن التراجع."

                )

            },

            confirmButton = {

                TextButton(

                    onClick = {

                        deleteTarget = null

                        viewModel.delete(target.id)

                    }

                ) { Text("حذف", color = FocusColors.onTint(FocusColors.Rose)) }

            },

            dismissButton = {

                TextButton(onClick = { deleteTarget = null }) { Text("إلغاء") }

            }

        )

    }

    // Edit task floating action

    val editingTask = editTarget
    if (showEditDialog && editingTask != null) {

        EditTaskDialog(

            onDismiss = { showEditDialog = false },

            subjects = subjectState.subjects,
            onEdit = { title, minutes, category, priority, dueAtString, recurrence, notes, subjectId ->

                viewModel.update(StudyTask(

                    id = editingTask.id,

                    title = title,

                    plannedMinutes = minutes,

                    doneMinutes = editingTask.doneMinutes,

                    isDone = editingTask.isDone,

                    blockedPackages = editingTask.blockedPackages,

                    createdAt = editingTask.createdAt,

                    description = notes.orEmpty(),

                    category = category,

                    priority = priority,

                    status = editingTask.status,

                    startAt = editingTask.startAt,

                    dueAt = parseDueDateMillis(dueAtString),

                    notes = notes.orEmpty(),

                    recurrence = recurrence,

                    sortOrder = editingTask.sortOrder,
                    subjectId = subjectId,
                    completedAt = editingTask.completedAt,
                    originalDueAt = editingTask.originalDueAt

                ))

                showEditDialog = false

            },

            targetTask = editingTask

        )

    }

}

@Composable

private fun NextStepCard(task: StudyTask, onStart: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = FocusColors.Surface),
        shape = RoundedCornerShape(22.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("خطوتك التالية", color = FocusColors.onTint(FocusColors.Teal), style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(3.dp))
                Text(task.title, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                Text(
                    "${task.remainingMinutes} دقيقة متبقية · ${task.priority.label}",
                    color = FocusColors.TextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            TextButton(onClick = onStart) { Text("ابدأ") }
        }
    }
}

@Composable
private fun DashboardEmptyStep(onAddTask: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = FocusColors.Surface), shape = RoundedCornerShape(22.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("جاهز لخطوتك الأولى؟", style = MaterialTheme.typography.titleSmall, color = FocusColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                Text("أضف مهمة وسنساعدك على تنظيم وقتها.", style = MaterialTheme.typography.bodySmall, color = FocusColors.TextSecondary)
            }
            TextButton(onClick = onAddTask) { Text("أضف مهمة") }
        }
    }
}

@Composable
private fun MoreSectionsScreen(
    onPlanner: () -> Unit,
    onAnalytics: () -> Unit,
    onSettings: () -> Unit,
    onSupport: () -> Unit,
    onUpdates: () -> Unit,
    onPrayerTimes: () -> Unit
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 20.dp)) {
        Text("المزيد", style = MaterialTheme.typography.headlineSmall, color = FocusColors.TextPrimary, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text("أدوات تساعدك على التخطيط ومراجعة تقدمك وتخصيص تجربتك.", style = MaterialTheme.typography.bodyMedium, color = FocusColors.TextSecondary)
        Spacer(Modifier.height(16.dp))
        MoreSectionCard("الخطة الأسبوعية", "حوّل مهامك إلى جلسات مذاكرة قابلة للتنفيذ.", "↗", onPlanner)
        MoreSectionCard("إحصاءاتي", "راجع وقت التركيز والمهام التي أنجزتها.", "◔", onAnalytics)
        MoreSectionCard("تخصيص التجربة", "اضبط اسمك وهدفك اليومي والتذكير والمظهر.", "⚙", onSettings)
        MoreSectionCard("مواقيت الصلاة", "احسب المواقيت حسب موقعك واضبط تذكيرًا محليًا.", "☾", onPrayerTimes)
        MoreSectionCard("تحديثات التطبيق", "افحص الإصدارات الجديدة واقرأ سجل التغييرات.", "↻", onUpdates)
        MoreSectionCard("الدعم الفني", "تواصل معنا إذا احتجت إلى مساعدة.", "?", onSupport)
    }
}

@Composable
private fun MoreSectionCard(title: String, description: String, mark: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp).clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = FocusColors.Surface),
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(FocusColors.SurfaceHigh), contentAlignment = Alignment.Center) {
                Text(mark, style = MaterialTheme.typography.titleMedium, color = FocusColors.onTint(FocusColors.Blue), fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = FocusColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                Text(description, style = MaterialTheme.typography.bodySmall, color = FocusColors.TextSecondary)
            }
            Text("‹", style = MaterialTheme.typography.titleLarge, color = FocusColors.TextMuted)
        }
    }
}

@Composable

private fun HeroCard(totalMinutes: Int, tasks: List<StudyTask>, preferences: FocusPreferences) {

    val todayTasks = tasks.filter { it.dueAt.isOnLocalDate(java.time.LocalDate.now()) }
    val done = todayTasks.count { it.isDone || it.status == TaskStatus.DONE }
    val percent = if (todayTasks.isEmpty()) 0 else done * 100 / todayTasks.size

    Box(

        modifier = Modifier

            .fillMaxWidth()

            .clip(RoundedCornerShape(28.dp))

            .background(FocusColors.hero)

            .padding(20.dp)

    ) {

        Row(verticalAlignment = Alignment.CenterVertically) {

            Column(modifier = Modifier.weight(1f)) {

                Text(

                    text = "مهام اليوم",

                    style = MaterialTheme.typography.labelLarge,

                    color = Color.White.copy(alpha = 0.95f)

                )

                Spacer(Modifier.height(4.dp))

                Row(verticalAlignment = Alignment.Bottom) {

                    Text(

                        text = "$done / ${todayTasks.size}",

                        fontSize = 40.sp,

                        fontWeight = FontWeight.Bold,

                        color = Color.White

                    )

                    Text(

                        text = " مكتملة",

                        style = MaterialTheme.typography.bodyMedium,

                        color = Color.White.copy(alpha = 0.95f),

                        modifier = Modifier.padding(bottom = 8.dp)

                    )

                }

                Spacer(Modifier.height(6.dp))

                Text(

                    text = "هدف المذاكرة: ${preferences.dailyGoalMinutes} دقيقة · التركيز الكلي: $totalMinutes دقيقة",

                    style = MaterialTheme.typography.bodySmall,

                    color = Color.White.copy(alpha = 0.95f)

                )

            }

            RingBadge(progress = percent / 100f, label = "$percent%")

        }

    }

}

@Composable

private fun RingBadge(progress: Float, label: String) {

    val animated by animateFloatAsState(

        targetValue = progress.coerceIn(0f, 1f),

        animationSpec = tween(900),

        label = "badge"

    )

    Box(modifier = Modifier.size(78.dp), contentAlignment = Alignment.Center) {

        Canvas(Modifier.fillMaxSize()) {

            val stroke = 8.dp.toPx()

            val arcSize = Size(size.width - stroke, size.height - stroke)

            val topLeft = Offset(stroke / 2, stroke / 2)

            drawArc(

                color = Color.White.copy(alpha = 0.32f),

                startAngle = -90f,

                sweepAngle = 360f,

                useCenter = false,

                topLeft = topLeft,

                size = arcSize,

                style = Stroke(stroke, cap = StrokeCap.Round)

            )

            drawArc(

                color = Color.White,

                startAngle = -90f,

                sweepAngle = 360f * animated,

                useCenter = false,

                topLeft = topLeft,

                size = arcSize,

                style = Stroke(stroke, cap = StrokeCap.Round)

            )

        }

        Text(

            text = label,

            style = MaterialTheme.typography.labelLarge,

            fontWeight = FontWeight.Bold,

            color = Color.White

        )

    }

}

@Composable

private fun TaskCard(

    task: StudyTask,

    subjectName: String?,

    accent: Color,

    onStart: () -> Unit,

    onEditBlocked: () -> Unit,

    onDelete: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onReschedule: () -> Unit,
    onSubtasks: () -> Unit,
    onToggleDone: () -> Unit

) {

    val animated by animateFloatAsState(

        targetValue = task.progress,

        animationSpec = tween(800),

        label = "task-progress"

    )

    val accentText = FocusColors.onTint(accent)
    var moreExpanded by remember { mutableStateOf(false) }

    Card(

        modifier = Modifier.fillMaxWidth().clickable(onClick = onStart),

        shape = RoundedCornerShape(24.dp),

        colors = CardDefaults.cardColors(containerColor = FocusColors.Surface)

    ) {

        Column {

            Box(

                modifier = Modifier

                    .fillMaxWidth()

                    .height(3.dp)

                    .background(Brush.horizontalGradient(listOf(accent, accent.copy(alpha = 0.05f))))

            )

            Column(modifier = Modifier.padding(16.dp)) {

                Row(verticalAlignment = Alignment.CenterVertically) {

                    Box(Modifier.size(9.dp).clip(CircleShape).background(accent))

                    Spacer(Modifier.width(9.dp))

                    Text(

                        text = task.title,

                        style = MaterialTheme.typography.titleMedium,

                        modifier = Modifier.weight(1f),

                        maxLines = 1

                    )

                    Checkbox(
                        checked = task.isDone,
                        onCheckedChange = { onToggleDone() },
                        colors = CheckboxDefaults.colors(checkedColor = FocusColors.Green)
                    )

                    Box(

                        modifier = Modifier

                            .clip(RoundedCornerShape(10.dp))

                            .background(accent.copy(alpha = 0.22f))

                            .padding(horizontal = 10.dp, vertical = 4.dp)

                    ) {

                        Text(

                            text = if (task.isDone) "مكتملة" else "${task.remainingMinutes} د",

                            style = MaterialTheme.typography.labelMedium,

                            color = accentText

                        )

                    }

                }

                Spacer(Modifier.height(12.dp))

                Box(

                    modifier = Modifier

                        .fillMaxWidth()

                        .height(8.dp)

                        .clip(RoundedCornerShape(8.dp))

                        .background(FocusColors.SurfaceHigh)

                ) {

                    Box(

                        modifier = Modifier

                            .fillMaxWidth(animated.coerceIn(0f, 1f))

                            .fillMaxSize()

                            .background(Brush.horizontalGradient(listOf(accent, accent.copy(alpha = 0.55f))))

                    )

                }

                Spacer(Modifier.height(8.dp))

                Text(

                    text = (subjectName?.let { "$it · " } ?: "") + "${task.category.label} · ${task.priority.label}" +
                        (task.dueAt?.let { " · الموعد ${formatDueDate(it)}" } ?: "") +
                        (task.originalDueAt?.let { " · كان ${formatDueDate(it)}" } ?: ""),

                    style = MaterialTheme.typography.bodySmall,

                    color = FocusColors.TextSecondary

                )

                Text(

                    text = "${task.doneMinutes} من ${task.plannedMinutes} دقيقة" +

                        if (task.blockedPackages.isEmpty()) " • بلا تطبيقات محددة"

                        else " • ${task.blockedPackages.size} تطبيقًا",

                    style = MaterialTheme.typography.bodySmall,

                    color = FocusColors.TextSecondary

                )

                HorizontalDivider(

                    modifier = Modifier.padding(vertical = 10.dp),

                    color = FocusColors.Outline

                )

                Row(verticalAlignment = Alignment.CenterVertically) {

                    TextButton(onClick = onStart) {

                        Text("ابدأ جلسة", color = accentText, fontWeight = FontWeight.SemiBold)

                    }

                    TextButton(onClick = onEditBlocked) {

                        Text("التطبيقات", color = FocusColors.TextSecondary)

                    }

                    Spacer(Modifier.weight(1f))

                    TextButton(onClick = onDelete) {

                        Text("حذف", color = FocusColors.onTint(FocusColors.Rose))

                    }

                    TextButton(onClick = onEdit) {

                        Text("تعديل", color = FocusColors.onTint(FocusColors.Green))

                    }

                    Box {
                        TextButton(onClick = { moreExpanded = true }) { Text("المزيد") }
                        DropdownMenu(expanded = moreExpanded, onDismissRequest = { moreExpanded = false }) {
                            DropdownMenuItem(text = { Text("المهام الفرعية") }, onClick = { moreExpanded = false; onSubtasks() })
                            DropdownMenuItem(text = { Text("تأجيل إلى غد") }, onClick = { moreExpanded = false; onReschedule() }, enabled = !task.isDone)
                            DropdownMenuItem(text = { Text("نسخ المهمة") }, onClick = { moreExpanded = false; onDuplicate() })
                        }
                    }

                }

            }

        }

    }}

@Composable

private fun SubtasksDialog(
    task: StudyTask,
    viewModel: TaskListViewModel,
    onDismiss: () -> Unit
) {
    var subtasks by remember(task.id) { mutableStateOf(emptyList<Subtask>()) }
    var title by rememberSaveable(task.id) { mutableStateOf("") }
    var refreshKey by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(task.id, refreshKey) { subtasks = viewModel.subtasks(task.id) }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        containerColor = FocusColors.SurfaceHigh,
        title = { Text("خطوات: ${task.title}") },
        text = {
            Column {
                Text("${subtasks.count { it.isDone }} من ${subtasks.size} مكتملة", color = FocusColors.TextSecondary)
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.fillMaxWidth().height(180.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(subtasks, key = { it.id }) { subtask ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = subtask.isDone,
                                onCheckedChange = { done ->
                                    scope.launch { viewModel.updateSubtask(subtask.id, done); refreshKey++ }
                                }
                            )
                            Text(subtask.title, modifier = Modifier.weight(1f))
                            TextButton(onClick = {
                                scope.launch { viewModel.removeSubtask(subtask.id); refreshKey++ }
                            }) { Text("حذف", color = FocusColors.onTint(FocusColors.Rose)) }
                        }
                    }
                    if (subtasks.isEmpty()) item { Text("قسّم المهمة إلى خطوات صغيرة تساعدك على البدء.", color = FocusColors.TextSecondary, modifier = Modifier.padding(vertical = 10.dp)) }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(80) },
                    label = { Text("خطوة جديدة") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(enabled = title.isNotBlank(), onClick = {
                scope.launch { viewModel.createSubtask(task.id, title); title = ""; refreshKey++ }
            }) { Text("إضافة خطوة") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إغلاق") } }
    )
}

@Composable

private fun EditTaskDialog(

    onDismiss: () -> Unit,

    onEdit: (String, Int, TaskCategory, TaskPriority, String?, Recurrence, String?, Long?) -> Unit,

    targetTask: StudyTask?,
    subjects: List<StudySubject>

) {

    var title by rememberSaveable { mutableStateOf(targetTask?.title.orEmpty()) }

    var minutesInput by rememberSaveable { mutableStateOf((targetTask?.plannedMinutes ?: 30).toString()) }
    var subjectId by rememberSaveable { mutableStateOf(targetTask?.subjectId) }

    var category by rememberSaveable { mutableStateOf(targetTask?.category ?: TaskCategory.STUDY) }

    var priority by rememberSaveable { mutableStateOf(targetTask?.priority ?: TaskPriority.MEDIUM) }

    var dueAtString by rememberSaveable { mutableStateOf(formatDueDate(targetTask?.dueAt)) }

    var notes by rememberSaveable { mutableStateOf(targetTask?.notes ?: "") }

    var recurrence by rememberSaveable { mutableStateOf(targetTask?.recurrence ?: Recurrence.NONE) }

    AlertDialog(

        onDismissRequest = onDismiss,

        shape = RoundedCornerShape(26.dp),

        containerColor = FocusColors.SurfaceHigh,

        title = { Text("تعديل المهمة") },

        text = {

            Column(modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {

                OutlinedTextField(

                    value = title,

                    onValueChange = { title = it },

                    singleLine = true,

                    label = { Text("اسم المهمة") },

                    shape = RoundedCornerShape(16.dp),

                    modifier = Modifier.fillMaxWidth()

                )

                Spacer(Modifier.height(16.dp))

                OutlinedTextField(
                    value = minutesInput,
                    onValueChange = { value -> if (value.length <= 3 && value.all(Char::isDigit)) minutesInput = value },
                    singleLine = true,
                    label = { Text("المدة المتوقعة بالدقائق (1–720)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = minutesInput.toIntOrNull() !in 1..720,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                SubjectChooser(subjects = subjects, selectedId = subjectId, onSelect = { subjectId = it })

                Text("التصنيف", style = MaterialTheme.typography.labelLarge)

                Spacer(Modifier.height(8.dp))

                Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {

                    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {

                        TaskCategoryChip(

                            selected = category == TaskCategory.STUDY,

                            onSelect = { category = TaskCategory.STUDY }

                        ) { Text("دراسة") }

                        TaskCategoryChip(

                            selected = category == TaskCategory.UNIVERSITY,

                            onSelect = { category = TaskCategory.UNIVERSITY }

                        ) { Text("جامعة") }

                        TaskCategoryChip(

                            selected = category == TaskCategory.PERSONAL,

                            onSelect = { category = TaskCategory.PERSONAL }

                        ) { Text("شخصي") }

                        TaskCategoryChip(

                            selected = category == TaskCategory.WORK,

                            onSelect = { category = TaskCategory.WORK }

                        ) { Text("عمل") }

                        TaskCategoryChip(

                            selected = category == TaskCategory.HABITS,

                            onSelect = { category = TaskCategory.HABITS }

                        ) { Text("عادات") }

                        TaskCategoryChip(

                            selected = category == TaskCategory.OTHER,

                            onSelect = { category = TaskCategory.OTHER }

                        ) { Text("أخرى") }

                    }

                }

                Spacer(Modifier.height(16.dp))

                Text("أولوية", style = MaterialTheme.typography.labelLarge)

                Spacer(Modifier.height(8.dp))

                Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {

                    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {

                        TaskPriorityChip(

                            selected = priority == TaskPriority.LOW,

                            onSelect = { priority = TaskPriority.LOW }

                        ) { Text("منخفضة") }

                        TaskPriorityChip(

                            selected = priority == TaskPriority.MEDIUM,

                            onSelect = { priority = TaskPriority.MEDIUM }

                        ) { Text("متوسطة") }

                        TaskPriorityChip(

                            selected = priority == TaskPriority.HIGH,

                            onSelect = { priority = TaskPriority.HIGH }

                        ) { Text("عالية") }

                        TaskPriorityChip(

                            selected = priority == TaskPriority.URGENT,

                            onSelect = { priority = TaskPriority.URGENT }

                        ) { Text("عاجلة") }

                    }

                }

                Spacer(Modifier.height(16.dp))

                Text("تاريخ الاستحقاق (اختياري)", style = MaterialTheme.typography.labelLarge)

                Spacer(Modifier.height(8.dp))

                OutlinedTextField(

                    value = dueAtString,

                    onValueChange = { dueAtString = it },

                    singleLine = true,

                    label = { Text("تاريخ (yyyy-mm-dd)") },

                    shape = RoundedCornerShape(16.dp),

                    modifier = Modifier.fillMaxWidth()

                )

                Spacer(Modifier.height(16.dp))

                Text("ملاحظات (اختياري)", style = MaterialTheme.typography.labelLarge)

                Spacer(Modifier.height(8.dp))

                OutlinedTextField(

                    value = notes,

                    onValueChange = { notes = it },

                    singleLine = true,

                    label = { Text("ملاحظات") },

                    shape = RoundedCornerShape(16.dp),

                    modifier = Modifier.fillMaxWidth()

                )

                Spacer(Modifier.height(16.dp))

                Text("التكرار", style = MaterialTheme.typography.labelLarge)

                Spacer(Modifier.height(8.dp))

                Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {

                    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {

                        RecurrenceChip(

                            selected = recurrence == Recurrence.NONE,

                            onSelect = { recurrence = Recurrence.NONE }

                        ) { Text("بدون تكرار") }

                        RecurrenceChip(

                            selected = recurrence == Recurrence.DAILY,

                            onSelect = { recurrence = Recurrence.DAILY }

                        ) { Text("يوميًا") }

                        RecurrenceChip(

                            selected = recurrence == Recurrence.WEEKLY,

                            onSelect = { recurrence = Recurrence.WEEKLY }

                        ) { Text("أسبوعيًا") }

                        RecurrenceChip(

                            selected = recurrence == Recurrence.MONTHLY,

                            onSelect = { recurrence = Recurrence.MONTHLY }

                        ) { Text("شهريًا") }

                    }

                }

            }

        },

        confirmButton = {

            TextButton(

                onClick = { if (title.isNotBlank()) onEdit(title.trim(), minutesInput.toIntOrNull() ?: 30, category, priority, dueAtString, recurrence, notes, subjectId) },

                enabled = title.isNotBlank() && minutesInput.toIntOrNull() in 1..720

            ) { Text("حفظ") }

        },

        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }

    )

}

@Composable

private fun EmptyTasks() {

    Column(

        modifier = Modifier.fillMaxWidth().padding(top = 28.dp),

        horizontalAlignment = Alignment.CenterHorizontally

    ) {

        FocusLogo(size = 64.dp, showTile = false)

        Spacer(Modifier.height(12.dp))

        Text(

            text = "ابدأ بمهمة واحدة",

            style = MaterialTheme.typography.titleSmall,
            color = FocusColors.TextPrimary

        )

        Spacer(Modifier.height(6.dp))

        Text(

            text = "أضف مهمة، حدّد التطبيقات المشتّتة، ثم ابدأ جلسة تركيز مقفلة.",

            style = MaterialTheme.typography.bodySmall,

            color = FocusColors.TextSecondary,

            textAlign = TextAlign.Center

        )

    }

}

@Composable

private fun AccessibilityWarningCard(onEnable: () -> Unit) {

    Card(

        modifier = Modifier.fillMaxWidth(),

        shape = RoundedCornerShape(22.dp),

        colors = CardDefaults.cardColors(containerColor = FocusColors.Amber.copy(alpha = 0.16f))

    ) {

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "فعّل حجب فتح التطبيقات",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = FocusColors.onTint(FocusColors.Amber)
                )
                Text(
                    text = "إذن الوصول يقرأ اسم التطبيق المفتوح فقط، ولا يقرأ محتواه.",
                    style = MaterialTheme.typography.bodySmall,
                    color = FocusColors.TextSecondary,
                    maxLines = 2
                )
            }
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = onEnable) { Text("تفعيل") }
        }

    }

}

/**

 * Pre-session screen: the learner sets both the duration and the apps that will be locked, grants

 * the required permissions, and only then can the locked session start.

 */

@Composable

private fun SessionSetupScreen(

    taskId: Long,
    defaultMinutes: Int,

    onBack: () -> Unit,

    onRequestNotifications: () -> Unit,

    onStart: (Long, String, Int, Set<String>) -> Unit

) {

    val context = LocalContext.current

    var title by rememberSaveable { mutableStateOf("") }

    var apps by remember { mutableStateOf(emptyList<InstalledApp>()) }

    var selected by rememberSaveable(

        stateSaver = listSaver<Set<String>, String>(

            save = { it.toList() },

            restore = { it.toSet() }

        )

    ) { mutableStateOf(emptySet<String>()) }

    var usePermanentApps by rememberSaveable { mutableStateOf(true) }
    var blockAllApps by rememberSaveable { mutableStateOf(false) }

    var query by rememberSaveable { mutableStateOf("") }

    var minutes by rememberSaveable { mutableStateOf(defaultMinutes.toFloat()) }

    var confirm by remember { mutableStateOf(false) }

    var restored by rememberSaveable { mutableStateOf(false) }

    var accessibilityOn by remember {

        mutableStateOf(AppBlockAccessibilityService.isEnabled(context))

    }

    var vpnReady by remember { mutableStateOf(isVpnConsentGranted(context)) }

    var notificationsOn by remember { mutableStateOf(hasNotificationPermission(context)) }

    var batteryOk by remember { mutableStateOf(isIgnoringBatteryOptimizations(context)) }

    var ownerActive by remember { mutableStateOf(DeviceOwnerController.isDeviceOwner(context)) }

    var showOwnerHelp by remember { mutableStateOf(false) }

    LaunchedEffect(taskId) {

        apps = withContext(Dispatchers.IO) { loadLaunchableApps(context) }

    }

    LaunchedEffect(taskId) {

        if (restored) return@LaunchedEffect

        val task = withContext(Dispatchers.IO) {

            StudyDb.get(context).tasks().firstOrNull { it.id == taskId }

        }

        title = task?.title.orEmpty()

        selected = task?.blockedPackages ?: emptySet()

        minutes = ((task?.remainingMinutes ?: defaultMinutes).coerceIn(5, 120)).toFloat()

        restored = true

    }

    RefreshOnResume {

        accessibilityOn = AppBlockAccessibilityService.isEnabled(context)

        vpnReady = isVpnConsentGranted(context)

        notificationsOn = hasNotificationPermission(context)

        batteryOk = isIgnoringBatteryOptimizations(context)

        ownerActive = DeviceOwnerController.isDeviceOwner(context)

    }

    val filtered = remember(apps, query) {

        if (query.isBlank()) {

            apps

        } else {

            apps.filter {

                it.label.contains(query, ignoreCase = true) ||

                    it.packageName.contains(query, ignoreCase = true)

            }

        }

    }

    val ready = accessibilityOn && vpnReady && notificationsOn
    val permanent = FocusPreferences.load(context).permanentBlockedPackages
    val homePackage = remember(context) {
        runCatching {
            context.packageManager.resolveActivity(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
                PackageManager.MATCH_DEFAULT_ONLY
            )?.activityInfo?.packageName
        }.getOrNull()
    }
    val protectedPackages = remember(context, homePackage) {
        setOfNotNull(
            context.packageName, homePackage, "com.android.systemui", "com.android.settings",
            "com.android.phone", "com.android.dialer", "com.google.android.dialer",
            "com.android.emergency", "com.android.packageinstaller"
        )
    }
    val effectiveSelected = when {
        blockAllApps -> apps.map { it.packageName }.filterNot { it in protectedPackages }.toSet()
        usePermanentApps -> selected + permanent
        else -> selected
    }
    val canStart = ready && effectiveSelected.isNotEmpty()

    val duration = minutes.toInt()

    Column(

        modifier = Modifier

            .fillMaxSize()

            .padding(horizontal = 18.dp)

            .padding(top = 18.dp, bottom = 10.dp)

    ) {

        Row(verticalAlignment = Alignment.CenterVertically) {

            Column(modifier = Modifier.weight(1f)) {

                Text(

                    text = "إعداد الجلسة",

                    style = MaterialTheme.typography.titleLarge,

                    fontWeight = FontWeight.Bold,
                    color = FocusColors.TextPrimary

                )

                Text(

                    text = title.ifBlank { "مهمة" },

                    style = MaterialTheme.typography.bodySmall,

                    color = FocusColors.TextSecondary

                )

            }

            TextButton(onClick = onBack) { Text("رجوع", color = FocusColors.TextSecondary) }

        }

        Spacer(Modifier.height(12.dp))

        LazyColumn(

            modifier = Modifier.fillMaxWidth().weight(1f),

            verticalArrangement = Arrangement.spacedBy(14.dp)

        ) {

            item {

                Card(

                    shape = RoundedCornerShape(22.dp),

                    colors = CardDefaults.cardColors(containerColor = FocusColors.Surface)

                ) {

                    Column(modifier = Modifier.padding(16.dp)) {

                        Row(verticalAlignment = Alignment.CenterVertically) {

                            Text(

                                text = "المدة",

                                style = MaterialTheme.typography.titleSmall,
                                color = FocusColors.TextPrimary

                            )

                            Spacer(Modifier.weight(1f))

                            Text(

                                text = "$duration دقيقة",

                                style = MaterialTheme.typography.titleSmall,

                                color = FocusColors.onTint(FocusColors.Blue)

                            )

                        }

                        Spacer(Modifier.height(10.dp))

                        DurationChips(selected = duration, onSelect = { minutes = it.toFloat() })

                        Slider(

                            value = minutes,

                            onValueChange = { minutes = it },

                            valueRange = 5f..120f,

                            steps = 22,

                            colors = SliderDefaults.colors(

                                thumbColor = FocusColors.Blue,

                                activeTrackColor = FocusColors.Blue,

                                inactiveTrackColor = FocusColors.Outline

                            )

                        )

                        Text(

                            text = "من 5 إلى 120 دقيقة. المؤقّت يبدأ بعد التأكيد فقط.",

                            style = MaterialTheme.typography.bodySmall,

                            color = FocusColors.TextSecondary

                        )

                    }

                }

            }

            item {
                Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = FocusColors.Surface)) {
                    Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("نطاق الحجب", style = MaterialTheme.typography.titleSmall, color = FocusColors.TextPrimary)
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { usePermanentApps = !usePermanentApps }) {
                            Checkbox(checked = usePermanentApps, onCheckedChange = { usePermanentApps = it })
                            Column {
                                Text("التطبيقات الثابتة + تطبيقات المهمة", color = FocusColors.TextPrimary)
                                Text("${permanent.size} ثابتة ستُضاف تلقائيًا", style = MaterialTheme.typography.bodySmall, color = FocusColors.TextSecondary)
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { blockAllApps = !blockAllApps }) {
                            Checkbox(checked = blockAllApps, onCheckedChange = { blockAllApps = it })
                            Column {
                                Text("حجب كل التطبيقات القابلة للفتح", color = FocusColors.TextPrimary)
                                Text("تظل الشاشة الرئيسية والاتصال والطوارئ متاحة. التطبيق لا يطفئ الهاتف.", style = MaterialTheme.typography.bodySmall, color = FocusColors.TextSecondary)
                            }
                        }
                    }
                }
            }

            item {

                Row(verticalAlignment = Alignment.CenterVertically) {

                    Text(

                        text = "التطبيقات التي ستُقفل",

                        style = MaterialTheme.typography.titleSmall,
                        color = FocusColors.TextPrimary

                    )

                    Spacer(Modifier.weight(1f))

                    Text(

                        text = "${effectiveSelected.size} سيُحجب",

                        style = MaterialTheme.typography.labelMedium,

                        color = FocusColors.onTint(FocusColors.Blue)

                    )

                }

            }

            item {

                OutlinedTextField(

                    value = query,

                    onValueChange = { query = it },

                    singleLine = true,

                    label = { Text("بحث عن تطبيق") },

                    shape = RoundedCornerShape(16.dp),

                    modifier = Modifier.fillMaxWidth()

                )

            }

            item {

                Row(verticalAlignment = Alignment.CenterVertically) {

                    TextButton(onClick = { selected = filtered.map { it.packageName }.toSet() }) {

                        Text("اختيار الكل")

                    }

                    TextButton(onClick = { selected = emptySet() }) {

                        Text("مسح", color = FocusColors.TextSecondary)

                    }

                }

            }

            if (selected.isNotEmpty()) item {
                SelectedAppsStrip(
                    selectedApps = apps.filter { it.packageName in selected },
                    onRemove = { packageName -> selected = selected - packageName }
                )
            }

            items(filtered, key = { it.packageName }) { app ->

                val checked = app.packageName in selected || (usePermanentApps && app.packageName in permanent) || blockAllApps

                Row(

                    modifier = Modifier

                        .fillMaxWidth()

                        .clip(RoundedCornerShape(16.dp))

                        .background(if (checked) FocusColors.SurfaceHigh else FocusColors.Surface)

                        .clickable {

                            if (blockAllApps || (usePermanentApps && app.packageName in permanent)) return@clickable

                            selected = if (checked) {

                                selected - app.packageName

                            } else {

                                selected + app.packageName

                            }

                        }

                        .padding(horizontal = 10.dp, vertical = 8.dp),

                    verticalAlignment = Alignment.CenterVertically

                ) {

                    Checkbox(

                        checked = checked,

                        onCheckedChange = { value ->

                            if (blockAllApps || (usePermanentApps && app.packageName in permanent)) return@Checkbox

                            selected = if (value) {

                                selected + app.packageName

                            } else {

                                selected - app.packageName

                            }

                        },

                        colors = CheckboxDefaults.colors(checkedColor = FocusColors.Blue)

                    )

                    Spacer(Modifier.width(8.dp))
                    InstalledAppIcon(app, Modifier.size(40.dp))
                    Spacer(Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {

                        Text(app.label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, color = FocusColors.TextPrimary)

                        Text(

                            text = app.packageName,

                            style = MaterialTheme.typography.labelSmall,

                            color = FocusColors.TextMuted,

                            maxLines = 1

                        )

                    }

                }

            }

            if (filtered.isEmpty()) {

                item {

                    Text(

                        text = "لا تطبيقات مطابقة للبحث.",

                        style = MaterialTheme.typography.bodySmall,

                        color = FocusColors.TextSecondary

                    )

                }

            }

            item {

                Card(

                    shape = RoundedCornerShape(22.dp),

                    colors = CardDefaults.cardColors(containerColor = FocusColors.Surface)

                ) {

                    Column(modifier = Modifier.padding(16.dp)) {

                        Text(

                            text = "الأذونات المطلوبة",

                            style = MaterialTheme.typography.titleSmall

                        )

                        Spacer(Modifier.height(4.dp))

                        Text(

                            text = "لن تبدأ الجلسة قبل منحها، لأنها الوسيلة الوحيدة لمنع فتح التطبيقات فعليًا.",

                            style = MaterialTheme.typography.bodySmall,

                            color = FocusColors.TextSecondary

                        )

                        Spacer(Modifier.height(12.dp))

                        PermissionRow(

                            title = "إذن الوصول (Accessibility)",

                            description = "يخفي التطبيق المقفل ويعيدك للرئيسية.",

                            granted = accessibilityOn,

                            required = true,

                            actionLabel = "منح",

                            onAction = { openAccessibilitySettings(context) }

                        )

                        PermissionRow(

                            title = "إذن VPN",

                            description = "يقطع إنترنت التطبيقات المختارة.",

                            granted = vpnReady,

                            required = true,

                            actionLabel = "منح",

                            onAction = {

                                val consent = VpnService.prepare(context)

                                if (consent != null) {

                                    runCatching { context.startActivity(consent) }

                                } else {

                                    vpnReady = true

                                }

                            }

                        )

                        PermissionRow(

                            title = "إذن الإشعارات",

                            description = "يعرض الجلسة الجارية والمؤقّت.",

                            granted = notificationsOn,

                            required = true,

                            actionLabel = "منح",

                            onAction = onRequestNotifications

                        )

                        PermissionRow(

                            title = "تعطيل تحسين البطارية",

                            description = "يمنع النظام من إيقاف الجلسة في الخلفية.",

                            granted = batteryOk,

                            required = false,

                            actionLabel = "فتح",

                            onAction = { openBatterySettings(context) }

                        )

                    }

                }

            }

            item {

                Card(

                    shape = RoundedCornerShape(22.dp),

                    colors = CardDefaults.cardColors(

                        containerColor = if (ownerActive) {

                            FocusColors.Green.copy(alpha = 0.14f)

                        } else {

                            FocusColors.Surface

                        }

                    )

                ) {

                    Column(modifier = Modifier.padding(16.dp)) {

                        Row(verticalAlignment = Alignment.CenterVertically) {

                            Text(

                                text = "تعليق التطبيقات نهائيًا",

                                style = MaterialTheme.typography.titleSmall

                            )

                            Spacer(Modifier.weight(1f))

                            Text(

                                text = if (ownerActive) "مفعّل" else "اختياري",

                                style = MaterialTheme.typography.labelMedium,

                                color = if (ownerActive) {

                                    FocusColors.onTint(FocusColors.Green)

                                } else {

                                    FocusColors.TextMuted

                                }

                            )

                        }

                        Spacer(Modifier.height(6.dp))

                        Text(

                            text = if (ownerActive) {

                                "التطبيقات المختارة ستُعلَّق على مستوى النظام: تُعطَّل أيقوناتها، تختفي من قائمة التطبيقات الأخيرة، ولا تعمل حتى تنتهي الجلسة."

                            } else {

                                "بدون وضع Device Owner يبقى المنع عبر شاشة الحجب وVPN. تفعيله يجعل النظام نفسه يوقف التطبيقات ويُعطّل أيقوناتها."

                            },

                            style = MaterialTheme.typography.bodySmall,

                            color = FocusColors.TextSecondary

                        )

                        if (!ownerActive) {

                            Spacer(Modifier.height(10.dp))

                            OutlinedButton(

                                onClick = { showOwnerHelp = true },

                                shape = RoundedCornerShape(14.dp)

                            ) { Text("طريقة التفعيل") }

                        }

                    }

                }

            }

            item {

                Row(

                    modifier = Modifier

                        .fillMaxWidth()

                        .clip(RoundedCornerShape(18.dp))

                        .background(FocusColors.Rose.copy(alpha = 0.14f))

                        .padding(14.dp),

                    verticalAlignment = Alignment.CenterVertically

                ) {

                    Text(

                        text = "بعد البدء لا يمكن إلغاء الجلسة أو تقصيرها؛ تُفتح التطبيقات تلقائيًا عند انتهاء المدة.",

                        style = MaterialTheme.typography.bodySmall,

                        color = FocusColors.onTint(FocusColors.Rose)

                    )

                }

            }

        }

        Spacer(Modifier.height(10.dp))

        Text(

            text = when {

                !ready -> "أكمل الأذونات المطلوبة أولًا."

                effectiveSelected.isEmpty() -> "اختر تطبيقًا واحدًا على الأقل."

                else -> "$duration دقيقة • ${effectiveSelected.size} تطبيقًا سيُقفل"

            },

            style = MaterialTheme.typography.bodySmall,

            color = if (canStart) FocusColors.TextSecondary

            else FocusColors.onTint(FocusColors.Amber)

        )

        Spacer(Modifier.height(6.dp))

        Button(

            onClick = { confirm = true },

            enabled = canStart,

            modifier = Modifier.fillMaxWidth().height(54.dp),

            shape = RoundedCornerShape(18.dp)

        ) { Text("ابدأ الجلسة", fontWeight = FontWeight.Bold) }

    }

    if (confirm) {

        AlertDialog(

            onDismissRequest = { confirm = false },

            shape = RoundedCornerShape(26.dp),

            containerColor = FocusColors.SurfaceHigh,

            title = { Text("جلسة مقفلة") },

            text = {

                Text(

                    "مدة $duration دقيقة، و${effectiveSelected.size} تطبيقًا سيُمنع فتحه وتُقطع شبكته.\n\n" +

                        "لن تستطيع إلغاء الجلسة أو فتح هذه التطبيقات قبل انتهاء الوقت."

                )

            },            confirmButton = {

                Button(

                    onClick = {

                        confirm = false

                        onStart(taskId, title, duration, effectiveSelected)

                    },

                    shape = RoundedCornerShape(14.dp)

                ) { Text("ابدأ الآن") }

            },

            dismissButton = {

                TextButton(onClick = { confirm = false }) { Text("رجوع") }

            }

        )

    }

}



@Composable

private fun DeviceOwnerDialog(command: String, onDismiss: () -> Unit) {

    val context = LocalContext.current

    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager



    AlertDialog(

        onDismissRequest = onDismiss,

        shape = RoundedCornerShape(26.dp),

        containerColor = FocusColors.SurfaceHigh,

        title = { Text("تفعيل Device Owner") },

        text = {

            Column {

                Text(

                    text = "خطوة اختيارية تُنفَّذ مرة واحدة من الكمبيوتر عبر USB. بعدها يعلّق النظام نفسه التطبيقات المختارة أثناء الجلسة ويُعطّل أيقوناتها.",

                    style = MaterialTheme.typography.bodySmall,

                    color = FocusColors.TextSecondary

                )

                Spacer(Modifier.height(12.dp))

                Text("1. فعّل خيارات المطوّر وتصحيح USB.", style = MaterialTheme.typography.bodySmall)

                Spacer(Modifier.height(4.dp))

                Text("2. الجهاز يجب ألا يحتوي على حسابات مسجّلة؛ قد يحتاج إعادة ضبط مصنع.", style = MaterialTheme.typography.bodySmall)

                Spacer(Modifier.height(4.dp))



                Text("3. نفّذ الأمر من الكمبيوتر:", style = MaterialTheme.typography.bodySmall)

                Spacer(Modifier.height(8.dp))

                Text(

                    text = command,

                    style = MaterialTheme.typography.labelMedium,

                    color = FocusColors.onTint(FocusColors.Blue)

                )

                Spacer(Modifier.height(10.dp))

                Text(                    text = "للإزالة لاحقًا: adb shell dpm remove-active-admin مع اسم الحزمة، أو أعد ضبط المصنع.",

                    style = MaterialTheme.typography.labelSmall,

                    color = FocusColors.TextMuted

                )

            }

        },

        confirmButton = {

            Button(

                onClick = {

                    clipboard?.setPrimaryClip(ClipData.newPlainText("Device Owner command", command))

                    Toast.makeText(context, "تم نسخ الأمر", Toast.LENGTH_SHORT).show()

                },

                shape = RoundedCornerShape(14.dp)

            ) { Text("نسخ الأمر") }

        },

        dismissButton = { TextButton(onClick = onDismiss) { Text("إغلاق") } }

    )

}


@Composable

private fun PermissionRow(

    title: String,

    description: String,

    granted: Boolean,

    required: Boolean,

    actionLabel: String,

    onAction: () -> Unit

) {

    Row(

        modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),

        verticalAlignment = Alignment.CenterVertically

    ) {

        Box(

            modifier = Modifier

                .size(10.dp)

                .clip(CircleShape)

                .background(if (granted) FocusColors.Green else FocusColors.Amber)

        )

        Spacer(Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {

            Row(verticalAlignment = Alignment.CenterVertically) {

                Text(title, style = MaterialTheme.typography.bodyLarge)

                if (!required) {

                    Spacer(Modifier.width(6.dp))

                    Text(

                        text = "اختياري",

                        style = MaterialTheme.typography.labelSmall,

                        color = FocusColors.TextMuted

                    )

                }

            }

            Text(

                text = description,

                style = MaterialTheme.typography.labelMedium,

                color = FocusColors.TextSecondary

            )

        }

        if (!granted) {

            OutlinedButton(onClick = onAction, shape = RoundedCornerShape(12.dp)) {

                Text(actionLabel)

            }

        } else {

            Text(

                text = "مفعّل",

                style = MaterialTheme.typography.labelMedium,

                color = FocusColors.onTint(FocusColors.Green)

            )

        }

    }

}

@Composable

private fun DurationChips(selected: Int, onSelect: (Int) -> Unit) {

    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {

        listOf(15, 25, 45, 60, 90).forEach { option ->

            val active = option == selected

            Box(

                modifier = Modifier

                    .clip(RoundedCornerShape(12.dp))

                    .background(if (active) FocusColors.Blue else FocusColors.SurfaceHigh)

                    .clickable { onSelect(option) }

                    .padding(horizontal = 12.dp, vertical = 9.dp)

            ) {

                Text(

                    text = "$option",

                    style = MaterialTheme.typography.labelLarge,

                    color = if (active) Color.White else FocusColors.TextSecondary

                )

            }

        }

    }

}

@Composable

private fun SessionScreen(

    session: SessionEngine.Active,

    remainingSeconds: Long,

    vpnLost: Boolean,

    onResumeVpn: () -> Unit

) {

    val context = LocalContext.current

    val ownerActive = remember { DeviceOwnerController.isDeviceOwner(context) }

    val progress = (remainingSeconds.toFloat() / session.totalSeconds.toFloat()).coerceIn(0f, 1f)

    val animatedProgress by animateFloatAsState(

        targetValue = progress,

        animationSpec = tween(600),

        label = "ring"

    )

    val pulse = rememberInfiniteTransition(label = "pulse")

    val glow by pulse.animateFloat(

        initialValue = 0.35f,

        targetValue = 0.9f,

        animationSpec = infiniteRepeatable(tween(1900, easing = LinearEasing), RepeatMode.Reverse),

        label = "glow"

    )

    Column(

        modifier = Modifier.fillMaxSize().padding(24.dp),

        horizontalAlignment = Alignment.CenterHorizontally,

        verticalArrangement = Arrangement.Center

    ) {

        Row(verticalAlignment = Alignment.CenterVertically) {

            Box(Modifier.size(9.dp).clip(CircleShape).background(FocusColors.Green.copy(alpha = glow)))

            Spacer(Modifier.width(8.dp))

            Text(

                text = "جلسة مقفلة — لا يمكن إلغاؤها",

                style = MaterialTheme.typography.labelLarge,

                color = FocusColors.TextSecondary

            )

        }

        Spacer(Modifier.height(14.dp))

        Text(

            text = session.taskTitle,

            style = MaterialTheme.typography.titleLarge,

            fontWeight = FontWeight.Bold,

            textAlign = TextAlign.Center

        )

        Spacer(Modifier.height(24.dp))

        Box(modifier = Modifier.size(258.dp), contentAlignment = Alignment.Center) {

            Canvas(Modifier.fillMaxSize()) {

                val stroke = 16.dp.toPx()

                val arcSize = Size(size.width - stroke, size.height - stroke)

                val topLeft = Offset(stroke / 2, stroke / 2)

                drawCircle(

                    color = FocusColors.Blue.copy(alpha = 0.08f * glow),

                    radius = size.minDimension * 0.48f

                )

                drawArc(

                    color = FocusColors.SurfaceHigh,

                    startAngle = -90f,

                    sweepAngle = 360f,

                    useCenter = false,

                    topLeft = topLeft,

                    size = arcSize,

                    style = Stroke(stroke, cap = StrokeCap.Round)

                )

                drawArc(

                    brush = Brush.sweepGradient(

                        listOf(FocusColors.Blue, FocusColors.Violet, FocusColors.Teal, FocusColors.Blue)

                    ),

                    startAngle = -90f,

                    sweepAngle = 360f * animatedProgress,

                    useCenter = false,

                    topLeft = topLeft,

                    size = arcSize,

                    style = Stroke(stroke, cap = StrokeCap.Round)

                )

            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {

                Text(

                    text = formatClock(remainingSeconds),

                    fontSize = 52.sp,

                    fontWeight = FontWeight.Bold,

                    color = Color.White

                )

                Text(

                    text = "متبقي حتى تُفتح التطبيقات",

                    style = MaterialTheme.typography.labelMedium,

                    color = FocusColors.TextSecondary

                )

            }

        }

        Spacer(Modifier.height(24.dp))

        Box(

            modifier = Modifier

                .clip(RoundedCornerShape(14.dp))

                .background(FocusColors.SurfaceHigh)

                .padding(horizontal = 14.dp, vertical = 8.dp)

        ) {

            Text(

                text = "${session.blockedPackages.size} تطبيقًا ممنوع من الفتح والإنترنت",

                style = MaterialTheme.typography.bodySmall,

                color = FocusColors.TextPrimary

            )

        }

        if (ownerActive) {

            Spacer(Modifier.height(8.dp))

            Box(

                modifier = Modifier

                    .clip(RoundedCornerShape(14.dp))

                    .background(FocusColors.Green.copy(alpha = 0.16f))

                    .padding(horizontal = 14.dp, vertical = 8.dp)

            ) {

                Text(

                    text = "التعليق النظامي مفعّل — أيقوناتها معطّلة",

                    style = MaterialTheme.typography.labelMedium,

                    color = FocusColors.onTint(FocusColors.Green)

                )

            }

        }

        if (vpnLost) {

            Spacer(Modifier.height(16.dp))

            Card(

                shape = RoundedCornerShape(18.dp),

                colors = CardDefaults.cardColors(containerColor = FocusColors.Amber.copy(alpha = 0.16f))

            ) {

                Column(modifier = Modifier.padding(14.dp)) {

                    Text(

                        text = "حجب الشبكة توقّف",

                        style = MaterialTheme.typography.titleSmall,

                        color = FocusColors.onTint(FocusColors.Amber)

                    )

                    Spacer(Modifier.height(4.dp))

                    Text(

                        text = "منع فتح التطبيقات ما زال يعمل. أعد تشغيل حجب الشبكة ليكتمل الحجب.",

                        style = MaterialTheme.typography.bodySmall,

                        color = FocusColors.TextSecondary

                    )

                    Spacer(Modifier.height(8.dp))

                    OutlinedButton(onClick = onResumeVpn, shape = RoundedCornerShape(12.dp)) {

                        Text("إعادة تشغيل الحجب")

                    }

                }

            }

        }

    }

}

@Composable

private fun AddTaskDialog(
    onDismiss: () -> Unit,
    subjects: List<StudySubject>,
    onAdd: (String, Int, TaskCategory, TaskPriority, String?, Recurrence, String?, Long?) -> Unit
) {

    var title by rememberSaveable { mutableStateOf("") }

    var minutesInput by rememberSaveable { mutableStateOf("30") }
    var subjectId by rememberSaveable { mutableStateOf<Long?>(null) }

    var category by rememberSaveable { mutableStateOf(TaskCategory.STUDY) }

    var priority by rememberSaveable { mutableStateOf(TaskPriority.MEDIUM) }

    var dueAtString by rememberSaveable { mutableStateOf("") }

    var notes by rememberSaveable { mutableStateOf("") }

    var recurrence by rememberSaveable { mutableStateOf(Recurrence.NONE) }



    AlertDialog(



        onDismissRequest = onDismiss,

        shape = RoundedCornerShape(26.dp),

        containerColor = FocusColors.SurfaceHigh,

        title = { Text("مهمة جديدة") },

        text = {

            Column(modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {

                OutlinedTextField(

                    value = title,

                    onValueChange = { title = it },

                    singleLine = true,

                    label = { Text("اسم المهمة") },

                    shape = RoundedCornerShape(16.dp),

                    modifier = Modifier.fillMaxWidth()

                )

                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = minutesInput,
                    onValueChange = { value -> if (value.length <= 3 && value.all(Char::isDigit)) minutesInput = value },
                    singleLine = true,
                    label = { Text("المدة المتوقعة بالدقائق (1–720)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = minutesInput.toIntOrNull() !in 1..720,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                SubjectChooser(subjects = subjects, selectedId = subjectId, onSelect = { subjectId = it })

                Spacer(Modifier.height(16.dp))

                Text("التصنيف", style = MaterialTheme.typography.labelLarge)

                Spacer(Modifier.height(8.dp))

                Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {

                    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {

                        TaskCategoryChip(

                            selected = category == TaskCategory.STUDY,

                            onSelect = { category = TaskCategory.STUDY }

                        ) { Text("دراسة") }

                        TaskCategoryChip(

                            selected = category == TaskCategory.UNIVERSITY,

                            onSelect = { category = TaskCategory.UNIVERSITY }

                        ) { Text("جامعة") }

                        TaskCategoryChip(

                            selected = category == TaskCategory.PERSONAL,

                            onSelect = { category = TaskCategory.PERSONAL }

                        ) { Text("شخصي") }

                        TaskCategoryChip(

                            selected = category == TaskCategory.WORK,

                            onSelect = { category = TaskCategory.WORK }

                        ) { Text("عمل") }

                        TaskCategoryChip(

                            selected = category == TaskCategory.HABITS,

                            onSelect = { category = TaskCategory.HABITS }

                        ) { Text("عادات") }

                        TaskCategoryChip(

                            selected = category == TaskCategory.OTHER,

                            onSelect = { category = TaskCategory.OTHER }

                        ) { Text("أخرى") }

                    }

                }

                Spacer(Modifier.height(16.dp))

                Text("أولوية", style = MaterialTheme.typography.labelLarge)

                Spacer(Modifier.height(8.dp))

                Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {

                    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {

                        TaskPriorityChip(

                            selected = priority == TaskPriority.LOW,

                            onSelect = { priority = TaskPriority.LOW }

                        ) { Text("منخفضة") }

                        TaskPriorityChip(

                            selected = priority == TaskPriority.MEDIUM,

                            onSelect = { priority = TaskPriority.MEDIUM }

                        ) { Text("متوسطة") }

                        TaskPriorityChip(

                            selected = priority == TaskPriority.HIGH,

                            onSelect = { priority = TaskPriority.HIGH }

                        ) { Text("عالية") }

                        TaskPriorityChip(

                            selected = priority == TaskPriority.URGENT,

                            onSelect = { priority = TaskPriority.URGENT }

                        ) { Text("عاجلة") }

                    }

                }

                Spacer(Modifier.height(16.dp))

                Text("تاريخ الاستحقاق (اختياري)", style = MaterialTheme.typography.labelLarge)

                Spacer(Modifier.height(8.dp))

                OutlinedTextField(

                    value = dueAtString,

                    onValueChange = { dueAtString = it },

                    singleLine = true,

                    label = { Text("تاريخ (yyyy-mm-dd)") },

                    shape = RoundedCornerShape(16.dp),

                    modifier = Modifier.fillMaxWidth()

                )

                Spacer(Modifier.height(16.dp))

                Text("ملاحظات (اختياري)", style = MaterialTheme.typography.labelLarge)

                Spacer(Modifier.height(8.dp))

                OutlinedTextField(

                    value = notes,

                    onValueChange = { notes = it },

                    singleLine = true,

                    label = { Text("ملاحظات") },

                    shape = RoundedCornerShape(16.dp),

                    modifier = Modifier.fillMaxWidth()

                )

                Spacer(Modifier.height(16.dp))

                Text("التكرار", style = MaterialTheme.typography.labelLarge)

                Spacer(Modifier.height(8.dp))

                Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {

                    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {

                        RecurrenceChip(

                            selected = recurrence == Recurrence.NONE,

                            onSelect = { recurrence = Recurrence.NONE }

                        ) { Text("بدون تكرار") }

                        RecurrenceChip(

                            selected = recurrence == Recurrence.DAILY,

                            onSelect = { recurrence = Recurrence.DAILY }

                        ) { Text("يوميًا") }

                        RecurrenceChip(

                            selected = recurrence == Recurrence.WEEKLY,

                            onSelect = { recurrence = Recurrence.WEEKLY }

                        ) { Text("أسبوعيًا") }

                        RecurrenceChip(

                            selected = recurrence == Recurrence.MONTHLY,

                            onSelect = { recurrence = Recurrence.MONTHLY }

                        ) { Text("شهريًا") }

                    }

                }

            }

        },

        confirmButton = {

            TextButton(

                onClick = { if (title.isNotBlank()) onAdd(title.trim(), minutesInput.toIntOrNull() ?: 30, category, priority, dueAtString, recurrence, notes, subjectId) },

                enabled = title.isNotBlank() && minutesInput.toIntOrNull() in 1..720

            ) { Text("إضافة") }

        },

        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }

    )

}

@Composable

private fun SubjectChooser(subjects: List<StudySubject>, selectedId: Long?, onSelect: (Long?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = subjects.firstOrNull { it.id == selectedId }?.name ?: "بدون مادة"
    Box(Modifier.fillMaxWidth()) {
        TextButton(onClick = { expanded = true }) {
            Text("المادة المرتبطة: $selectedName", color = FocusColors.TextSecondary)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("بدون مادة") }, onClick = { onSelect(null); expanded = false })
            subjects.forEach { subject ->
                DropdownMenuItem(text = { Text(subject.name) }, onClick = { onSelect(subject.id); expanded = false })
            }
            if (subjects.isEmpty()) {
                DropdownMenuItem(text = { Text("أضف مادة من قسم موادي أولًا", color = FocusColors.TextSecondary) }, onClick = { expanded = false })
            }
        }
    }
}

@Composable
private fun TaskCategoryChip(selected: Boolean, onSelect: () -> Unit, content: @Composable () -> Unit) {

    Box(

        modifier = Modifier

            .clip(RoundedCornerShape(12.dp))

            .background(if (selected) FocusColors.Blue else FocusColors.SurfaceHigh)

            .clickable { onSelect() }

            .padding(horizontal = 8.dp, vertical = 4.dp)

    ) {

        CompositionLocalProvider(LocalContentColor provides if (selected) Color.White else FocusColors.TextSecondary) {
            ProvideTextStyle(MaterialTheme.typography.labelLarge, content)
        }

    }

}

@Composable

private fun TaskPriorityChip(selected: Boolean, onSelect: () -> Unit, content: @Composable () -> Unit) {

    Box(

        modifier = Modifier

            .clip(RoundedCornerShape(12.dp))

            .background(if (selected) FocusColors.Blue else FocusColors.SurfaceHigh)

            .clickable { onSelect() }

            .padding(horizontal = 8.dp, vertical = 4.dp)

    ) {

        CompositionLocalProvider(LocalContentColor provides if (selected) Color.White else FocusColors.TextSecondary) {
            ProvideTextStyle(MaterialTheme.typography.labelLarge, content)
        }

    }

}

@Composable

private fun RecurrenceChip(selected: Boolean, onSelect: () -> Unit, content: @Composable () -> Unit) {

    Box(

        modifier = Modifier

            .clip(RoundedCornerShape(12.dp))

            .background(if (selected) FocusColors.Blue else FocusColors.SurfaceHigh)

            .clickable { onSelect() }

            .padding(horizontal = 8.dp, vertical = 4.dp)

    ) {

        CompositionLocalProvider(LocalContentColor provides if (selected) Color.White else FocusColors.TextSecondary) {
            ProvideTextStyle(MaterialTheme.typography.labelLarge, content)
        }

    }

}

@Composable
private fun BlockedAppsScreen(taskId: Long, onDone: () -> Unit) {
    val context = LocalContext.current
    var apps by remember { mutableStateOf(emptyList<InstalledApp>()) }
    var selected by remember(taskId) { mutableStateOf(emptySet<String>()) }
    var query by rememberSaveable(taskId) { mutableStateOf("") }
    var taskTitle by remember { mutableStateOf("") }

    LaunchedEffect(taskId) {
        val (loadedApps, task) = withContext(Dispatchers.IO) {
            val currentTask = StudyDb.get(context).task(taskId)
            loadLaunchableApps(context) to currentTask
        }
        apps = loadedApps
        selected = task?.blockedPackages.orEmpty()
        taskTitle = task?.title.orEmpty()
    }

    val filteredApps = remember(apps, query) {
        val term = query.trim()
        if (term.isEmpty()) apps else apps.filter { app ->
            app.label.contains(term, ignoreCase = true) || app.packageName.contains(term, ignoreCase = true)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onDone) { Text("رجوع") }
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("التطبيقات المشتتة", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = FocusColors.TextPrimary)
                if (taskTitle.isNotBlank()) {
                    Text(taskTitle, style = MaterialTheme.typography.bodySmall, color = FocusColors.TextSecondary)
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            label = { Text("ابحث عن تطبيق") },
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("المحدد ${selected.size}", style = MaterialTheme.typography.labelMedium, color = FocusColors.TextSecondary)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { selected = selected + filteredApps.map { it.packageName } }) {
                Text("اختيار النتائج")
            }
            TextButton(onClick = { selected = emptySet() }) {
                Text("مسح", color = FocusColors.TextSecondary)
            }
        }
        if (selected.isNotEmpty()) {
            SelectedAppsStrip(
                selectedApps = apps.filter { it.packageName in selected },
                onRemove = { packageName -> selected = selected - packageName }
            )
        }
        LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
            items(filteredApps, key = { it.packageName }) { app ->
                val checked = app.packageName in selected
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (checked) FocusColors.SurfaceHigh else FocusColors.Surface)
                        .clickable {
                            selected = if (checked) selected - app.packageName else selected + app.packageName
                        }
                        .padding(horizontal = 6.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = checked,
                        onCheckedChange = { value ->
                            selected = if (value) selected + app.packageName else selected - app.packageName
                        },
                        colors = CheckboxDefaults.colors(checkedColor = FocusColors.Blue)
                    )
                    Spacer(Modifier.width(8.dp))
                    InstalledAppIcon(app, Modifier.size(40.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(app.label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, color = FocusColors.TextPrimary)
                        Text(app.packageName, style = MaterialTheme.typography.labelSmall, color = FocusColors.TextSecondary, maxLines = 1)
                    }
                }
            }
            if (filteredApps.isEmpty()) {
                item {
                    Text(
                        if (apps.isEmpty()) "جارٍ تحميل التطبيقات أو لا توجد تطبيقات قابلة للفتح." else "لا توجد تطبيقات تطابق البحث.",
                        modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp),
                        color = FocusColors.TextSecondary
                    )
                }
            }
        }
        Button(
            onClick = {
                SessionEngine.updateTaskBlocked(taskId, selected)
                onDone()
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text("حفظ التطبيقات (${selected.size})")
        }
    }
}

@Composable
private fun InstalledAppIcon(app: InstalledApp, modifier: Modifier = Modifier.size(40.dp)) {
    val bitmap = remember(app.packageName, app.icon) {
        runCatching { app.icon?.toBitmap(96, 96)?.asImageBitmap() }.getOrNull()
    }
    Box(
        modifier = modifier.clip(CircleShape).background(FocusColors.SurfaceHigh),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) Image(bitmap, contentDescription = "أيقونة ${app.label}", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(5.dp))
        else Text(app.label.firstOrNull()?.uppercase().orEmpty(), color = FocusColors.TextPrimary, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SelectedAppsStrip(selectedApps: List<InstalledApp>, onRemove: (String) -> Unit) {
    if (selectedApps.isEmpty()) return
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        selectedApps.forEach { app ->
            Card(colors = CardDefaults.cardColors(containerColor = FocusColors.SurfaceHigh), shape = RoundedCornerShape(16.dp)) {
                Row(
                    modifier = Modifier.padding(start = 8.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    InstalledAppIcon(app, Modifier.size(30.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(app.label, maxLines = 1, style = MaterialTheme.typography.labelMedium, modifier = Modifier.widthIn(max = 110.dp), color = FocusColors.TextPrimary)
                    Box(
                        modifier = Modifier.size(30.dp).clip(CircleShape).clickable { onRemove(app.packageName) },
                        contentAlignment = Alignment.Center
                    ) { Text("×", color = FocusColors.TextSecondary, fontSize = 20.sp) }
                }
            }
        }
    }
}

@Composable
private fun RefreshOnResume(block: () -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val current = rememberUpdatedState(block)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) current.value()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}

private fun hasNotificationPermission(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED

private fun isVpnConsentGranted(context: Context): Boolean =
    runCatching { VpnService.prepare(context) == null }.getOrDefault(false)

private fun isIgnoringBatteryOptimizations(context: Context): Boolean {
    val power = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
    return power.isIgnoringBatteryOptimizations(context.packageName)
}

private fun openBatterySettings(context: Context) {
    val direct = Intent(
        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
        Uri.parse("package:${context.packageName}")
    )
    val opened = runCatching { context.startActivity(direct) }.isSuccess
    if (!opened) {
        runCatching { context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
    }
}

private fun openAccessibilitySettings(context: Context) {
    runCatching { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
}

private fun formatClock(seconds: Long): String {
    val safe = seconds.coerceAtLeast(0)
    return String.format(Locale.US, "%02d:%02d", safe / 60, safe % 60)
}


