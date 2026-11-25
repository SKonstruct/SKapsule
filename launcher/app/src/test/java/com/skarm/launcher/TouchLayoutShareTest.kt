package com.skarm.launcher

import com.skarm.launcher.touch.LayoutFormatException
import com.skarm.launcher.touch.LayoutFormatException.Reason
import com.skarm.launcher.touch.TouchControlManager
import com.skarm.launcher.touch.TouchLayoutShare
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * The export/import format. The two fixtures are the same files the iOS tests parse
 * (SKLayoutShareTests.m): one written by each app, so an export from either must import on the
 * other. Keep src/test/resources identical to SKapsule-iOS/Natives/Tests/fixtures.
 */
class TouchLayoutShareTest {
    private fun fixture(name: String) =
        javaClass.classLoader!!.getResourceAsStream(name)!!.bufferedReader().use { it.readText() }

    private fun valid(): JSONObject = JSONObject(
        """{"format":"skapsule-controls","version":1,"name":"Test","globalOpacity":0.5,"actionBarVisible":true,
            "nodes":[{"id":"btn_esc","xPercent":0.1,"yPercent":0.2,"scale":1.5,"visible":true}]}""",
    )

    private fun reason(text: String): Reason? = try {
        TouchLayoutShare.parse(text)
        null
    } catch (e: LayoutFormatException) {
        e.reason
    }

    @Test
    fun bothAppsExportsParse() {
        for (file in listOf("controls-android-export.json", "controls-ios-export.json")) {
            val l = TouchLayoutShare.parse(fixture(file))
            assertEquals("$file: all 16 known nodes", 16, l.nodes.size)
            assertNull("an id this build lacks is ignored", l.nodes["btn_future"])
            assertEquals(0.12f, l.nodes.getValue("btn_esc").xPercent, 1e-6f)
            assertEquals(1.5f, l.nodes.getValue("btn_esc").scale, 1e-6f)
            assertFalse(l.nodes.getValue("btn_strafe").visible)
            assertEquals(0.55f, l.globalOpacity, 1e-6f)
            assertTrue(l.actionBarVisible)
        }
    }

    @Test
    fun exportThenImportIsLossless() {
        val l = TouchLayoutShare.parse(fixture("controls-ios-export.json"))
        val again = TouchLayoutShare.parse(TouchLayoutShare.toJson(l))
        assertEquals(l.name, again.name)
        assertEquals(l.nodes.keys, again.nodes.keys)
        assertEquals(l.nodes.getValue("btn_item2").xPercent, again.nodes.getValue("btn_item2").xPercent, 1e-6f)
        // iOS reads this back with its integer check, so version must be a bare 1, not 1.0.
        assertEquals(1, JSONObject(TouchLayoutShare.toJson(l)).get("version"))
    }

    @Test
    fun theWholeFileIsRefusedNeverPartly() {
        assertEquals(Reason.INVALID, reason(valid().put("format", "other").toString()))
        assertEquals(Reason.NEWER, reason(valid().put("version", 2).toString()))
        assertEquals(Reason.INVALID, reason(valid().put("version", 1.5).toString()))
        assertEquals(Reason.INVALID, reason(valid().put("version", 0).toString()))
        assertEquals(Reason.INVALID, reason(valid().put("nodes", JSONArray()).toString()))
        assertEquals(Reason.INVALID, reason(valid().apply { remove("actionBarVisible") }.toString()))
        assertEquals(Reason.INVALID, reason("not json"))
        assertEquals(Reason.INVALID, reason(""))
        assertEquals(Reason.TOO_LARGE, reason(" ".repeat(TouchLayoutShare.MAX_BYTES + 1)))

        fun withNodes(vararg nodes: String) =
            valid().put("nodes", JSONArray("[" + nodes.joinToString(",") + "]")).toString()
        val esc = """{"id":"btn_esc","xPercent":0.1,"yPercent":0.2,"scale":1,"visible":true}"""
        assertEquals(
            "only unknown ids",
            Reason.INVALID,
            reason(withNodes("""{"id":"btn_future","xPercent":0.1,"yPercent":0.1,"scale":1,"visible":true}""")),
        )
        assertEquals("a repeated known id", Reason.INVALID, reason(withNodes(esc, esc)))
        assertEquals(
            "a number given as a string",
            Reason.INVALID,
            reason(withNodes("""{"id":"btn_esc","xPercent":"0.1","yPercent":0.2,"scale":1,"visible":true}""")),
        )
        assertEquals(
            "a number given as a boolean",
            Reason.INVALID,
            reason(withNodes("""{"id":"btn_esc","xPercent":true,"yPercent":0.2,"scale":1,"visible":true}""")),
        )
        assertEquals(
            "malformed even though its id is unknown",
            Reason.INVALID,
            reason(withNodes("""{"id":"btn_future","xPercent":"bad","yPercent":0.2,"scale":1,"visible":true}""", esc)),
        )
        assertEquals(
            "more than 64 nodes",
            Reason.INVALID,
            reason(withNodes(*Array(TouchLayoutShare.MAX_NODES + 1) { esc })),
        )
    }

    @Test
    fun valuesAreClampedToWhatTheEditorAllows() {
        val text = valid().put("globalOpacity", 0.0).put(
            "nodes",
            JSONArray("""[{"id":"btn_esc","xPercent":-3,"yPercent":9,"scale":100,"visible":false}]"""),
        ).toString()
        val l = TouchLayoutShare.parse(text)
        val n = l.nodes.getValue("btn_esc")
        assertEquals(0f, n.xPercent, 0f)
        assertEquals(1f, n.yPercent, 0f)
        assertEquals(TouchControlManager.MAX_NODE_SCALE, n.scale, 0f)
        assertEquals(TouchControlManager.MIN_OPACITY, l.globalOpacity, 0f)
    }

    @Test
    fun namesAreTidy() {
        assertEquals("ab", TouchLayoutShare.cleanName("  a\nb\t "))
        assertEquals("Imported", TouchLayoutShare.cleanName("   "))
        assertEquals("Imported", TouchLayoutShare.cleanName(null))
        assertEquals(TouchLayoutShare.MAX_NAME, TouchLayoutShare.cleanName("x".repeat(80)).length)
    }

    @Test
    fun aFileCanMoveControlsButNeverChangeWhatTheySend() {
        val base = TouchControlManager.createDefaultLayout().also {
            it.controlsEnabled = false
            it.renderScale = 0.9f
        }
        val applied = TouchLayoutShare.applyTo(TouchLayoutShare.parse(fixture("controls-android-export.json")), base)

        // Moved, as the file says...
        val esc = applied.nodes.first { it.id == "btn_esc" }
        assertEquals(0.12f, esc.xPercent, 1e-6f)
        assertEquals(1.5f, esc.scale, 1e-6f)
        assertFalse(applied.nodes.first { it.id == "btn_strafe" }.visible)
        // ...but what each control sends is the app's own, and no control was added.
        val canonical = TouchControlManager.createDefaultLayout().nodes.associateBy { it.id }
        assertEquals(canonical.keys, applied.nodes.map { it.id }.toSet())
        for (node in applied.nodes) {
            val c = canonical.getValue(node.id)
            assertEquals(c.keyCode, node.keyCode)
            assertEquals(c.buttonCode, node.buttonCode)
            assertEquals(c.isToggle, node.isToggle)
            assertEquals(c.isAxisTrigger, node.isAxisTrigger)
            assertEquals(c.label, node.label)
            assertEquals(c.type, node.type)
        }
        // Device and user settings stay as they were.
        assertFalse(applied.controlsEnabled)
        assertEquals(0.9f, applied.renderScale, 0f)
    }

    @Test
    fun aNodeTheFileOmitsKeepsItsDefaultPosition() {
        val applied = TouchLayoutShare.applyTo(
            TouchLayoutShare.parse(valid().toString()), // only btn_esc
            TouchControlManager.createDefaultLayout(),
        )
        val canonical = TouchControlManager.createDefaultLayout().nodes.first { it.id == "joy_move" }
        val joy = applied.nodes.first { it.id == "joy_move" }
        assertEquals(canonical.xPercent, joy.xPercent, 0f)
        assertNotNull(applied.nodes.first { it.id == "btn_esc" })
    }

    @Test
    fun aFloatingStickTravelsInTheFileAndAFileWithoutItIsNotFloating() {
        val layout = TouchControlManager.createDefaultLayout()
        layout.nodes.first { it.id == "joy_move" }.floating = true
        val json = TouchLayoutShare.toJson(TouchLayoutShare.capture(layout, "Floaty"))
        val back = TouchLayoutShare.parse(json)
        assertTrue(back.nodes.getValue("joy_move").floating)
        assertFalse(back.nodes.getValue("joy_face").floating)
        // Written only when on, so a file from before floating sticks reads as not floating.
        assertFalse(json.contains("\"floating\": false"))
        assertFalse(TouchLayoutShare.parse(fixture("controls-android-export.json")).nodes.getValue("joy_move").floating)
    }

    @Test
    fun floatingMustBeABoolean() {
        val bad = valid().put(
            "nodes",
            JSONArray("""[{"id":"joy_move","xPercent":0.1,"yPercent":0.2,"scale":1,"visible":true,"floating":"yes"}]"""),
        )
        assertEquals(Reason.INVALID, reason(bad.toString()))
    }

    @Test
    fun onlyAJoystickCanFloat() {
        val text = valid().put(
            "nodes",
            JSONArray(
                """[{"id":"joy_move","xPercent":0.1,"yPercent":0.2,"scale":1,"visible":true,"floating":true},
                    {"id":"btn_esc","xPercent":0.1,"yPercent":0.2,"scale":1,"visible":true,"floating":true}]""",
            ),
        ).toString()
        val applied = TouchLayoutShare.applyTo(TouchLayoutShare.parse(text), TouchControlManager.createDefaultLayout())
        assertTrue(applied.nodes.first { it.id == "joy_move" }.floating)
        assertFalse("a file cannot make a button float", applied.nodes.first { it.id == "btn_esc" }.floating)
    }

    @Test
    fun aNewerFileIsRefusedWithTheNewerReason() {
        try {
            TouchLayoutShare.parse(valid().put("version", 99).toString())
            fail("expected a refusal")
        } catch (e: LayoutFormatException) {
            assertEquals(Reason.NEWER, e.reason)
        }
    }
}
