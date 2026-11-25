package com.skarm.launcher

import android.content.Context

/**
 * The graphics settings next to the resolution slider (which lives in the touch layout): the FPS
 * counter, sharpening and Sharp HUD. The pure helpers decide what the game is told, so the game
 * surface and the input mapping agree with the native pass (cpp/skgfx.c: keep the formulas in step).
 */
object GfxSettings {
    private const val PREFS = "launcher_prefs"
    private const val KEY_FPS = "gfx_show_fps"
    private const val KEY_SHARPEN = "gfx_sharpen_percent"
    private const val KEY_SHARP_HUD = "gfx_sharp_hud"

    const val MAX_SHARPEN = 100

    /** At or above this the scene already fills the window, so Sharp HUD has nothing to separate. */
    private const val FULL_SCALE = 0.999f

    fun showFps(context: Context): Boolean = prefs(context).getBoolean(KEY_FPS, true)

    fun setShowFps(context: Context, show: Boolean) = prefs(context).edit().putBoolean(KEY_FPS, show).apply()

    fun sharpenPercent(context: Context): Int = prefs(context).getInt(KEY_SHARPEN, 0).coerceIn(0, MAX_SHARPEN)

    fun setSharpenPercent(context: Context, percent: Int) =
        prefs(context).edit().putInt(KEY_SHARPEN, percent.coerceIn(0, MAX_SHARPEN)).apply()

    fun sharpHud(context: Context): Boolean = prefs(context).getBoolean(KEY_SHARP_HUD, false)

    fun setSharpHud(context: Context, on: Boolean) = prefs(context).edit().putBoolean(KEY_SHARP_HUD, on).apply()

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

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
