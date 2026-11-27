package com.skarm.launcher.touch

import android.graphics.Path
import androidx.core.graphics.PathParser

/**
 * Icons for the combat buttons, drawn as vector glyphs on a 24x24 grid instead of text.
 * The path data and colours are identical to the iOS port (SKGlyphForNode in SKTouchControlOverlay.m): keep the two in step.
 *
 * Colours come from the game's own HUD icons: attack red, buff-ring cyan for Dodge, the stun
 * star's gold for Bash. Defend is a deeper blue so it reads apart from Dodge.
 */
internal object ControlGlyphs {
    class Glyph(val pathData: String, val color: Int) {
        /** Parsed once; the button only scales it. */
        val path: Path by lazy { PathParser.createPathFromPathData(pathData) }
    }

    const val GRID = 24f

    const val COLOR_ATTACK = 0xFFEB2121.toInt()
    const val COLOR_DEFEND = 0xFF3B82F6.toInt()
    const val COLOR_DODGE = 0xFF28D9FF.toInt()
    const val COLOR_BASH = 0xFFFEB200.toInt()
    const val COLOR_NEUTRAL = 0xFFFFFFFF.toInt()

    private val glyphs: Map<String, Glyph> = mapOf(
        // Two opposed arrows: strafe lock.
        "btn_strafe" to Glyph(
            "M2 6 L15 6 L15 3.5 L22 7.5 L15 11.5 L15 9 L2 9 Z M22 15 L9 15 L9 12.5 L2 16.5 L9 20.5 L9 18 L22 18 Z",
            COLOR_NEUTRAL,
        ),
        // Shield.
        "btn_defend" to Glyph(
            "M12 2 L20 5 L20 11 C20 16.5 16.5 20.5 12 22 C7.5 20.5 4 16.5 4 11 L4 5 Z",
            COLOR_DEFEND,
        ),
        // Sword, tip up and to the right.
        "btn_attack" to Glyph(
            "M19.4 4.6 L18.6 8.7 L11.5 15.7 L8.3 12.5 L15.3 5.4 Z M5.8 10.0 L14.0 18.2 L12.3 19.9 L4.1 11.7 Z " +
                "M7.3 15.0 L9.0 16.7 L6.5 19.2 L4.8 17.5 Z M4.2 16.9 L7.1 19.8 L6.0 21.0 L3.0 18.0 Z",
            COLOR_ATTACK,
        ),
        // Double chevron: a dash.
        "btn_dodge" to Glyph(
            "M3 5 L10 12 L3 19 L6.6 19 L13.6 12 L6.6 5 Z M11 5 L18 12 L11 19 L14.6 19 L21.6 12 L14.6 5 Z",
            COLOR_DODGE,
        ),
        // The stun star.
        "btn_shieldbash" to Glyph(
            "M12.0 1.5 L14.6 8.4 L22.0 8.8 L16.2 13.4 L18.2 20.5 L12.0 16.4 L5.8 20.5 L7.8 13.4 L2.0 8.8 L9.4 8.4 Z",
            COLOR_BASH,
        ),
    )

    fun forNode(id: String): Glyph? = glyphs[id]
}
