package com.roadcast.companion

import android.app.Activity
import android.content.pm.PackageManager
import android.content.Intent
import android.net.Network
import android.provider.Settings
import okhttp3.ConnectionPool
import java.net.Proxy
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.InputType
import android.util.Base64
import android.view.WindowManager
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Read-only LAN display. Activity lifecycle owns all sockets, retries and screen-on state. */
class MainActivity : Activity() {
    private val main = Handler(Looper.getMainLooper())
    private val client = OkHttpClient.Builder().pingInterval(2, TimeUnit.SECONDS).connectTimeout(5, TimeUnit.SECONDS).build()
    private lateinit var address: EditText
    private lateinit var code: EditText
    private lateinit var status: TextView
    private lateinit var navigation: TextView
    private lateinit var music: TextView
    private lateinit var cover: ImageView
    private lateinit var setup: LinearLayout
    private lateinit var cards: LinearLayout
    private var socket: WebSocket? = null
    private var desired = false
    private var resumed = false
    private var generation = 0
    private var retrySeconds = 1L
    private var lastReceived = 0L
    private var session = ""
    private var sequence = -1L
    private var lastCover = ""
    private var endpoint = ""
    private var peerAddress = ""
    private var authToken = ""
    private var manualPairing = false
    private var discovery: CompanionDiscovery? = null
    private var hotspot: CompanionHotspotLocator? = null
    private var wifiNetwork: Network? = null
    private var wifiClient: OkHttpClient? = null
    private var pairingRefused = false
    private var nextDiscovery = 0L
    private val retry = Runnable { if (desired && resumed) connect() }
    private val watchdog = object : Runnable {
        override fun run() {
            if (lastReceived != 0L && SystemClock.elapsedRealtime() - lastReceived > 5000) {
                clearDisplay(R.string.companion_stale)
                socket?.cancel()
                socket = null
                generation++
                queueRetry()
            }
            if (desired && socket == null && SystemClock.elapsedRealtime() >= nextDiscovery) discover()
            if (resumed) main.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val gap = (16 * resources.displayMetrics.density).toInt()
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(gap, gap, gap, gap); layoutDirection = resources.configuration.layoutDirection }
        fun label(size: Float) = TextView(this).apply { textSize = size; setTextColor(Color.WHITE) }
        column.addView(label(26f).apply { setText(R.string.companion_receiver_title) })
        setup = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        column.addView(Button(this).apply {
            setText(R.string.companion_connection)
            setOnClickListener { setup.visibility = if (setup.visibility == View.VISIBLE) View.GONE else View.VISIBLE }
        })
        column.addView(setup)
        address = EditText(this).apply {
            setHint(R.string.companion_address_hint)
            setSingleLine()
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            setText(getPreferences(MODE_PRIVATE).getString("address", ""))
        }
        code = EditText(this).apply { setHint(R.string.companion_code_hint); setSingleLine(); inputType = InputType.TYPE_CLASS_NUMBER }
        setup.addView(label(17f).apply { setText(R.string.companion_hotspot_help) })
        setup.addView(Button(this).apply {
            setText(R.string.companion_wifi_settings)
            setOnClickListener { startActivity(Intent(Settings.ACTION_WIFI_SETTINGS)) }
        })
        code.visibility = if (getPreferences(MODE_PRIVATE).getString("token", "").isNullOrEmpty()) View.VISIBLE else View.GONE
        setup.addView(code)
        setup.addView(Button(this).apply {
            setText(R.string.companion_connect)
            setOnClickListener {
                if (Build.VERSION.SDK_INT >= 37 && checkSelfPermission("android.permission.ACCESS_LOCAL_NETWORK") != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(arrayOf("android.permission.ACCESS_LOCAL_NETWORK"), 37)
                    status.setText(R.string.companion_permission)
                } else if (!code.text.toString().matches(Regex("[0-9]{8}")) && !(code.text.isBlank() && getPreferences(MODE_PRIVATE).getString("token", "").orEmpty().isNotEmpty())) {
                    status.setText(R.string.companion_need_code)
                } else {
                    stopConnection()
                    desired = true
                    pairingRefused = false
                    manualPairing = code.text.isNotBlank()
                    authToken = if (manualPairing) "" else getPreferences(MODE_PRIVATE).getString("token", "").orEmpty()
                    peerAddress = address.text.toString().trim()
                    endpoint = "ws://$peerAddress/${if (manualPairing) code.text else "paired"}"
                    getPreferences(MODE_PRIVATE).edit().putString("address", address.text.toString().trim()).apply()
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    startHotspotDetection()
                    discover()
                    connect()
                }
            }
        })
        setup.addView(Button(this).apply { setText(R.string.companion_disconnect); setOnClickListener { stopConnection() } })
        setup.addView(Button(this).apply {
            setText(R.string.companion_forget)
            setOnClickListener {
                stopConnection()
                getPreferences(MODE_PRIVATE).edit().remove("id").remove("token").apply()
                authToken = ""; code.setText(""); code.visibility = View.VISIBLE
            }
        })
        status = label(16f)
        navigation = label(38f)
        music = label(26f)
        cover = ImageView(this).apply { adjustViewBounds = true; maxHeight = (220 * resources.displayMetrics.density).toInt(); scaleType = ImageView.ScaleType.FIT_CENTER }
        column.addView(status)
        cards = LinearLayout(this)
        fun card() = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(gap, gap, gap, gap)
            background = GradientDrawable().apply { setColor(Color.rgb(24, 36, 51)); cornerRadius = gap.toFloat() }
        }
        cards.addView(card().apply { addView(navigation) })
        cards.addView(card().apply {
            addView(cover); addView(music)
            addView(label(14f).apply { setText(R.string.companion_text_note) })
        })
        arrangeCards()
        column.addView(cards)
        setContentView(ScrollView(this).apply { setBackgroundColor(Color.rgb(15, 22, 34)); addView(column) })
        clearDisplay(R.string.companion_idle)
        val preferences = getPreferences(MODE_PRIVATE)
        authToken = preferences.getString("token", "").orEmpty()
        peerAddress = preferences.getString("address", "").orEmpty()
        desired = preferences.getBoolean("auto", false) && authToken.isNotEmpty()
        if (desired) endpoint = "ws://$peerAddress/paired"
    }

    override fun onResume() {
        super.onResume(); resumed = true; main.post(watchdog)
        startHotspotDetection()
        if (desired) {
            if (Build.VERSION.SDK_INT >= 37 && checkSelfPermission("android.permission.ACCESS_LOCAL_NETWORK") != PackageManager.PERMISSION_GRANTED) {
                status.setText(R.string.companion_permission)
            } else {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); discover(); connect()
            }
        }
    }
    override fun onPause() {
        resumed = false; generation++; socket?.cancel(); socket = null
        main.removeCallbacks(retry); main.removeCallbacks(watchdog)
        discovery?.stop()
        hotspot?.stop(); hotspot = null
        wifiClient?.connectionPool?.evictAll(); wifiClient = null; wifiNetwork = null
        clearDisplay(R.string.companion_stale)
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        super.onPause()
    }
    override fun onDestroy() { client.dispatcher.executorService.shutdown(); client.connectionPool.evictAll(); super.onDestroy() }
    override fun onConfigurationChanged(newConfig: Configuration) { super.onConfigurationChanged(newConfig); arrangeCards() }

    private fun arrangeCards() {
        val wide = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        cover.maxHeight = ((if (wide) 128 else 180) * resources.displayMetrics.density).toInt()
        cards.orientation = if (wide) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
        val gap = (12 * resources.displayMetrics.density).toInt()
        for (index in 0 until cards.childCount) {
            cards.getChildAt(index).layoutParams = LinearLayout.LayoutParams(
                if (wide) 0 else LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT,
                if (wide) 1f else 0f).apply {
                if (index > 0) { if (wide) marginStart = gap else topMargin = gap }
            }
        }
    }

    private fun connect() {
        main.removeCallbacks(retry)
        if (!validAddress(peerAddress) || wifiClient == null) {
            status.setText(R.string.companion_searching)
            discover()
            return
        }
        val currentGeneration = ++generation
        socket?.cancel()
        lastReceived = SystemClock.elapsedRealtime(); session = ""; sequence = -1
        status.setText(R.string.companion_connecting)
        val request = Request.Builder().url(endpoint).apply {
            if (authToken.isNotEmpty()) header("Authorization", "Bearer $authToken")
        }.build()
        socket = (wifiClient ?: client).newWebSocket(request, object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                if (text.length > 256_000) { webSocket.cancel(); return }
                val json = runCatching { JSONObject(text) }.getOrNull() ?: return
                if (json.optInt("version") != 1) { webSocket.cancel(); return }
                main.post {
                    if (generation != currentGeneration || !resumed || !desired) return@post
                    val pairing = json.optJSONObject("pairing")
                    if (pairing != null) {
                        if (!rememberPairing(pairing)) { webSocket.cancel(); return@post }
                        if (json.optString("type") == "paired") {
                            lastReceived = SystemClock.elapsedRealtime()
                            status.setText(R.string.companion_pairing_saved)
                            setup.visibility = View.GONE
                            return@post
                        }
                    }
                    val nextSession = json.optString("session")
                    val nextSequence = json.optLong("sequence", -1)
                    if (nextSession.isEmpty() || nextSequence < 0 || (nextSession == session && nextSequence <= sequence)) return@post
                    session = nextSession; sequence = nextSequence
                    lastReceived = SystemClock.elapsedRealtime(); retrySeconds = 1
                    render(json)
                }
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) = failed(response?.code)
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) { webSocket.close(code, reason) }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = failed()
            private fun failed(statusCode: Int? = null) { main.post {
                if (generation == currentGeneration && desired && resumed) {
                    socket = null
                    clearDisplay(when (statusCode) {
                        403 -> R.string.companion_pairing_refused
                        503 -> R.string.companion_sender_busy
                        else -> R.string.companion_retry
                    })
                    if (statusCode == 403) {
                        if (authToken.isNotEmpty() && !manualPairing) {
                            status.setText(R.string.companion_remembered_retry)
                            retrySeconds = 15
                            queueRetry()
                        } else {
                            pairingRefused = true; desired = false; setup.visibility = View.VISIBLE
                        }
                    } else queueRetry()
                }
            } }
        })
    }

    private fun queueRetry() {
        main.removeCallbacks(retry)
        if (desired && resumed) {
            main.postDelayed(retry, retrySeconds * 1000)
            retrySeconds = (retrySeconds * 2).coerceAtMost(15)
        }
    }
    private fun rememberPairing(pairing: JSONObject): Boolean {
        val id = pairing.optString("id")
        val token = pairing.optString("token")
        if (!id.matches(Regex("[a-fA-F0-9-]{36}")) || !token.matches(Regex("[a-fA-F0-9]{32}"))) return false
        val prefs = getPreferences(MODE_PRIVATE)
        if (!manualPairing && prefs.getString("id", "") != id) return false
        if (manualPairing || authToken != token || !prefs.getBoolean("auto", false)) {
            if (!prefs.edit().putString("id", id).putString("token", token).putBoolean("auto", true).commit()) {
                status.setText(R.string.companion_save_failed)
                return false
            }
        }
        authToken = token; manualPairing = false
        endpoint = "ws://$peerAddress/paired"
        code.setText(""); code.visibility = View.GONE
        return true
    }
    private fun stopConnection() {
        desired = false; generation++; socket?.cancel(); socket = null
        getPreferences(MODE_PRIVATE).edit().putBoolean("auto", false).apply()
        discovery?.stop()
        main.removeCallbacks(retry)
        clearDisplay(R.string.companion_idle)
        setup.visibility = View.VISIBLE
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
    private fun discover() {
        if (!desired || !resumed || SystemClock.elapsedRealtime() < nextDiscovery) return
        if (Build.VERSION.SDK_INT >= 37 && checkSelfPermission("android.permission.ACCESS_LOCAL_NETWORK") != PackageManager.PERMISSION_GRANTED) return
        val id = if (manualPairing) "" else getPreferences(MODE_PRIVATE).getString("id", "").orEmpty()
        nextDiscovery = SystemClock.elapsedRealtime() + 15_000
        val browser = discovery ?: CompanionDiscovery(this) { foundId, foundAddress ->
            if (!desired || !resumed) return@CompanionDiscovery
            val connectivity = getSystemService(android.net.ConnectivityManager::class.java)
            val network = connectivity.allNetworks.firstOrNull {
                connectivity.getNetworkCapabilities(it)?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) == true
            } ?: return@CompanionDiscovery
            acceptSender(network, foundId, foundAddress)
        }.also { discovery = it }
        browser.start(id)
    }
    private fun startHotspotDetection() {
        if (hotspot != null || !resumed) return
        if (Build.VERSION.SDK_INT >= 37 && checkSelfPermission("android.permission.ACCESS_LOCAL_NETWORK") != PackageManager.PERMISSION_GRANTED) return
        hotspot = CompanionHotspotLocator(this, ::acceptSender) { hasWifi ->
            if (socket == null && (!pairingRefused || !hasWifi)) clearDisplay(if (hasWifi) R.string.companion_sender_missing else R.string.companion_no_wifi)
        }.also { it.start() }
    }
    private fun acceptSender(network: Network, id: String, foundAddress: String) {
        if (!resumed) return
        val remembered = getPreferences(MODE_PRIVATE).getString("id", "").orEmpty()
        if (!manualPairing && remembered.isNotEmpty() && remembered != id) {
            if (socket == null) status.setText(R.string.companion_wrong_sender)
            return
        }
        val changed = peerAddress != foundAddress || wifiNetwork != network
        if (wifiNetwork != network) {
            wifiClient?.connectionPool?.evictAll()
            wifiNetwork = network
            wifiClient = client.newBuilder().socketFactory(network.socketFactory).proxy(Proxy.NO_PROXY).connectionPool(ConnectionPool()).build()
        }
        peerAddress = foundAddress; address.setText(foundAddress)
        getPreferences(MODE_PRIVATE).edit().putString("address", foundAddress).apply()
        endpoint = "ws://$foundAddress/${if (manualPairing) code.text else "paired"}"
        if (desired) { if (changed || socket == null) connect() }
        else if (!pairingRefused) status.setText(R.string.companion_sender_found)
    }
    private fun clearDisplay(message: Int) {
        lastReceived = 0
        status.setText(message); navigation.setText(R.string.companion_no_route); music.setText(R.string.companion_no_music)
        cover.setImageDrawable(null); lastCover = ""
    }
    private fun render(json: JSONObject) {
        setup.visibility = View.GONE
        if (!json.optBoolean("connected")) { clearDisplay(R.string.companion_waiting); return }
        status.setText(R.string.companion_live)
        val nav = json.optJSONObject("navigation") ?: JSONObject()
        navigation.text = if (!nav.has("maneuver")) getString(R.string.companion_no_route) else {
            val distance = getString(R.string.companion_distance, nav.optInt("distanceMeters").coerceAtLeast(0))
            val remaining = if (nav.has("remainingMeters") && nav.has("remainingSeconds"))
                "\n" + getString(R.string.companion_remaining, "${nav.optLong("remainingMeters") / 1000.0} km", "${nav.optLong("remainingSeconds") / 60}") else ""
            "${arrow(nav.optInt("maneuver"), nav.optInt("drivingSide"))} $distance\n${nav.optString("road").take(512)}$remaining"
        }
        val song = json.optJSONObject("music") ?: JSONObject()
        music.text = listOf(song.optString("title").take(2048), song.optString("artist").take(512), song.optString("album").take(512),
            if (!song.optBoolean("playing")) getString(R.string.companion_paused) else "").filter { it.isNotBlank() }.joinToString("\n").ifBlank { getString(R.string.companion_no_music) }
        val encoded = song.optString("artwork")
        if (encoded != lastCover) {
            lastCover = encoded
            val bitmap = runCatching {
                val bytes = Base64.decode(encoded, Base64.DEFAULT)
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                if (bounds.outWidth !in 1..256 || bounds.outHeight !in 1..256) null else BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }.getOrNull()
            cover.setImageBitmap(bitmap)
        }
    }
    private fun validAddress(value: String): Boolean {
        val parts = value.split(':')
        if (parts.size != 2 || parts[1].toIntOrNull() !in 1..65535) return false
        val octets = parts[0].split('.')
        return octets.size == 4 && octets.all { it.isNotEmpty() && it.all(Char::isDigit) && it.toIntOrNull() in 0..255 }
    }
    private fun arrow(type: Int, side: Int): String = when (type) {
        1, 20 -> "←"; 2, 21 -> "→"; 47 -> "↰"; 48 -> "↱"
        13, 22, 49, 52 -> "↖"; 14, 23, 50, 53 -> "↗"
        4, 18, 19, 26 -> if (side == 1) "↷" else "↶"
        6, 7, in 28..46 -> "⟳"
        10, 12, 24, 25, 27 -> "⚑"
        3, 5, 8, 9, 11, 51 -> "↑"
        else -> ""
    }
}
