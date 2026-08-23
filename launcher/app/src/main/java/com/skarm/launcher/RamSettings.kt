package com.skarm.launcher

import android.app.ActivityManager
import android.content.Context

/**
 * The Java heap cap, shared by the launcher's setting UI and the JVM launch.
 *
 * SK's live set peaks around 300MB, so the JVM's own ergonomics (a fraction of total
 * device RAM) commit far more than it ever needs. KnightLauncher ships a fixed 512M for
 * this game; the default here leaves headroom above that without inflating the footprint.
 */
object RamSettings {
    const val KEY = "max_ram_mb"
    const val MIN_MB = 512
    const val STEP_MB = 128
    const val DEFAULT_MB = 768

    /** Upper bound: half of physical RAM, so the heap can't crowd out the JIT mapping,
     *  textures and the rest of the process into an OOM kill. */
    fun maxMb(context: Context): Int {
        val info = ActivityManager.MemoryInfo()
        activityManager(context).getMemoryInfo(info)
        val half = (info.totalMem / (1024 * 1024) / 2).toInt()
        return (half / STEP_MB * STEP_MB).coerceIn(MIN_MB, 4096)
    }

    fun totalMb(context: Context): Int {
        val info = ActivityManager.MemoryInfo()
        activityManager(context).getMemoryInfo(info)
        return (info.totalMem / (1024 * 1024)).toInt()
    }

    fun availableMb(context: Context): Int {
        val info = ActivityManager.MemoryInfo()
        activityManager(context).getMemoryInfo(info)
        return (info.availMem / (1024 * 1024)).toInt()
    }

    fun get(context: Context): Int {
        val prefs = context.getSharedPreferences("launcher_prefs", Context.MODE_PRIVATE)
        return prefs.getInt(KEY, DEFAULT_MB).coerceIn(MIN_MB, maxMb(context))
    }

    fun set(context: Context, mb: Int) {
        context.getSharedPreferences("launcher_prefs", Context.MODE_PRIVATE)
            .edit().putInt(KEY, mb.coerceIn(MIN_MB, maxMb(context))).apply()
    }

    private fun activityManager(context: Context) =
        context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
}
