package com.skarm.launcher

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.text.InputType
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import kotlin.random.Random
import kotlinx.coroutines.delay
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.Lifecycle
import android.graphics.Bitmap
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

    /** Registered lazily by installUpdate; see the note there. */
    private var installReceiver: android.content.BroadcastReceiver? = null

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

        binding.btnDonate.setOnClickListener { openUrl(DONATE_URL) }

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
        binding.modeToggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            val button = when (checkedId) {
                R.id.btn_mode_steam -> binding.btnModeSteam
                R.id.btn_mode_web -> binding.btnModeWeb
                else -> return@addOnButtonCheckedListener
            }
            if (isChecked) {
                button.textSize = 15f
                button.setTypeface(null, android.graphics.Typeface.BOLD)
                button.setBackgroundColor(getColor(R.color.sk_pink))
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
            binding.btnModeSteam.setBackgroundColor(getColor(R.color.sk_pink))
            binding.btnModeSteam.setTextColor(android.graphics.Color.WHITE)
            binding.btnModeWeb.textSize = 13f
            binding.btnModeWeb.setTypeface(null, android.graphics.Typeface.NORMAL)
            binding.btnModeWeb.setBackgroundColor(android.graphics.Color.TRANSPARENT)
            binding.btnModeWeb.setTextColor(android.graphics.Color.parseColor("#A0A0A0"))
        } else {
            binding.modeToggleGroup.check(R.id.btn_mode_web)
            binding.btnModeWeb.textSize = 15f
            binding.btnModeWeb.setTypeface(null, android.graphics.Typeface.BOLD)
            binding.btnModeWeb.setBackgroundColor(getColor(R.color.sk_pink))
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
            if (mode == LoginMode.Steam && ServerStore.selected(this) == null) {
                onPlaySteam()
            } else {
                launchGame(LoginMode.Web)
            }
        }

        // Sidebar click listeners
        binding.sidebarBtnShareLogs.setOnClickListener { LogExporter.captureAndShare(this) }
        binding.sidebarBtnDownloadMods.setOnClickListener { downloadMods() }
        binding.sidebarBtnApplyMods.setOnClickListener { applyMods() }
        binding.sidebarBtnRemoveMods.setOnClickListener { removeMods() }
        binding.sidebarBtnOpenFolder.setOnClickListener { openFolder() }
        binding.sidebarBtnEditControls.setOnClickListener { openControlsEditor() }
        binding.sidebarBtnCheckUpdates.setOnClickListener { checkForUpdateNow() }
        binding.sidebarBtnGithub.setOnClickListener { openUrl("https://github.com/SKonstruct/SKapsule") }
        binding.sidebarBtnDiscord.setOnClickListener { openUrl("https://dankware.alwaysdata.net/discord") }
        binding.sidebarBtnLogout.setOnClickListener { onLogout() }
        binding.sidebarBtnWebAccount.setOnClickListener { editWebAccount() }
        binding.serverPick.setOnClickListener { showServers() }
        binding.serverAction.setOnClickListener { editServer(ServerStore.selected(this)) }
        showSelectedServer()

        // Avoid screen edges switch
        val avoidEdgesSwitch = binding.switchAvoidEdges
        avoidEdgesSwitch.isChecked = launcherPrefs.getBoolean("avoid_screen_edges", false)
        avoidEdgesSwitch.setOnCheckedChangeListener { _, isChecked ->
            launcherPrefs.edit().putBoolean("avoid_screen_edges", isChecked).apply()
        }

        val crashSwitch = binding.switchCrashReporting
        crashSwitch.isChecked = CrashReporting.isEnabled(this)
        crashSwitch.setOnCheckedChangeListener { _, isChecked ->
            CrashReporting.setEnabled(this, isChecked)
        }

        // Max RAM stepper
        binding.btnRamMinus.setOnClickListener { stepRam(-RamSettings.STEP_MB) }
        binding.btnRamPlus.setOnClickListener { stepRam(RamSettings.STEP_MB) }
        refreshRam()

        // If the previous session crashed, the handler auto-saved a dump. Offer to
        // share it right away (once per launch); the button stays available too.
        LogExporter.unseenCrash(this)?.let { crash ->
            LogExporter.markCrashSeen(this, crash)
            AlertDialog.Builder(this)
                .setMessage(R.string.share_logs_crash_found)
                .setPositiveButton(R.string.share_logs) { _, _ -> LogExporter.share(this, crash) }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }

        binding.subtitle.setOnClickListener {
            if (ServerStore.selected(this) == null) showPlayerCountBreakdown()
        }
        fetchPlayerCount()
        fetchNews()
        checkForUpdate()
        ensureRuntime()
    }

    /**
     * Gate the Share Logs button on there being something worth sending: a launch
     * was attempted this install, or a crash dump is on disk. Re-checked on resume
     * because the launch marker is written by the :game process while we're paused.
     */
    override fun onDestroy() {
        installReceiver?.let { runCatching { unregisterReceiver(it) } }
        installReceiver = null
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        setLaunchButtonLoading(false)
        // The game process can have found damaged files and invalidated them; repair before
        // the next Play rather than failing the same way again.
        ensureRuntime()
        val hasLogs = LogExporter.wasLaunchAttempted(this) || LogExporter.latestCrash(this) != null
        binding.sidebarBtnShareLogs.isEnabled = hasLogs

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

    /**
     * Opens the standalone control editor.
     *
     * Guarded on the game still being alive: GameActivity runs in its own `:game` process,
     * which keeps a separate SharedPreferences cache *and* its own in-memory copy of the
     * layout that it rewrites wholesale when its editor closes. Editing in both at once
     * silently loses one set of changes, so say so rather than let it happen quietly.
     *
     * The launcher cannot end that process itself — killBackgroundProcesses needs a
     * permission this app has no other use for, and the game's own exit path runs inside
     * the game — so the choice is the user's.
     */
    private fun openControlsEditor() {
        if (!isGameProcessRunning()) {
            startActivity(Intent(this, ControlsEditorActivity::class.java))
            return
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.editor_game_running_title)
            .setMessage(R.string.editor_game_running_message)
            .setPositiveButton(R.string.editor_edit_anyway) { _, _ ->
                startActivity(Intent(this, ControlsEditorActivity::class.java))
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun isGameProcessRunning(): Boolean {
        val am = getSystemService(ACTIVITY_SERVICE) as android.app.ActivityManager
        // Own processes are always visible to us, whatever the package visibility rules.
        return am.runningAppProcesses.orEmpty().any {
            it.processName == "$packageName:game" &&
                it.importance < android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_GONE
        }
    }

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: Exception) {
            Toast.makeText(this, "No browser found to open link", Toast.LENGTH_SHORT).show()
        }
    }

    /** Save, change or remove the web account that web launches sign in with. */
    private fun editWebAccount() {
        val saved = WebAccountStore.load(this)
        val pad = (resources.displayMetrics.density * 20).toInt()
        val limit = android.text.InputFilter.LengthFilter(WebAccountStore.MAX_LENGTH)
        val userField = EditText(this).apply {
            hint = getString(R.string.web_account_username)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            filters = arrayOf(limit)
            setText(saved?.username.orEmpty())
        }
        val passField = EditText(this).apply {
            hint = getString(
                if (saved != null) R.string.web_account_password_saved else R.string.web_account_password,
            )
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            filters = arrayOf(limit)
        }
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad / 2, pad, 0)
            addView(userField)
            addView(passField)
        }
        val builder = AlertDialog.Builder(this)
            .setTitle(R.string.web_account_title)
            .setMessage(R.string.web_account_message)
            .setView(layout)
            .setPositiveButton(R.string.web_account_save, null)
            .setNegativeButton(android.R.string.cancel, null)
        if (saved != null) {
            builder.setNeutralButton(R.string.web_account_clear) { _, _ ->
                WebAccountStore.clear(this)
                Toast.makeText(this, R.string.web_account_removed, Toast.LENGTH_SHORT).show()
            }
        }
        val dialog = builder.show()
        // Overridden after show() so a missing field keeps the dialog open.
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val user = userField.text.toString().trim()
            val typed = passField.text.toString()
            val pass = typed.ifEmpty { saved?.password.orEmpty() }
            if (user.isEmpty() || pass.isEmpty()) {
                Toast.makeText(this, R.string.web_account_incomplete, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val ok = WebAccountStore.save(this, WebAccountStore.Account(user, pass))
            Toast.makeText(
                this,
                if (ok) R.string.web_account_saved else R.string.web_account_save_failed,
                Toast.LENGTH_SHORT,
            ).show()
            if (ok) dialog.dismiss()
        }
    }

    /**
     * Shows which server Play connects to. Steam sign-in belongs to Official, so the Web/Steam
     * switch is hidden for a private server and Play uses Web; the saved choice is left alone.
     */
    private fun showSelectedServer() {
        val server = ServerStore.selected(this)
        val name = server?.name ?: getString(R.string.servers_official)
        binding.serverName.text = name
        binding.serverPick.contentDescription = getString(R.string.servers_pick_description, name)
        binding.serverAction.setImageResource(if (server == null) R.drawable.ic_add else R.drawable.ic_edit)
        binding.serverAction.contentDescription = getString(if (server == null) R.string.servers_add else R.string.servers_edit)
        binding.modeToggleGroup.visibility = if (server == null) View.VISIBLE else View.GONE
        // The player count is Official's.
        binding.subtitle.text = if (server == null && playerTotal > 0) {
            getString(R.string.subtitle_player_count, playerTotal)
        } else {
            getString(R.string.subtitle_android)
        }
    }

    /** Pick Official or a saved server; Add server is in the dialog, editing is the pencil beside the picker. */
    private fun showServers() {
        val servers = ServerStore.list(this)
        val names = (listOf(getString(R.string.servers_official)) + servers.map { it.name }).toTypedArray()
        val selectedId = ServerStore.selected(this)?.id
        AlertDialog.Builder(this)
            .setTitle(R.string.servers_title)
            .setSingleChoiceItems(names, servers.indexOfFirst { it.id == selectedId } + 1) { dialog, which ->
                ServerStore.select(this, servers.getOrNull(which - 1)?.id)
                showSelectedServer()
                dialog.dismiss()
            }
            .setNeutralButton(R.string.servers_add) { _, _ -> editServer(null) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /** Add a server, or edit or delete [existing]. */
    private fun editServer(existing: ServerStore.Server?) {
        val pad = (resources.displayMetrics.density * 20).toInt()
        fun field(hint: Int, value: String, type: Int = InputType.TYPE_CLASS_TEXT) = EditText(this).apply {
            setHint(hint)
            inputType = type or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            setText(value)
        }
        val name = field(R.string.servers_name, existing?.name.orEmpty())
        val host = field(R.string.servers_host, existing?.host.orEmpty(), InputType.TYPE_TEXT_VARIATION_URI)
        val port = field(R.string.servers_port, (existing?.port ?: ServerConnection.OFFICIAL_PORT).toString(), InputType.TYPE_CLASS_NUMBER)
        val key = field(R.string.servers_key, existing?.publicKey.orEmpty())
        val client = field(R.string.servers_client_url, existing?.clientUrl.orEmpty(), InputType.TYPE_TEXT_VARIATION_URI)
        val content = field(R.string.servers_content_url, existing?.contentUrl.orEmpty(), InputType.TYPE_TEXT_VARIATION_URI)
        val savedLogin = existing?.let { ServerStore.credentials(this, it.id) }
        val limit = android.text.InputFilter.LengthFilter(WebAccountStore.MAX_LENGTH)
        val user = field(R.string.servers_username, savedLogin?.username.orEmpty()).apply { filters = arrayOf(limit) }
        val pass = field(
            if (savedLogin != null) R.string.servers_password_saved else R.string.servers_password,
            "",
            InputType.TYPE_TEXT_VARIATION_PASSWORD,
        ).apply { filters = arrayOf(limit) }
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad / 2, pad, 0)
            listOf(name, host, port, key, client, content, user, pass).forEach { addView(it) }
        }
        val builder = AlertDialog.Builder(this)
            .setTitle(if (existing == null) R.string.servers_add else R.string.servers_edit)
            .setMessage(R.string.servers_form_message)
            .setView(android.widget.ScrollView(this).apply { addView(form) })
            .setPositiveButton(R.string.web_account_save, null)
            .setNegativeButton(android.R.string.cancel, null)
        if (existing != null) {
            builder.setNeutralButton(R.string.servers_delete) { _, _ ->
                ServerStore.delete(this, existing.id)
                showSelectedServer()
            }
        }
        val dialog = builder.show()
        // Overridden after show() so a bad field keeps the dialog open.
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val candidate = ServerStore.Server(
                id = existing?.id ?: ServerStore.newId(),
                name = name.text.toString().trim(),
                host = host.text.toString().trim(),
                port = port.text.toString().trim().toIntOrNull() ?: 0,
                publicKey = key.text.toString().trim(),
                clientUrl = client.text.toString().trim(),
                contentUrl = content.text.toString().trim(),
            )
            val problem = ServerStore.validate(candidate, ServerStore.list(this))
            if (problem != null) {
                Toast.makeText(this, serverProblemText(problem), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val login = user.text.toString().trim()
            val typed = pass.text.toString()
            val password = typed.ifEmpty { savedLogin?.password.orEmpty() }
            // A username needs a password and the other way round; both blank means no saved login.
            if ((login.isEmpty() && typed.isNotEmpty()) || (login.isNotEmpty() && password.isEmpty()) || '\n' in login + typed) {
                Toast.makeText(this, R.string.servers_err_login, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            ServerStore.save(this, candidate)
            val saved = ServerStore.saveCredentials(
                this, candidate.id, if (login.isEmpty()) null else WebAccountStore.Account(login, password),
            )
            if (!saved) Toast.makeText(this, R.string.web_account_save_failed, Toast.LENGTH_SHORT).show()
            // A server you just added or edited is the one you mean to play on.
            ServerStore.select(this, candidate.id)
            showSelectedServer()
            dialog.dismiss()
        }
    }

    private fun serverProblemText(problem: ServerStore.Problem): Int = when (problem) {
        ServerStore.Problem.NAME -> R.string.servers_err_name
        ServerStore.Problem.DUPLICATE -> R.string.servers_err_duplicate
        ServerStore.Problem.HOST -> R.string.servers_err_host
        ServerStore.Problem.PORT -> R.string.servers_err_port
        ServerStore.Problem.KEY -> R.string.servers_err_key
        ServerStore.Problem.URL -> R.string.servers_err_url
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

    private fun ensureRuntime() {
        if (installingRuntime) {
            // Another instance of this screen (recreated mid-install) owns the job. Wait for it
            // instead of starting a second extraction over the same directory.
            setButtonsEnabled(false)
            binding.setupGroup.visibility = View.VISIBLE
            lifecycleScope.launch {
                while (installingRuntime) delay(500)
                ensureRuntime()
            }
            return
        }
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
        installingRuntime = true

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
                // The screen going away is not a failed install; the work itself ran to the end.
                if (t is kotlinx.coroutines.CancellationException) throw t
                Log.e(TAG, "Runtime install failed", t)
                CrashReporting.report(
                    "runtime_install_failed", t.toString(),
                    mapOf("abis" to android.os.Build.SUPPORTED_ABIS.joinToString()),
                )
                // A bundled file that cannot be opened means the installed package is damaged
                // or was replaced under a running app; installing again fixes it.
                val hint = if (t is java.io.FileNotFoundException) "\nReinstall SKapsule to repair it." else ""
                binding.setupStatus.text = "Runtime setup failed: ${t.message}$hint"
            } finally {
                installingRuntime = false
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

    /** The launch to continue with once Local network access is answered (see [launchGame]). */
    private var afterLocalNetworkPrompt: (() -> Unit)? = null
    private val localNetworkPermission = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val resume = afterLocalNetworkPrompt
        afterLocalNetworkPrompt = null
        if (granted) resume?.invoke() else showMessage(R.string.servers_title, getString(R.string.servers_local_network_denied))
    }

    private fun launchGame(requested: LoginMode, steamUser: String = "", steamPass: String = "") {
        val chosen = ServerStore.selected(this)
        // Android 17 stops an app that targets it from reaching the local network (a server on a PC
        // at home, the emulator's host) until it is allowed; ask first, then carry on.
        if (chosen != null && Build.VERSION.SDK_INT >= 37 && ServerStore.isLocalNetworkHost(chosen.host) &&
            checkSelfPermission(LOCAL_NETWORK_PERMISSION) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            afterLocalNetworkPrompt = { launchGame(requested, steamUser, steamPass) }
            localNetworkPermission.launch(LOCAL_NETWORK_PERMISSION)
            return
        }
        setLaunchButtonLoading(true)
        val server = chosen
        // Steam tickets and the saved Spiral Knights account belong to Official; a private
        // server gets the stock logon window instead.
        val mode = if (server != null) LoginMode.Web else requested
        lifecycleScope.launch {
            try {
                val pointed = withContext(Dispatchers.IO) {
                    ServerConnection.apply(SkInstaller.homeDir(this@LauncherActivity), server)
                }
                if (!pointed && server != null) {
                    setLaunchButtonLoading(false)
                    showMessage(R.string.servers_title, getString(R.string.servers_need_install))
                    return@launch
                }
                // Checked after the edit so a stale Official-bound jar is never left behind either way.
                if (server != null && withContext(Dispatchers.IO) {
                        ServerConnection.updatePending(SkInstaller.homeDir(this@LauncherActivity))
                    }
                ) {
                    setLaunchButtonLoading(false)
                    showMessage(R.string.servers_title, getString(R.string.servers_update_pending))
                    return@launch
                }
            } catch (e: Exception) {
                Log.w("LauncherActivity", "Could not point the game at the server", e)
                setLaunchButtonLoading(false)
                showMessage(R.string.servers_title, getString(R.string.servers_apply_failed, e.message.orEmpty()))
                return@launch
            }
            val login = withContext(Dispatchers.IO) {
                if (mode != LoginMode.Web) null
                else if (server != null) ServerStore.credentials(this@LauncherActivity, server.id)
                else WebAccountStore.load(this@LauncherActivity)
            }
            startGame(mode, login, ServerConnection.desired(server).getValue("server_host"), steamUser, steamPass)
        }
    }

    private fun showMessage(title: Int, message: String) {
        AlertDialog.Builder(this).setTitle(title).setMessage(message)
            .setPositiveButton(android.R.string.ok, null).show()
    }

    private fun startGame(mode: LoginMode, login: WebAccountStore.Account?, host: String, steamUser: String, steamPass: String) {
        startActivity(
            Intent(this, GameActivity::class.java).apply {
                putExtra(EXTRA_LOGIN_MODE, mode.name)
                // Passed explicitly: GameActivity runs in :game, and that process can
                // outlive a launch with a stale SharedPreferences cache of this value.
                putExtra(EXTRA_MAX_HEAP_MB, RamSettings.get(this@LauncherActivity))
                if (steamUser.isNotEmpty()) {
                    putExtra(EXTRA_STEAM_USER, steamUser)
                    putExtra(EXTRA_STEAM_PASS, steamPass)
                }
                if (login != null) {
                    putExtra(EXTRA_WEB_USER, login.username)
                    putExtra(EXTRA_WEB_PASS, login.password)
                    // The game withholds the login unless it is really set up for this server.
                    putExtra(EXTRA_LOGIN_HOST, host)
                }
            },
        )
    }

    /**
     * Steam only knows about Steam players, so both servers' counts are scaled by
     * [PLAYER_ESTIMATE_MULT] to account for standalone installs and then summed —
     * the same arithmetic KnightLauncher does, so the two agree.
     */
    private fun fetchPlayerCount() {
        lifecycleScope.launch {
            val live = withContext(Dispatchers.IO) { steamPlayers(APPID_LIVE) }
            val preview = withContext(Dispatchers.IO) { steamPlayers(APPID_PREVIEW) }
            // Only give up when neither server answered; a dead preview leg must
            // not hide the live number.
            if (live < 0 && preview < 0) return@launch

            playerCounts = Pair(maxOf(live, 0), maxOf(preview, 0))
            val total = estimate(maxOf(live, 0)) + estimate(maxOf(preview, 0))
            playerTotal = total
            // The count is Official's.
            if (total > 0 && ServerStore.selected(this@LauncherActivity) == null) {
                binding.subtitle.text = getString(R.string.subtitle_player_count, total)
            }
        }
    }

    /** @return the Steam player count, or -1 if the request failed. */
    private fun steamPlayers(appId: Int): Int = try {
        val url = java.net.URL(
            "https://api.steampowered.com/ISteamUserStats/GetNumberOfCurrentPlayers/v1/?appid=$appId"
        )
        val conn = (url.openConnection() as java.net.HttpURLConnection).apply {
            connectTimeout = 5000
            readTimeout = 5000
        }
        try {
            org.json.JSONObject(conn.inputStream.bufferedReader().readText())
                .getJSONObject("response")
                .optInt("player_count", -1)
        } finally {
            conn.disconnect()
        }
    } catch (e: Exception) {
        -1
    }

    private fun estimate(steam: Int): Int = Math.round(steam * PLAYER_ESTIMATE_MULT)

    /** Live and preview Steam counts from the last successful fetch, or null. */
    private var playerCounts: Pair<Int, Int>? = null
    private var playerTotal = 0

    /** Explains where the number in the subtitle comes from. */
    private fun showPlayerCountBreakdown() {
        val (live, preview) = playerCounts ?: return
        AlertDialog.Builder(this)
            .setTitle(R.string.player_count_title)
            .setMessage(
                getString(
                    R.string.player_count_breakdown,
                    live, estimate(live), preview, estimate(preview),
                )
            )
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun localVersion(): String = try {
        packageManager.getPackageInfo(packageName, 0).versionName ?: "0.0.0"
    } catch (e: Exception) {
        "0.0.0"
    }

    /**
     * The sidebar's Check for Updates: asks GitHub now instead of trusting the six-hour cache, and
     * always answers, unlike the quiet check at startup. Choosing it again also forgets a
     * "skip this version", since the user is plainly asking for the update.
     */
    private fun checkForUpdateNow() {
        val button = binding.sidebarBtnCheckUpdates
        button.isEnabled = false
        Toast.makeText(this, R.string.update_checking, Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            val release = AppUpdater.fetchLatest(this@LauncherActivity, force = true)
            button.isEnabled = true
            if (release == null) {
                Toast.makeText(this@LauncherActivity, R.string.update_check_failed, Toast.LENGTH_LONG).show()
                return@launch
            }
            val localVer = localVersion()
            val cmp = AppUpdater.compareVersions(localVer, release.version)
            when {
                cmp < 0 -> {
                    AppUpdater.clearSkippedVersion(this@LauncherActivity)
                    binding.drawerLayout.closeDrawer(GravityCompat.END)
                    showUpdateBanner(release)
                    showUpdateDialog(release)
                }
                cmp == 0 -> {
                    // A stale answer may have painted the title as a development build.
                    binding.title.text = "SKapsule v$localVer"
                    binding.title.setTextColor(Color.WHITE)
                    Toast.makeText(
                        this@LauncherActivity, getString(R.string.update_up_to_date, localVer), Toast.LENGTH_LONG,
                    ).show()
                }
                else -> Toast.makeText(
                    this@LauncherActivity, getString(R.string.update_dev_build, localVer, release.version),
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    private fun checkForUpdate() {
        lifecycleScope.launch {
            val release = AppUpdater.fetchLatest(this@LauncherActivity) ?: return@launch
            val localVer = localVersion()

            when {
                AppUpdater.compareVersions(localVer, release.version) < 0 -> {
                    if (AppUpdater.skippedVersion(this@LauncherActivity) != release.version) {
                        showUpdateBanner(release)
                    }
                }
                AppUpdater.compareVersions(localVer, release.version) > 0 ->
                    showDevBuildIndicator(localVer)
            }
        }
    }


    /**
     * Fills the home-screen card from the feed and, when it carries more than one
     * event, rotates through them as KnightLauncher does: a random first event, then
     * the next every [NewsFeed.ROTATE_INTERVAL_MS]. The card stays gone when there is
     * nothing to show, so a quiet week or an unreachable service leaves the screen as
     * it was. Rotation only runs while the screen is started.
     */
    private fun fetchNews() {
        lifecycleScope.launch {
            val events = NewsFeed.fetch(this@LauncherActivity)
            if (events.isEmpty()) return@launch
            binding.newsCard.visibility = View.VISIBLE

            // Each banner is requested once per feed load, in this scope rather than the
            // rotation's so a stop/start does not abandon it, and a failing one is not
            // retried every rotation. Text never waits on it.
            val scope = this
            val width = binding.newsCard.width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels
            val loaded = HashMap<String, Bitmap?>()
            val requested = HashSet<String>()
            var current: NewsFeed.Event? = null
            fun request(url: String) {
                if (!requested.add(url)) return
                scope.launch {
                    loaded[url] = NewsFeed.image(this@LauncherActivity, url, width)
                    if (current?.imageUrl == url) binding.newsImage.setImageBitmap(loaded[url])
                }
            }

            repeatOnLifecycle(Lifecycle.State.STARTED) {
                var index = Random.nextInt(events.size)
                var first = true
                while (true) {
                    val event = events[index]
                    current = event
                    // Read the banner when it is applied, not now: one finishing mid-fade
                    // would otherwise be replaced by a stale null.
                    val bitmap = { event.imageUrl?.let { loaded[it] } }
                    if (first) {
                        // A restart can land mid-fade; drop it so it cannot apply an old event.
                        binding.newsCard.animate().cancel()
                        binding.newsCard.alpha = 1f
                        showNews(event, bitmap())
                    } else {
                        crossfadeNews(event, bitmap)
                    }
                    event.imageUrl?.let(::request)
                    first = false
                    if (events.size == 1) break
                    index = (index + 1) % events.size
                    // Have the next banner ready by the time we rotate to it.
                    events[index].imageUrl?.let(::request)
                    delay(NewsFeed.ROTATE_INTERVAL_MS)
                }
            }
        }
    }

    private fun crossfadeNews(event: NewsFeed.Event, bitmap: () -> Bitmap?) {
        val card = binding.newsCard
        card.animate().cancel()
        card.animate().alpha(0f).setDuration(NEWS_FADE_MS).withEndAction {
            showNews(event, bitmap())
            card.animate().alpha(1f).setDuration(NEWS_FADE_MS).start()
        }.start()
    }

    private fun showNews(event: NewsFeed.Event, bitmap: Bitmap?) {
        val status = NewsFeed.statusLabel(this, event.startsAt, event.endsAt)
        // Evergreen events carry no window; drop the chip rather than inventing a countdown.
        binding.newsEndsIn.visibility = if (status != null) View.VISIBLE else View.GONE
        binding.newsEndsIn.text = status
        val endingSoon = event.isEndingSoon()
        binding.newsEndsIn.backgroundTintList =
            if (endingSoon) ColorStateList.valueOf(NEWS_ENDING_SOON_COLOR) else null
        binding.newsEndsIn.setTextColor(if (endingSoon) NEWS_ENDING_SOON_TEXT else Color.WHITE)
        binding.newsTitle.text = event.title
        binding.newsBody.text = event.body
        binding.newsBody.visibility = if (event.body.isEmpty()) View.GONE else View.VISIBLE
        binding.newsImage.setImageBitmap(bitmap)
        val link = event.link
        if (link != null) {
            binding.newsCard.setOnClickListener { openUrl(link) }
        } else {
            binding.newsCard.setOnClickListener(null)
            binding.newsCard.isClickable = false
        }
    }

    private fun showUpdateBanner(release: AppUpdater.Release) {
        binding.updateTitle.text = "Update available — v${release.version}"
        binding.updateBanner.setOnClickListener { showUpdateDialog(release) }
        binding.updateBanner.visibility = View.VISIBLE
        binding.updateBanner.alpha = 0f
        binding.updateBanner.translationY = -20f
        binding.updateBanner.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(400)
            .start()
    }

    private fun showUpdateDialog(release: AppUpdater.Release) {
        val builder = AlertDialog.Builder(this)
            .setTitle("Update to v${release.version}?")
            .setNegativeButton("Skip this version") { _, _ ->
                AppUpdater.skipVersion(this, release.version)
                binding.updateBanner.visibility = View.GONE
            }
            .setNeutralButton("Release notes") { _, _ -> openUrl(release.htmlUrl) }
        if (release.apkUrl != null) {
            builder.setMessage("Download and install the new version?")
                .setPositiveButton("Install") { _, _ -> installUpdate(release) }
        } else {
            builder.setMessage("This release has no APK attached — open it on GitHub instead.")
        }
        builder.show()
    }

    private fun installUpdate(release: AppUpdater.Release) {
        val progress = showProgressDialog(R.string.update_downloading, "Starting download…")
        // The receiver has to outlive this call: PackageInstaller.commit() broadcasts
        // asynchronously, so STATUS_PENDING_USER_ACTION -- the intent that actually opens
        // the system installer -- arrives well after downloadAndInstall() returns. It is
        // registered for the Activity's lifetime and torn down in onDestroy.
        if (installReceiver == null) {
            installReceiver = AppUpdater.registerInstallReceiver(this) { message ->
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            }
        }
        lifecycleScope.launch {
            try {
                AppUpdater.downloadAndInstall(this@LauncherActivity, release) { done, total ->
                    val pct = if (total > 0) ((done * 100) / total).toInt() else 0
                    runOnUiThread {
                        progress.updateProgress("Downloading update… $pct%", pct, 100)
                    }
                }
            } catch (e: Exception) {
                Log.e("LauncherActivity", "Update install failed", e)
                val offline = CrashReporting.isConnectionProblem(e)
                if (!offline) CrashReporting.report("update_install_failed", e.toString(), mapOf("version" to release.version))
                Toast.makeText(
                    this@LauncherActivity,
                    if (offline) getString(R.string.update_download_failed) else "Update failed: ${e.message}",
                    Toast.LENGTH_LONG,
                ).show()
            } finally {
                progress.dialog.dismissSafely()
            }
        }
    }

    private fun showDevBuildIndicator(localVer: String) {
        binding.title.text = "SKapsule v$localVer-dev"
        binding.title.setTextColor(Color.parseColor("#F2BF3A"))
    }

    private class ProgressDialogInfo(
        val dialog: AlertDialog,
        val updateProgress: (String, Int, Int) -> Unit,
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
            // The dialog follows the system light/dark theme; fixed white was unreadable in light mode.
            setTextColor(
                com.google.android.material.color.MaterialColors.getColor(
                    this, com.google.android.material.R.attr.colorOnSurface, Color.WHITE,
                ),
            )
            textSize = 16f
        }
        val progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = true
            progressTintList = ColorStateList.valueOf(getColor(R.color.sk_pink))
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
                progressInfo.dialog.dismissSafely()
                Toast.makeText(
                    this@LauncherActivity,
                    "Download complete: ${stats.downloaded} downloaded, ${stats.skipped} skipped, ${stats.deleted} deleted",
                    Toast.LENGTH_LONG,
                ).show()
            } catch (e: Exception) {
                progressInfo.dialog.dismissSafely()
                val offline = CrashReporting.isConnectionProblem(e)
                if (!offline) CrashReporting.report("mods_download_failed", e.toString())
                AlertDialog.Builder(this@LauncherActivity)
                    .setTitle("Download Failed")
                    .setMessage(if (offline) getString(R.string.mods_download_offline) else e.message ?: "Unknown error")
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
            }
        }
    }

    /** Mods rewrite the code jars the running game has open; changing them under it corrupts it. */
    private fun refuseWhileGameRuns(): Boolean {
        if (!isGameProcessRunning()) return false
        Toast.makeText(this, R.string.mods_game_running, Toast.LENGTH_LONG).show()
        return true
    }

    private fun applyMods() {
        if (refuseWhileGameRuns()) return
        val progressInfo = showProgressDialog(R.string.mod_apply_title, "Starting apply…")

        lifecycleScope.launch {
            try {
                val gameHome = SkInstaller.homeDir(this@LauncherActivity)
                val stats = ModsApplier.apply(gameHome, progressInfo.updateProgress)
                progressInfo.dialog.dismissSafely()
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
                progressInfo.dialog.dismissSafely()
                if (e !is UserActionException) CrashReporting.report("mods_apply_failed", e.toString())
                AlertDialog.Builder(this@LauncherActivity)
                    .setTitle("Apply Failed")
                    .setMessage(e.message ?: "Unknown error")
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
            }
        }
    }

    private fun removeMods() {
        if (refuseWhileGameRuns()) return
        val progressInfo = showProgressDialog(R.string.mod_remove_title, "Starting remove…")

        lifecycleScope.launch {
            try {
                val gameHome = SkInstaller.homeDir(this@LauncherActivity)
                val jarsUnpacked = ModsApplier.remove(gameHome, progressInfo.updateProgress)
                progressInfo.dialog.dismissSafely()
                AlertDialog.Builder(this@LauncherActivity)
                    .setTitle(R.string.mod_completed)
                    .setMessage(getString(R.string.mod_removed, jarsUnpacked))
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
            } catch (e: Exception) {
                progressInfo.dialog.dismissSafely()
                if (e !is UserActionException) CrashReporting.report("mods_remove_failed", e.toString())
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
        private const val LOCAL_NETWORK_PERMISSION = "android.permission.ACCESS_LOCAL_NETWORK"
        /** Process-wide: a recreated screen must not start a second install over the first. */
        @Volatile private var installingRuntime = false

        private const val DONATE_URL = "https://buymeacoffee.com/sirdankk"
        private const val NEWS_FADE_MS = 180L
        // KnightLauncher's CustomColors.WARNING, with dark text as on its badge.
        private const val NEWS_ENDING_SOON_COLOR = 0xFFFBC65B.toInt()
        private const val NEWS_ENDING_SOON_TEXT = 0xFF202020.toInt()
        private const val TAG = "LauncherActivity"
        const val EXTRA_LOGIN_MODE = "com.skarm.launcher.LOGIN_MODE"
        /** Scales a Steam-only count to an estimated total including standalone players. */
        private const val PLAYER_ESTIMATE_MULT = 1.4f
        private const val APPID_LIVE = 99900
        private const val APPID_PREVIEW = 99920

        const val EXTRA_MAX_HEAP_MB = "com.skarm.launcher.MAX_HEAP_MB"
        const val EXTRA_STEAM_USER = "com.skarm.launcher.STEAM_USER"
        const val EXTRA_STEAM_PASS = "com.skarm.launcher.STEAM_PASS"
        const val EXTRA_WEB_USER = "com.skarm.launcher.WEB_USER"
        const val EXTRA_WEB_PASS = "com.skarm.launcher.WEB_PASS"
        const val EXTRA_LOGIN_HOST = "com.skarm.launcher.LOGIN_HOST"
    }

    private fun stepRam(deltaMb: Int) {
        RamSettings.set(this, RamSettings.get(this) + deltaMb)
        refreshRam()
    }

    private fun refreshRam() {
        val current = RamSettings.get(this)
        val max = RamSettings.maxMb(this)
        binding.textRamValue.text = getString(R.string.ram_value, current)
        binding.btnRamMinus.isEnabled = current > RamSettings.MIN_MB
        binding.btnRamPlus.isEnabled = current < max
        binding.textRamFree.text =
            getString(R.string.ram_free, RamSettings.availableMb(this), RamSettings.totalMb(this))
    }
}

/** Dismissing throws once the window is gone (the screen was recreated while the work ran). */
private fun android.app.Dialog.dismissSafely() {
    try {
        if (isShowing) dismiss()
    } catch (_: IllegalArgumentException) {
    }
}
