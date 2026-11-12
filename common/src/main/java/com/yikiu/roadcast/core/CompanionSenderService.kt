package com.yikiu.roadcast.core

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.os.Build
import android.os.IBinder
import android.util.Base64
import com.yikiu.roadcast.core.glance.CarPlayGlance
import com.yikiu.roadcast.core.host.R
import com.yikiu.roadcast.core.media.CarPlayNowPlaying
import java.io.ByteArrayOutputStream
import java.security.SecureRandom
import java.util.Locale
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.json.JSONObject

/** Remembers the driver sharing preference; short codes rotate while paired-phone tokens survive restarts. */
class CompanionSenderService : Service() {
    private val worker = Executors.newSingleThreadScheduledExecutor()
    private var server: CompanionSocketServer? = null
    private var advertisement: CompanionAdvertisement? = null
    private var stopMusic: (() -> Unit)? = null
    @Volatile private var music = CarPlayNowPlaying()
    @Volatile private var cover = ""
    private var lastArtwork: Bitmap? = null
    private val artworkLock = Any()
    private var pendingCover: Bitmap? = null
    private var coverChanged = false
    private val session = UUID.randomUUID().toString()
    private var sequence = 0L

    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean("enabled", false).apply(); stopSelf(); return START_NOT_STICKY }
        if (server != null) return START_NOT_STICKY
        val pending = PendingIntent.getActivity(this, 0, Intent(this, CompanionSenderActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val manager = getSystemService(NotificationManager::class.java)
        val builder = if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL, getString(R.string.companion_sender_title), NotificationManager.IMPORTANCE_LOW))
            Notification.Builder(this, CHANNEL)
        } else @Suppress("DEPRECATION") Notification.Builder(this)
        val stop = PendingIntent.getService(this, 1, Intent(this, CompanionSenderService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE)
        val notification = builder.setSmallIcon(R.drawable.ic_roadcast_notification)
            .setContentTitle(getString(R.string.companion_sender_title)).setContentText(getString(R.string.companion_running))
            .setContentIntent(pending).setOngoing(true)
            .addAction(Notification.Action.Builder(null, getString(R.string.companion_stop), stop).build()).build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(8766, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        else startForeground(8766, notification)
        val code = String.format(Locale.ROOT, "%08d", SecureRandom().nextInt(100_000_000))
        val preferences = getSharedPreferences(PREFS, MODE_PRIVATE)
        val identity = preferences.getString("id", null) ?: UUID.randomUUID().toString()
        val token = preferences.getString("token", null) ?: UUID.randomUUID().toString().replace("-", "")
        val next = CompanionSocketServer(code, token = token)
        try {
            next.start(6_000, true)
            server = next
            pairingCode = code
            error = false
            preferences.edit().putString("id", identity).putString("token", token).putBoolean("enabled", true).apply()
            advertisement = CompanionAdvertisement(this).also { it.start(identity) }
        } catch (_: Exception) {
            next.stop(); error = true; stopSelf(); return START_NOT_STICKY
        }
        stopMusic = ClusterMusicState.observe { snapshot ->
            music = snapshot.info
            if (snapshot.artwork !== lastArtwork) {
                lastArtwork = snapshot.artwork
                // Copy on the owner thread, then encode off the UI / audio thread.
                val copy = runCatching { snapshot.artwork?.let { image ->
                    val scale = minOf(1f, 256f / maxOf(image.width, image.height))
                    val scaled = Bitmap.createScaledBitmap(image, maxOf(1, (image.width * scale).toInt()), maxOf(1, (image.height * scale).toInt()), true)
                    val owned = scaled.copy(Bitmap.Config.ARGB_8888, false)
                    if (scaled !== image) scaled.recycle()
                    owned
                } }.getOrNull()
                synchronized(artworkLock) {
                    pendingCover?.recycle()
                    pendingCover = copy
                    coverChanged = true
                }
            }
        }
        worker.scheduleWithFixedDelay({
            runCatching {
                val pending = synchronized(artworkLock) {
                    if (!coverChanged) null else (pendingCover to true).also { pendingCover = null; coverChanged = false }
                }
                if (pending != null) {
                    val image = pending.first
                    cover = try {
                        if (image == null) "" else ByteArrayOutputStream().use { out ->
                            image.compress(Bitmap.CompressFormat.JPEG, 70, out)
                            Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
                        }
                    } finally { image?.recycle() }
                }
                val json = JSONObject(CompanionWire.snapshot(session, ++sequence, CarPlayGlance.snapshot(), music, cover))
                json.put("pairing", JSONObject().put("id", identity).put("token", token))
                next.publish(json.toString())
            }
        }, 0, 1, TimeUnit.SECONDS)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        pairingCode = null
        stopMusic?.invoke()
        advertisement?.stop()
        server?.stop()
        worker.shutdownNow()
        synchronized(artworkLock) { pendingCover?.recycle(); pendingCover = null }
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
    companion object {
        const val ACTION_STOP = "com.yikiu.roadcast.STOP_COMPANION"
        private const val CHANNEL = "roadcast-companion"
        private const val PREFS = "roadcast-companion-sender"
        fun restoreIfEnabled(context: Context) {
            if (!context.getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean("enabled", false)) return
            if (Build.VERSION.SDK_INT >= 37 && context.checkSelfPermission("android.permission.ACCESS_LOCAL_NETWORK") != PackageManager.PERMISSION_GRANTED) return
            runCatching {
                val intent = Intent(context, CompanionSenderService::class.java)
                if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent) else context.startService(intent)
            }
        }
        fun disable(context: Context) {
            context.getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean("enabled", false).apply()
            context.stopService(Intent(context, CompanionSenderService::class.java))
        }
        @Volatile var pairingCode: String? = null
            private set
        @Volatile var error = false
            private set
    }
}
