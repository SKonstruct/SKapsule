package com.skarm.launcher.touch

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * The part of a touch layout that is worth sharing between devices and platforms: where each
 * control sits, how big it is, whether it shows, plus the global opacity and "Show Buttons".
 *
 * Left out on purpose: `renderScale` (a per-device performance setting -- a phone's export must
 * not change a tablet's framebuffer) and `controlsEnabled` (a user switch, not an arrangement).
 * Everything that decides what a control *does* (type, button/key code, label, flags) is also
 * left out: it always comes from the app's own node definitions, never from a file.
 */
class SharedLayout(
    val name: String,
    val globalOpacity: Float,
    val actionBarVisible: Boolean,
    val nodes: Map<String, NodeState>,
) {
    class NodeState(
        val xPercent: Float,
        val yPercent: Float,
        val scale: Float,
        val visible: Boolean,
    )
}

/** Why a file was refused. The whole file is refused: nothing is ever applied partially. */
class LayoutFormatException(val reason: Reason, message: String) : Exception(message) {
    enum class Reason { INVALID, NEWER, TOO_LARGE }
}

/**
 * The export/import file format, shared with the iOS app (`SKTouchControls.m`), so a layout
 * exported on one imports on the other:
 *
 * ```
 * { "format": "skapsule-controls", "version": 1, "name": "My layout",
 *   "globalOpacity": 0.4, "actionBarVisible": false,
 *   "nodes": [ { "id": "btn_esc", "xPercent": 0.06, "yPercent": 0.1, "scale": 1.0, "visible": true } ] }
 * ```
 *
 * Positions are fractions of the landscape surface, so they hold across screen sizes. A file is
 * untrusted input: [parse] checks the format and version, caps size and node count, requires
 * finite numbers and clamps them to the ranges the editor itself allows, rejects duplicate ids,
 * and ignores ids this build does not know. [applyTo] then lays the values over the app's own
 * nodes, so a file can move and resize controls but can never add one or change what it sends.
 */
object TouchLayoutShare {
    const val FORMAT = "skapsule-controls"
    const val VERSION = 1
    const val MAX_BYTES = 64 * 1024
    const val MAX_NODES = 64
    const val MAX_NAME = 40
    const val DEFAULT_IMPORT_NAME = "Imported"

    fun capture(layout: TouchLayoutData, name: String): SharedLayout = SharedLayout(
        name = cleanName(name),
        globalOpacity = layout.globalOpacity,
        actionBarVisible = layout.actionBarVisible,
        nodes = layout.nodes.associate {
            it.id to SharedLayout.NodeState(it.xPercent, it.yPercent, it.scale, it.visible)
        },
    )

    fun toJson(shared: SharedLayout): String = JSONObject().apply {
        put("format", FORMAT)
        put("version", VERSION)
        put("name", shared.name)
        put("globalOpacity", shared.globalOpacity.toDouble())
        put("actionBarVisible", shared.actionBarVisible)
        put(
            "nodes",
            JSONArray().apply {
                shared.nodes.forEach { (id, n) ->
                    put(
                        JSONObject().apply {
                            put("id", id)
                            put("xPercent", n.xPercent.toDouble())
                            put("yPercent", n.yPercent.toDouble())
                            put("scale", n.scale.toDouble())
                            put("visible", n.visible)
                        },
                    )
                }
            },
        )
    }.toString(2)

    fun parse(text: String): SharedLayout {
        if (text.length > MAX_BYTES) throw LayoutFormatException(LayoutFormatException.Reason.TOO_LARGE, "file too large")
        try {
            val root = JSONObject(text)
            if (root.opt("format") != FORMAT) throw invalid("not a SKapsule controls file")
            val version = root.opt("version") as? Int ?: throw invalid("no version")
            if (version > VERSION) {
                throw LayoutFormatException(LayoutFormatException.Reason.NEWER, "made by a newer version")
            }
            if (version != VERSION) throw invalid("unsupported version $version")

            val known = TouchControlManager.createDefaultLayout().nodes.map { it.id }.toSet()
            val array = root.optJSONArray("nodes") ?: throw invalid("no nodes")
            if (array.length() == 0 || array.length() > MAX_NODES) throw invalid("bad node count")

            val nodes = LinkedHashMap<String, SharedLayout.NodeState>()
            for (i in 0 until array.length()) {
                val o = array.optJSONObject(i) ?: throw invalid("node $i is not an object")
                val id = o.opt("id") as? String ?: throw invalid("node $i has no id")
                val state = SharedLayout.NodeState(
                    xPercent = o.finite("xPercent").toFloat().coerceIn(0f, 1f),
                    yPercent = o.finite("yPercent").toFloat().coerceIn(0f, 1f),
                    scale = o.finite("scale").toFloat()
                        .coerceIn(TouchControlManager.MIN_NODE_SCALE, TouchControlManager.MAX_NODE_SCALE),
                    visible = o.opt("visible") as? Boolean ?: throw invalid("node $i has no visibility"),
                )
                // An id this build doesn't have (a newer control) is skipped, but a repeat of a
                // known one means the file is inconsistent, so it is refused.
                if (id !in known) continue
                if (nodes.put(id, state) != null) throw invalid("duplicate id $id")
            }
            if (nodes.isEmpty()) throw invalid("no usable controls")

            return SharedLayout(
                name = cleanName(root.opt("name") as? String),
                globalOpacity = root.finite("globalOpacity").toFloat()
                    .coerceIn(TouchControlManager.MIN_OPACITY, 1f),
                actionBarVisible = root.opt("actionBarVisible") as? Boolean ?: throw invalid("no actionBarVisible"),
                nodes = nodes,
            )
        } catch (e: JSONException) {
            throw invalid(e.message ?: "unreadable JSON")
        }
    }

    /**
     * [base] with this arrangement laid over it: opacity and each known node's
     * position, size and visibility. `controlsEnabled` and `renderScale` stay [base]'s, and a
     * node the file doesn't mention keeps its default position.
     */
    fun applyTo(shared: SharedLayout, base: TouchLayoutData): TouchLayoutData {
        val nodes = TouchControlManager.createDefaultLayout().nodes.map { canonical ->
            val s = shared.nodes[canonical.id] ?: return@map canonical
            canonical.copy(
                xPercent = s.xPercent,
                yPercent = s.yPercent,
                scale = s.scale,
                visible = s.visible,
            )
        }.toMutableList()
        return TouchLayoutData(
            globalOpacity = shared.globalOpacity,
            controlsEnabled = base.controlsEnabled,
            // "Show Buttons" is per session, so a file cannot turn it on; it stays as it is.
            actionBarVisible = base.actionBarVisible,
            defaultVersion = TouchControlManager.DEFAULT_LAYOUT_VERSION,
            renderScale = base.renderScale,
            nodes = nodes,
        )
    }

    /** A name that fits a list row and a file name: trimmed, one line, no control characters. */
    fun cleanName(raw: String?): String {
        val s = raw.orEmpty().filter { !it.isISOControl() }.trim().take(MAX_NAME).trim()
        return s.ifEmpty { DEFAULT_IMPORT_NAME }
    }

    private fun JSONObject.finite(key: String): Double {
        val v = (opt(key) as? Number)?.toDouble() ?: throw invalid("$key is not a number")
        if (!v.isFinite()) throw invalid("$key is not finite")
        return v
    }

    private fun invalid(message: String) = LayoutFormatException(LayoutFormatException.Reason.INVALID, message)
}
