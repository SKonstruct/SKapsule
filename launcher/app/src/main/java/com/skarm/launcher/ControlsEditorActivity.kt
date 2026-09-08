package com.skarm.launcher

import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.doOnLayout
import com.skarm.launcher.databinding.ActivityControlsEditorBinding

/**
 * The touch-control editor, without a game behind it.
 *
 * Hosts the same [com.skarm.launcher.touch.TouchControlOverlay] the game uses, in edit
 * mode, over a plain backdrop — so controls can be arranged from the launcher instead of
 * booting the JVM and logging in first. In edit mode the overlay reports no input and
 * touches neither GL nor the JVM, so there is nothing to stand in for.
 *
 * Landscape, because the layout is stored as percentages of the landscape game surface;
 * arranging it in portrait would put everything somewhere else in play. Full-screen with
 * no insets, matching how the game hosts the overlay (there the "avoid screen edges"
 * margin applies to the surface only, not to the controls).
 */
class ControlsEditorActivity : AppCompatActivity() {

    private lateinit var binding: ActivityControlsEditorBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityControlsEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        enableImmersiveMode()

        // After layout: the overlay positions its controls from its own measured
        // bounds, so entering edit mode earlier would arrange them against zero.
        val overlay = binding.touchOverlay
        overlay.doOnLayout { if (!overlay.isEditing) overlay.toggleEditMode() }

        binding.btnDone.setOnClickListener { finishEditing() }
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() = finishEditing()
            },
        )
    }

    /**
     * Leaving edit mode is what writes the layout to disk, so it has to happen before the
     * activity goes away — and exactly once, however the user chose to leave.
     */
    private fun finishEditing() {
        if (binding.touchOverlay.isEditing) binding.touchOverlay.toggleEditMode()
        finish()
    }

    override fun onPause() {
        // Save on the way out for the paths that never reach finishEditing (recents, a
        // call, Home) -- leaving edit mode is what writes the layout.
        if (!isFinishing && binding.touchOverlay.isEditing) {
            binding.touchOverlay.toggleEditMode()
        }
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        // ...and back in, or the editor returns from the background as an inert play-mode
        // overlay: nothing draggable, and Done with nothing left to save.
        val overlay = binding.touchOverlay
        if (!overlay.isEditing) {
            overlay.doOnLayout { if (!overlay.isEditing) overlay.toggleEditMode() }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enableImmersiveMode()
    }

    private fun enableImmersiveMode() {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }
}
