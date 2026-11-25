package com.skarm.launcher.touch

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.util.SparseArray
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import com.skarm.launcher.GfxSettings
import com.skarm.launcher.NativeBridge
import com.skarm.launcher.R

class TouchControlOverlay @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private var layoutData: TouchLayoutData = TouchControlManager.loadLayout(context)
    private val controlViews = mutableListOf<BaseTouchControl>()
    private var inEditMode = false

    /** True while the editor is open, so a host can leave it cleanly (leaving saves). */
    val isEditing: Boolean get() = inEditMode

    // Play-mode touch routing: which control each active pointer was captured by, keyed
    // by pointer id. A pointer stays with the control it landed on for its whole life.
    private val pointerTargets = SparseArray<BaseTouchControl>()

    // Pointer currently driving SK's mouse cursor (the one that landed on no control).
    // SK's UI is a single cursor, so only the first such pointer is forwarded.
    private var cursorPointerId = MotionEvent.INVALID_POINTER_ID

    /** Latched at DOWN: buttonState reads 0 by the time the release arrives. */
    private var cursorButton = 0

    // A mouse is one pointer however many buttons it has: the second button never gets a DOWN
    // of its own, and releasing the first while the second is held never gets an UP. So a mouse
    // cursor is driven by diffing buttonState (below), not by pointer events.
    private var cursorIsMouse = false
    private var mouseButtons = 0
    private var cursorX = 0f
    private var cursorY = 0f

    // Edit state
    private var selectedView: BaseTouchControl? = null
    private var dX = 0f
    private var dY = 0f

    /** The editor has two pages: arranging the controls, and the full settings with the controls locked. */
    enum class EditorPage { EDIT, SETTINGS }

    private var page = EditorPage.EDIT

    // The selected control's dialog and the Edit | Settings | Close strip (both built in init).
    private val nodePanel: LinearLayout
    private val tabStrip: LinearLayout

    /** The Close tab was pressed. Unset it closes the editor; the standalone editor leaves its Activity. */
    var closeRequestListener: (() -> Unit)? = null

    /** The standalone editor has its own Done button, so it hides the Close tab. */
    var closeTabVisible: Boolean = true
        set(value) {
            field = value
            tabStrip.findViewWithTag<View>("tabClose")?.visibility = if (value) View.VISIBLE else View.GONE
        }

    // Editor UI Panel
    private val editorPanel: LinearLayout

    // The panel scrolls: the graphics rows make it taller than a landscape phone screen.
    private val editorScroll: android.widget.ScrollView

    // Cached node settings views for fast updates
    private var cachedNodeTitle: TextView? = null
    private var cachedVisibleSwitch: Switch? = null
    private var cachedScaleLabel: TextView? = null
    private var cachedScaleSlider: SeekBar? = null

    // Reset Layout double-tap gate
    private var resetArmed = false
    private val resetDisarm = Runnable {
        resetArmed = false
        editorPanel.findViewWithTag<Button>("resetButton")?.text = RESET_LABEL
    }

    init {
        // Build editor panel
        editorPanel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(HudPalette.SURFACE)
            setPadding(32, 32, 32, 32)
            // Bound the panel width so it doesn't stretch across the whole
            // screen (a MATCH_PARENT separator otherwise forces a WRAP_CONTENT
            // vertical LinearLayout to fill the full width).
            val panelWidth = (320 * resources.displayMetrics.density).toInt()
            layoutParams = LayoutParams(panelWidth, LayoutParams.WRAP_CONTENT, Gravity.CENTER)

            val panel = this

            // Global Settings
            val globalTitle = TextView(context).apply {
                text = "Global Settings"
                setTextColor(Color.WHITE)
            }
            addView(globalTitle)

            val enableSwitch = Switch(context).apply {
                text = "Show Controls"
                setTextColor(Color.WHITE)
                tag = "enableSwitch"
                isChecked = layoutData.controlsEnabled
                setOnCheckedChangeListener { _, isChecked ->
                    layoutData.controlsEnabled = isChecked
                    applyControlAppearance()
                }
            }
            addView(enableSwitch)

            val actionBarSwitch = Switch(context).apply {
                text = "Show Buttons"
                setTextColor(Color.WHITE)
                tag = "actionBarSwitch"
                isChecked = layoutData.actionBarVisible
                setOnCheckedChangeListener { _, isChecked ->
                    layoutData.actionBarVisible = isChecked
                    applyControlAppearance()
                    buttonsVisibleChangeListener?.invoke(isChecked)
                }
            }
            addView(actionBarSwitch)

            val opacityLabel = TextView(context).apply {
                text = "Opacity"
                setTextColor(Color.WHITE)
            }
            addView(opacityLabel)
            val opacitySlider = SeekBar(context).apply {
                max = 100
                tag = "opacitySlider"
                progress = opacityToProgress(layoutData.globalOpacity)
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                        layoutData.globalOpacity = progressToOpacity(progress)
                        applyControlAppearance()
                    }
                    override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                    override fun onStopTrackingTouch(seekBar: SeekBar?) {}
                })
            }
            addView(opacitySlider)

            // Resolution (render scale): lower = the game renders fewer pixels and
            // the display upscales, making the HUD/UI physically larger and easier
            // to touch on high-DPI screens. 0 -> 0.5x, 100 -> 1.0x.
            val resolutionLabel = TextView(context).apply {
                text = "Resolution"
                setTextColor(Color.WHITE)
            }
            addView(resolutionLabel)
            val resolutionSlider = SeekBar(context).apply {
                max = 100
                tag = "resolutionSlider"
                progress = renderScaleToProgress(layoutData.renderScale)
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                        // Track the value live, but defer the (surface-rebuilding)
                        // apply to release so a drag doesn't thrash the EGL surface.
                        layoutData.renderScale = progressToRenderScale(progress)
                        updateGfxEnabled(panel)
                    }
                    override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                    override fun onStopTrackingTouch(seekBar: SeekBar?) {
                        renderScaleChangeListener?.invoke(layoutData.renderScale)
                    }
                })
            }
            addView(resolutionSlider)

            // Graphics (cpp/skgfx.c). Stored apart from the touch layout, so Reset Layout leaves them alone.
            val fpsSwitch = Switch(context).apply {
                text = context.getString(R.string.gfx_show_fps)
                setTextColor(Color.WHITE)
                tag = "fpsSwitch"
                isChecked = GfxSettings.showFps(context)
                setOnCheckedChangeListener { _, isChecked ->
                    GfxSettings.setShowFps(context, isChecked)
                    fpsChangeListener?.invoke(isChecked)
                }
            }
            addView(fpsSwitch)

            val sharpenLabel = TextView(context).apply {
                setTextColor(Color.WHITE)
                tag = "sharpenLabel"
                text = sharpenText(GfxSettings.sharpenPercent(context))
            }
            addView(sharpenLabel)
            val sharpenRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val sharpenSlider = SeekBar(context).apply {
                max = GfxSettings.MAX_SHARPEN
                tag = "sharpenSlider"
                progress = GfxSettings.sharpenPercent(context)
                layoutParams = LinearLayout.LayoutParams(0, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                        sharpenLabel.text = sharpenText(progress)
                    }
                    override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                    override fun onStopTrackingTouch(seekBar: SeekBar?) {
                        // Applied on release, like the resolution: the surface may be rebuilt.
                        GfxSettings.setSharpenPercent(context, progress)
                        gfxChangeListener?.invoke()
                    }
                })
            }
            sharpenRow.addView(sharpenSlider)
            sharpenRow.addView(
                Button(context).apply {
                    text = context.getString(R.string.gfx_sharpen_reset)
                    setOnClickListener {
                        sharpenSlider.progress = 0
                        GfxSettings.setSharpenPercent(context, 0)
                        gfxChangeListener?.invoke()
                    }
                },
            )
            addView(sharpenRow)

            val sharpHudSwitch = Switch(context).apply {
                text = context.getString(R.string.gfx_sharp_hud)
                setTextColor(Color.WHITE)
                tag = "sharpHudSwitch"
                isChecked = GfxSettings.sharpHud(context)
                setOnCheckedChangeListener { _, isChecked ->
                    GfxSettings.setSharpHud(context, isChecked)
                    gfxChangeListener?.invoke()
                }
            }
            addView(sharpHudSwitch)
            addView(
                TextView(context).apply {
                    setTextColor(0xFFB0B0B0.toInt())
                    textSize = 12f
                    tag = "gfxHint"
                },
            )
            updateGfxEnabled(panel)

            // Floating sticks: a stick that appears where the thumb lands and follows it.
            fun floatingSwitch(label: Int, name: String, type: ControlType) = Switch(context).apply {
                text = context.getString(label)
                setTextColor(Color.WHITE)
                tag = name
                isChecked = layoutData.nodes.any { it.type == type && it.floating }
                setOnCheckedChangeListener { _, on -> setFloating(type, on) }
            }.also { addView(it) }
            floatingSwitch(R.string.touch_floating_left, "floatLeftSwitch", ControlType.JOYSTICK_LEFT)
            floatingSwitch(R.string.touch_floating_right, "floatRightSwitch", ControlType.JOYSTICK_RIGHT)
            addView(
                TextView(context).apply {
                    text = context.getString(R.string.touch_floating_hint)
                    setTextColor(0xFFB0B0B0.toInt())
                    textSize = 12f
                },
            )

            // Layout library: presets, export, import. The host Activity provides it (it needs
            // the file pickers of an Activity), so the button stays hidden until a host sets one.
            val layoutsButton = Button(context).apply {
                text = context.getString(R.string.layouts_button)
                tag = "layoutsButton"
                visibility = View.GONE
                setOnClickListener { layoutLibraryRequestListener?.invoke() }
            }
            addView(layoutsButton)

            // Reset Layout (double-tap gated so a stray tap doesn't wipe work)
            val resetButton = Button(context).apply {
                text = RESET_LABEL
                tag = "resetButton"
                setOnClickListener { handleResetTap(this) }
            }
            addView(resetButton)

        }

        editorScroll = android.widget.ScrollView(context).apply {
            visibility = View.GONE
            // Below the tab strip, and scrolling in what is left: the panel is taller than a landscape phone.
            val d = resources.displayMetrics.density
            layoutParams = LayoutParams((320 * d).toInt(), LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.CENTER_HORIZONTAL)
                .apply {
                    topMargin = (64 * d).toInt()
                    bottomMargin = (8 * d).toInt()
                }
            addView(editorPanel)
        }

        // The selected control's dialog: its own small panel, docked away from the control (see
        // dockNodePanel) so the control being dragged is never under it. Edit page only.
        val density = resources.displayMetrics.density
        nodePanel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(HudPalette.SURFACE)
            setPadding(24, 24, 24, 24)
            visibility = View.GONE
            isClickable = true // a tap on the panel itself must not fall through and deselect
            layoutParams = LayoutParams((NODE_PANEL_DP * density).toInt(), LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.START)
            cachedNodeTitle = TextView(context).apply { setTextColor(Color.WHITE) }.also { addView(it) }
            cachedVisibleSwitch = Switch(context).apply {
                text = "Visible"
                setTextColor(Color.WHITE)
                setOnCheckedChangeListener { _, isChecked ->
                    selectedView?.let {
                        it.node.visible = isChecked
                        it.alpha = if (isChecked) layoutData.globalOpacity else 0.3f // keep visible in edit mode
                    }
                }
            }.also { addView(it) }
            cachedScaleLabel = TextView(context).apply {
                text = "Scale"
                setTextColor(Color.WHITE)
            }.also { addView(it) }
            cachedScaleSlider = SeekBar(context).apply {
                max = 200 // 0.5x to 2.5x
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                        selectedView?.let {
                            it.node.scale = 0.5f + (progress / 100f)
                            requestLayout()
                        }
                    }

                    override fun onStartTrackingTouch(seekBar: SeekBar?) {}

                    // The control grew or shrank: dock again, so it stays clear of the dialog.
                    override fun onStopTrackingTouch(seekBar: SeekBar?) {
                        post { dockNodePanel() }
                    }
                })
            }.also { addView(it) }
        }

        // Edit | Settings | Close, top centre, while the editor is open.
        tabStrip = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(HudPalette.SURFACE)
            setPadding(8, 8, 8, 8)
            visibility = View.GONE
            isClickable = true
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.CENTER_HORIZONTAL)
                .apply { topMargin = (8 * density).toInt() }
            fun tab(label: Int, name: String, onClick: () -> Unit) = Button(context).apply {
                text = context.getString(label)
                tag = name
                setOnClickListener { onClick() }
            }.also { addView(it) }
            tab(R.string.editor_tab_edit, "tabEdit") { showPage(EditorPage.EDIT) }
            tab(R.string.editor_tab_settings, "tabSettings") { showPage(EditorPage.SETTINGS) }
            tab(R.string.editor_tab_close, "tabClose") { closeRequestListener?.invoke() ?: toggleEditMode() }
        }

        buildControls()
        addView(editorScroll)
        addView(nodePanel)
        addView(tabStrip)
    }

    private fun sharpenText(percent: Int): String =
        if (percent <= 0) context.getString(R.string.gfx_sharpen_off) else context.getString(R.string.gfx_sharpen_value, percent)

    /**
     * Sharp HUD needs a scene smaller than the window, so it is greyed out at 100% resolution, with
     * the hint saying what to change. The hint also says what the offscreen features have in common.
     */
    private fun updateGfxEnabled(panel: LinearLayout) {
        val applies = GfxSettings.sharpHudApplies(layoutData.renderScale)
        panel.findViewWithTag<Switch>("sharpHudSwitch")?.apply {
            isEnabled = applies
            alpha = if (applies) 1f else 0.4f
        }
        panel.findViewWithTag<TextView>("gfxHint")?.text = context.getString(R.string.gfx_hint_offscreen) + "\n" +
            context.getString(if (applies) R.string.gfx_hint_sharp_hud else R.string.gfx_hint_sharp_hud_needs_scale)
    }

    private fun buildControls() {
        // A pointer still routed to a view we are about to discard would leave its
        // button or axis held down in the native mailbox for good.
        cancelActivePointers()
        controlViews.forEach { removeView(it) }
        controlViews.clear()

        for (node in layoutData.nodes) {
            val view = when (node.type) {
                ControlType.JOYSTICK_LEFT, ControlType.JOYSTICK_RIGHT -> TouchJoystickView(context, node)
                ControlType.BUTTON -> TouchButtonView(context, node)
            }
            addView(view)
            controlViews.add(view)
        }

        applyControlAppearance()
    }

    /**
     * Single source of truth for control visibility and alpha. Call this on any
     * state change (mode toggle, opacity change, enable toggle, rebuild) so the
     * displayed appearance never drifts out of sync.
     *
     * In edit mode visible controls are boosted to at least [EDIT_MODE_MIN_OPACITY]
     * so they're easy to see and drag; hidden controls show faintly. Exiting edit
     * mode restores the user's chosen global opacity (and hides hidden controls).
     */
    /**
     * "Show Buttons" off hides every button except ESC. The joysticks are not buttons so
     * they stay, and ESC stays because without a hardware keyboard it is the only way out
     * of a menu.
     */
    private fun isHiddenByShowButtons(node: ControlNode): Boolean =
        !layoutData.actionBarVisible &&
            node.type == ControlType.BUTTON &&
            node.id != TouchControlManager.NODE_ESC

    /** Current state, for the chrome eye button's icon. */
    val buttonsVisible: Boolean get() = layoutData.actionBarVisible

    /** Flips "Show Buttons" from outside the editor (the chrome eye button). */
    fun toggleButtonsVisible(): Boolean {
        layoutData.actionBarVisible = !layoutData.actionBarVisible
        editorPanel.findViewWithTag<Switch>("actionBarSwitch")?.isChecked = layoutData.actionBarVisible
        applyControlAppearance()
        TouchControlManager.saveLayout(context, layoutData)
        return layoutData.actionBarVisible
    }

    private fun applyControlAppearance() {
        val enabled = layoutData.controlsEnabled
        if (!enabled) cancelActivePointers()
        for (view in controlViews) {
            // The Settings page shows the controls as they play, so opacity and Show Buttons can be judged.
            if (inEditMode && page == EditorPage.EDIT) {
                applyEditModeAppearance(view)
            } else {
                applyPlayModeAppearance(view, enabled)
            }
        }

        // Keep SK's view of the virtual controller in sync: it must see the pad as
        // connected whenever the controls are enabled, or its glfwGetGamepadState
        // poll reports "no controller" and every touch input is dropped.
        NativeBridge.onVirtualGamepadConnected(enabled)

        // Notify Activity if there's a listener to update static buttons (Keyboard, Gear)
        opacityChangeListener?.invoke(layoutData.globalOpacity)
        // Editing keeps them on screen regardless, or they could not be repositioned.
        controlsEnabledChangeListener?.invoke(enabled || inEditMode)
    }

    private fun applyEditModeAppearance(view: BaseTouchControl) {
        view.visibility = View.VISIBLE
        val shown = view.node.visible && !isHiddenByShowButtons(view.node)
        view.alpha = if (shown) {
            maxOf(layoutData.globalOpacity, EDIT_MODE_MIN_OPACITY)
        } else {
            EDIT_HIDDEN_OPACITY
        }
    }

    private fun applyPlayModeAppearance(view: BaseTouchControl, enabled: Boolean) {
        val shown = view.node.visible && !isHiddenByShowButtons(view.node)
        view.visibility = if (enabled && shown) View.VISIBLE else View.GONE
        view.alpha = layoutData.globalOpacity
    }

    var opacityChangeListener: ((Float) -> Unit)? = null

    /** Notified when "Show Buttons" changes, so the chrome eye icon can follow. */
    var buttonsVisibleChangeListener: ((Boolean) -> Unit)? = null

    /**
     * Notified with whether the keyboard and eye buttons should be on screen: they follow
     * "Show Controls", since both only act on the touch controls, but stay up while the
     * layout is being edited so they can be dragged. The gear is deliberately left out --
     * it is the only way back into the editor to turn the controls on again.
     */
    var controlsEnabledChangeListener: ((Boolean) -> Unit)? = null

    val controlsEnabled: Boolean get() = layoutData.controlsEnabled

    // Notified with the chosen render scale (0.5..1.0) when the resolution slider
    // moves; the host Activity applies it to the game surface.
    var renderScaleChangeListener: ((Float) -> Unit)? = null

    /**
     * Set by the host Activity (the launcher's editor and the game) to open the layout library
     * (presets, export, import). Setting it is what reveals the panel's "Layouts" button.
     */
    var layoutLibraryRequestListener: (() -> Unit)? = null
        set(value) {
            field = value
            editorPanel.findViewWithTag<Button>("layoutsButton")?.visibility =
                if (value != null) View.VISIBLE else View.GONE
        }

    // Notified when sharpening or Sharp HUD changes (read back from GfxSettings), and with the FPS
    // counter's new state.
    var gfxChangeListener: (() -> Unit)? = null
    var fpsChangeListener: ((Boolean) -> Unit)? = null

    // Notified whenever edit mode is entered/exited so the host can toggle
    // drag-to-reposition on the static chrome buttons (gear/keyboard).
    var editModeChangeListener: ((Boolean) -> Unit)? = null

    fun currentRenderScale(): Float = layoutData.renderScale

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)

        val w = right - left
        val h = bottom - top

        // Base sizes
        val baseJoySize = w * 0.135f
        // Same edge length as the chrome icon buttons. The old w*0.067 fraction came out
        // noticeably larger than them on a phone, which is what made the row look oversized.
        val baseBtnSize = resources.getDimension(R.dimen.chrome_button_size)

        for (view in controlViews) {
            val node = view.node
            val size = (if (node.type == ControlType.BUTTON) baseBtnSize else baseJoySize) * node.scale

            val cx = w * node.xPercent
            val cy = h * node.yPercent

            val l = (cx - size / 2).toInt()
            val t = (cy - size / 2).toInt()
            val r = (cx + size / 2).toInt()
            val b = (cy + size / 2).toInt()

            // A held floating stick sits where the thumb put it; every other control is back at its saved spot.
            if (!(view is TouchJoystickView && view.isHeld)) {
                view.translationX = 0f
                view.translationY = 0f
            }
            view.layout(l, t, r, b)
        }

        bringChromeToFront()
        dockNodePanel()
    }

    fun toggleEditMode() {
        // Whatever is under a finger right now stops receiving events on the other side
        // of this switch, so release it first.
        cancelActivePointers()
        inEditMode = !inEditMode

        if (!inEditMode) {
            // Save layout when exiting edit mode
            TouchControlManager.saveLayout(context, layoutData)
            selectedView = null
        }

        applyPage()
        editModeChangeListener?.invoke(inEditMode)
    }

    /** Switches between the Edit and Settings pages (the tab strip). Only while the editor is open. */
    fun showPage(next: EditorPage) {
        if (!inEditMode) return
        page = next
        if (next != EditorPage.EDIT) selectView(null)
        applyPage()
    }

    /**
     * Shows what the current page needs and nothing else. Edit: the controls draggable and outlined, plus the
     * selected control's small dialog. Settings: the controls as they play but locked (no game input), plus
     * the full panel. Closed: neither.
     */
    private fun applyPage() {
        tabStrip.visibility = if (inEditMode) View.VISIBLE else View.GONE
        tabStrip.findViewWithTag<View>("tabClose")?.visibility = if (closeTabVisible) View.VISIBLE else View.GONE
        tabStrip.findViewWithTag<View>("tabEdit")?.alpha = if (page == EditorPage.EDIT) 1f else 0.55f
        tabStrip.findViewWithTag<View>("tabSettings")?.alpha = if (page == EditorPage.SETTINGS) 1f else 0.55f
        editorScroll.visibility = if (inEditMode && page == EditorPage.SETTINGS) View.VISIBLE else View.GONE

        for (view in controlViews) {
            // Locked, not "playing": both pages keep inEditMode so no touch reaches the game.
            view.inEditMode = inEditMode
            view.outlineVisible = page == EditorPage.EDIT
            view.isSelectedNode = false
            view.invalidate()
        }

        applyControlAppearance()
        updateEditorPanel()
        bringChromeToFront()
    }

    /** The panels and the strip sit above the controls, whatever order they were added in. */
    private fun bringChromeToFront() {
        editorScroll.bringToFront()
        nodePanel.bringToFront()
        tabStrip.bringToFront()
    }

    private fun setFloating(type: ControlType, on: Boolean) {
        layoutData.nodes.filter { it.type == type }.forEach { it.floating = on }
        controlViews.forEach { it.invalidate() }
    }

    private fun handleResetTap(button: Button) {
        if (resetArmed) {
            removeCallbacks(resetDisarm)
            resetArmed = false
            button.text = RESET_LABEL
            resetLayout()
        } else {
            // First tap: arm and wait for a confirming second tap.
            resetArmed = true
            button.text = RESET_CONFIRM_LABEL
            removeCallbacks(resetDisarm)
            postDelayed(resetDisarm, RESET_CONFIRM_WINDOW_MS)
        }
    }

    private fun resetLayout() = applyLayout(TouchControlManager.createDefaultLayout())

    /** A copy of the live layout, safe to keep or edit without touching the controls on screen. */
    fun currentLayoutSnapshot(): TouchLayoutData =
        layoutData.copy(nodes = layoutData.nodes.map { it.copy() }.toMutableList())

    /** Replaces the layout wholesale (Reset, or a preset/import from the layout library). */
    fun applyLayout(data: TouchLayoutData) {
        val previousRenderScale = layoutData.renderScale
        layoutData = data
        selectView(null)
        buildControls()
        TouchControlManager.saveLayout(context, layoutData)

        // Refresh the global controls to reflect the restored defaults.
        editorPanel.findViewWithTag<Switch>("enableSwitch")?.isChecked = layoutData.controlsEnabled
        editorPanel.findViewWithTag<Switch>("actionBarSwitch")?.isChecked = layoutData.actionBarVisible
        // Moving the slider fires its listener, which rounds the opacity to a whole slider step;
        // put the exact value back so a preset or import keeps what it said.
        val exactOpacity = layoutData.globalOpacity
        editorPanel.findViewWithTag<SeekBar>("opacitySlider")?.progress = opacityToProgress(exactOpacity)
        layoutData.globalOpacity = exactOpacity
        editorPanel.findViewWithTag<SeekBar>("resolutionSlider")?.progress =
            renderScaleToProgress(layoutData.renderScale)
        updateGfxEnabled(editorPanel)
        // In game this resizes the surface, so only when the scale actually changed: a preset
        // keeps the current one, Reset may not.
        if (layoutData.renderScale != previousRenderScale) {
            renderScaleChangeListener?.invoke(layoutData.renderScale)
        }

        editorPanel.findViewWithTag<Switch>("floatLeftSwitch")?.isChecked =
            layoutData.nodes.any { it.type == ControlType.JOYSTICK_LEFT && it.floating }
        editorPanel.findViewWithTag<Switch>("floatRightSwitch")?.isChecked =
            layoutData.nodes.any { it.type == ControlType.JOYSTICK_RIGHT && it.floating }

        bringChromeToFront()
    }

    /**
     * Forwarded pointer events that hit no control, in this overlay's coordinate space,
     * with a `MotionEvent.ACTION_DOWN`/`_MOVE`/`_UP` action. The host Activity turns
     * these into SK mouse-cursor input. Set by GameActivity.
     *
     * The overlay claims every gesture in play mode, so it — not the SurfaceView
     * underneath — is the only thing that sees touches; without forwarding, a tap that
     * misses a control would be lost, and (worse) letting the SurfaceView take the
     * gesture instead means no *later* finger in it can ever reach a control.
     */
    var cursorTouchListener: ((action: Int, x: Float, y: Float, button: Int) -> Unit)? = null

    /**
     * Take every gesture ourselves instead of letting ViewGroup hand one to a child.
     *
     * Without this the platform routes each pointer straight to whichever control view
     * contains it, [handlePlayTouch] only ever sees the gestures that missed everything,
     * and [pointerTargets] stays empty — so nothing can release a held control when the
     * layout is rebuilt or the controls are switched off, and the cursor pass-through
     * only works for the first finger. Owning the dispatch keeps all of that in one place.
     */
    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        // Never in edit mode: the editor panel is a child, and its sliders and switches
        // need their own touches. Control views return false from onTouchEvent while
        // editing, so drag gestures still fall through to us there.
        return !inEditMode
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!inEditMode) {
            return handlePlayTouch(event)
        }
        // The Settings page shows the controls but does not let them be moved or pressed.
        if (page == EditorPage.SETTINGS) return true

        // --- Edit Mode Logic ---
        val action = event.actionMasked

        when (action) {
            MotionEvent.ACTION_DOWN -> {
                // Find tapped view
                var tappedView: BaseTouchControl? = null
                for (i in controlViews.size - 1 downTo 0) {
                    val view = controlViews[i]
                    if (isPointInsideView(event.x, event.y, view)) {
                        tappedView = view
                        break
                    }
                }

                if (tappedView != null) {
                    selectView(tappedView)
                    // Layout coordinates, as the hit test uses. `x`/`y` include the view's
                    // translation, which would put the grab point somewhere else.
                    dX = tappedView.left - event.x
                    dY = tappedView.top - event.y
                } else {
                    // Tap on empty space deselects
                    selectView(null)
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                selectedView?.let { view ->
                    // The saved percent is the only position; onLayout places the view from
                    // it. Moving the view as well (view.x = ...) left a translation behind
                    // that the next layout added on top, so a control jumped on the next tap.
                    view.node.xPercent = dragPercent(event.x, dX, view.width.toFloat(), width.toFloat())
                    view.node.yPercent = dragPercent(event.y, dY, view.height.toFloat(), height.toFloat())
                    requestLayout()
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                // Done dragging: the control may have moved, so the dialog finds its spot again.
                dockNodePanel()
                return true
            }
        }
        return false
    }

    /**
     * Which mouse button a pointer represents.
     *
     * A finger has no buttons and reports 0, which is what we want — left. A real mouse
     * fills buttonState, and reporting its secondary click as a left click (which is what
     * happened before) makes context menus unreachable.
     */
    private fun glfwButton(event: MotionEvent): Int = when {
        event.buttonState and MotionEvent.BUTTON_SECONDARY != 0 -> 1
        event.buttonState and MotionEvent.BUTTON_TERTIARY != 0 -> 2
        else -> 0
    }

    private fun isFloatingStick(view: BaseTouchControl) = view is TouchJoystickView && view.node.floating

    /** The free floating stick for a touch at [x]: the left stick takes the left half, the right stick the right half. */
    private fun floatingStickFor(x: Float): TouchJoystickView? =
        controlViews.filterIsInstance<TouchJoystickView>().firstOrNull {
            it.node.floating && it.visibility == View.VISIBLE && !it.isHeld &&
                (if (it.node.type == ControlType.JOYSTICK_LEFT) x < width / 2f else x >= width / 2f)
        }

    private fun isPointInsideView(x: Float, y: Float, view: View): Boolean {
        return x >= view.left && x <= view.right && y >= view.top && y <= view.bottom
    }

    /**
     * Routes each pointer independently.
     *
     * The previous version hit-tested with `event.x`/`event.y`, which are pointer *0*'s
     * coordinates whatever the action is. So while one thumb held a joystick, every other
     * finger was tested at the joystick's position and no button could ever be pressed —
     * and a release was delivered to whichever control held pointer 0, leaving buttons
     * stuck down. Android's own dispatch doesn't help here (it would hand a whole gesture
     * to one child), hence the manual per-pointer routing.
     */
    private fun handlePlayTouch(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val index = event.actionIndex
                val id = event.getPointerId(index)
                val x = event.getX(index)
                val y = event.getY(index)

                // A floating stick has no fixed spot to hit, so it is not found by position: a touch that
                // lands on nothing else, in its half of the screen, starts it there.
                val target = controlViews.lastOrNull { view ->
                    view.visibility == View.VISIBLE && !isFloatingStick(view) && isPointInsideView(x, y, view)
                } ?: floatingStickFor(x)?.also { it.floatTo(x, y) }
                if (target != null && pointerTargets.indexOfValue(target) >= 0) {
                    // A second finger on a control another finger already holds: swallow
                    // it rather than letting it click through into the game world.
                    return true
                }
                // Capture only if the control actually took the press: a joystick refuses
                // a touch inside its bounding box but outside its circle, and that
                // pointer should still reach the game cursor.
                if (target != null && dispatchToControl(target, MotionEvent.ACTION_DOWN, event, index)) {
                    pointerTargets.put(id, target)
                } else if (cursorPointerId == MotionEvent.INVALID_POINTER_ID) {
                    cursorPointerId = id
                    cursorX = x
                    cursorY = y
                    cursorIsMouse = event.getToolType(index) == MotionEvent.TOOL_TYPE_MOUSE
                    if (cursorIsMouse) {
                        mouseButtons = 0
                        syncMouseButtons(event.buttonState, x, y)
                    } else {
                        cursorButton = glfwButton(event)
                        cursorTouchListener?.invoke(MotionEvent.ACTION_DOWN, x, y, cursorButton)
                    }
                }
                // Always claim the gesture: a miss must not hand it to the SurfaceView,
                // or the fingers that follow could never reach a control.
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                for (index in 0 until event.pointerCount) {
                    val id = event.getPointerId(index)
                    val target = pointerTargets.get(id)
                    if (target != null) {
                        dispatchToControl(target, MotionEvent.ACTION_MOVE, event, index)
                    } else if (id == cursorPointerId) {
                        cursorX = event.getX(index)
                        cursorY = event.getY(index)
                        if (cursorIsMouse) {
                            syncMouseButtons(event.buttonState, event.getX(index), event.getY(index))
                        }
                        cursorTouchListener?.invoke(
                            MotionEvent.ACTION_MOVE, event.getX(index), event.getY(index),
                            cursorButton,
                        )
                    }
                }
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val index = event.actionIndex
                val id = event.getPointerId(index)
                val target = pointerTargets.get(id)
                if (target != null) {
                    pointerTargets.remove(id)
                    dispatchToControl(target, MotionEvent.ACTION_UP, event, index)
                } else if (id == cursorPointerId) {
                    cursorPointerId = MotionEvent.INVALID_POINTER_ID
                    if (cursorIsMouse) {
                        // The last button up ends the gesture; anything still held is released.
                        syncMouseButtons(0, event.getX(index), event.getY(index))
                        cursorIsMouse = false
                    } else {
                        cursorTouchListener?.invoke(
                            MotionEvent.ACTION_UP, event.getX(index), event.getY(index),
                            cursorButton,
                        )
                    }
                }
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                cancelActivePointers()
                // cancelActivePointers has already released the cursor.
                return true
            }
        }
        return false
    }

    /**
     * Hands one pointer to one control as a single-pointer event in the control's own
     * coordinate space, so `getX(actionIndex)` inside the control is always that pointer.
     */
    private fun dispatchToControl(
        view: BaseTouchControl,
        action: Int,
        event: MotionEvent,
        pointerIndex: Int,
    ): Boolean {
        val copy = MotionEvent.obtain(
            event.downTime,
            event.eventTime,
            action,
            // A floating stick is translated away from where it was laid out; every other control has none.
            event.getX(pointerIndex) - view.left - view.translationX,
            event.getY(pointerIndex) - view.top - view.translationY,
            event.metaState,
        )
        val handled = view.dispatchTouchEvent(copy)
        copy.recycle()
        return handled
    }

    /** Releases every control currently held, so nothing stays latched in the native
     *  gamepad mailbox when the layout is rebuilt or the controls are switched off. */
    private fun cancelActivePointers() {
        // Sweep every control, not just the ones we hold a pointer for: a control can
        // also be mid-press from a touch the platform delivered directly.
        controlViews.forEach { dispatchCancel(it) }
        pointerTargets.clear()
        if (cursorPointerId != MotionEvent.INVALID_POINTER_ID) {
            cursorPointerId = MotionEvent.INVALID_POINTER_ID
            if (cursorIsMouse) {
                syncMouseButtons(0, cursorX, cursorY)
                cursorIsMouse = false
            } else {
                cursorTouchListener?.invoke(MotionEvent.ACTION_UP, cursorX, cursorY, cursorButton)
            }
        }
    }

    /**
     * Mouse button changes that arrive outside the touch stream (a button pressed or released
     * while another is held is a generic motion event). Ignored unless the mouse is the cursor.
     */
    fun onMouseButtonChange(buttonState: Int, x: Float, y: Float) {
        if (cursorIsMouse) syncMouseButtons(buttonState, x, y)
    }

    /** Sends a press or release for each mouse button whose state differs from the last call. */
    private fun syncMouseButtons(buttonState: Int, x: Float, y: Float) {
        cursorX = x
        cursorY = y
        val now = mouseButtonMask(buttonState)
        val (pressed, released) = mouseButtonChanges(mouseButtons, now)
        mouseButtons = now
        for (button in 0..2) {
            if (released and (1 shl button) != 0) {
                cursorTouchListener?.invoke(MotionEvent.ACTION_UP, x, y, button)
            }
            if (pressed and (1 shl button) != 0) {
                cursorTouchListener?.invoke(MotionEvent.ACTION_DOWN, x, y, button)
            }
        }
    }

    private fun dispatchCancel(view: BaseTouchControl) {
        val copy = MotionEvent.obtain(
            0L, 0L, MotionEvent.ACTION_CANCEL, 0f, 0f, 0,
        )
        view.dispatchTouchEvent(copy)
        copy.recycle()
    }

    private fun selectView(view: BaseTouchControl?) {
        selectedView?.isSelectedNode = false
        selectedView?.invalidate()

        selectedView = view

        selectedView?.isSelectedNode = true
        selectedView?.invalidate()

        updateEditorPanel()
    }

    private fun updateEditorPanel() {
        val nodeTitle = cachedNodeTitle ?: return
        val visibleSwitch = cachedVisibleSwitch ?: return
        val scaleSlider = cachedScaleSlider ?: return

        val selected = selectedView
        if (selected != null && inEditMode && page == EditorPage.EDIT) {
            val node = selected.node
            nodeTitle.text = "Settings: ${node.label.ifEmpty { node.id }}"
            visibleSwitch.isChecked = node.visible
            scaleSlider.progress = ((node.scale - 0.5f) * 100).toInt()
            nodePanel.visibility = View.VISIBLE
            dockNodePanel()
        } else {
            nodePanel.visibility = View.GONE
        }
    }

    /**
     * Puts the selected control's dialog where it does not cover that control: the player is dragging the
     * control, and a dialog on top of it would be in the way (DialogDock picks the spot).
     */
    private fun dockNodePanel() {
        val selected = selectedView ?: return
        if (nodePanel.visibility != View.VISIBLE || width == 0 || height == 0) return
        val d = resources.displayMetrics.density
        nodePanel.measure(
            MeasureSpec.makeMeasureSpec((NODE_PANEL_DP * d).toInt(), MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
        )
        val reserved = listOf(
            // The tab strip, and the standalone editor's Done button at the top end.
            Box(width / 2f - 160 * d, 0f, width / 2f + 160 * d, 64 * d),
            Box(width - 140 * d, 0f, width.toFloat(), 80 * d),
        )
        val (x, y) = DialogDock.place(
            screenW = width.toFloat(),
            screenH = height.toFloat(),
            dialogW = nodePanel.measuredWidth.toFloat(),
            dialogH = nodePanel.measuredHeight.toFloat(),
            control = Box(selected.left.toFloat(), selected.top.toFloat(), selected.right.toFloat(), selected.bottom.toFloat()),
            reserved = reserved,
            margin = 12 * d,
            gap = 12 * d,
            others = controlViews.filter { it !== selected && it.node.visible }
                .map { Box(it.left.toFloat(), it.top.toFloat(), it.right.toFloat(), it.bottom.toFloat()) },
        )
        nodePanel.translationX = x
        nodePanel.translationY = y
    }

    companion object {
        private const val NODE_PANEL_DP = 240
        private const val RESET_LABEL = "Reset Layout"
        private const val RESET_CONFIRM_LABEL = "Tap again to confirm"
        private const val RESET_CONFIRM_WINDOW_MS = 2500L

        // In edit mode, visible controls are shown at least this opaque so they're
        // easy to see and drag, even if the user's global opacity is very low.
        private const val EDIT_MODE_MIN_OPACITY = 0.85f

        // Hidden controls are shown faintly in edit mode so they can be re-enabled.
        private const val EDIT_HIDDEN_OPACITY = 0.3f

        // Resolution slider spans render scale MIN_RENDER_SCALE..1.0 across its 0..100 range.
        private val MIN_RENDER_SCALE = TouchControlManager.MIN_RENDER_SCALE
        private val MIN_OPACITY = TouchControlManager.MIN_OPACITY

        /** The slider bottoms out at MIN_OPACITY, not at invisible. */
        private fun progressToOpacity(progress: Int): Float =
            MIN_OPACITY + (progress / 100f) * (1f - MIN_OPACITY)

        private fun opacityToProgress(opacity: Float): Int =
            (((opacity - MIN_OPACITY) / (1f - MIN_OPACITY)) * 100f).toInt().coerceIn(0, 100)

        private fun progressToRenderScale(progress: Int): Float =
            MIN_RENDER_SCALE + (progress / 100f) * (1f - MIN_RENDER_SCALE)

        private fun renderScaleToProgress(scale: Float): Int =
            (((scale - MIN_RENDER_SCALE) / (1f - MIN_RENDER_SCALE)) * 100f).toInt().coerceIn(0, 100)
    }
}

/** Android's mouse button bits as GLFW button bits: bit 0 left, 1 right, 2 middle. */
internal fun mouseButtonMask(buttonState: Int): Int {
    var mask = 0
    if (buttonState and MotionEvent.BUTTON_PRIMARY != 0) mask = mask or 1
    if (buttonState and MotionEvent.BUTTON_SECONDARY != 0) mask = mask or 2
    if (buttonState and MotionEvent.BUTTON_TERTIARY != 0) mask = mask or 4
    return mask
}

/** The (newly pressed, newly released) button masks going from [previous] to [now]. */
internal fun mouseButtonChanges(previous: Int, now: Int): Pair<Int, Int> =
    Pair(now and previous.inv(), previous and now.inv())
