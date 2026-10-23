package com.skarm.launcher

/**
 * Removes what a log is known to carry that must not leave the device: Steam and getdown
 * credentials, tokens and cookies, email addresses, SteamID64s and the app's private paths.
 * Applied to the log a player shares and to everything sent to Sentry.
 */
object LogScrubber {
    private const val SECRET_KEYS =
        "FRENCHPRESS_STEAM_USER|FRENCHPRESS_STEAM_PASS|SK_WEB_USER|SK_WEB_PASS|pass(?:word|wd)?|(?:refresh_?|access_?)?token|" +
            "session(?:id)?|auth|cookie|ticket|secret|api[_-]?key"

    // "token":"abc" / "password": "abc"
    private val JSON_SECRET = Regex("(?i)(\"(?:$SECRET_KEYS)\")\\s*:\\s*\"[^\"]*\"")

    // Authorization: Bearer abc.def  (the generic rule below would stop at "Bearer")
    private val AUTHORIZATION = Regex("(?i)\\b(authorization)\\s*[:=]\\s*(?:(?:bearer|basic)\\s+)?\\S+")

    // token=abc, password: abc, FRENCHPRESS_STEAM_PASS=abc
    private val KEYED = Regex("(?i)\\b($SECRET_KEYS)\\s*[=:]\\s*\\S+")

    private val EMAIL = Regex("[\\w.+-]+@[\\w-]+\\.[\\w.]{2,}")
    private val STEAM_ID64 = Regex("\\b7656119\\d{10}\\b")

    /** [appDirs] are private directories (the data dir in both spellings) shown as `<app>`. */
    fun scrub(text: String, appDirs: List<String> = emptyList()): String {
        var out = text
        for (dir in appDirs) if (dir.length > 1) out = out.replace(dir, "<app>")
        return out
            .replace(JSON_SECRET, "$1:\"[redacted]\"")
            .replace(AUTHORIZATION, "$1: [redacted]")
            .replace(KEYED, "$1=[redacted]")
            .replace(EMAIL, "[redacted-email]")
            .replace(STEAM_ID64, "[steamid]")
    }
}
