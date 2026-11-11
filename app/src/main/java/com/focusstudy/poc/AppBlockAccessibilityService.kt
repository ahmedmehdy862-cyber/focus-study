package com.focusstudy.poc

import android.accessibilityservice.AccessibilityService
import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Locale

/**
 * Prevents the selected apps from being used while a focus session is running.
 *
 * Why an accessibility service: a normal app without root or Device Owner cannot stop another app
 * from launching. The supported approach is to watch foreground window changes, hide the blocked
 * app behind an opaque TYPE_ACCESSIBILITY_OVERLAY (which needs no SYSTEM_ALERT_WINDOW), send the
 * user home and drop the app's background process so it does not stay warm behind the launcher.
 *
 * Privacy: only package names of the foreground window are read. No screen content, text,
 * messages, passwords or keystrokes are collected, and nothing leaves the device.
 */
class AppBlockAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var overlay: View? = null
    private var overlayHost: WindowManager? = null
    private var taskView: TextView? = null
    private var timeView: TextView? = null

    @Volatile
    private var lastForegroundPackage: String? = null

    /** Runs while the session is active: keeps the blocked app covered and sends it home. */
    private val watchdog = object : Runnable {
        override fun run() {
            val session = SessionEngine.active.value
            if (session == null) {
                hideOverlay()
                return
            }
            updateOverlayText()
            val foreground = lastForegroundPackage
            if (foreground == packageName) {
                hideOverlay()
            } else if (BlockGate.isBlocked(foreground)) {
                if (overlay == null) showOverlay()
                performGlobalAction(GLOBAL_ACTION_HOME)
            }
            if (overlay != null) handler.postDelayed(this, 900L)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOWS_CHANGED
        ) return

        val packageName = event.packageName?.toString() ?: return
        lastForegroundPackage = packageName

        if (packageName == this.packageName) {
            hideOverlay()
            return
        }
        if (BlockGate.isBlocked(packageName)) {
            showOverlay()
            performGlobalAction(GLOBAL_ACTION_HOME)
            dropApp(packageName)
        }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        hideOverlay()
        if (instance === this) instance = null
        super.onDestroy()
    }

    /** Drops the blocked app's cached process so it cannot stay alive in the background. */
    private fun dropApp(packageName: String) {
        runCatching {
            getSystemService(ActivityManager::class.java)?.killBackgroundProcesses(packageName)
        }
    }

    fun hideOverlay() {
        val view = overlay ?: return
        handler.removeCallbacks(watchdog)
        runCatching { overlayHost?.removeView(view) }
        overlay = null
        overlayHost = null
        taskView = null
        timeView = null
    }

    private fun showOverlay() {
        if (overlay != null) {
            updateOverlayText()
            return
        }
        val manager = getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.OPAQUE
        ).apply { gravity = Gravity.CENTER }

        val view = buildOverlay()
        val added = runCatching { manager.addView(view, params) }.isSuccess
        if (!added) return

        overlay = view
        overlayHost = manager
        updateOverlayText()
        handler.postDelayed(watchdog, 900L)
    }

    private fun buildOverlay(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            // Fully opaque: the blocked app is hidden, not just dimmed.
            setBackgroundColor(0xFF070B16.toInt())
            setPadding(56, 56, 56, 56)
        }

        val title = TextView(this).apply {
            text = "هذا التطبيق مقفل"
            setTextColor(0xFFF6F9FF.toInt())
            textSize = 26f
            gravity = Gravity.CENTER
        }
        val explanation = TextView(this).apply {
            text = "أنت داخل جلسة تركيز. لا يمكن فتح التطبيقات التي اخترتها حتى تنتهي الجلسة."
            setTextColor(0xFFC3D0EA.toInt())
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(0, 20, 0, 0)
        }
        taskView = TextView(this).apply {
            setTextColor(0xFFC3D0EA.toInt())
            textSize = 17f
            gravity = Gravity.CENTER
            setPadding(0, 28, 0, 0)
        }
        timeView = TextView(this).apply {
            setTextColor(0xFF7CB2FF.toInt())
            textSize = 44f
            gravity = Gravity.CENTER
            setPadding(0, 16, 0, 36)
        }

        val backToFocus = Button(this).apply {
            text = "العودة إلى التركيز"
            setOnClickListener {
                val intent = Intent(this@AppBlockAccessibilityService, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                runCatching { startActivity(intent) }
                hideOverlay()
            }
        }

        root.addView(title, linearParams())
        root.addView(explanation, linearParams())
        root.addView(taskView, linearParams())
        root.addView(timeView, linearParams())
        root.addView(backToFocus, linearParams())
        return root
    }

    private fun linearParams(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )

    private fun updateOverlayText() {
        val session = SessionEngine.active.value
        if (session == null) {
            hideOverlay()
            return
        }
        taskView?.text = session.taskTitle
        val left = (session.endsAt - System.currentTimeMillis()) / 1000L
        timeView?.text = String.format(
            Locale.US,
            "%02d:%02d",
            (left / 60).coerceAtLeast(0),
            (left % 60).coerceAtLeast(0)
        )
    }

    companion object {
        @Volatile
        private var instance: AppBlockAccessibilityService? = null

        fun hideOverlayIfShown() {
            instance?.hideOverlay()
        }

        fun isEnabled(context: Context): Boolean {
            val expected = ComponentName(context, AppBlockAccessibilityService::class.java)
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            val splitter = TextUtils.SimpleStringSplitter(':')
            splitter.setString(enabled)
            while (splitter.hasNext()) {
                val component = ComponentName.unflattenFromString(splitter.next())
                if (component == expected) return true
            }
            return false
        }
    }
}
