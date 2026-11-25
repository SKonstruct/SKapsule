package com.skarm.launcher

import android.content.Context
import java.io.File
import java.security.MessageDigest

enum class ModsSyncAction { NONE, APPLY, RESTORE }

/**
 * When the launcher applies the mods by itself: the same rule and the same cases as the iOS app (SKModSync.h).
 *
 * The inputs are two fingerprints: the mods folder (name, size and time of each mod) and the game's own file list
 * (digest2.txt, it changes when the game updates and puts its jars back). They are saved after every apply or reset.
 * Applying again is needed when either changed. Removing every mod restores the game once. After "Reset Game Files"
 * the current fingerprints are saved, so the mods already in the folder stay unapplied until the folder changes.
 */
object ModsSync {
    private const val PREFS = "launcher_prefs"
    private const val KEY_MODS = "mods_sync_mods"
    private const val KEY_GAME = "mods_sync_game"

    /** `storedMods` / `storedGame` are null before anything was applied or reset; `storedMods` is "" after a restore with no mods. */
    internal fun decide(
        gameInstalled: Boolean,
        mods: String,
        game: String,
        storedMods: String?,
        storedGame: String?,
    ): ModsSyncAction {
        if (!gameInstalled) return ModsSyncAction.NONE
        val haveMods = mods.isNotEmpty()
        if (storedMods == null) return if (haveMods) ModsSyncAction.APPLY else ModsSyncAction.NONE
        if (!haveMods) return if (storedMods.isNotEmpty()) ModsSyncAction.RESTORE else ModsSyncAction.NONE
        return if (mods == storedMods && game == (storedGame ?: "")) ModsSyncAction.NONE else ModsSyncAction.APPLY
    }

    /** What the launcher should do now; reads only file attributes and one small file. */
    fun pending(context: Context, gameHome: File): ModsSyncAction {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return decide(
            File(gameHome, "rsrc").exists(),
            modsFingerprint(gameHome),
            gameFingerprint(gameHome),
            prefs.getString(KEY_MODS, null),
            prefs.getString(KEY_GAME, null),
        )
    }

    /** The state a sync decision rests on, so a failed one is reported once rather than on every return. */
    fun key(gameHome: File): String = modsFingerprint(gameHome) + "#" + gameFingerprint(gameHome)

    /** What was just applied or restored, so the same folder is not applied again. */
    fun record(context: Context, gameHome: File) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_MODS, modsFingerprint(gameHome))
            .putString(KEY_GAME, gameFingerprint(gameHome))
            .apply()
    }

    internal fun modsFingerprint(gameHome: File): String =
        File(gameHome, "mods").listFiles { f -> f.isFile && (f.name.endsWith(".zip") || f.name.endsWith(".modpack")) }
            .orEmpty()
            .sortedBy { it.name }
            .joinToString("|") { "${it.name}:${it.length()}:${it.lastModified() / 1000}" }

    /** Not the jars: a private-server choice rewrites config.jar after every apply. */
    internal fun gameFingerprint(gameHome: File): String {
        val digest = File(gameHome, "digest2.txt")
        if (!digest.isFile) return ""
        return MessageDigest.getInstance("SHA-1").digest(digest.readBytes()).joinToString("") { "%02x".format(it) }
    }
}
