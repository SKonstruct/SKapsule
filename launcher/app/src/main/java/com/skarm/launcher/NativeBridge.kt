package com.skarm.launcher

import android.view.Surface

/**
 * Single entry point into the native side.
 */
object NativeBridge {

    init {
        // Load gl4es first so libsklauncher's dlopen("libgl4es.so") just
        // returns the existing handle. System.loadLibrary picks the right
        // ABI variant from jniLibs/.
        System.loadLibrary("gl4es")
        System.loadLibrary("sklauncher")
    }

    external fun onSurfaceCreated(surface: Surface)
    external fun onSurfaceChanged(width: Int, height: Int)

    /** The graphics pass (cpp/skgfx.c): offscreen on/off, scene scale, sharpening 0..1, Sharp HUD, native window size. */
    external fun setGfxConfig(enabled: Boolean, scale: Float, sharpen: Float, hud: Boolean, nativeWidth: Int, nativeHeight: Int)

    /** Frames the game has presented so far, for the FPS counter. */
    external fun frameCount(): Long
    external fun onSurfaceDestroyed()

    /**
     * Spins up the JVM on its own pthread and returns immediately since JVM init happens async.
     * @param cacioDir directory holding the staged cacio jars (AWT bridge); empty to disable.
     * @param frenchpressJar path to frenchpress.jar; prepended to SK's classpath (Steam-login
     *   shim). Empty to disable.
     * @param credFile path to frenchpress's Steam refresh-token store (FRENCHPRESS_CRED_FILE).
     * @param binDir directory prepended to the JVM's PATH; holds the xdg-open shim symlink so
     *   SK's Runtime.exec("xdg-open <url>") resolves to it. Empty to leave PATH untouched.
     */
    /**
     * Process-wide, because the JVM is: a GameActivity that is destroyed and recreated while the
     * game runs (a configuration change, say) must neither start it again nor show the boot
     * overlay over a game that is already drawing.
     */
    @Volatile var jvmStarted = false

    @Volatile var renderReady = false

    /** The saved web account, handed to the game as its login. Call before [startJvm]. */
    external fun setWebLogin(user: String, pass: String)

    external fun startJvm(
        jreHome: String,
        classpath: String,
        libPath: String,
        appFiles: String,
        cacioDir: String,
        frenchpressJar: String,
        credFile: String,
        binDir: String,
        screenWidth: Int,
        screenHeight: Int,
        maxHeapMb: Int,
    )

    /**
     * Forwards a touch as a mouse event into the native input queue.
     * @param action 0=down, 1=move, 2=up
     * @param x,y framebuffer pixels (y-down, top-left origin)
     * @param button GLFW mouse button (0=left, 1=right, 2=middle)
     */
    external fun onTouchEvent(action: Int, x: Int, y: Int, button: Int)

    /** One notch of the wheel; positive is up. SK reads discrete ticks, not pixels. */
    external fun onScroll(ticks: Int)

    /** Marks a controller connected/disconnected. Disconnect zeroes held state. */
    external fun onGamepadConnected(connected: Boolean)

    /**
     * Marks the on-screen touch controls (a virtual controller) active/inactive.
     * SK treats the pad as connected while either this or a physical pad is on,
     * so touch button/axis writes aren't dropped when no real controller exists.
     */
    external fun onVirtualGamepadConnected(connected: Boolean)

    /** Sets a button in the GLFW standard layout (index 0..14). */
    external fun onGamepadButton(index: Int, pressed: Boolean)

    /** Sets an axis in the GLFW standard layout (index 0..5), value already normalized. */
    external fun onGamepadAxis(index: Int, value: Float)

    /** A key transition. key = GLFW keycode; action 1=press, 2=repeat, 0=release. */
    external fun onKeyEvent(key: Int, action: Int, mods: Int)

    /** A typed character (Unicode codepoint) for text entry. */
    external fun onCharInput(codepoint: Int)

    // --- JVM/native -> Android boot-overlay callbacks --------------------------
    // Invoked FROM native (cached JavaVM -> FindClass NativeBridge -> static call),
    // not from Kotlin. The bootstrap jar and frenchpress run in SK's classloader
    // (parent = platform classloader) and can't see app classes, so getdown
    // progress and Steam-auth status reach the Activity through native, which
    // fans out to whichever Activity registered as the listener.

    /** Implemented by GameActivity to receive boot-phase status on the UI thread. */
    interface BootListener {
        fun onLaunchStatus(message: String)
        fun onRenderReady()

        /** The JVM thread gave up before the game started; [message] says why, for users. */
        fun onBootFailed(message: String)
    }

    @Volatile
    private var bootListener: BootListener? = null

    fun setBootListener(listener: BootListener?) {
        bootListener = listener
    }

    /** Native -> a human-readable boot-phase status (getdown %, "Waiting for Steam…"). */
    @JvmStatic
    fun onLaunchStatus(message: String) {
        bootListener?.onLaunchStatus(message)
    }

    /** Native -> the JVM could not start or died before the game did. */
    @JvmStatic
    fun onBootFailed(message: String) {
        bootListener?.onBootFailed(message)
    }

    /** Native -> SK has produced its first frame; the overlay can be dismissed. */
    @JvmStatic
    fun onRenderReady() {
        renderReady = true
        bootListener?.onRenderReady()
    }

    // --- JVM/native -> the game's own mouse cursor --------------------------------
    // The game's cursor is window-cursor art (RGBA pixels and a hotspot) that the GLFW shim in the bootstrap hands to
    // native when the game selects it; native passes it here and GameActivity draws it, if the user switched the
    // cursor on. [rgba] is null when the game has no cursor art selected.

    /** Implemented by GameActivity to draw the game's cursor on the UI thread. */
    interface CursorListener {
        fun onCursorImage(width: Int, height: Int, rgba: ByteArray?, hotX: Int, hotY: Int)
    }

    private class CursorArt(val width: Int, val height: Int, val rgba: ByteArray?, val hotX: Int, val hotY: Int)

    private val cursorLock = Any()
    private var cursorListener: CursorListener? = null
    private var cursorArt: CursorArt? = null

    /** The art the game selected last is replayed to a newly attached listener (activity recreated). */
    fun setCursorListener(listener: CursorListener?) {
        val replay = synchronized(cursorLock) {
            cursorListener = listener
            cursorArt
        }
        if (listener != null && replay != null) listener.onCursorImage(replay.width, replay.height, replay.rgba, replay.hotX, replay.hotY)
    }

    /** Native (a game thread) -> the game selected this cursor art, or none. */
    @JvmStatic
    fun onCursorImage(width: Int, height: Int, rgba: ByteArray?, hotX: Int, hotY: Int) {
        val listener = synchronized(cursorLock) {
            cursorArt = CursorArt(width, height, rgba, hotX, hotY)
            cursorListener
        }
        listener?.onCursorImage(width, height, rgba, hotX, hotY)
    }

    // --- JVM/native -> Android Steam-login keep-alive --------------------------
    // frenchpress brackets its whole auth attempt with steamKeepAlive(true/false)
    // (via SkBootstrap.nativeSteamKeepAlive -> sklauncher.c -> here). While active,
    // a foreground service holds the :game process at a priority that survives the
    // user tabbing out to the Steam Mobile App to approve the sign-in. Routed
    // through the application Context, not the Activity, because the Activity's
    // surface is destroyed during exactly that tab-out.

    @Volatile
    private var appContext: android.content.Context? = null

    /** Called once per process from SkApplication.onCreate. */
    fun attachContext(ctx: android.content.Context) {
        appContext = ctx.applicationContext
    }

    /** Native -> start (active) or stop the sign-in keep-alive foreground service. */
    @JvmStatic
    fun steamKeepAlive(active: Boolean) {
        val ctx = appContext ?: return
        val intent = android.content.Intent(ctx, SteamAuthService::class.java)
        try {
            if (active) ctx.startForegroundService(intent) else ctx.stopService(intent)
        } catch (t: Throwable) {
            // A denied background-start (rare: auth somehow began off-foreground)
            // must not crash the login thread; degrade to the old kill-prone path.
            android.util.Log.w("NativeBridge", "steamKeepAlive($active) failed", t)
        }
    }

    // --- JVM/native -> Android Steam sign-in (QR) -------------------------------
    // frenchpress's login thread (a HotSpot thread attached to ART) shows the Steam challenge
    // through showSignIn, again whenever Steam rotates it, and polls signInCancelled until the
    // user approves in the Steam app, cancels, or it times out. None of it blocks.

    /** Implemented by GameActivity to surface the sign-in dialog on the UI thread. */
    interface SignInListener {
        /** Show the QR for [url] (rows of '1'/'0' in [modules]); update in place if already showing. */
        fun onShowSignIn(url: String, modules: String)

        fun onHideSignIn()
    }

    private val signInLock = Any()
    private var signInListener: SignInListener? = null
    private var activeSignIn: Pair<String, String>? = null

    @Volatile
    private var cancelRequested = false

    /** A sign-in already showing is replayed to a newly attached listener (activity recreated). */
    fun setSignInListener(listener: SignInListener?) {
        val replay = synchronized(signInLock) {
            signInListener = listener
            activeSignIn
        }
        if (listener != null && replay != null) listener.onShowSignIn(replay.first, replay.second)
    }

    /** Native (login thread) -> show or refresh the sign-in QR. */
    @JvmStatic
    fun showSignIn(url: String, modules: String) {
        val listener = synchronized(signInLock) {
            if (activeSignIn == null) cancelRequested = false
            activeSignIn = url to modules
            signInListener
        }
        listener?.onShowSignIn(url, modules)
    }

    /** Native (login thread) -> the sign-in ended (approved, cancelled, timed out or failed). */
    @JvmStatic
    fun hideSignIn() {
        val listener = synchronized(signInLock) {
            activeSignIn = null
            signInListener
        }
        listener?.onHideSignIn()
    }

    /** Native (login thread) -> has the user pressed Cancel? */
    @JvmStatic
    fun signInCancelled(): Boolean = cancelRequested

    /** UI thread -> the user gave up on signing in; frenchpress carries on without Steam. */
    fun cancelSignIn() {
        cancelRequested = true
    }
}
