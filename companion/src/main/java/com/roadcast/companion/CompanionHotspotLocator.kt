package com.roadcast.companion

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import okhttp3.Call
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.Inet4Address
import java.net.Proxy
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Probes only Wi-Fi gateways, never scans a subnet or uses mobile data to locate a car. */
internal class CompanionHotspotLocator(context: Context,
    private val found: (Network, String, String) -> Unit,
    private val unavailable: (Boolean) -> Unit) {
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadScheduledExecutor()
    private val client = OkHttpClient.Builder().proxy(Proxy.NO_PROXY).connectTimeout(2, TimeUnit.SECONDS)
        .readTimeout(2, TimeUnit.SECONDS).callTimeout(3, TimeUnit.SECONDS).build()
    @Volatile private var running = false
    @Volatile private var call: Call? = null
    fun start() {
        if (running) return
        running = true
        worker.scheduleWithFixedDelay({ runCatching { probe() } }, 0, 5, TimeUnit.SECONDS)
    }
    private fun probe() {
        val networks = connectivity.allNetworks.filter {
            connectivity.getNetworkCapabilities(it)?.let { caps ->
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) && !caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
            } == true
        }
        for (network in networks) {
            val gateways = connectivity.getLinkProperties(network)?.routes.orEmpty()
                .filter { it.isDefaultRoute }.mapNotNull { it.gateway as? Inet4Address }
                .filter { !it.isAnyLocalAddress && !it.isLoopbackAddress }.distinct()
            for (gateway in gateways) {
                if (!running) return
                val address = "${gateway.hostAddress}:8766"
                val transport = client.newBuilder().socketFactory(network.socketFactory).connectionPool(ConnectionPool()).build()
                try {
                    val request = transport.newCall(Request.Builder().url("http://$address/companion-info").header("Connection", "close").build())
                    call = request
                    val id = request.execute().use { response ->
                        if (!response.isSuccessful) return@use null
                        val source = response.body?.source() ?: return@use null
                        source.request(4097)
                        if (source.buffer.size > 4096) return@use null
                        parseIdentity(source.readUtf8())
                    }
                    if (id != null) {
                        main.post { if (running) found(network, id, address) }
                        return
                    }
                } catch (_: Exception) { /* A router or stopped sender is not a paired car. */ }
                finally { call = null; transport.connectionPool.evictAll() }
            }
        }
        main.post { if (running) unavailable(networks.isNotEmpty()) }
    }
    fun stop() {
        running = false; call?.cancel(); worker.shutdownNow()
        client.connectionPool.evictAll(); client.dispatcher.executorService.shutdown()
    }
    companion object {
        fun parseIdentity(text: String): String? = runCatching {
            val json = JSONObject(text)
            json.optString("id").takeIf {
                json.optString("service") == "roadcast-companion" && json.optInt("version") == 1 &&
                    it.matches(Regex("[a-fA-F0-9-]{36}"))
            }
        }.getOrNull()
    }
}
