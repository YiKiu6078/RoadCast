package com.yikiu.roadcast.core

import android.os.SystemClock
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoWSD
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicReference

/** One read-only companion. Pairing failures are rate limited across all network interfaces. */
internal class CompanionSocketServer(private val code: String, port: Int = PORT, private val token: String = "", private val senderId: String = "", private val connected: (Boolean) -> Unit = {}) : NanoWSD(port) {
    private val client = AtomicReference<WebSocket?>()
    private var attempts = 0
    private var attemptWindow = 0L

    @Synchronized override fun serve(session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
        if (session.method == NanoHTTPD.Method.GET && session.uri == "/companion-info") {
            return newFixedLengthResponse(NanoHTTPD.Response.Status.OK, "application/json",
                org.json.JSONObject().put("service", "roadcast-companion").put("version", 1).put("id", senderId).toString())
        }
        val now = SystemClock.elapsedRealtime()
        if (now - attemptWindow >= 60_000) { attempts = 0; attemptWindow = now }
        val remembered = token.isNotEmpty() && MessageDigest.isEqual(session.headers["authorization"].orEmpty().toByteArray(), "Bearer $token".toByteArray())
        val validCode = MessageDigest.isEqual(session.uri.toByteArray(), "/$code".toByteArray())
        if ((!remembered && (attempts >= 10 || !validCode)) || !isWebsocketRequested(session)) {
            attempts++
            return newFixedLengthResponse(NanoHTTPD.Response.Status.FORBIDDEN, "text/plain", "Pairing refused")
        }
        if (client.get() != null) {
            return newFixedLengthResponse(NanoHTTPD.Response.Status.SERVICE_UNAVAILABLE, "text/plain", "Receiver already connected")
        }
        return super.serve(session)
    }

    override fun openWebSocket(handshake: NanoHTTPD.IHTTPSession): WebSocket = object : WebSocket(handshake) {
        override fun onOpen() {
            if (!client.compareAndSet(null, this)) {
                close(WebSocketFrame.CloseCode.PolicyViolation, "Receiver already connected", false)
                return
            }
            try {
                if (token.isNotEmpty() && senderId.isNotEmpty()) {
                    send(org.json.JSONObject().put("version", 1).put("type", "paired")
                        .put("pairing", org.json.JSONObject().put("id", senderId).put("token", token)).toString())
                }
                connected(true)
            } catch (exception: IOException) {
                if (client.compareAndSet(this, null)) connected(false)
                throw exception
            }
        }
        override fun onClose(code: WebSocketFrame.CloseCode?, reason: String?, initiatedByRemote: Boolean) {
            if (client.compareAndSet(this, null)) connected(false)
        }
        override fun onMessage(message: WebSocketFrame) {
            // Commands are deliberately unsupported in this read-only version.
            close(WebSocketFrame.CloseCode.PolicyViolation, "Read only", false)
        }
        override fun onPong(pong: WebSocketFrame) = Unit
        override fun onException(exception: IOException) { if (client.compareAndSet(this, null)) connected(false) }
    }

    fun publish(snapshot: String) {
        val socket = client.get() ?: return
        try { socket.send(snapshot) } catch (_: IOException) { if (client.compareAndSet(socket, null)) connected(false) }
    }

    override fun stop() { super.stop(); client.set(null); connected(false) }
    companion object { const val PORT = 8766 }
}
