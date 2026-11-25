package com.skarm.launcher.touch

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * The user's saved layouts, by name. The built-in default is not stored here: it is the app's
 * own [TouchControlManager.createDefaultLayout], always available and never deletable.
 *
 * Kept apart from the live layout (`touch_controls_prefs`), so Reset Layout and edits never
 * touch a saved preset. The library is opened from the launcher's editor (main process) and from
 * the in-game editor (`:game` process), and SharedPreferences is cached per process, so this is a
 * plain file read fresh on every call and replaced atomically on every write: each process always
 * sees the other's changes. Each entry is stored as the export format and re-validated through
 * [TouchLayoutShare.parse] on every read, so a corrupt entry is skipped, never applied.
 */
class TouchLayoutPresets(context: Context) {
    private val file = File(context.applicationContext.filesDir, FILE_NAME)

    private class State(val active: String?, val presets: List<SharedLayout>)

    private fun read(): State {
        val root = runCatching { JSONObject(file.readText()) }.getOrNull() ?: return State(null, emptyList())
        val array = root.optJSONArray("presets") ?: JSONArray()
        val presets = (0 until array.length()).mapNotNull { i ->
            val entry = array.optJSONObject(i) ?: return@mapNotNull null
            runCatching { TouchLayoutShare.parse(entry.toString()) }.getOrNull()
        }
        return State(root.optString("active").takeIf { it.isNotEmpty() }, presets)
    }

    private fun write(state: State) {
        val root = JSONObject().apply {
            state.active?.let { put("active", it) }
            put("presets", JSONArray().apply { state.presets.forEach { put(JSONObject(TouchLayoutShare.toJson(it))) } })
        }
        val tmp = File(file.parentFile, "$FILE_NAME.tmp")
        tmp.writeText(root.toString())
        if (!tmp.renameTo(file)) tmp.delete()
    }

    /** The last preset applied (or saved/imported), for the checkmark in the list. It is a
     *  marker, not a promise: editing afterwards does not clear it. */
    var active: String?
        get() = read().active
        set(value) = write(State(value, read().presets))

    fun all(): List<SharedLayout> = read().presets

    fun get(name: String): SharedLayout? = all().firstOrNull { it.name == name }

    /**
     * Saves [layout] as a new preset and returns the name it got: its own name, or "name (2)"
     * and so on when that is taken or is the built-in's name. Never overwrites. Null when the
     * list is full.
     */
    fun add(layout: SharedLayout): String? {
        val state = read()
        if (state.presets.size >= MAX_PRESETS) return null
        val taken = state.presets.map { it.name.lowercase() }.toSet() + BUILT_IN_NAME.lowercase()
        val base = TouchLayoutShare.cleanName(layout.name)
        var name = base
        var n = 2
        while (name.lowercase() in taken) {
            val suffix = " ($n)"
            name = base.take(TouchLayoutShare.MAX_NAME - suffix.length).trim() + suffix
            n++
        }
        write(State(state.active, state.presets + SharedLayout(name, layout.globalOpacity, layout.actionBarVisible, layout.nodes)))
        return name
    }

    fun delete(name: String) {
        val state = read()
        write(State(state.active.takeIf { it != name }, state.presets.filter { it.name != name }))
    }

    companion object {
        private const val FILE_NAME = "touch_layout_presets.json"
        const val MAX_PRESETS = 32

        /** Reserved: a user preset can't take this name, so the list never shows two. */
        const val BUILT_IN_NAME = "Default"
    }
}
