package com.focusstudy.poc

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.batoulapps.adhan.CalculationMethod
import com.batoulapps.adhan.Coordinates
import com.batoulapps.adhan.PrayerTimes
import com.batoulapps.adhan.data.DateComponents
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

internal data class PrayerMoment(val name: String, val atMillis: Long)

internal object PrayerPrefs {
    private const val FILE = "prayer_preferences"
    private fun prefs(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    fun latitude(context: Context) = prefs(context).getFloat("latitude", Float.NaN).toDouble()
    fun longitude(context: Context) = prefs(context).getFloat("longitude", Float.NaN).toDouble()
    fun method(context: Context) = prefs(context).getString("method", "EGYPTIAN") ?: "EGYPTIAN"
    fun enabled(context: Context) = prefs(context).getBoolean("enabled", false)
    fun saveLocation(context: Context, lat: Double, lon: Double) {
        prefs(context).edit().putFloat("latitude", lat.toFloat()).putFloat("longitude", lon.toFloat()).apply()
    }
    fun saveMethod(context: Context, method: String) { prefs(context).edit().putString("method", method).apply() }
    fun setEnabled(context: Context, enabled: Boolean) { prefs(context).edit().putBoolean("enabled", enabled).apply() }
}

internal object PrayerCalculator {
    private fun parameters(method: String) = when (method) {
        "UMM_AL_QURA" -> CalculationMethod.UMM_AL_QURA.parameters
        "MUSLIM_WORLD_LEAGUE" -> CalculationMethod.MUSLIM_WORLD_LEAGUE.parameters
        "KARACHI" -> CalculationMethod.KARACHI.parameters
        else -> CalculationMethod.EGYPTIAN.parameters
    }

    fun today(context: Context, date: Date = Date()): List<PrayerMoment> {
        val lat = PrayerPrefs.latitude(context)
        val lon = PrayerPrefs.longitude(context)
        require(lat.isFinite() && lon.isFinite() && lat in -90.0..90.0 && lon in -180.0..180.0)
        val cal = java.util.Calendar.getInstance().apply { time = date }
        val components = DateComponents(cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.MONTH) + 1, cal.get(java.util.Calendar.DAY_OF_MONTH))
        val prayers = PrayerTimes(Coordinates(lat, lon), components, parameters(PrayerPrefs.method(context)))
        return listOf(
            PrayerMoment("الفجر", prayers.fajr.time), PrayerMoment("الظهر", prayers.dhuhr.time),
            PrayerMoment("العصر", prayers.asr.time), PrayerMoment("المغرب", prayers.maghrib.time),
            PrayerMoment("العشاء", prayers.isha.time)
        )
    }

    fun next(context: Context, now: Long = System.currentTimeMillis()): PrayerMoment? {
        val upcomingToday = today(context).firstOrNull { it.atMillis > now }
        if (upcomingToday != null) return upcomingToday
        val tomorrow = java.util.Calendar.getInstance().apply { timeInMillis = now; add(java.util.Calendar.DAY_OF_YEAR, 1) }.time
        return today(context, tomorrow).firstOrNull()
    }

    fun format(millis: Long): String = SimpleDateFormat("h:mm a", Locale("ar")).apply {
        timeZone = TimeZone.getDefault()
    }.format(Date(millis))
}

internal object PrayerReminderScheduler {
    private const val ACTION = "com.focusstudy.poc.PRAYER_REMINDER"
    private const val REQUEST = 6201
    private const val NOTIFICATION = 6202
    private const val CHANNEL = "prayer_times"

    fun schedule(context: Context) {
        val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val baseIntent = Intent(context, PrayerReminderReceiver::class.java).setAction(ACTION)
        val pending = PendingIntent.getBroadcast(context, REQUEST, baseIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        if (!PrayerPrefs.enabled(context)) { manager.cancel(pending); return }
        val next = runCatching { PrayerCalculator.next(context) }.getOrNull() ?: run { manager.cancel(pending); return }
        baseIntent.putExtra("prayer_name", next.name)
        val updatedPending = PendingIntent.getBroadcast(context, REQUEST, baseIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val canUseExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || runCatching { manager.canScheduleExactAlarms() }.getOrDefault(false)
        if (canUseExact) manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.atMillis, updatedPending)
        else manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.atMillis, updatedPending)
    }

    fun notifyPrayer(context: Context, prayerName: String?) {
        val name = prayerName?.takeIf { it.isNotBlank() } ?: "الصلاة"
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(NotificationChannel(CHANNEL, "تذكير الصلاة", NotificationManager.IMPORTANCE_DEFAULT).apply { description = "تذكير اختياري بمواقيت الصلاة المحسوبة محليًا" })
        val allowed = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (allowed) manager.notify(NOTIFICATION, NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_focus).setContentTitle("حان وقت صلاة $name")
            .setContentText("تذكير من Focus Study حسب موقعك وطريقة الحساب المختارة")
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT).build())
        schedule(context)
    }
}

class PrayerReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == "com.focusstudy.poc.PRAYER_REMINDER") PrayerReminderScheduler.notifyPrayer(context, intent.getStringExtra("prayer_name"))
    }
}

internal suspend fun currentApproximateLocation(context: Context): android.location.Location? = suspendCancellableCoroutine { continuation ->
    val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    if (Build.VERSION.SDK_INT < 30) {
        @Suppress("DEPRECATION")
        val listener = android.location.LocationListener { location -> if (continuation.isActive) continuation.resume(location) }
        try {
            @Suppress("DEPRECATION")
            manager.requestSingleUpdate(LocationManager.NETWORK_PROVIDER, listener, android.os.Looper.getMainLooper())
        } catch (_: Exception) { if (continuation.isActive) continuation.resume(null) }
        return@suspendCancellableCoroutine
    }
    val provider = runCatching { if (manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) LocationManager.NETWORK_PROVIDER else LocationManager.GPS_PROVIDER }.getOrNull()
    if (provider == null) { continuation.resume(null); return@suspendCancellableCoroutine }
    val cancellation = CancellationSignal()
    continuation.invokeOnCancellation { cancellation.cancel() }
    try {
        manager.getCurrentLocation(provider, cancellation, context.mainExecutor) { location -> if (continuation.isActive) continuation.resume(location) }
    } catch (_: SecurityException) { if (continuation.isActive) continuation.resume(null) }
}
