package com.focusstudy.poc

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.ZonedDateTime

internal data class AppRelease(
    val packageName: String,
    val versionCode: Long,
    val versionName: String,
    val storeUrl: String,
    val releasePageUrl: String,
    val releaseNotes: String
)

internal sealed interface UpdateCheckResult {
    data object NotConfigured : UpdateCheckResult
    data object UpToDate : UpdateCheckResult
    data class Available(val release: AppRelease) : UpdateCheckResult
    data class Failed(val message: String) : UpdateCheckResult
}

internal object AppUpdateManager {
    const val EXTRA_OPEN_UPDATES = "com.focusstudy.poc.OPEN_UPDATES"
    private const val ACTION_CHECK = "com.focusstudy.poc.CHECK_UPDATES"
    private const val FEED_PREFS = "focus_study_update_state"
    private const val CHANNEL_ID = "app_updates"
    private const val NOTIFICATION_ID = 8201
    private const val REQUEST_CODE = 8202
    private const val CHECK_INTERVAL = 24 * 60 * 60 * 1000L
    private const val CHECK_THROTTLE = 6 * 60 * 60 * 1000L
    private const val MAX_FEED_BYTES = 64 * 1024

    fun configured(): Boolean = BuildConfig.UPDATE_FEED_URL.isNotBlank()

    suspend fun check(context: Context, manual: Boolean = false): UpdateCheckResult = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        if (!configured()) return@withContext UpdateCheckResult.NotConfigured

        val preferences = app.getSharedPreferences(FEED_PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (!manual && now - preferences.getLong("last_check_at", 0L) < CHECK_THROTTLE) {
            val cached = readCachedResult(app)
            if (cached is UpdateCheckResult.Available) notifyIfNeeded(app, cached.release)
            return@withContext cached
        }

        try {
            val feedUrl = URL(BuildConfig.UPDATE_FEED_URL)
            require(feedUrl.protocol.equals("https", ignoreCase = true)) { "مصدر التحديثات يجب أن يستخدم اتصالًا آمنًا (HTTPS)." }
            val connection = (feedUrl.openConnection() as HttpURLConnection).apply {
                connectTimeout = 8_000
                readTimeout = 8_000
                requestMethod = "GET"
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Cache-Control", "no-cache")
            }
            val jsonText = try {
                val code = connection.responseCode
                if (code !in 200..299) error("تعذر الوصول إلى مصدر التحديثات (HTTP $code).")
                if (connection.contentLengthLong > MAX_FEED_BYTES) error("ملف تحديثات أكبر من الحجم المسموح.")
                connection.inputStream.use { input ->
                    val output = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(4096)
                    var total = 0
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        if (total > MAX_FEED_BYTES) error("ملف تحديثات أكبر من الحجم المسموح.")
                        output.write(buffer, 0, count)
                    }
                    output.toString(Charsets.UTF_8.name())
                }
            } finally {
                connection.disconnect()
            }

            val json = JSONObject(jsonText)
            val packageName = json.getString("packageName")
            require(packageName == app.packageName) { "ملف التحديث لا يخص هذا التطبيق." }
            val versionCode = json.getLong("versionCode")
            require(versionCode > 0) { "رقم إصدار غير صالح." }
            val releasePage = requireHttps(json.getString("releasePageUrl"), "صفحة الإصدار")
            val storeUrl = json.optString("storeUrl").trim().takeIf { it.isNotEmpty() }
                ?.let { requireHttps(it, "رابط المتجر") }.orEmpty()
            val release = AppRelease(
                packageName = packageName,
                versionCode = versionCode,
                versionName = json.optString("versionName", versionCode.toString()),
                storeUrl = storeUrl,
                releasePageUrl = releasePage,
                releaseNotes = json.optString("releaseNotes", "تحسينات وإصلاحات جديدة.")
            )

            preferences.edit()
                .putLong("last_check_at", now)
                .putString("latest_release", jsonText)
                .apply()
            val currentVersion = currentVersionCode(app)
            if (release.versionCode > currentVersion) {
                notifyIfNeeded(app, release)
                UpdateCheckResult.Available(release)
            } else {
                UpdateCheckResult.UpToDate
            }
        } catch (exception: Exception) {
            UpdateCheckResult.Failed(exception.message?.take(160) ?: "تعذر فحص التحديثات الآن.")
        }
    }

    private fun readCachedResult(context: Context): UpdateCheckResult {
        val raw = context.getSharedPreferences(FEED_PREFS, Context.MODE_PRIVATE)
            .getString("latest_release", null) ?: return UpdateCheckResult.Failed("تعذر قراءة نتيجة الفحص السابق.")
        return runCatching {
            val json = JSONObject(raw)
            val release = AppRelease(
                packageName = json.getString("packageName"),
                versionCode = json.getLong("versionCode"),
                versionName = json.optString("versionName", ""),
                storeUrl = json.optString("storeUrl").trim().takeIf { it.isNotEmpty() }
                    ?.let { requireHttps(it, "رابط المتجر") }.orEmpty(),
                releasePageUrl = requireHttps(json.getString("releasePageUrl"), "صفحة الإصدار"),
                releaseNotes = json.optString("releaseNotes", "تحسينات وإصلاحات جديدة.")
            )
            if (release.versionCode > currentVersionCode(context)) UpdateCheckResult.Available(release)
            else UpdateCheckResult.UpToDate
        }.getOrElse { UpdateCheckResult.Failed("تعذر قراءة بيانات الإصدار المحفوظة.") }
    }

    private fun requireHttps(value: String, label: String): String {
        val uri = Uri.parse(value)
        require(uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank()) { "$label يجب أن يكون رابط HTTPS صالحًا." }
        return value
    }

    private fun currentVersionCode(context: Context): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0).versionCode.toLong()
        }

    private fun notifyIfNeeded(context: Context, release: AppRelease) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val prefs = context.getSharedPreferences(FEED_PREFS, Context.MODE_PRIVATE)
        if (prefs.getLong("last_notified_version", 0L) >= release.versionCode) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (!manager.areNotificationsEnabled()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "تحديثات التطبيق", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "تنبيه عند توفر إصدار جديد من Focus Study"
                }
            )
            if (manager.getNotificationChannel(CHANNEL_ID)?.importance == NotificationManager.IMPORTANCE_NONE) return
        }
        val openUpdates = PendingIntent.getActivity(
            context,
            REQUEST_CODE,
            Intent(context, MainActivity::class.java)
                .putExtra(EXTRA_OPEN_UPDATES, true)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val message = "الإصدار ${release.versionName} يضيف تحسينات جديدة. افتح التطبيق للتحديث من المتجر."
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_focus)
            .setContentTitle("تحديث جديد لـ Focus Study")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(openUpdates)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
        prefs.edit().putLong("last_notified_version", release.versionCode).apply()
    }

    fun schedule(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pending = checkPendingIntent(context)
        if (!configured()) {
            alarmManager.cancel(pending)
            return
        }
        val triggerAt = ZonedDateTime.now().plusMinutes(10).toInstant().toEpochMilli()
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
    }

    private fun checkPendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        Intent(context, AppUpdateReceiver::class.java).setAction(ACTION_CHECK),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    suspend fun dispatchScheduledCheck(context: Context) {
        check(context, manual = false)
        val pending = checkPendingIntent(context)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            System.currentTimeMillis() + CHECK_INTERVAL,
            pending
        )
    }
}

class AppUpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val result = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                AppUpdateManager.dispatchScheduledCheck(context.applicationContext)
            } finally {
                result.finish()
            }
        }
    }
}
