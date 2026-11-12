package com.roadcast.companion

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.Base64
import android.view.View
import android.widget.TextView
import java.io.File
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "zh-rCN-w400dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CompanionDisplayTest {
    @Test fun disconnectClearsPreviouslyVisibleGuidanceAndSong() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        try {
            val activity = controller.get()
            show(activity, sample())
            val nav = ReflectionHelpers.getField<TextView>(activity, "navigation")
            assertTrue(nav.text.contains("150"))
            show(activity, JSONObject().put("connected", false))
            assertFalse(nav.text.contains("150"))
            assertEquals(activity.getString(R.string.companion_no_music), ReflectionHelpers.getField<TextView>(activity, "music").text.toString())
        } finally { controller.pause().stop().destroy() }
    }
    @Test fun capturesCompactLargeFont() = capture("compact-zh", 400, 800)
    @Test fun remembersPairingAcrossRecreationButExplicitDisconnectDisablesAutoConnect() {
        val first = Robolectric.buildActivity(MainActivity::class.java).create()
        first.get().getPreferences(0).edit().putString("id", "12345678-1234-1234-1234-123456789abc")
            .putString("token", "0123456789abcdef0123456789abcdef").putString("address", "192.168.1.10:8766")
            .putBoolean("auto", true).commit()
        first.destroy()
        val restored = Robolectric.buildActivity(MainActivity::class.java).create()
        try {
            assertTrue(ReflectionHelpers.getField<Boolean>(restored.get(), "desired"))
            assertEquals("ws://192.168.1.10:8766/paired", ReflectionHelpers.getField<String>(restored.get(), "endpoint"))
            ReflectionHelpers.callInstanceMethod<Unit>(restored.get(), "stopConnection")
            assertFalse(restored.get().getPreferences(0).getBoolean("auto", true))
            assertFalse(restored.get().getPreferences(0).getString("token", "").isNullOrEmpty())
        } finally {
            restored.get().getPreferences(0).edit().clear().commit()
            restored.destroy()
        }
    }
    @Test fun staleConnectionClearsGuidanceWithoutWaitingForSocketFailure() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        try {
            val activity = controller.get()
            show(activity, sample())
            ReflectionHelpers.setField(activity, "lastReceived", android.os.SystemClock.elapsedRealtime() - 6000)
            ReflectionHelpers.getField<Runnable>(activity, "watchdog").run()
            assertEquals(activity.getString(R.string.companion_stale), ReflectionHelpers.getField<TextView>(activity, "status").text.toString())
            assertEquals(activity.getString(R.string.companion_no_route), ReflectionHelpers.getField<TextView>(activity, "navigation").text.toString())
        } finally { controller.pause().stop().destroy() }
    }
    @Test @Config(qualifiers = "en-w960dp-h540dp-land-mdpi")
    fun capturesLandscapeLargeFont() = capture("landscape-en", 960, 540)
    @Test @Config(qualifiers = "ar-ldrtl-w400dp-h800dp-mdpi")
    fun capturesRtlLargeFont() = capture("compact-ar", 400, 800)

    private fun sample() = JSONObject().put("connected", true)
        .put("navigation", JSONObject().put("maneuver", 2).put("distanceMeters", 150).put("road", "中山路 · Zhongshan Road")
            .put("remainingMeters", 12400).put("remainingSeconds", 1080))
        .put("music", JSONObject().put("title", "此刻的歌词 · Current lyric line").put("artist", "RoadCast Music").put("album", "Live companion").put("playing", true).put("artwork", artwork()))
    private fun artwork(): String {
        val bitmap = Bitmap.createBitmap(160, 160, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.rgb(32, 100, 170))
            canvas.drawText("♪", 52f, 116f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 100f })
            return java.io.ByteArrayOutputStream().use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out); Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP) }
        } finally { bitmap.recycle() }
    }
    private fun show(activity: MainActivity, json: JSONObject) {
        ReflectionHelpers.callInstanceMethod<Unit>(activity, "render", ReflectionHelpers.ClassParameter.from(JSONObject::class.java, json))
    }
    private fun capture(name: String, width: Int, height: Int) {
        RuntimeEnvironment.setFontScale(1.5f)
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        try {
            val activity = controller.get()
            show(activity, sample())
            val root = activity.window.decorView
            root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
            root.layout(0, 0, width, height)
            val image = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            try {
                root.draw(Canvas(image))
                val output = File("build/reports/companion-ui/$name.png")
                output.parentFile.mkdirs()
                output.outputStream().use { assertTrue(image.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            } finally { image.recycle() }
        } finally { controller.pause().stop().destroy() }
    }
}
