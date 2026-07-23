package com.skarm.launcher

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.CircularProgressIndicatorSpec
import com.google.android.material.progressindicator.IndeterminateDrawable
import com.skarm.launcher.databinding.ActivityLauncherBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Launcher home screen. Two paths to Spiral Knights:
 *   - Web account login
 *   - Steam-linked account login
 *
 * Also responsible for first-launch JRE 25 extraction. Buttons are disabled
 * until the runtime is on disk; subsequent launches skip the unpack entirely.
 */
class LauncherActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLauncherBinding

    // Captured dump awaiting the SAF picker's chosen destination (the save flow is
    // async: launch picker -> onResult writes to the URI). Held across that hop.
    private var pendingSave: File? = null

    // CreateDocument picker (no storage permission needed; DocumentsUI does the
    // write). Result is the user-chosen content:// URI, or null if cancelled.
    private val saveLog = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain"),
    ) { uri ->
        val src = pendingSave.also { pendingSave = null }
        if (uri == null || src == null) return@registerForActivityResult
        val ok = LogExporter.copyToUri(this, src, uri)
        Toast.makeText(
            this,
            if (ok) R.string.save_logs_saved else R.string.save_logs_failed,
            Toast.LENGTH_SHORT,
        ).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLauncherBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Set title version dynamically
        val ver = try {
            packageManager.getPackageInfo(packageName, 0).versionName
        } catch (e: Exception) {
            "1.0.0"
        }
        binding.title.text = "SKapsule v$ver"

        // Open options sidebar
        binding.btnOptions.setOnClickListener {
            binding.drawerLayout.openDrawer(GravityCompat.END)
        }

        // Adjust sidebar padding for safe area (camera cutout, status bar, navigation bar)
        val originalPaddingTop = binding.sidebarContentLayout.paddingTop
        val originalPaddingBottom = binding.sidebarContentLayout.paddingBottom
        val originalPaddingLeft = binding.sidebarContentLayout.paddingLeft
        val originalPaddingRight = binding.sidebarContentLayout.paddingRight

        ViewCompat.setOnApplyWindowInsetsListener(binding.sidebarContentLayout) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val cutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout())

            val topInset = maxOf(systemBars.top, cutout.top)
            val leftInset = maxOf(systemBars.left, cutout.left)
            val rightInset = maxOf(systemBars.right, cutout.right)
            val bottomInset = maxOf(systemBars.bottom, cutout.bottom)

            view.setPadding(
                originalPaddingLeft + leftInset,
                originalPaddingTop + topInset,
                originalPaddingRight + rightInset,
                originalPaddingBottom + bottomInset,
            )
            insets
        }

        val launcherPrefs = getSharedPreferences("launcher_prefs", MODE_PRIVATE)

        // Segmented control listener
        binding.modeToggleGroup.addOnButtonCheckedListener { group, checkedId, isChecked ->
            val button = group.findViewById<MaterialButton>(checkedId) ?: return@addOnButtonCheckedListener
            if (isChecked) {
                button.textSize = 15f
                button.setTypeface(null, android.graphics.Typeface.BOLD)
                button.setBackgroundColor(android.graphics.Color.parseColor("#ab4a81"))
                button.setTextColor(android.graphics.Color.WHITE)
                val mode = if (checkedId == R.id.btn_mode_steam) "Steam" else "Web"
                launcherPrefs.edit().putString("login_mode", mode).apply()
            } else {
                button.textSize = 13f
                button.setTypeface(null, android.graphics.Typeface.NORMAL)
                button.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                button.setTextColor(android.graphics.Color.parseColor("#A0A0A0"))
            }
        }

        // Restore saved selection
        val savedMode = launcherPrefs.getString("login_mode", "Web")
        if (savedMode == "Steam") {
            binding.modeToggleGroup.check(R.id.btn_mode_steam)
            binding.btnModeSteam.textSize = 15f
            binding.btnModeSteam.setTypeface(null, android.graphics.Typeface.BOLD)
            binding.btnModeSteam.setBackgroundColor(android.graphics.Color.parseColor("#ab4a81"))
            binding.btnModeSteam.setTextColor(android.graphics.Color.WHITE)
            binding.btnModeWeb.textSize = 13f
            binding.btnModeWeb.setTypeface(null, android.graphics.Typeface.NORMAL)
            binding.btnModeWeb.setBackgroundColor(android.graphics.Color.TRANSPARENT)
            binding.btnModeWeb.setTextColor(android.graphics.Color.parseColor("#A0A0A0"))
        } else {
            binding.modeToggleGroup.check(R.id.btn_mode_web)
            binding.btnModeWeb.textSize = 15f
            binding.btnModeWeb.setTypeface(null, android.graphics.Typeface.BOLD)
            binding.btnModeWeb.setBackgroundColor(android.graphics.Color.parseColor("#ab4a81"))
            binding.btnModeWeb.setTextColor(android.graphics.Color.WHITE)
            binding.btnModeSteam.textSize = 13f
            binding.btnModeSteam.setTypeface(null, android.graphics.Typeface.NORMAL)
            binding.btnModeSteam.setBackgroundColor(android.graphics.Color.TRANSPARENT)
            binding.btnModeSteam.setTextColor(android.graphics.Color.parseColor("#A0A0A0"))
        }

        // Launch Button click
        binding.btnLaunch.setOnClickListener {
            val checkedId = binding.modeToggleGroup.checkedButtonId
            val mode = if (checkedId == R.id.btn_mode_steam) LoginMode.Steam else LoginMode.Web
            if (mode == LoginMode.Steam) {
                onPlaySteam()
            } else {
                launchGame(LoginMode.Web)
            }
        }

        // Sidebar click listeners
        binding.sidebarBtnShareLogs.setOnClickListener { LogExporter.captureAndShare(this) }
        binding.sidebarBtnSaveLogs.setOnClickListener { onSaveLogs() }
        binding.sidebarBtnDownloadMods.setOnClickListener { downloadMods() }
        binding.sidebarBtnApplyMods.setOnClickListener { applyMods() }
        binding.sidebarBtnRemoveMods.setOnClickListener { removeMods() }
        binding.sidebarBtnOpenFolder.setOnClickListener { openFolder() }
        binding.sidebarBtnGithub.setOnClickListener { openUrl("https://github.com/SKonstruct/SKapsule") }
        binding.sidebarBtnDiscord.setOnClickListener { openUrl("https://dankware.alwaysdata.net/discord") }
        binding.sidebarBtnLogout.setOnClickListener { onLogout() }

        // Avoid screen edges switch
        val avoidEdgesSwitch = binding.switchAvoidEdges
        avoidEdgesSwitch.isChecked = launcherPrefs.getBoolean("avoid_screen_edges", false)
        avoidEdgesSwitch.setOnCheckedChangeListener { _, isChecked ->
            launcherPrefs.edit().putBoolean("avoid_screen_edges", isChecked).apply()
        }

        // If the previous session crashed, the handler auto-saved a dump. Offer to
        // share it right away (once per launch); the button stays available too.
        LogExporter.latestCrash(this)?.let { crash ->
            AlertDialog.Builder(this)
                .setMessage(R.string.share_logs_crash_found)
                .setPositiveButton(R.string.share_logs) { _, _ -> LogExporter.share(this, crash) }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }

        fetchPlayerCount()
        ensureRuntime()
    }

    /**
     * Gate the Share Logs button on there being something worth sending: a launch
     * was attempted this install, or a crash dump is on disk. Re-checked on resume
     * because the launch marker is written by the :game process while we're paused.
     */
    override fun onResume() {
        super.onResume()
        setLaunchButtonLoading(false)
        val hasLogs = LogExporter.wasLaunchAttempted(this) || LogExporter.latestCrash(this) != null
        binding.sidebarBtnShareLogs.isEnabled = hasLogs
        binding.sidebarBtnSaveLogs.isEnabled = hasLogs

        // Update logout button visibility
        val showLogout = FrenchpressInstaller.credFile(this).exists()
        binding.sidebarBtnLogout.visibility = if (showLogout) View.VISIBLE else View.GONE
        binding.sidebarDividerLogout.visibility = if (showLogout) View.VISIBLE else View.GONE
    }

    private fun openFolder() {
        val authority = "$packageName.documents"
        try {
            // Build root URI to open directly in Files app
            val rootUri = android.provider.DocumentsContract.buildRootUri(authority, filesDir.absolutePath)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(rootUri, "vnd.android.document/root")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        } catch (e: Exception) {
            try {
                // Fallback: open system file picker pointing to our DocumentsProvider root
                val fallbackIntent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                    val rootUri = android.provider.DocumentsContract.buildRootUri(authority, filesDir.absolutePath)
                    putExtra(android.provider.DocumentsContract.EXTRA_INITIAL_URI, rootUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                }
                startActivity(fallbackIntent)
            } catch (ex: Exception) {
                Toast.makeText(this, "Could not open folder", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: Exception) {
            Toast.makeText(this, "No browser found to open link", Toast.LENGTH_SHORT).show()
        }
    }

    private fun onLogout() {
        AlertDialog.Builder(this)
            .setMessage(R.string.logout_confirm_message)
            .setPositiveButton(R.string.logout_confirm_yes) { _, _ ->
                val cred = FrenchpressInstaller.credFile(this)
                if (cred.exists()) {
                    cred.delete()
                }
                binding.sidebarBtnLogout.visibility = View.GONE
                binding.sidebarDividerLogout.visibility = View.GONE
                Toast.makeText(this, "Logged out successfully", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /** Captures a dump, then opens the SAF picker (suggesting its filename) to save it. */
    private fun onSaveLogs() {
        val file = LogExporter.capture(this)
        if (file == null) {
            Toast.makeText(this, R.string.share_logs_empty, Toast.LENGTH_SHORT).show()
            return
        }
        pendingSave = file
        saveLog.launch(file.name)
    }

    private fun ensureRuntime() {
        val jreReady = JreInstaller.isInstalled(this)
        val lwjglReady = LwjglInstaller.isInstalled(this)
        val skBootstrapped = SkInstaller.isBootstrapped(this)
        if (jreReady && lwjglReady && skBootstrapped) {
            Log.i(TAG, "Runtime already installed (jre=$jreReady lwjgl=$lwjglReady sk=$skBootstrapped)")
            setButtonsEnabled(true)
            return
        }

        setButtonsEnabled(false)
        binding.setupGroup.visibility = View.VISIBLE
        binding.setupStatus.text = getString(R.string.setup_starting)

        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val report: (String) -> Unit = { msg ->
                        runOnUiThread { binding.setupStatus.text = msg }
                    }
                    if (!jreReady) JreInstaller.install(this@LauncherActivity, report)
                    if (!lwjglReady) LwjglInstaller.install(this@LauncherActivity, report)
                    if (!skBootstrapped) SkInstaller.bootstrap(this@LauncherActivity, report)
                }
                binding.setupGroup.visibility = View.GONE
                setButtonsEnabled(true)
            } catch (t: Throwable) {
                Log.e(TAG, "Runtime install failed", t)
                binding.setupStatus.text = "Runtime setup failed: ${t.message}"
            }
        }
    }

    private fun setButtonsEnabled(enabled: Boolean) {
        binding.btnLaunch.isEnabled = enabled
        binding.btnLaunch.alpha = if (enabled) 1.0f else 0.5f
    }

    /**
     * Steam path. frenchpress persists a Steam refresh token after the first
     * successful login, so we only prompt for username/password when no creds file
     * exists yet; subsequent launches resume silently from the token. The collected
     * credentials are passed straight through to the game JVM (env vars set in
     * sklauncher.c) and never stored in plaintext by the launcher.
     */
    private fun onPlaySteam() {
        if (FrenchpressInstaller.credFile(this).exists()) {
            launchGame(LoginMode.Steam)
        } else {
            promptSteamLogin()
        }
    }

    private fun promptSteamLogin() {
        val pad = (resources.displayMetrics.density * 20).toInt()
        val userField = EditText(this).apply {
            hint = getString(R.string.steam_username)
            inputType = InputType.TYPE_CLASS_TEXT
        }
        val passField = EditText(this).apply {
            hint = getString(R.string.steam_password)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad / 2, pad, 0)
            addView(userField)
            addView(passField)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.steam_login_title)
            .setMessage(R.string.steam_login_message)
            .setView(layout)
            .setPositiveButton(R.string.steam_login_ok) { _, _ ->
                val user = userField.text.toString().trim()
                val pass = passField.text.toString()
                // Empty username => web account; frenchpress treats it as such.
                launchGame(LoginMode.Steam, user, pass)
            }
            .setNegativeButton(android.R.string.cancel) { _, _ ->
                setLaunchButtonLoading(false)
            }
            .show()
    }

    private fun setLaunchButtonLoading(loading: Boolean) {
        if (loading) {
            val spec = CircularProgressIndicatorSpec(this, null).apply {
                indicatorColors = intArrayOf(Color.WHITE)
                trackThickness = (3 * resources.displayMetrics.density).toInt()
                indicatorSize = (20 * resources.displayMetrics.density).toInt()
            }
            val progressDrawable = IndeterminateDrawable.createCircularDrawable(this, spec)
            binding.btnLaunch.icon = progressDrawable
            binding.btnLaunch.iconTint = ColorStateList.valueOf(Color.WHITE)
            binding.btnLaunch.text = "Launching..."
            binding.btnLaunch.setTextColor(Color.WHITE)
        } else {
            binding.btnLaunch.icon = androidx.core.content.ContextCompat.getDrawable(this, android.R.drawable.ic_media_play)
            binding.btnLaunch.iconTint = ColorStateList.valueOf(Color.WHITE)
            binding.btnLaunch.text = getString(R.string.launch)
            binding.btnLaunch.setTextColor(Color.WHITE)
        }
    }

    private fun launchGame(mode: LoginMode, steamUser: String = "", steamPass: String = "") {
        setLaunchButtonLoading(true)
        startActivity(
            Intent(this, GameActivity::class.java).apply {
                putExtra(EXTRA_LOGIN_MODE, mode.name)
                if (steamUser.isNotEmpty()) {
                    putExtra(EXTRA_STEAM_USER, steamUser)
                    putExtra(EXTRA_STEAM_PASS, steamPass)
                }
            },
        )
    }

    private fun fetchPlayerCount() {
        lifecycleScope.launch {
            val count = withContext(Dispatchers.IO) {
                try {
                    val url = java.net.URL("https://api.steampowered.com/ISteamUserStats/GetNumberOfCurrentPlayers/v1/?appid=99900")
                    val conn = url.openConnection() as java.net.HttpURLConnection
                    conn.connectTimeout = 5000
                    conn.readTimeout = 5000
                    val text = conn.inputStream.bufferedReader().readText()
                    conn.disconnect()
                    val json = org.json.JSONObject(text)
                    val steam = json.getJSONObject("response").getInt("player_count")
                    Math.round(steam * 1.4f)
                } catch (e: Exception) {
                    -1
                }
            }
            if (count > 0) {
                binding.subtitle.text = "Fight alongside ~$count other Spiral Knights!"
            }
        }
    }

    private class ProgressDialogInfo(
        val dialog: AlertDialog,
        val updateProgress: (String, Int, Int) -> Unit
    )

    private fun showProgressDialog(titleRes: Int, initialText: String): ProgressDialogInfo {
        val builder = AlertDialog.Builder(this)
        val pad = (resources.displayMetrics.density * 20).toInt()
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }
        val statusText = TextView(this).apply {
            text = initialText
            setTextColor(Color.WHITE)
            textSize = 16f
        }
        val progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = true
            progressTintList = ColorStateList.valueOf(Color.parseColor("#ab4a81"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = (resources.displayMetrics.density * 12).toInt()
            }
        }
        layout.addView(statusText)
        layout.addView(progressBar)

        builder.setTitle(titleRes)
        builder.setView(layout)
        builder.setCancelable(false)
        val dialog = builder.create()
        dialog.show()

        val updateProgress: (String, Int, Int) -> Unit = { status, current, total ->
            runOnUiThread {
                statusText.text = status
                if (total > 0) {
                    progressBar.isIndeterminate = false
                    progressBar.max = total
                    progressBar.progress = current
                } else {
                    progressBar.isIndeterminate = true
                }
            }
        }

        return ProgressDialogInfo(dialog, updateProgress)
    }

    private fun downloadMods() {
        val progressInfo = showProgressDialog(R.string.mod_sync_title, "Starting download…")

        lifecycleScope.launch {
            try {
                val modsDir = File(SkInstaller.homeDir(this@LauncherActivity), "mods")
                val stats = ModsDownloader.sync(modsDir, progressInfo.updateProgress)
                progressInfo.dialog.dismiss()
                Toast.makeText(
                    this@LauncherActivity,
                    "Download complete: ${stats.downloaded} downloaded, ${stats.skipped} skipped, ${stats.deleted} deleted",
                    Toast.LENGTH_LONG,
                ).show()
            } catch (e: Exception) {
                progressInfo.dialog.dismiss()
                AlertDialog.Builder(this@LauncherActivity)
                    .setTitle("Download Failed")
                    .setMessage(e.message ?: "Unknown error")
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
            }
        }
    }

    private fun applyMods() {
        val progressInfo = showProgressDialog(R.string.mod_apply_title, "Starting apply…")

        lifecycleScope.launch {
            try {
                val gameHome = SkInstaller.homeDir(this@LauncherActivity)
                val stats = ModsApplier.apply(gameHome, progressInfo.updateProgress)
                progressInfo.dialog.dismiss()
                val message = buildString {
                    append("Applied: ${stats.getTotalModsApplied()} mods (${stats.resourceModsApplied} resource, ${stats.classModsApplied} class, ${stats.modpacksApplied} modpacks).\n")
                    append("Unpacked ${stats.jarsUnpacked} resources.")
                    if (stats.warnings.isNotEmpty()) {
                        append("\n\nWarnings:\n")
                        stats.warnings.forEach { append("- ").append(it).append("\n") }
                    }
                }
                AlertDialog.Builder(this@LauncherActivity)
                    .setTitle(R.string.mod_completed)
                    .setMessage(message.trim())
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
            } catch (e: Exception) {
                progressInfo.dialog.dismiss()
                AlertDialog.Builder(this@LauncherActivity)
                    .setTitle("Apply Failed")
                    .setMessage(e.message ?: "Unknown error")
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
            }
        }
    }

    private fun removeMods() {
        val progressInfo = showProgressDialog(R.string.mod_remove_title, "Starting remove…")

        lifecycleScope.launch {
            try {
                val gameHome = SkInstaller.homeDir(this@LauncherActivity)
                val jarsUnpacked = ModsApplier.remove(gameHome, progressInfo.updateProgress)
                progressInfo.dialog.dismiss()
                AlertDialog.Builder(this@LauncherActivity)
                    .setTitle(R.string.mod_completed)
                    .setMessage(getString(R.string.mod_removed, jarsUnpacked))
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
            } catch (e: Exception) {
                progressInfo.dialog.dismiss()
                AlertDialog.Builder(this@LauncherActivity)
                    .setTitle("Remove Failed")
                    .setMessage(e.message ?: "Unknown error")
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
            }
        }
    }

    enum class LoginMode { Web, Steam }

    companion object {
        private const val TAG = "LauncherActivity"
        const val EXTRA_LOGIN_MODE = "com.skarm.launcher.LOGIN_MODE"
        const val EXTRA_STEAM_USER = "com.skarm.launcher.STEAM_USER"
        const val EXTRA_STEAM_PASS = "com.skarm.launcher.STEAM_PASS"
    }
}
