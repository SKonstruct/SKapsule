package com.skarm.launcher

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.util.Locale
import java.util.UUID

/**
 * The servers the game can connect to besides Official, and which one is selected.
 *
 * A server is only connection settings (host, port, public key, two URLs); the game files are
 * the stock client's, which [ServerConnection] points at the chosen server at launch.
 */
object ServerStore {
    private const val PREFS = "servers"
    private const val KEY_LIST = "list"
    private const val KEY_SELECTED = "selected"
    private const val CREDENTIALS = "server_credentials"

    data class Server(
        val id: String,
        val name: String,
        val host: String,
        val port: Int,
        /** The server's RSA public key, `modulus#exponent` in hex. Blank uses Official's. */
        val publicKey: String = "",
        /** Optional root URL of the client files (the game wants `scheme://host`). Blank uses Official's. */
        val clientUrl: String = "",
        /** Optional content URL. Blank uses Official's. */
        val contentUrl: String = "",
    )

    fun list(context: Context): List<Server> =
        parse(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_LIST, null))

    /** The selected server, or null for Official (also when the selected one was deleted). */
    fun selected(context: Context): Server? {
        val id = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_SELECTED, null)
            ?: return null
        return list(context).firstOrNull { it.id == id }
    }

    /** Selects [id], or Official for null. */
    fun select(context: Context, id: String?) {
        val edit = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        if (id == null) edit.remove(KEY_SELECTED) else edit.putString(KEY_SELECTED, id)
        edit.apply()
    }

    /** Adds the server, or replaces the one with the same id. */
    fun save(context: Context, server: Server) {
        val all = list(context).filter { it.id != server.id } + server
        write(context, all)
    }

    /**
     * The optional login saved for a server, for quick sign-in. Encrypted like the web account:
     * with the Android Keystore, so it is useless off this device and out of backups.
     */
    fun credentials(context: Context, id: String): WebAccountStore.Account? {
        val blob = context.getSharedPreferences(CREDENTIALS, Context.MODE_PRIVATE).getString(id, null) ?: return null
        return try {
            WebAccountStore.decode(WebAccountStore.decrypt(blob))
        } catch (e: Exception) {
            null // a reset keystore cannot open the old blob; treat it as no login
        }
    }

    /** Saves the login for a server, or removes it for null. @return false if it could not be saved. */
    fun saveCredentials(context: Context, id: String, account: WebAccountStore.Account?): Boolean {
        val edit = context.getSharedPreferences(CREDENTIALS, Context.MODE_PRIVATE).edit()
        if (account == null) {
            edit.remove(id).apply()
            return true
        }
        return try {
            edit.putString(id, WebAccountStore.encrypt(WebAccountStore.encode(account))).apply()
            true
        } catch (e: Exception) {
            false
        }
    }

    fun delete(context: Context, id: String) {
        saveCredentials(context, id, null)
        write(context, list(context).filter { it.id != id })
        if (context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_SELECTED, null) == id) {
            select(context, null)
        }
    }

    fun newId(): String = UUID.randomUUID().toString()

    private fun write(context: Context, servers: List<Server>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_LIST, serialize(servers)).apply()
    }

    internal fun serialize(servers: List<Server>): String = JSONArray().also { array ->
        servers.forEach {
            array.put(
                JSONObject()
                    .put("id", it.id).put("name", it.name).put("host", it.host).put("port", it.port)
                    .put("publicKey", it.publicKey).put("clientUrl", it.clientUrl)
                    .put("contentUrl", it.contentUrl),
            )
        }
    }.toString()

    /** Skips malformed entries instead of failing, so one bad record never hides the rest. */
    internal fun parse(json: String?): List<Server> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val array = JSONArray(json)
            (0 until array.length()).mapNotNull { i ->
                val o = array.optJSONObject(i) ?: return@mapNotNull null
                val id = o.optString("id")
                val name = o.optString("name")
                val host = o.optString("host")
                val port = o.optInt("port", 0)
                if (id.isEmpty() || name.isEmpty() || host.isEmpty() || port !in 1..65535) return@mapNotNull null
                Server(id, name, host, port, o.optString("publicKey"), o.optString("clientUrl"), o.optString("contentUrl"))
            }
        } catch (e: org.json.JSONException) {
            emptyList()
        }
    }

    enum class Problem { NAME, HOST, PORT, KEY, URL, DUPLICATE }

    /**
     * What is wrong with a server as typed, or null if it is fine. Fields are expected trimmed.
     * [others] are the other saved servers, for the duplicate-name check.
     */
    fun validate(server: Server, others: List<Server>): Problem? {
        if (server.name.isEmpty() || server.name.length > 40) return Problem.NAME
        if (server.name.equals("Official", ignoreCase = true) ||
            others.any { it.id != server.id && it.name.equals(server.name, ignoreCase = true) }
        ) return Problem.DUPLICATE
        // The host ends up in a Java properties file and a socket address: no whitespace or '/'.
        if (server.host.isEmpty() || server.host.length > 253 || !HOST.matches(server.host)) return Problem.HOST
        if (server.port !in 1..65535) return Problem.PORT
        if (server.publicKey.isNotEmpty() && !KEY.matches(server.publicKey)) return Problem.KEY
        if (!isHttpUrlOrBlank(server.clientUrl) || !isHttpUrlOrBlank(server.contentUrl)) return Problem.URL
        return null
    }

    /**
     * Whether [host] is a place Android 17's local network protection covers: a private or link-local
     * address, a `.local` name, or a bare hostname (no dot), which only resolves on the local network.
     */
    fun isLocalNetworkHost(host: String): Boolean {
        val h = host.trim().trim('[', ']').lowercase()
        if (h.isEmpty() || h == "localhost") return false
        val v4 = Regex("(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})").matchEntire(h)
        if (v4 != null) {
            val (a, b) = v4.groupValues[1].toInt() to v4.groupValues[2].toInt()
            return a == 10 || (a == 172 && b in 16..31) || (a == 192 && b == 168) || (a == 169 && b == 254)
        }
        if (':' in h) return h.startsWith("fe80") || h.startsWith("fc") || h.startsWith("fd")
        return h.endsWith(".local") || '.' !in h
    }

    private val HOST = Regex("[A-Za-z0-9._:\\[\\]-]+")
    private val KEY = Regex("[0-9a-fA-F]+#[0-9a-fA-F]+")

    private fun isHttpUrlOrBlank(url: String): Boolean {
        if (url.isEmpty()) return true
        return try {
            val uri = URI(url)
            (uri.scheme.equals("http", true) || uri.scheme.equals("https", true)) && !uri.host.isNullOrEmpty()
        } catch (e: java.net.URISyntaxException) {
            false
        }
    }

    /** The game wants `scheme://host[:port]`, not the full client path a user pastes in. */
    fun toClientRoot(url: String): String {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return ""
        val schemeEnd = trimmed.indexOf("://")
        val scheme = if (schemeEnd > 0) trimmed.substring(0, schemeEnd).lowercase(Locale.ROOT) else "http"
        val rest = if (schemeEnd > 0) trimmed.substring(schemeEnd + 3) else trimmed
        return (if (scheme == "https") "https" else "http") + "://" + rest.substringBefore('/')
    }
}
