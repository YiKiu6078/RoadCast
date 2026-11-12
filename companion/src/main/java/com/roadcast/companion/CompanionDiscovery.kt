package com.roadcast.companion

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import java.net.Inet4Address

/** Resolves only while the screen is visible. The persisted sender ID selects the trusted peer. */
internal class CompanionDiscovery(context: Context, private val found: (String, String) -> Unit) {
    private val manager = context.getSystemService(NsdManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private val multicast = (context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager)
        .createMulticastLock("roadcast-companion-discovery").apply { setReferenceCounted(false) }
    private var listener: NsdManager.DiscoveryListener? = null
    private var generation = 0
    private var resolving = false
    fun start(expectedId: String) {
        stop()
        val active = generation
        val callback = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(type: String) = Unit
            override fun onDiscoveryStopped(type: String) = Unit
            override fun onStartDiscoveryFailed(type: String, error: Int) = Unit
            override fun onStopDiscoveryFailed(type: String, error: Int) = Unit
            override fun onServiceLost(info: NsdServiceInfo) = Unit
            override fun onServiceFound(info: NsdServiceInfo) { main.post {
                if (active != generation || resolving || !info.serviceName.startsWith("RoadCast-${expectedId.take(8)}")) return@post
                resolving = true
                @Suppress("DEPRECATION")
                runCatching { manager.resolveService(info, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(info: NsdServiceInfo, error: Int) { main.post { if (active == generation) resolving = false } }
                    override fun onServiceResolved(info: NsdServiceInfo) { main.post {
                        if (active != generation) return@post
                        resolving = false
                        @Suppress("DEPRECATION") val host = info.host
                        val id = info.attributes["id"]?.toString(Charsets.UTF_8).orEmpty()
                        if (host is Inet4Address && id.isNotBlank()) found(id, "${host.hostAddress}:${info.port}")
                    } }
                }) }.onFailure { resolving = false }
            } }
        }
        listener = callback
        runCatching { multicast.acquire(); manager.discoverServices("_roadcast._tcp.", NsdManager.PROTOCOL_DNS_SD, callback) }
    }
    fun stop() {
        generation++; resolving = false
        listener?.let { runCatching { manager.stopServiceDiscovery(it) } }
        listener = null
        if (multicast.isHeld) multicast.release()
    }
}
