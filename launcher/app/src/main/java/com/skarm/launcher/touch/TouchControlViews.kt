package com.skarm.launcher.touch

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import com.skarm.launcher.GameActivity
import com.skarm.launcher.NativeBridge
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

abstract class BaseTouchControl(context: Context, val node: ControlNode) : View(context) {
    var inEditMode: Boolean = false

    /** The dashed edit outline. The settings page keeps controls static (no game input) but unoutlined. */
    var outlineVisible: Boolean = true
    var isSelectedNode: Boolean = false

    private val editPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        pathEffect = android.graphics.DashPathEffect(floatArrayOf(10f, 10f), 0f)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (inEditMode && outlineVisible) {
            editPaint.color = if (isSelectedNode) Color.YELLOW else Color.GRAY
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), editPaint)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (inEditMode) return false // Overlay handles edit touches
        return handleGameTouch(event)
    }

    abstract fun handleGameTouch(event: MotionEvent): Boolean
}

object HudPalette {
    // Shared neutral palette for the in-game HUD (top bar, touch controls,
    // editor popup). Picked to feel like overlay chrome rather than themed UI:
    // dark, semi-transparent, no dynamic-color tint.
    const val SURFACE: Int = 0xB3000000.toInt() // popup panel
    const val BUTTON_IDLE: Int = 0x99202020.toInt() // touch button idle
    const val BUTTON_PRESSED: Int = 0xCC606060.toInt() // touch button pressed/toggled
    const val BUTTON_OUTLINE: Int = 0x47FFFFFF // touch button hairline edge, matches iOS
    const val JOY_BG: Int = 0x66202020.toInt() // joystick base (more transparent)
    const val JOY_KNOB: Int = 0xCC808080.toInt() // joystick knob
    const val NEW_MARK: Int = 0xFF963F6E.toInt() // brand pinkPressed (4.8:1 on the light button): the "new default" mark on Reset Layout
}

/** How visible an idle stick's resting spot is (0..255). */
private const val GHOST_ALPHA = 0x30

class TouchJoystickView(context: Context, node: ControlNode) : BaseTouchControl(context, node) {
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = HudPalette.JOY_BG
        style = Paint.Style.FILL
    }

    private val knobPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = HudPalette.JOY_KNOB
        style = Paint.Style.FILL
    }

    // What a stick shows while nobody holds it: a faint base where it would start from.
    private val ghostPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = HudPalette.JOY_BG
        alpha = GHOST_ALPHA
        style = Paint.Style.FILL
    }

    // The square zone behind a stick, and its hairline edge (same as the buttons').
    private val zonePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = HudPalette.JOY_BG
        style = Paint.Style.FILL
    }
    private val zoneOutlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = HudPalette.BUTTON_OUTLINE
        style = Paint.Style.STROKE
        strokeWidth = context.resources.displayMetrics.density
    }

    private var knobX = 0f
    private var knobY = 0f
    private var isDragging = false
    private var pointerId = -1

    /** True while a finger holds this stick; a held stick must not be put back by a layout pass. */
    val isHeld: Boolean get() = isDragging

    /**
     * Radius of the stick itself. The view is the larger square zone the stick moves in, so the
     * overlay says how big the stick inside it is.
     */
    var stickRadius = 0f

    private fun radius(): Float =
        if (stickRadius > 0f) stickRadius else min(width, height) / 2f

    // Where the stick's centre sits inside the view: the middle at rest, then wherever the thumb
    // lands in its zone.
    private var baseX = 0f
    private var baseY = 0f

    private val zoneRect = RectF()

    /** Moves the base to [x],[y] (view coordinates), kept inside the zone; returns how far it moved. */
    private fun placeBase(x: Float, y: Float): Pair<Float, Float> {
        val r = radius()
        val newX = x.coerceIn(r, maxOf(r, width - r))
        val newY = y.coerceIn(r, maxOf(r, height - r))
        val moved = Pair(newX - baseX, newY - baseY)
        baseX = newX
        baseY = newY
        return moved
    }

    // Specific mapping info
    private val axisX = if (node.type == ControlType.JOYSTICK_LEFT) GameActivity.GP_AXIS_LEFT_X else GameActivity.GP_AXIS_RIGHT_X
    private val axisY = if (node.type == ControlType.JOYSTICK_LEFT) GameActivity.GP_AXIS_LEFT_Y else GameActivity.GP_AXIS_RIGHT_Y

    // Holding previous aim direction briefly on release
    private var releaseAimTask: Runnable? = null
    private var lastReportedX = 0f
    private var lastReportedY = 0f

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        resetKnob()
    }

    private fun resetKnob() {
        baseX = width / 2f
        baseY = height / 2f
        knobX = baseX
        knobY = baseY
        if (node.type == ControlType.JOYSTICK_RIGHT) {
            // Keep the last reported direction for 200ms, then zero it out
            releaseAimTask?.let { removeCallbacks(it) }
            val task = Runnable { reportAxis(0f, 0f) }
            releaseAimTask = task
            postDelayed(task, 200)
        } else {
            reportAxis(0f, 0f)
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val radius = radius()

        // The zone the stick may start in: a rounded square, always shown so the player knows
        // where to put a thumb. The stick itself only appears under a held thumb.
        zoneRect.set(0f, 0f, width.toFloat(), height.toFloat())
        val corner = min(width, height) * 0.12f
        canvas.drawRoundRect(zoneRect, corner, corner, zonePaint)
        canvas.drawRoundRect(zoneRect, corner, corner, zoneOutlinePaint)
        if (isDragging) {
            canvas.drawCircle(baseX, baseY, radius, bgPaint)
            canvas.drawCircle(knobX, knobY, radius * 0.4f, knobPaint)
        } else {
            canvas.drawCircle(baseX, baseY, radius, ghostPaint)
        }

        super.onDraw(canvas)
    }

    override fun handleGameTouch(event: MotionEvent): Boolean {
        val action = event.actionMasked
        val pointerIndex = event.actionIndex

        when (action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                if (!isDragging) {
                    val x = event.getX(pointerIndex)
                    val y = event.getY(pointerIndex)
                    val radius = radius()
                    // The stick starts anywhere in its zone, under the thumb.
                    placeBase(x, y)
                    val dx = x - baseX
                    val dy = y - baseY

                    if (sqrt((dx * dx + dy * dy).toDouble()) <= radius) {
                        isDragging = true
                        pointerId = event.getPointerId(pointerIndex)

                        releaseAimTask?.let { removeCallbacks(it) }

                        if (node.type == ControlType.JOYSTICK_RIGHT) {
                            // Charge starts on the first press: hold the right trigger
                            // while the stick is held (SK charges while RT = +1) and aim
                            // with the stick. A quick tap-and-release is just a short RT
                            // press, i.e. a basic primary attack.
                            NativeBridge.onGamepadAxis(TouchControlManager.AXIS_RTRIGGER, 1f)
                        }

                        updateKnob(x, y)
                    }
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (isDragging) {
                    val index = event.findPointerIndex(pointerId)
                    if (index >= 0) {
                        updateKnob(event.getX(index), event.getY(index))
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                if (isDragging && event.getPointerId(pointerIndex) == pointerId) {
                    release()
                }
            }
            // A cancel (system gesture, layout rebuild, edit mode) carries no meaningful
            // pointer, so release unconditionally rather than matching the id — otherwise
            // the drag and the right-trigger charge stay asserted for good.
            MotionEvent.ACTION_CANCEL -> {
                if (isDragging) release()
            }
        }
        return isDragging
    }

    private fun release() {
        isDragging = false
        pointerId = -1
        // resetKnob also puts the stick back at the middle of its zone.
        resetKnob()

        if (node.type == ControlType.JOYSTICK_RIGHT) {
            // Release the charge (or end the tap-attack) on lift.
            NativeBridge.onGamepadAxis(TouchControlManager.AXIS_RTRIGGER, -1f)
        }
    }

    private fun updateKnob(x: Float, y: Float) {
        val radius = radius()
        val maxDist = radius * 0.6f
        if (maxDist <= 0f) return  // degenerate bounds would push NaN into the axis

        var dx = x - baseX
        var dy = y - baseY
        var dist = sqrt((dx * dx + dy * dy).toDouble()).toFloat()

        // The base follows a thumb dragged past its range, so it never runs out of
        // travel. The base moves by the excess, so the thumb ends up at the stick's edge. It stays
        // inside the zone.
        if (dist > maxDist) {
            val over = dist - maxDist
            val (movedX, movedY) = placeBase(baseX + dx / dist * over, baseY + dy / dist * over)
            dx -= movedX
            dy -= movedY
            dist = sqrt((dx * dx + dy * dy).toDouble()).toFloat()
        }

        val centerX = baseX
        val centerY = baseY
        if (dist <= maxDist) {
            knobX = centerX + dx
            knobY = centerY + dy
        } else {
            val angle = atan2(dy.toDouble(), dx.toDouble())
            knobX = centerX + (maxDist * cos(angle)).toFloat()
            knobY = centerY + (maxDist * sin(angle)).toFloat()
        }

        val normX = (knobX - centerX) / maxDist
        val normY = (knobY - centerY) / maxDist

        // If holding attack, report the tap direction even before the finger moves far
        reportAxis(normX, normY)
        invalidate()
    }

    private fun reportAxis(x: Float, y: Float) {
        lastReportedX = x
        lastReportedY = y
        NativeBridge.onGamepadAxis(axisX, x)
        NativeBridge.onGamepadAxis(axisY, y)
    }
}

class TouchButtonView(context: Context, node: ControlNode) : BaseTouchControl(context, node) {
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = HudPalette.BUTTON_IDLE
        style = Paint.Style.FILL
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        textSize = 40f
    }

    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = HudPalette.BUTTON_OUTLINE
        style = Paint.Style.STROKE
        strokeWidth = context.resources.displayMetrics.density
    }

    private val glyphPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    private fun withAlpha(color: Int, alpha: Int): Int = (color and 0x00FFFFFF) or (alpha shl 24)

    private var isPressedState = false
    private var isToggledOn = false

    private val buttonRect = RectF()

    override fun onDraw(canvas: Canvas) {
        val centerX = width / 2f
        val centerY = height / 2f
        val size = min(width, height).toFloat()

        buttonRect.set(
            centerX - size / 2f,
            centerY - size / 2f,
            centerX + size / 2f,
            centerY + size / 2f,
        )

        val lit = isPressedState || isToggledOn
        bgPaint.color = if (lit) HudPalette.BUTTON_PRESSED else HudPalette.BUTTON_IDLE

        // Use a rounded square instead of a circle
        val cornerRadius = size * 0.25f
        canvas.drawRoundRect(buttonRect, cornerRadius, cornerRadius, bgPaint)

        // An icon button is tinted in its colour: a wash over the dark fill, a coloured edge, and the glyph.
        val glyph = ControlGlyphs.forNode(node.id)
        if (glyph != null && glyph.color != ControlGlyphs.COLOR_NEUTRAL) {
            bgPaint.color = withAlpha(glyph.color, if (lit) 0x80 else 0x33)
            canvas.drawRoundRect(buttonRect, cornerRadius, cornerRadius, bgPaint)
        }

        // Hairline edge, matching the iOS buttons. Inset by half the stroke so it sits
        // inside the fill rather than straddling its edge.
        val half = outlinePaint.strokeWidth / 2f
        outlinePaint.color = if (glyph != null && glyph.color != ControlGlyphs.COLOR_NEUTRAL) {
            withAlpha(glyph.color, 0x99)
        } else {
            HudPalette.BUTTON_OUTLINE
        }
        buttonRect.inset(half, half)
        canvas.drawRoundRect(buttonRect, cornerRadius - half, cornerRadius - half, outlinePaint)
        buttonRect.inset(-half, -half)

        if (glyph != null) {
            val glyphSize = size * 0.56f
            val s = glyphSize / ControlGlyphs.GRID
            glyphPaint.color = glyph.color
            canvas.save()
            canvas.translate(centerX - glyphSize / 2f, centerY - glyphSize / 2f)
            canvas.scale(s, s)
            canvas.drawPath(glyph.path, glyphPaint)
            canvas.restore()
        } else if (node.label.isNotEmpty()) {
            // Draw label — scale text with button size, then shrink-to-fit so
            // multi-character labels don't run past the edge on small screens.
            val maxWidth = size * 0.85f
            var ts = size * 0.32f
            textPaint.textSize = ts
            val measured = textPaint.measureText(node.label)
            if (measured > maxWidth) {
                ts *= maxWidth / measured
                textPaint.textSize = ts
            }
            val metrics = textPaint.fontMetrics
            val baseline = centerY - (metrics.ascent + metrics.descent) / 2
            canvas.drawText(node.label, centerX, baseline, textPaint)
        }

        super.onDraw(canvas)
    }

    override fun handleGameTouch(event: MotionEvent): Boolean {
        val action = event.actionMasked

        when (action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                isPressedState = true
                if (node.isToggle) {
                    isToggledOn = !isToggledOn
                    reportInput(isToggledOn)
                } else {
                    reportInput(true)
                }
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                isPressedState = false
                if (!node.isToggle) {
                    reportInput(false)
                }
                invalidate()
                return true
            }
        }
        return false
    }

    private fun reportInput(pressed: Boolean) {
        when {
            node.keyCode >= 0 -> NativeBridge.onKeyEvent(node.keyCode, if (pressed) 1 else 0, 0)
            node.isAxisTrigger -> NativeBridge.onGamepadAxis(node.buttonCode, if (pressed) 1f else -1f)
            else -> NativeBridge.onGamepadButton(node.buttonCode, pressed)
        }
    }
}
