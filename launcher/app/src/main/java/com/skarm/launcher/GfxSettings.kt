package com.skarm.launcher

import android.content.Context
import java.io.File
import java.util.Properties

/**
 * The graphics settings next to the resolution slider (which lives in the touch layout): the FPS
 * counter, sharpening and Sharp HUD. The pure helpers decide what the game is told, so the game
 * surface and the input mapping agree with the native pass (cpp/skgfx.c: keep the formulas in step).
 *
 * They are set from the launcher's sidebar (main process) and from the in-game panel (`:game`).
 * SharedPreferences is cached per process, so one side would overwrite the other's change with a
 * stale copy; a small file read fresh on every call and replaced atomically has no such cache.
 */
object GfxSettings {
    private const val FILE_NAME = "gfx_settings.properties"
    private const val KEY_FPS = "fps"
    private const val KEY_SHARPEN = "sharpen"
    private const val KEY_SHARP_HUD = "hud"

    const val MAX_SHARPEN = 100

    /** At or above this the scene already fills the window, so Sharp HUD has nothing to separate. */
    private const val FULL_SCALE = 0.999f

    fun showFps(context: Context): Boolean = read(context).getProperty(KEY_FPS)?.toBooleanStrictOrNull() ?: true

    fun setShowFps(context: Context, show: Boolean) = write(context, KEY_FPS, show.toString())

    fun sharpenPercent(context: Context): Int =
        (read(context).getProperty(KEY_SHARPEN)?.toIntOrNull() ?: 0).coerceIn(0, MAX_SHARPEN)

    fun setSharpenPercent(context: Context, percent: Int) =
        write(context, KEY_SHARPEN, percent.coerceIn(0, MAX_SHARPEN).toString())

    fun sharpHud(context: Context): Boolean = read(context).getProperty(KEY_SHARP_HUD)?.toBooleanStrictOrNull() ?: false

    fun setSharpHud(context: Context, on: Boolean) = write(context, KEY_SHARP_HUD, on.toString())

    /** Sharp HUD only changes anything while the scene is smaller than the window. */
    fun sharpHudApplies(scale: Float): Boolean = scale < FULL_SCALE

    /**
     * Whether the game draws offscreen (sharpening or Sharp HUD asked for) instead of into a surface
     * the compositor upscales.
     */
    fun usesOffscreen(scale: Float, sharpenPercent: Int, sharpHud: Boolean): Boolean =
        sharpenPercent > 0 || (sharpHud && sharpHudApplies(scale))

    /**
     * The size the game should believe its window is, for a native window of [width] x [height] and
     * the offscreen path on. Sharp HUD lays the game out at the full size; otherwise it is the scene's.
     */
    fun gameSize(width: Int, height: Int, scale: Float, sharpHud: Boolean): Pair<Int, Int> =
        if (sharpHud && sharpHudApplies(scale)) Pair(width, height) else Pair(scaled(width, scale), scaled(height, scale))

    /** As the native pass rounds it: per mille, half up, at least 1. */
    fun scaled(size: Int, scale: Float): Int {
        val perMille = (scale.coerceIn(0.25f, 1f) * 1000f + 0.5f).toInt()
        return ((size.toLong() * perMille + 500) / 1000).toInt().coerceAtLeast(1)
    }

    private fun file(context: Context) = File(context.applicationContext.filesDir, FILE_NAME)

    private fun read(context: Context): Properties = Properties().apply {
        runCatching { file(context).inputStream().use(::load) }
    }

    private fun write(context: Context, key: String, value: String) {
        val properties = read(context).apply { setProperty(key, value) }
        val target = file(context)
        val tmp = File(target.parentFile, "$FILE_NAME.tmp")
        runCatching {
            tmp.outputStream().use { properties.store(it, null) }
            if (!tmp.renameTo(target)) tmp.delete()
        }
    }
}
