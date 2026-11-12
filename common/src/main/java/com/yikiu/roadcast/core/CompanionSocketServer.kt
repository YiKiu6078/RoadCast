package com.yikiu.roadcast.core

import android.os.SystemClock
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoWSD
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicReference

/** One read-only companion. Pairing failures are rate limited across all network interfaces. */
internal class CompanionSocketServer(private val code: String, port: Int = PORT, private val token: String = "") : NanoWSD(port) {
    private val client = AtomicReference<WebSocket?>()
    private var attempts = 0
    private var attemptWindow = 0L

    @Synchronized override fun serve(session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
        val now = SystemClock.elapsedRealtime()
        if (now - attemptWindow >= 60_000) { attempts = 0; attemptWindow = now }
        val valid = MessageDigest.isEqual(session.uri.toByteArray(), "/$code".toByteArray()) ||
            (token.isNotEmpty() && MessageDigest.isEqual(session.headers["authorization"].orEmpty().toByteArray(), "Bearer $token".toByteArray()))
        if (attempts >= 10 || !valid || !isWebsocketRequested(session)) {
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
            if (!client.compareAndSet(null, this)) close(WebSocketFrame.CloseCode.PolicyViolation, "Receiver already connected", false)
        }
        override fun onClose(code: WebSocketFrame.CloseCode?, reason: String?, initiatedByRemote: Boolean) {
            client.compareAndSet(this, null)
        }
        override fun onMessage(message: WebSocketFrame) {
            // Commands are deliberately unsupported in this read-only version.
            close(WebSocketFrame.CloseCode.PolicyViolation, "Read only", false)
        }
        override fun onPong(pong: WebSocketFrame) = Unit
        override fun onException(exception: IOException) { client.compareAndSet(this, null) }
    }

    fun publish(snapshot: String) {
        val socket = client.get() ?: return
        try { socket.send(snapshot) } catch (_: IOException) { client.compareAndSet(socket, null) }
    }

    override fun stop() { super.stop(); client.set(null) }
    companion object { const val PORT = 8766 }
}
