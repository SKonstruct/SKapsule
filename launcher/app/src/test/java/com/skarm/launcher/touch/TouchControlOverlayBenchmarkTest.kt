package com.skarm.launcher.touch

import android.content.Context
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements

// Mock NativeBridge so it doesn't fail on JNI calls during Robolectric tests
@Implements(com.skarm.launcher.NativeBridge::class)
class ShadowNativeBridge {
    companion object {
        @JvmStatic
        @Implementation
        fun onVirtualGamepadConnected(connected: Boolean) {
            // No-op
        }
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], shadows = [ShadowNativeBridge::class])
class TouchControlOverlayBenchmarkTest {

    private lateinit var classUnderTest: TouchControlOverlay
    private lateinit var context: Context
    private val iterations = 100_000

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        classUnderTest = TouchControlOverlay(context)

        // Set up mock layout data and controls to simulate many views
        val testLayoutData = TouchLayoutData(
            controlsEnabled = true,
            nodes = (0..50).map {
                ControlNode("btn$it", ControlType.BUTTON, it * 0.01f, it * 0.01f, 1.0f)
            }.toMutableList(),
        )

        val layoutDataField = TouchControlOverlay::class.java.getDeclaredField("layoutData")
        layoutDataField.isAccessible = true
        layoutDataField.set(classUnderTest, testLayoutData)

        val buildControlsMethod = TouchControlOverlay::class.java.getDeclaredMethod("buildControls")
        buildControlsMethod.isAccessible = true
        buildControlsMethod.invoke(classUnderTest)

        // Layout the view so controls have bounds
        classUnderTest.layout(0, 0, 1920, 1080)

        // Give them arbitrary bounds manually to make sure isPointInsideView works
        val controlViewsField = TouchControlOverlay::class.java.getDeclaredField("controlViews")
        controlViewsField.isAccessible = true
        val views = controlViewsField.get(classUnderTest) as List<View>

        for ((i, view) in views.withIndex()) {
            val left = i * 10
            val top = i * 10
            view.layout(left, top, left + 100, top + 100)
            view.visibility = View.VISIBLE
        }
    }

    @Test
    fun benchmarkOnTouchEvent() {
        // Create an event that hits the last view which is effectively top-most in layout
        // Last view index is 50, its layout is 500, 500, 600, 600
        val downTime = SystemClock.uptimeMillis()
        val eventTime = SystemClock.uptimeMillis()

        val x = 550f
        val y = 550f
        val event = MotionEvent.obtain(downTime, eventTime, MotionEvent.ACTION_DOWN, x, y, 0)

        // Warm up
        for (i in 0..10_000) {
            classUnderTest.onTouchEvent(event)
        }

        val startTime = System.nanoTime()

        for (i in 0 until iterations) {
            classUnderTest.onTouchEvent(event)
        }

        val endTime = System.nanoTime()
        val durationMs = (endTime - startTime) / 1_000_000.0

        println("Benchmark TouchControlOverlay.onTouchEvent ($iterations iterations): $durationMs ms")

        event.recycle()
        assertTrue(durationMs > 0)
    }
}
