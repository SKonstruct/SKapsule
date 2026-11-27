package com.skarm.launcher.touch

import android.content.Context
import android.util.Log
import androidx.annotation.Keep
import com.google.gson.Gson

/**
 * The saved centre, as a fraction of [total], for a control dragged so that [touch] plus the
 * [grabOffset] taken at touch-down is its new top-left edge. Kept inside the screen, like the
 * editor shows it.
 */
internal fun dragPercent(touch: Float, grabOffset: Float, size: Float, total: Float): Float =
    ((touch + grabOffset).coerceIn(0f, maxOf(0f, total - size)) + size / 2f) / total

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
    /** A joystick that appears where the thumb lands instead of sitting at its saved spot. */
    var floating: Boolean = false,
) {
    /** Part of the bottom action row (attacks + items), which has its own show/hide
     *  switch. Derived from the id rather than persisted, so layouts saved by older
     *  builds pick it up too. */
    val isActionBar: Boolean get() = id.startsWith("btn_ab") || id.startsWith("btn_item")
}

@Keep
data class TouchLayoutData(
    var globalOpacity: Float = TouchControlManager.DEFAULT_OPACITY,
    var controlsEnabled: Boolean = true,
    var actionBarVisible: Boolean = false,
    /** Render-scale multiplier for the game framebuffer, 0.5..1.0. Lower = the
     *  game renders fewer pixels and the display upscales, so the HUD/UI grows.
     *  Defaults to the minimum so the HUD starts at its largest / most touchable. */
    var renderScale: Float = TouchControlManager.MIN_RENDER_SCALE,
    val nodes: MutableList<ControlNode> = mutableListOf(),
)

object TouchControlManager {
    private const val TAG = "TouchControlManager"
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
    // 0.6, not 0.5: at half scale a 1080p-tall device renders a 540 px surface and
    // SK's own UI stops laying out -- character select never appears. 578 px worked,
    // 540 did not, so the floor sits above that with room to spare.
    /** Fully transparent controls are unusable, and Gson zero-fills a missing field. */
    /** The one button "Show Buttons" never hides. */
    const val NODE_ESC = "btn_esc"

    /** ESC's default centre; the chrome row (gear, keyboard, eye) sits on the same line to its right. */
    const val ESC_X = 0.197f
    const val ESC_Y = 0.05f

    const val MIN_OPACITY = 0.2f
    const val DEFAULT_OPACITY = 1.0f

    const val MIN_RENDER_SCALE = 0.6f
    const val MAX_RENDER_SCALE = 1.0f

    /** Per-control size multiplier bounds; the editor's slider and imported layouts share them. */
    const val MIN_NODE_SCALE = 0.5f
    const val MAX_NODE_SCALE = 2.5f

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

    /** Action-row labels, keyed by node id. Applied on load so layouts saved by older
     *  builds show 1-7 instead of the old A1/I1 naming. */
    private val ACTION_BAR_LABELS = mapOf(
        "btn_ab1" to "1", "btn_ab2" to "2", "btn_ab3" to "3",
        "btn_item1" to "4", "btn_item2" to "5", "btn_item3" to "6", "btn_item4" to "7",
    )

    fun loadLayout(context: Context): TouchLayoutData {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(KEY_LAYOUT_DATA, null)

        if (jsonStr.isNullOrEmpty()) {
            return createDefaultLayout()
        }

        return try {
            val data = gson.fromJson(jsonStr, TouchLayoutData::class.java) ?: createDefaultLayout()
            // A save with no controls leaves the editor and the game with nothing to show or
            // drag. Same guard as the iOS port.
            if (data.nodes.isNullOrEmpty()) return createDefaultLayout()
            // Gson zero-fills fields missing from layouts saved before renderScale
            // existed, which would give a 0 scale (a 1x1 framebuffer). Clamp to the
            // valid range so old saves stay renderable.
            data.renderScale = data.renderScale.coerceIn(MIN_RENDER_SCALE, MAX_RENDER_SCALE)
            // Was unclamped: a layout saved before the floor existed (or zero-filled by
            // Gson) left every control invisible with no way back except Reset.
            data.globalOpacity = data.globalOpacity.coerceIn(MIN_OPACITY, 1f)
            data.nodes.replaceAll { node ->
                // Gson zero-fills a `scale` missing from layouts saved before it existed,
                // which lays the control out at zero size. Same guard as renderScale.
                if (node.scale <= 0f) node.scale = 1.0f
                node.scale = node.scale.coerceIn(MIN_NODE_SCALE, MAX_NODE_SCALE)
                ACTION_BAR_LABELS[node.id]?.let { node.copy(label = it) } ?: node
            }
            if (data.nodes.none { it.id == "btn_attack" }) data.nodes.add(defaultAttackNode())
            data
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse touch layout data", e)
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

    /**
     * Attack (RTrig) - hold button straight above Defend: the same primary attack as tapping the
     * right stick, for a thumb that would rather not use the stick. Added to layouts saved before it existed.
     */
    private fun defaultAttackNode() = ControlNode(
        "btn_attack", ControlType.BUTTON, 0.664f, 0.431f,
        buttonCode = AXIS_RTRIGGER, isAxisTrigger = true, label = "Attack",
    )

    fun createDefaultLayout(): TouchLayoutData {
        // Positions are the author's hand-tuned layout, snapped to a grid (the iOS port holds the same numbers,
        // SKTouchControls.m): the sticks mirror each other, the right cluster is two columns by four rows, and
        // the bottom row is one even pitch with a wider gap between the attacks and the items.
        val layout = TouchLayoutData(actionBarVisible = true)

        // Both sticks float: each appears under the thumb inside a square pad in its half of the screen.
        // Left Joystick (Move)
        layout.nodes.add(ControlNode("joy_move", ControlType.JOYSTICK_LEFT, 0.205f, 0.617f, floating = true))

        // Right Joystick (Face) - hold to charge / aim, tap to Primary Attack;
        // handled in the Joystick implementation.
        layout.nodes.add(ControlNode("joy_face", ControlType.JOYSTICK_RIGHT, 0.795f, 0.617f, floating = true))

        // Escape - taps the ESC key (opens/closes SK menus). Top-left, level with the chrome row beside it.
        layout.nodes.add(ControlNode("btn_esc", ControlType.BUTTON, ESC_X, ESC_Y, keyCode = KEY_ESCAPE, label = "ESC"))

        // Strafe (R3) - toggle button above the bottom row's second button, level with Next Weap.
        layout.nodes.add(ControlNode("btn_strafe", ControlType.BUTTON, 0.3517f, 0.768f, buttonCode = GP_BTN_RIGHT_THUMB, isToggle = true, label = "Strafe"))

        // Right cluster, outer column (x 0.664): Prev Weap (L1), Attack (RTrig), Defend (LTrig), Next Weap (R1).
        layout.nodes.add(ControlNode("btn_defend", ControlType.BUTTON, 0.664f, 0.648f, buttonCode = AXIS_LTRIGGER, isAxisTrigger = true, label = "Defend"))

        layout.nodes.add(defaultAttackNode())

        // Inner column (x 0.607): ShieldBash (Y) above Dodge (L3); Prev and Next Weap sit in the outer column.
        layout.nodes.add(ControlNode("btn_dodge", ControlType.BUTTON, 0.607f, 0.648f, buttonCode = GP_BTN_LEFT_THUMB, label = "Dodge"))
        layout.nodes.add(ControlNode("btn_shieldbash", ControlType.BUTTON, 0.607f, 0.431f, buttonCode = GP_BTN_Y, label = "Bash"))
        layout.nodes.add(ControlNode("btn_prevweap", ControlType.BUTTON, 0.664f, 0.312f, buttonCode = GP_BTN_LEFT_BUMPER, label = "↑"))
        layout.nodes.add(ControlNode("btn_nextweap", ControlType.BUTTON, 0.664f, 0.768f, buttonCode = GP_BTN_RIGHT_BUMPER, label = "↓"))

        // Bottom row, 1-7: one even pitch, with the attacks (1-3) and the items (4-7) set apart by a wider gap.
        val pitch = 0.0575f
        val startX = 0.2942f
        val bottomY = 0.9f
        val itemsX = 0.6692f - pitch * 3f

        layout.nodes.add(ControlNode("btn_ab1", ControlType.BUTTON, startX, bottomY, buttonCode = GP_BTN_A, label = "1"))
        layout.nodes.add(ControlNode("btn_ab2", ControlType.BUTTON, startX + pitch, bottomY, buttonCode = GP_BTN_B, label = "2"))
        layout.nodes.add(ControlNode("btn_ab3", ControlType.BUTTON, startX + pitch * 2, bottomY, buttonCode = GP_BTN_X, label = "3"))

        layout.nodes.add(ControlNode("btn_item1", ControlType.BUTTON, itemsX, bottomY, buttonCode = GP_BTN_DPAD_UP, label = "4"))
        layout.nodes.add(ControlNode("btn_item2", ControlType.BUTTON, itemsX + pitch, bottomY, buttonCode = GP_BTN_DPAD_RIGHT, label = "5"))
        layout.nodes.add(ControlNode("btn_item3", ControlType.BUTTON, itemsX + pitch * 2, bottomY, buttonCode = GP_BTN_DPAD_DOWN, label = "6"))
        layout.nodes.add(ControlNode("btn_item4", ControlType.BUTTON, itemsX + pitch * 3, bottomY, buttonCode = GP_BTN_DPAD_LEFT, label = "7"))

        return layout
    }
}
