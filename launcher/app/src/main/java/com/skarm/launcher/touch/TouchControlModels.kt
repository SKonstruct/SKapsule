package com.skarm.launcher.touch

import android.content.Context
import androidx.annotation.Keep
import com.google.gson.Gson

enum class ControlType {
    BUTTON,
    JOYSTICK_LEFT,
    JOYSTICK_RIGHT,
}

@Keep
data class ControlNode(
    val id: String,
    val type: ControlType,
    /** 0.0 to 1.0 (relative to screen width) */
    var xPercent: Float,
    /** 0.0 to 1.0 (relative to screen height) */
    var yPercent: Float,
    var scale: Float = 1.0f,
    var visible: Boolean = true,
    /** GameActivity.GP_BTN_* or axis for triggers */
    val buttonCode: Int = -1,
    /** True if buttonCode represents an axis (like LTrig) */
    val isAxisTrigger: Boolean = false,
    /** True for the Strafe button */
    val isToggle: Boolean = false,
    val label: String = "",
    /** GLFW keycode this button taps (e.g. 256 = ESC); -1 = not a key button */
    val keyCode: Int = -1,
)

@Keep
data class TouchLayoutData(
    var globalOpacity: Float = 0.5f,
    var controlsEnabled: Boolean = true,
    /** Render-scale multiplier for the game framebuffer, 0.5..1.0. Lower = the
     *  game renders fewer pixels and the display upscales, so the HUD/UI grows.
     *  Defaults to the minimum so the HUD starts at its largest / most touchable. */
    var renderScale: Float = TouchControlManager.MIN_RENDER_SCALE,
    val nodes: MutableList<ControlNode> = mutableListOf(),
)

object TouchControlManager {
    private const val PREFS_NAME = "touch_controls_prefs"
    private const val KEY_LAYOUT_DATA = "layout_data"

    private val gson = Gson()

    // Axis codes for triggers, mapped to GameActivity constants
    const val AXIS_LTRIGGER = 4
    const val AXIS_RTRIGGER = 5

    // GLFW keycodes emitted by key buttons (see ControlNode.keyCode).
    const val KEY_ESCAPE = 256

    // Render-scale (resolution slider) bounds. Default is the minimum, for the
    // largest / most touchable HUD.
    const val MIN_RENDER_SCALE = 0.5f
    const val MAX_RENDER_SCALE = 1.0f

    // Button codes from GameActivity
    const val GP_BTN_A = 0
    const val GP_BTN_B = 1
    const val GP_BTN_X = 2
    const val GP_BTN_Y = 3
    const val GP_BTN_LEFT_BUMPER = 4
    const val GP_BTN_RIGHT_BUMPER = 5
    const val GP_BTN_BACK = 6
    const val GP_BTN_START = 7
    const val GP_BTN_GUIDE = 8
    const val GP_BTN_LEFT_THUMB = 9
    const val GP_BTN_RIGHT_THUMB = 10
    const val GP_BTN_DPAD_UP = 11
    const val GP_BTN_DPAD_RIGHT = 12
    const val GP_BTN_DPAD_DOWN = 13
    const val GP_BTN_DPAD_LEFT = 14

    fun loadLayout(context: Context): TouchLayoutData {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(KEY_LAYOUT_DATA, null)

        if (jsonStr.isNullOrEmpty()) {
            return createDefaultLayout()
        }

        return try {
            val data = gson.fromJson(jsonStr, TouchLayoutData::class.java) ?: createDefaultLayout()
            // Gson zero-fills fields missing from layouts saved before renderScale
            // existed, which would give a 0 scale (a 1x1 framebuffer). Clamp to the
            // valid range so old saves stay renderable.
            data.renderScale = data.renderScale.coerceIn(MIN_RENDER_SCALE, MAX_RENDER_SCALE)
            data
        } catch (e: Exception) {
            e.printStackTrace()
            createDefaultLayout()
        }
    }

    fun saveLayout(context: Context, layout: TouchLayoutData) {
        val jsonStr = gson.toJson(layout)

        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LAYOUT_DATA, jsonStr)
            .apply()
    }

    fun createDefaultLayout(): TouchLayoutData {
        val layout = TouchLayoutData()

        // Left Joystick (Move)
        layout.nodes.add(ControlNode("joy_move", ControlType.JOYSTICK_LEFT, 0.15f, 0.7f))

        // Right Joystick (Face) - hold to charge / aim, tap to Primary Attack;
        // handled in the Joystick implementation.
        layout.nodes.add(ControlNode("joy_face", ControlType.JOYSTICK_RIGHT, 0.85f, 0.7f))

        // Escape - taps the ESC key (opens/closes SK menus). Top-left by default,
        // clear of the joysticks and the top chrome buttons.
        layout.nodes.add(ControlNode("btn_esc", ControlType.BUTTON, 0.06f, 0.10f, keyCode = KEY_ESCAPE, label = "ESC"))

        // Strafe (R3) - toggle button offset to the lower right of Movement Circle pad.
        // Kept above the bottom Ability/Item row (y=0.9) so they don't overlap.
        layout.nodes.add(ControlNode("btn_strafe", ControlType.BUTTON, 0.28f, 0.72f, buttonCode = GP_BTN_RIGHT_THUMB, isToggle = true, label = "Strafe"))

        // Defend (LTrig) - hold button offset to lower left of Facing Circle pad.
        layout.nodes.add(ControlNode("btn_defend", ControlType.BUTTON, 0.72f, 0.72f, buttonCode = AXIS_LTRIGGER, isAxisTrigger = true, label = "Defend"))

        // Dodge (L3) - tap button offset above the Facing Circle pad
        layout.nodes.add(ControlNode("btn_dodge", ControlType.BUTTON, 0.78f, 0.45f, buttonCode = GP_BTN_LEFT_THUMB, label = "Dodge"))

        // ShieldBash (Y) - tap button offset above the Facing Circle pad
        layout.nodes.add(ControlNode("btn_shieldbash", ControlType.BUTTON, 0.88f, 0.45f, buttonCode = GP_BTN_Y, label = "Bash"))

        // Prev Weap (L1) - Up glyph arrow button offset to the right of the Facing Circle pad
        layout.nodes.add(ControlNode("btn_prevweap", ControlType.BUTTON, 0.95f, 0.55f, buttonCode = GP_BTN_LEFT_BUMPER, label = "↑"))

        // Next Weap (R1) - Down glyph arrow button offset to the right of the Facing Circle pad
        layout.nodes.add(ControlNode("btn_nextweap", ControlType.BUTTON, 0.95f, 0.85f, buttonCode = GP_BTN_RIGHT_BUMPER, label = "↓"))

        // Bottom centered buttons in a row from left to right by default.
        // Keep the 7-button row centered on screen and clear of the move
        // joystick (right edge ~0.22) and facing joystick (left edge ~0.78).
        val gap = 0.07f
        val bottomY = 0.9f
        // Center the row around 0.5: first button = 0.5 - gap * (count-1)/2
        val centerStartX = 0.5f - gap * 3f

        layout.nodes.add(ControlNode("btn_ab1", ControlType.BUTTON, centerStartX + gap * 0, bottomY, buttonCode = GP_BTN_A, label = "A1"))
        layout.nodes.add(ControlNode("btn_ab2", ControlType.BUTTON, centerStartX + gap * 1, bottomY, buttonCode = GP_BTN_B, label = "A2"))
        layout.nodes.add(ControlNode("btn_ab3", ControlType.BUTTON, centerStartX + gap * 2, bottomY, buttonCode = GP_BTN_X, label = "A3"))

        layout.nodes.add(ControlNode("btn_item1", ControlType.BUTTON, centerStartX + gap * 3, bottomY, buttonCode = GP_BTN_DPAD_UP, label = "I1"))
        layout.nodes.add(ControlNode("btn_item2", ControlType.BUTTON, centerStartX + gap * 4, bottomY, buttonCode = GP_BTN_DPAD_RIGHT, label = "I2"))
        layout.nodes.add(ControlNode("btn_item3", ControlType.BUTTON, centerStartX + gap * 5, bottomY, buttonCode = GP_BTN_DPAD_DOWN, label = "I3"))
        layout.nodes.add(ControlNode("btn_item4", ControlType.BUTTON, centerStartX + gap * 6, bottomY, buttonCode = GP_BTN_DPAD_LEFT, label = "I4"))

        return layout
    }
}
