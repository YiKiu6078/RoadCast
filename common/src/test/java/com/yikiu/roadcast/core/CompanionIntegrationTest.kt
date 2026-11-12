package com.yikiu.roadcast.core

import com.yikiu.roadcast.core.glance.CarPlayGlance
import com.yikiu.roadcast.core.media.CarPlayNowPlaying
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.Socket

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class CompanionIntegrationTest {
    @Test fun disconnectedSnapshotNeverLeaksRetainedSongOrArtwork() {
        val json = JSONObject(CompanionWire.snapshot("session", 1, CarPlayGlance.Snapshot(),
            CarPlayNowPlaying(title = "old track", artist = "old artist"), "old cover"))
        assertEquals(0, json.getJSONObject("music").length())
        assertEquals(0, json.getJSONObject("navigation").length())
        assertFalse(json.getBoolean("connected"))
    }

    @Test fun snapshotPreservesNavigationAndDynamicTitleWithoutInventingLyrics() {
        val json = JSONObject(CompanionWire.snapshot("session", 3,
            CarPlayGlance.Snapshot(connected = true, maneuverType = 2, distanceMeters = 150, road = "road", remainingSeconds = 600),
            CarPlayNowPlaying(title = "current lyrics line", playing = true), ""))
        assertEquals(150, json.getJSONObject("navigation").getInt("distanceMeters"))
        assertEquals("current lyrics line", json.getJSONObject("music").getString("title"))
        assertEquals(3, json.getLong("sequence"))
    }

    @Test fun pairingIsCheckedBeforeUpgradeAndAnAuthenticatedSocketReceivesSnapshots() {
        val server = CompanionSocketServer("12345678", 0)
        server.start(3000, true)
        try {
            Socket("127.0.0.1", server.listeningPort).use { socket ->
                socket.soTimeout = 3000
                socket.getOutputStream().write("GET /00000000 HTTP/1.1\r\nHost: localhost\r\n\r\n".toByteArray())
                assertTrue(headers(socket).contains("403"))
            }
            Socket("127.0.0.1", server.listeningPort).use { socket ->
                socket.soTimeout = 3000
                socket.getOutputStream().write(("GET /12345678 HTTP/1.1\r\nHost: localhost\r\nUpgrade: websocket\r\n" +
                    "Connection: Upgrade\r\nSec-WebSocket-Version: 13\r\nSec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\n\r\n").toByteArray())
                assertTrue(headers(socket).contains("101"))
                // NanoWSD invokes onOpen just after flushing the handshake.
                val deadline = System.nanoTime() + 2_000_000_000L
                while (socket.getInputStream().available() == 0 && System.nanoTime() < deadline) {
                    server.publish("hello")
                    Thread.sleep(10)
                }
                val input = socket.getInputStream()
                assertEquals(0x81, input.read())
                assertEquals(5, input.read())
                val text = ByteArray(5)
                java.io.DataInputStream(input).readFully(text)
                assertEquals("hello", String(text))
            }
        } finally { server.stop() }
    }

    private fun headers(socket: Socket): String {
        val text = StringBuilder()
        while (!text.endsWith("\r\n\r\n") && text.length < 8192) {
            val next = socket.getInputStream().read()
            check(next >= 0)
            text.append(next.toChar())
        }
        return text.toString()
    }

    @Test fun rememberedTokenWorksAfterTheShortPairingCodeChanges() {
        val token = "0123456789abcdef0123456789abcdef"
        val server = CompanionSocketServer("87654321", 0, token)
        server.start(3000, true)
        try {
            Socket("127.0.0.1", server.listeningPort).use { socket ->
                socket.soTimeout = 3000
                socket.getOutputStream().write("GET /12345678 HTTP/1.1\r\nHost: localhost\r\n\r\n".toByteArray())
                assertTrue(headers(socket).contains("403"))
            }
            Socket("127.0.0.1", server.listeningPort).use { socket ->
                socket.soTimeout = 3000
                socket.getOutputStream().write(("GET /paired HTTP/1.1\r\nHost: localhost\r\nUpgrade: websocket\r\n" +
                    "Authorization: Bearer $token\r\nConnection: Upgrade\r\nSec-WebSocket-Version: 13\r\n" +
                    "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\n\r\n").toByteArray())
                assertTrue(headers(socket).contains("101"))
            }
        } finally { server.stop() }
    }
}
