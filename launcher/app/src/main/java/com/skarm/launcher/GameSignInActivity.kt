package com.skarm.launcher

/**
 * The game screen for a first Steam sign-in: declared portrait in the manifest, so the QR is shown
 * in the same orientation as the launcher, and turned landscape by [GameActivity] once the sign-in is
 * over. A separate class because an activity's launch orientation can only come from the manifest.
 */
class GameSignInActivity : GameActivity() {
    override fun startsInPortrait() = true
}
