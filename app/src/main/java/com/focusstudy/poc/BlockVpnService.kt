package com.focusstudy.poc

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.FileInputStream
import java.io.IOException
import kotlin.concurrent.thread

/**
 * Network firewall for the focus session.
 *
 * The tunnel accepts only the apps the learner selected (addAllowedApplication). Their packets are
 * read from the TUN descriptor and discarded, so they have no network at all. Every other app is
 * not in the allowed list and therefore keeps the real network. allowBypass() is never called.
 *
 * The notification has no "stop" action: a focus session can only end at its own end time.
 */
class BlockVpnService : VpnService() {

    private var tunnel: ParcelFileDescriptor? = null

    @Volatile
    private var running = false

    private var worker: Thread? = null

    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "جلسة التركيز", NotificationManager.IMPORTANCE_LOW)
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startBlocking(
                intent.getStringArrayExtra(EXTRA_PACKAGES)?.toList().orEmpty()
            )
            ACTION_STOP -> stopBlocking()
            else -> if (!running) stopSelf()
        }
        return START_STICKY
    }

    /**
     * Revoking the VPN (system dialog or another VPN app) only drops network filtering. The focus
     * session is locked, so it keeps running; it is reported to the UI instead of being cancelled.
     */
    override fun onRevoke() {
        Log.i(TAG, "VPN permission revoked; session keeps running without network filtering")
        SessionEngine.onVpnLost()
        stopBlocking()
        super.onRevoke()
    }

    override fun onDestroy() {
        running = false
        isRunning = false
        closeTunnel()
        super.onDestroy()
    }

    private fun startBlocking(packages: List<String>) {
        if (running || packages.isEmpty()) {
            if (packages.isEmpty()) stopSelf()
            return
        }

        startForeground(NOTIFICATION_ID, buildNotification(SessionEngine.active.value?.taskTitle))

        val builder = Builder()
            .setSession("Focus Study")
            .setMtu(1500)
            .addAddress("10.111.222.1", 24)
            .addRoute("0.0.0.0", 0)
            .addAddress("fd00:1:fd00:1::1", 64)
            .addRoute("::", 0)
            .setBlocking(true)

        var added = 0
        for (packageName in packages) {
            try {
                builder.addAllowedApplication(packageName)
                added++
            } catch (e: PackageManager.NameNotFoundException) {
                Log.w(TAG, "package not installed: $packageName")
            }
        }

        if (added == 0) {
            Log.w(TAG, "no installed package to block, stopping")
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }

        val descriptor = try {
            builder.establish()
        } catch (e: Exception) {
            Log.w(TAG, "establish() threw", e)
            null
        }

        if (descriptor == null) {
            Log.w(TAG, "establish() returned null")
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }

        tunnel = descriptor
        running = true
        isRunning = true
        Log.i(TAG, "session started, blockedApps=$added")
        worker = thread(name = "vpn-drain", isDaemon = true) { drain(descriptor) }
    }

    /** Reads and discards everything the selected apps send. */
    private fun drain(descriptor: ParcelFileDescriptor) {
        val buffer = ByteArray(32_767)
        try {
            FileInputStream(descriptor.fileDescriptor).use { input ->
                while (running) {
                    if (input.read(buffer) < 0) break
                }
            }
        } catch (e: IOException) {
            // Expected when the tunnel is closed.
        }
    }

    private fun stopBlocking() {
        Log.i(TAG, "network filtering stopped")
        running = false
        isRunning = false
        closeTunnel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun closeTunnel() {
        try {
            tunnel?.close()
        } catch (e: IOException) {
            // Already closed.
        }
        tunnel = null
        worker?.interrupt()
        worker = null
    }

    private fun buildNotification(taskTitle: String?): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val subtitle = SessionEngine.active.value?.blockedPackages?.size ?: 0

        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(taskTitle?.let { "تركيز: $it" } ?: "جلسة تركيز قيد التشغيل")
            .setContentText("$subtitle تطبيقًا ممنوع من الفتح والإنترنت حتى نهاية المدة")
            .setSmallIcon(R.drawable.ic_stat_focus)
            .setOngoing(true)
            .setContentIntent(open)
            .build()
    }

    companion object {
        const val ACTION_START = "com.focusstudy.poc.action.START"
        const val ACTION_STOP = "com.focusstudy.poc.action.STOP"
        const val EXTRA_PACKAGES = "com.focusstudy.poc.extra.PACKAGES"

        private const val TAG = "FocusVpn"
        private const val CHANNEL_ID = "focus_session"
        private const val NOTIFICATION_ID = 42

        /** Read by the UI to warn when filtering silently stopped. */
        @Volatile
        var isRunning: Boolean = false
            private set

        fun startIntent(context: Context, packages: List<String>): Intent =
            Intent(context, BlockVpnService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_PACKAGES, packages.toTypedArray())

        fun stopIntent(context: Context): Intent =
            Intent(context, BlockVpnService::class.java).setAction(ACTION_STOP)
    }
}
