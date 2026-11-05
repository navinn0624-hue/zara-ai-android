package com.zara.assistant.engine

import android.content.Context
import android.content.Intent

object AppLauncherHelper {

    fun launch(context: Context, appQuery: String): Boolean {
        val pm = context.packageManager
        val clean = appQuery.lowercase().trim()

        // 1. Direct package map for instant launch
        val directMap = mapOf(
            "whatsapp" to "com.whatsapp",
            "insta" to "com.instagram.android",
            "instagram" to "com.instagram.android",
            "youtube" to "com.google.android.youtube",
            "camera" to "com.google.android.GoogleCamera",
            "spotify" to "com.spotify.music",
            "settings" to "com.android.settings",
            "calculator" to "com.google.android.calculator",
            "chrome" to "com.android.chrome",
            "gallery" to "com.android.gallery3d",
            "photos" to "com.google.android.apps.photos",
            "swiggy" to "in.swiggy.android",
            "zomato" to "com.application.zomato",
            "paytm" to "net.one97.paytm",
            "gpay" to "com.google.android.apps.nbu.paisa.user",
            "phonepe" to "com.phonepe.app",
            "free fire" to "com.dts.freefireth"
        )

        for ((key, pkg) in directMap) {
            if (clean.contains(key) || key.contains(clean)) {
                val intent = pm.getLaunchIntentForPackage(pkg)
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    return true
                }
            }
        }

        // 2. Scan all installed launcher apps
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val appList = pm.queryIntentActivities(mainIntent, 0)
        for (resolveInfo in appList) {
            val label = resolveInfo.loadLabel(pm).toString().lowercase()
            if (label.contains(clean) || clean.contains(label)) {
                val launchIntent = pm.getLaunchIntentForPackage(resolveInfo.activityInfo.packageName)
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launchIntent)
                    return true
                }
            }
        }

        return false
    }
}
