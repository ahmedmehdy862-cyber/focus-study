package com.focusstudy.poc

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable

data class InstalledApp(
    val packageName: String,
    val label: String,
    val icon: Drawable? = null
)

/**
 * Lists apps that have a launcher entry. This works through the <queries> element in the
 * manifest, so the restricted QUERY_ALL_PACKAGES permission is not needed.
 */
fun loadLaunchableApps(context: Context): List<InstalledApp> {
    val pm: PackageManager = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    val resolved = pm.queryIntentActivities(intent, 0)
    val seen = HashSet<String>()
    val result = ArrayList<InstalledApp>()

    for (info in resolved) {
        val packageName = info.activityInfo?.packageName ?: continue
        if (packageName == context.packageName) continue
        if (!seen.add(packageName)) continue
        val label = info.loadLabel(pm)?.toString()?.trim().orEmpty()
        result.add(
            InstalledApp(
                packageName = packageName,
                label = label.ifBlank { packageName },
                icon = runCatching { info.loadIcon(pm) }.getOrNull()
            )
        )
    }

    return result.sortedBy { it.label.lowercase() }
}
