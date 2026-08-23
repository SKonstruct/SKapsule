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
import com.skarm.launcher.NativeBridge

class TouchControlOverlay @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private var layoutData: TouchLayoutData = TouchControlManager.loadLayout(context)
    private val controlViews = mutableListOf<BaseTouchControl>()
    private var inEditMode = false

    // Play-mode touch routing: which control each active pointer was captured by, keyed
    // by pointer id. A pointer stays with the control it landed on for its whole life.
    private val pointerTargets = SparseArray<BaseTouchControl>()

    // Pointer currently driving SK's mouse cursor (the one that landed on no control).
    // SK's UI is a single cursor, so only the first such pointer is forwarded.
    private var cursorPointerId = MotionEvent.INVALID_POINTER_ID

    // Edit state
    private var selectedView: BaseTouchControl? = null
    private var dX = 0f
    private var dY = 0f

    // Editor UI Panel
    private val editorPanel: LinearLayout

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
            visibility = View.GONE
            // Bound the panel width so it doesn't stretch across the whole
            // screen (a MATCH_PARENT separator otherwise forces a WRAP_CONTENT
            // vertical LinearLayout to fill the full width).
            val panelWidth = (320 * resources.displayMetrics.density).toInt()
            layoutParams = LayoutParams(panelWidth, LayoutParams.WRAP_CONTENT, Gravity.CENTER)

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
                text = "Show Action Bar"
                setTextColor(Color.WHITE)
                tag = "actionBarSwitch"
                isChecked = layoutData.actionBarVisible
                setOnCheckedChangeListener { _, isChecked ->
                    layoutData.actionBarVisible = isChecked
                    applyControlAppearance()
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
                progress = (layoutData.globalOpacity * 100).toInt()
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                        layoutData.globalOpacity = progress / 100f
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
                    }
                    override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                    override fun onStopTrackingTouch(seekBar: SeekBar?) {
                        renderScaleChangeListener?.invoke(layoutData.renderScale)
                    }
                })
            }
            addView(resolutionSlider)

            // Reset Layout (double-tap gated so a stray tap doesn't wipe work)
            val resetButton = Button(context).apply {
                text = RESET_LABEL
                tag = "resetButton"
                setOnClickListener { handleResetTap(this) }
            }
            addView(resetButton)

            // Separator
            addView(
                View(context).apply {
                    setBackgroundColor(0x66FFFFFF.toInt())
                    layoutParams = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 2).apply {
                        setMargins(0, 16, 0, 16)
                    }
                },
            )

            // Node Settings (Hidden by default)
            val nodeTitle = TextView(context).apply {
                text = "Node Settings"
                setTextColor(Color.WHITE)
                tag = "nodeTitle"
                visibility = View.GONE
            }
            addView(nodeTitle)
            cachedNodeTitle = nodeTitle

            val visibleSwitch = Switch(context).apply {
                text = "Visible"
                setTextColor(Color.WHITE)
                tag = "visibleSwitch"
                visibility = View.GONE
                setOnCheckedChangeListener { _, isChecked ->
                    selectedView?.let {
                        it.node.visible = isChecked
                        it.alpha = if (isChecked) layoutData.globalOpacity else 0.3f // keep visible in edit mode
                    }
                }
            }
            addView(visibleSwitch)
            cachedVisibleSwitch = visibleSwitch

            val scaleLabel = TextView(context).apply {
                text = "Scale"
                setTextColor(Color.WHITE)
                tag = "scaleLabel"
                visibility = View.GONE
            }
            addView(scaleLabel)
            cachedScaleLabel = scaleLabel

            val scaleSlider = SeekBar(context).apply {
                max = 200 // 0.5x to 2.5x
                tag = "scaleSlider"
                visibility = View.GONE
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                        selectedView?.let {
                            val scale = 0.5f + (progress / 100f)
                            it.node.scale = scale
                            requestLayout()
                        }
                    }
                    override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                    override fun onStopTrackingTouch(seekBar: SeekBar?) {}
                })
            }
            addView(scaleSlider)
            cachedScaleSlider = scaleSlider
        }

        buildControls()
        addView(editorPanel)
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
    private fun applyControlAppearance() {
        val enabled = layoutData.controlsEnabled
        if (!enabled) cancelActivePointers()
        for (view in controlViews) {
            if (inEditMode) {
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
    }

    private fun applyEditModeAppearance(view: BaseTouchControl) {
        view.visibility = View.VISIBLE
        val shown = view.node.visible && !(view.node.isActionBar && !layoutData.actionBarVisible)
        view.alpha = if (shown) {
            maxOf(layoutData.globalOpacity, EDIT_MODE_MIN_OPACITY)
        } else {
            EDIT_HIDDEN_OPACITY
        }
    }

    private fun applyPlayModeAppearance(view: BaseTouchControl, enabled: Boolean) {
        val shown = view.node.visible && !(view.node.isActionBar && !layoutData.actionBarVisible)
        view.visibility = if (enabled && shown) View.VISIBLE else View.GONE
        view.alpha = layoutData.globalOpacity
    }

    var opacityChangeListener: ((Float) -> Unit)? = null

    // Notified with the chosen render scale (0.5..1.0) when the resolution slider
    // moves; the host Activity applies it to the game surface.
    var renderScaleChangeListener: ((Float) -> Unit)? = null

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
        val baseBtnSize = w * 0.067f

        for (view in controlViews) {
            val node = view.node
            val size = (if (node.type == ControlType.BUTTON) baseBtnSize else baseJoySize) * node.scale

            val cx = w * node.xPercent
            val cy = h * node.yPercent

            val l = (cx - size / 2).toInt()
            val t = (cy - size / 2).toInt()
            val r = (cx + size / 2).toInt()
            val b = (cy + size / 2).toInt()

            view.layout(l, t, r, b)
        }

        // Ensure editor panel is brought to front and centered
        editorPanel.bringToFront()
    }

    fun toggleEditMode() {
        // Whatever is under a finger right now stops receiving events on the other side
        // of this switch, so release it first.
        cancelActivePointers()
        inEditMode = !inEditMode
        editorPanel.visibility = if (inEditMode) View.VISIBLE else View.GONE

        if (!inEditMode) {
            // Save layout when exiting edit mode
            TouchControlManager.saveLayout(context, layoutData)
            selectedView = null
        }

        for (view in controlViews) {
            view.inEditMode = inEditMode
            view.isSelectedNode = false
            view.invalidate()
        }

        applyControlAppearance()
        updateEditorPanel()
        editModeChangeListener?.invoke(inEditMode)
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

    private fun resetLayout() {
        layoutData = TouchControlManager.createDefaultLayout()
        selectView(null)
        buildControls()
        TouchControlManager.saveLayout(context, layoutData)

        // Refresh the global controls to reflect the restored defaults.
        editorPanel.findViewWithTag<Switch>("enableSwitch")?.isChecked = layoutData.controlsEnabled
        editorPanel.findViewWithTag<Switch>("actionBarSwitch")?.isChecked = layoutData.actionBarVisible
        editorPanel.findViewWithTag<SeekBar>("opacitySlider")?.progress =
            (layoutData.globalOpacity * 100).toInt()
        editorPanel.findViewWithTag<SeekBar>("resolutionSlider")?.progress =
            renderScaleToProgress(layoutData.renderScale)
        renderScaleChangeListener?.invoke(layoutData.renderScale)

        editorPanel.bringToFront()
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
    var cursorTouchListener: ((action: Int, x: Float, y: Float) -> Unit)? = null

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!inEditMode) {
            return handlePlayTouch(event)
        }

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
                    dX = tappedView.x - event.x
                    dY = tappedView.y - event.y
                } else {
                    // Tap on empty space deselects
                    selectView(null)
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                selectedView?.let { view ->
                    var newX = event.x + dX
                    var newY = event.y + dY

                    // Constrain to screen
                    newX = newX.coerceIn(0f, width.toFloat() - view.width)
                    newY = newY.coerceIn(0f, height.toFloat() - view.height)

                    view.x = newX
                    view.y = newY

                    // Update node percent
                    view.node.xPercent = (newX + view.width / 2f) / width
                    view.node.yPercent = (newY + view.height / 2f) / height
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                // Done dragging
                return true
            }
        }
        return false
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

                val target = controlViews.lastOrNull { view ->
                    view.visibility == View.VISIBLE && isPointInsideView(x, y, view)
                }
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
                    cursorTouchListener?.invoke(MotionEvent.ACTION_DOWN, x, y)
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
                        cursorTouchListener?.invoke(
                            MotionEvent.ACTION_MOVE, event.getX(index), event.getY(index)
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
                    cursorTouchListener?.invoke(
                        MotionEvent.ACTION_UP, event.getX(index), event.getY(index)
                    )
                }
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                cancelActivePointers()
                if (cursorPointerId != MotionEvent.INVALID_POINTER_ID) {
                    cursorPointerId = MotionEvent.INVALID_POINTER_ID
                    cursorTouchListener?.invoke(MotionEvent.ACTION_UP, event.x, event.y)
                }
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
            event.getX(pointerIndex) - view.left,
            event.getY(pointerIndex) - view.top,
            event.metaState,
        )
        val handled = view.dispatchTouchEvent(copy)
        copy.recycle()
        return handled
    }

    /** Releases every control currently held, so nothing stays latched in the native
     *  gamepad mailbox when the layout is rebuilt or the controls are switched off. */
    private fun cancelActivePointers() {
        for (i in 0 until pointerTargets.size()) {
            dispatchCancel(pointerTargets.valueAt(i))
        }
        pointerTargets.clear()
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
        val scaleLabel = cachedScaleLabel ?: return
        val scaleSlider = cachedScaleSlider ?: return

        if (selectedView != null) {
            val node = selectedView!!.node
            nodeTitle.visibility = View.VISIBLE
            nodeTitle.text = "Settings: ${node.label.ifEmpty { node.id }}"

            visibleSwitch.visibility = View.VISIBLE
            visibleSwitch.isChecked = node.visible

            scaleLabel.visibility = View.VISIBLE
            scaleSlider.visibility = View.VISIBLE
            scaleSlider.progress = ((node.scale - 0.5f) * 100).toInt()
        } else {
            nodeTitle.visibility = View.GONE
            visibleSwitch.visibility = View.GONE
            scaleLabel.visibility = View.GONE
            scaleSlider.visibility = View.GONE
        }
    }

    companion object {
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

        private fun progressToRenderScale(progress: Int): Float =
            MIN_RENDER_SCALE + (progress / 100f) * (1f - MIN_RENDER_SCALE)

        private fun renderScaleToProgress(scale: Float): Int =
            (((scale - MIN_RENDER_SCALE) / (1f - MIN_RENDER_SCALE)) * 100f).toInt().coerceIn(0, 100)
    }
}
