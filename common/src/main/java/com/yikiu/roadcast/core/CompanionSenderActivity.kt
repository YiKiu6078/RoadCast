package com.yikiu.roadcast.core

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.yikiu.roadcast.core.host.R
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections

/** Separate entry keeps sharing opt-in without changing the existing settings layout. */
class CompanionSenderActivity : Activity() {
    private val main = Handler(Looper.getMainLooper())
    private lateinit var status: TextView
    private val refresh = object : Runnable {
        override fun run() {
            val code = CompanionSenderService.pairingCode
            status.text = when {
                code != null -> getString(R.string.companion_code, code) + "\n\n" + getString(R.string.companion_addresses, addresses())
                CompanionSenderService.error -> getString(R.string.companion_error)
                else -> getString(R.string.companion_idle)
            }
            main.postDelayed(this, 1000)
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val gap = (20 * resources.displayMetrics.density).toInt()
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(gap, gap, gap, gap)
        }
        fun text(value: String, size: Float) = TextView(this).apply { text = value; textSize = size; setTextColor(Color.WHITE) }
        content.addView(text(getString(R.string.companion_sender_title), 28f))
        content.addView(text(getString(R.string.companion_help), 18f))
        status = text("", 24f).apply { setTextIsSelectable(true) }
        content.addView(status)
        content.addView(Button(this).apply {
            setText(R.string.companion_start)
            setOnClickListener {
                if (Build.VERSION.SDK_INT >= 37 && checkSelfPermission("android.permission.ACCESS_LOCAL_NETWORK") != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(arrayOf("android.permission.ACCESS_LOCAL_NETWORK"), 37)
                    android.widget.Toast.makeText(context, R.string.companion_permission, android.widget.Toast.LENGTH_LONG).show()
                } else {
                    val intent = Intent(this@CompanionSenderActivity, CompanionSenderService::class.java)
                    if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent) else startService(intent)
                }
            }
        })
        content.addView(Button(this).apply {
            setText(R.string.companion_stop)
            setOnClickListener { CompanionSenderService.disable(this@CompanionSenderActivity) }
        })
        content.addView(Button(this).apply {
            setText(R.string.companion_reset)
            setOnClickListener {
                CompanionSenderService.disable(this@CompanionSenderActivity)
                getSharedPreferences("roadcast-companion-sender", MODE_PRIVATE).edit().remove("token").apply()
            }
        })
        setContentView(ScrollView(this).apply { setBackgroundColor(Color.rgb(15, 22, 34)); addView(content) })
    }
    override fun onResume() { super.onResume(); main.post(refresh) }
    override fun onPause() { main.removeCallbacks(refresh); super.onPause() }
    private fun addresses(): String = runCatching {
        Collections.list(NetworkInterface.getNetworkInterfaces()).filter { it.isUp && !it.isLoopback }
            .flatMap { Collections.list(it.inetAddresses) }.filterIsInstance<Inet4Address>()
            .filter { !it.isLoopbackAddress && !it.isLinkLocalAddress }
            .joinToString("\n") { "${it.hostAddress}:${CompanionSocketServer.PORT}" }
    }.getOrDefault("").ifBlank { getString(R.string.companion_no_address) }
}
