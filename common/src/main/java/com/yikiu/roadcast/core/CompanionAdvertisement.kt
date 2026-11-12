package com.yikiu.roadcast.core

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager

/** Advertises identity and port only. Pairing secrets never enter mDNS records. */
internal class CompanionAdvertisement(context: Context) {
    private val manager = context.getSystemService(NsdManager::class.java)
    private val multicast = (context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager)
        .createMulticastLock("roadcast-companion").apply { setReferenceCounted(false) }
    private var listener: NsdManager.RegistrationListener? = null
    fun start(id: String) {
        val callback = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) = Unit
            override fun onServiceUnregistered(info: NsdServiceInfo) = Unit
            override fun onRegistrationFailed(info: NsdServiceInfo, error: Int) = Unit
            override fun onUnregistrationFailed(info: NsdServiceInfo, error: Int) = Unit
        }
        listener = callback
        runCatching {
            multicast.acquire()
            manager.registerService(NsdServiceInfo().apply {
                serviceName = "RoadCast-${id.take(8)}"
                serviceType = "_roadcast._tcp."
                port = CompanionSocketServer.PORT
                setAttribute("id", id)
            }, NsdManager.PROTOCOL_DNS_SD, callback)
        }
    }
    fun stop() {
        listener?.let { runCatching { manager.unregisterService(it) } }
        listener = null
        if (multicast.isHeld) multicast.release()
    }
}
