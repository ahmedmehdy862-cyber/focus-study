package com.focusstudy.poc

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.util.Log

/**
 * Optional Device Owner support.
 *
 * When the app is provisioned as Device Owner, the selected apps can be suspended at the OS level
 * with [DevicePolicyManager.setPackagesSuspended]: their launcher icons are disabled (greyed out),
 * they disappear from Recents, they cannot start activities, show notifications, play audio or
 * vibrate, and tapping them shows the system "app suspended" dialog. Suspension is lifted when the
 * session ends.
 *
 * Without Device Owner the app still works exactly as before: the accessibility overlay hides the
 * app and the VPN drops its traffic. Nothing here is required for a normal install.
 */
object DeviceOwnerController {

    private const val TAG = "FocusOwner"
    private const val PREFS = "focus_device_owner"
    private const val KEY_SUSPENDED = "suspended_packages"

    fun isDeviceOwner(context: Context): Boolean {
        val manager = context.getSystemService(DevicePolicyManager::class.java) ?: return false
        return runCatching { manager.isDeviceOwnerApp(context.packageName) }.getOrDefault(false)
    }

    /** Human readable provisioning command, shown in the in-app help dialog. */
    fun provisioningCommand(context: Context): String =
        "adb shell dpm set-device-owner ${context.packageName}/.FocusDeviceAdminReceiver"

    /**
     * Suspends [packages] and returns how many were actually suspended. Packages the system refuses
     * (system apps, the launcher, packages with an active admin) are reported by the platform and
     * are simply skipped; the accessibility layer still covers them.
     */
    fun suspend(context: Context, packages: Set<String>): Int {
        if (packages.isEmpty() || !isDeviceOwner(context)) return 0
        val manager = context.getSystemService(DevicePolicyManager::class.java) ?: return 0
        val targets = packages.filter { it != context.packageName }.toSet()
        if (targets.isEmpty()) return 0

        return runCatching {
            val failed = manager
                .setPackagesSuspended(admin(context), targets.toTypedArray(), true)
                ?.toSet()
                .orEmpty()
            val done = targets - failed
            if (failed.isNotEmpty()) Log.w(TAG, "could not suspend: $failed")
            remember(context, done)
            done.size
        }.onFailure { Log.w(TAG, "setPackagesSuspended(true) failed", it) }.getOrDefault(0)
    }

    /** Lifts every suspension this app applied. Safe to call when nothing is suspended. */
    fun unsuspendAll(context: Context) {
        val stored = stored(context)
        if (stored.isEmpty()) return
        val manager = context.getSystemService(DevicePolicyManager::class.java)
        if (manager != null && isDeviceOwner(context)) {
            runCatching {
                manager.setPackagesSuspended(admin(context), stored.toTypedArray(), false)
            }.onFailure { Log.w(TAG, "setPackagesSuspended(false) failed", it) }
        }
        forget(context)
    }

    fun hasSuspended(context: Context): Boolean = stored(context).isNotEmpty()

    private fun admin(context: Context): ComponentName =
        ComponentName(context, FocusDeviceAdminReceiver::class.java)

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun stored(context: Context): Set<String> =
        prefs(context).getStringSet(KEY_SUSPENDED, emptySet()).orEmpty().toSet()

    private fun remember(context: Context, packages: Set<String>) {
        if (packages.isEmpty()) return
        prefs(context).edit()
            .putStringSet(KEY_SUSPENDED, stored(context) + packages)
            .apply()
    }

    private fun forget(context: Context) {
        prefs(context).edit().remove(KEY_SUSPENDED).apply()
    }
}
