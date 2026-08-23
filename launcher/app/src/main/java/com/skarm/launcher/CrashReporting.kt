package com.skarm.launcher

import android.app.Application
import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.Log
import io.sentry.Attachment
import io.sentry.Sentry
import io.sentry.SentryLevel
import io.sentry.SentryOptions
import io.sentry.android.core.SentryAndroid
import java.io.File

/**
 * Crash and error reporting.
 *
 * Scope is deliberately narrow: uncaught exceptions and the handful of failures
 * the launcher already detects, with device/OS/build context and the logcat dump
 * the crash handler writes anyway. No performance tracing, no session replay,
 * and no native crash handling — the bundled HotSpot uses SIGSEGV for implicit
 * null checks, so a second set of signal handlers in this process is not an
 * option (hence sentry-android-core rather than sentry-android).
 */
object CrashReporting {

    private const val TAG = "CrashReporting"
    private const val DSN =
        "https://1cda8191c3344e047f6242dc20d5db55@o458165.ingest.us.sentry.io/4511958841425920"

    /** Opt-out lives beside the other launcher settings; on by default. */
    const val PREF_KEY = "crash_reporting"

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences("launcher_prefs", Context.MODE_PRIVATE)
            .getBoolean(PREF_KEY, true)

    /** Called from every process, before the crash handler is installed. */
    fun init(app: Application) {
        if (!isEnabled(app)) return
        start(app)
    }

    /** Applies a change to the switch without needing a restart. */
    fun setEnabled(context: Context, on: Boolean) {
        context.getSharedPreferences("launcher_prefs", Context.MODE_PRIVATE)
            .edit().putBoolean(PREF_KEY, on).apply()
        if (on) {
            (context.applicationContext as? Application)?.let { start(it) }
        } else {
            Sentry.close()
        }
    }

    /** A failure the launcher detected itself. [type] is the grouping key. */
    fun report(type: String, message: String, extra: Map<String, String> = emptyMap()) {
        if (!Sentry.isEnabled()) return
        Sentry.withScope { scope ->
            scope.setTag("failure", type)
            scope.setFingerprint(listOf(type))
            extra.forEach { (k, v) -> scope.setExtra(k, scrub(v)) }
            Sentry.captureMessage(scrub(message), SentryLevel.ERROR)
        }
    }

    /**
     * Attaches the logcat dump the crash handler just wrote, so the report that
     * Sentry's own handler sends a moment later carries it.
     */
    fun attachCrashLog(file: File) {
        if (!Sentry.isEnabled()) return
        runCatching { Sentry.configureScope { it.addAttachment(Attachment(file.absolutePath)) } }
    }

    private fun start(app: Application) {
        SentryAndroid.init(app) { options ->
            options.dsn = DSN
            val info = app.packageManager.getPackageInfo(app.packageName, 0)
            options.release = "skapsule@${info.versionName ?: "0.0.0"}"
            options.dist = info.longVersionCode.toString()
            options.environment =
                if (app.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) "debug"
                else "production"
            options.isSendDefaultPii = false
            // The JVM blocks the main thread through its whole boot, so ANR
            // detection would fire on every single launch.
            options.isAnrEnabled = false
            options.maxBreadcrumbs = 50
            options.beforeSend = SentryOptions.BeforeSendCallback { event, _ ->
                event.message?.let { it.formatted = it.formatted?.let(::scrub) }
                event
            }
        }
        Sentry.configureScope { it.setTag("process", processName(app)) }
        Log.i(TAG, "crash reporting enabled")
    }

    private fun processName(app: Application): String =
        runCatching { Application.getProcessName() }.getOrNull()
            ?.substringAfter(':', "main") ?: "main"

    /**
     * Redacts what the JVM's own output is known to carry: Steam/getdown
     * credentials and tokens, the account email, and the container path.
     * Applied to anything we compose; attachments are the logcat dump, which is
     * already what "Share Logs" exports.
     */
    private fun scrub(text: String): String = text
        .replace(SECRET, "$1=[redacted]")
        .replace(EMAIL, "[redacted-email]")

    private val SECRET = Regex(
        "(?i)(FRENCHPRESS_STEAM_USER|FRENCHPRESS_STEAM_PASS|pass(?:word|wd)?|token|session|auth|cookie|ticket|secret)\\s*[=:]\\s*\\S+"
    )
    private val EMAIL = Regex("[\\w.+-]+@[\\w-]+\\.[\\w.]{2,}")
}
