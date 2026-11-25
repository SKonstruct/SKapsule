package com.skarm.launcher

import android.net.Uri
import android.text.InputFilter
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.skarm.launcher.touch.LayoutFormatException
import com.skarm.launcher.touch.SharedLayout
import com.skarm.launcher.touch.TouchControlManager
import com.skarm.launcher.touch.TouchControlOverlay
import com.skarm.launcher.touch.TouchLayoutPresets
import com.skarm.launcher.touch.TouchLayoutShare
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

/**
 * The editor's layout library: a built-in default, the user's named presets, and export/import of
 * a layout as a file. Owned by the Activity that hosts the editor ([ControlsEditorActivity] and
 * [GameActivity]; the pickers need an Activity result registry, so it must be created in
 * `onCreate`). Files go through the system picker, so the app needs no storage permission and only
 * ever touches the one file the user chose.
 *
 * Applying anything replaces the arrangement, so it always asks first. Import never overwrites a
 * preset: it saves under a new name and then offers to apply it. What a file may change is
 * decided in [TouchLayoutShare], not here.
 */
class LayoutLibraryController(
    private val activity: AppCompatActivity,
    private val overlay: TouchControlOverlay,
) {
    private val presets = TouchLayoutPresets(activity)

    private val exportLauncher = activity.registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> if (uri != null) writeExport(uri) }

    private val importLauncher = activity.registerForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> if (uri != null) readImport(uri) }

    private fun str(@StringRes id: Int, vararg args: Any) = activity.getString(id, *args)

    fun show() {
        val saved = presets.all()
        val active = presets.active
        val labels = ArrayList<String>()
        val actions = ArrayList<() -> Unit>()
        fun row(label: String, action: () -> Unit) {
            labels += label
            actions += action
        }

        row(mark(str(R.string.layouts_default_row), active == TouchLayoutPresets.BUILT_IN_NAME)) {
            confirmApply(TouchLayoutPresets.BUILT_IN_NAME, null, null)
        }
        saved.forEach { preset ->
            row(mark(preset.name, active == preset.name)) { confirmApply(preset.name, preset, null) }
        }
        row(str(R.string.layouts_save_current)) { promptSave() }
        row(str(R.string.layouts_export)) { startExport() }
        row(str(R.string.layouts_import)) { importLauncher.launch(arrayOf("*/*")) }

        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.layouts_title)
            .setItems(labels.toTypedArray()) { _, which -> actions[which]() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun mark(label: String, active: Boolean) = if (active) "✓ $label" else label

    /** [preset] null means the built-in default. [intro] leads the message after an import. */
    private fun confirmApply(name: String, preset: SharedLayout?, intro: String?) {
        val builder = MaterialAlertDialogBuilder(activity)
            .setTitle(str(R.string.layouts_apply_title, name))
            .setMessage((intro?.plus("\n\n") ?: "") + str(R.string.layouts_apply_message))
            .setPositiveButton(R.string.layouts_apply) { _, _ -> apply(name, preset) }
            .setNegativeButton(android.R.string.cancel, null)
        // The built-in default can't be deleted.
        if (preset != null) builder.setNeutralButton(R.string.layouts_delete) { _, _ -> confirmDelete(name) }
        builder.show()
    }

    private fun apply(name: String, preset: SharedLayout?) {
        val shared = preset ?: TouchLayoutShare.capture(TouchControlManager.createDefaultLayout(), name)
        overlay.applyLayout(TouchLayoutShare.applyTo(shared, overlay.currentLayoutSnapshot()))
        presets.active = name
        toast(str(R.string.layouts_applied, name))
    }

    private fun confirmDelete(name: String) {
        MaterialAlertDialogBuilder(activity)
            .setTitle(str(R.string.layouts_delete_title, name))
            .setMessage(R.string.layouts_delete_message)
            .setPositiveButton(R.string.layouts_delete) { _, _ ->
                presets.delete(name)
                toast(str(R.string.layouts_deleted, name))
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun promptSave() {
        val input = EditText(activity).apply {
            setHint(R.string.layouts_name_hint)
            setSingleLine()
            // Landscape: without this the keyboard goes full-screen and hides the Save button.
            imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI
            filters = arrayOf(InputFilter.LengthFilter(TouchLayoutShare.MAX_NAME))
        }
        val pad = (20 * activity.resources.displayMetrics.density).toInt()
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.layouts_save_title)
            .setView(input, pad, pad / 2, pad, 0)
            .setPositiveButton(R.string.layouts_save) { _, _ ->
                val shared = TouchLayoutShare.capture(overlay.currentLayoutSnapshot(), input.text.toString())
                val name = presets.add(shared)
                if (name == null) {
                    toast(str(R.string.layouts_full, TouchLayoutPresets.MAX_PRESETS))
                } else {
                    presets.active = name
                    toast(str(R.string.layouts_saved, name))
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun exportName() =
        presets.active?.takeIf { it != TouchLayoutPresets.BUILT_IN_NAME } ?: str(R.string.layouts_default_name)

    private fun startExport() {
        val slug = exportName().lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').ifEmpty { "layout" }
        exportLauncher.launch("skapsule-controls-$slug.json")
    }

    private fun writeExport(uri: Uri) {
        val json = TouchLayoutShare.toJson(TouchLayoutShare.capture(overlay.currentLayoutSnapshot(), exportName()))
        try {
            val out = activity.contentResolver.openOutputStream(uri, "wt") ?: throw IOException("no stream")
            out.use { it.write(json.toByteArray(Charsets.UTF_8)) }
            toast(str(R.string.layouts_exported))
        } catch (e: Exception) {
            toast(str(R.string.layouts_write_failed))
        }
    }

    private fun readImport(uri: Uri) {
        val shared = try {
            val stream = activity.contentResolver.openInputStream(uri) ?: throw IOException("no stream")
            TouchLayoutShare.parse(stream.use(::readCapped))
        } catch (e: LayoutFormatException) {
            toast(
                str(
                    when (e.reason) {
                        LayoutFormatException.Reason.NEWER -> R.string.layouts_err_newer
                        LayoutFormatException.Reason.TOO_LARGE -> R.string.layouts_err_large
                        LayoutFormatException.Reason.INVALID -> R.string.layouts_err_invalid
                    },
                ),
            )
            return
        } catch (e: Exception) {
            toast(str(R.string.layouts_read_failed))
            return
        }
        val name = presets.add(shared)
        if (name == null) {
            toast(str(R.string.layouts_full, TouchLayoutPresets.MAX_PRESETS))
            return
        }
        confirmApply(name, presets.get(name), str(R.string.layouts_imported_as, name))
    }

    /** At most [TouchLayoutShare.MAX_BYTES] of text; a bigger file is refused, not truncated. */
    private fun readCapped(stream: InputStream): String {
        val buffer = ByteArrayOutputStream()
        val chunk = ByteArray(8192)
        while (true) {
            val n = stream.read(chunk)
            if (n < 0) break
            buffer.write(chunk, 0, n)
            if (buffer.size() > TouchLayoutShare.MAX_BYTES) {
                throw LayoutFormatException(LayoutFormatException.Reason.TOO_LARGE, "file too large")
            }
        }
        return buffer.toString(Charsets.UTF_8.name()).removePrefix("﻿")
    }

    private fun toast(message: String) = Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
}
