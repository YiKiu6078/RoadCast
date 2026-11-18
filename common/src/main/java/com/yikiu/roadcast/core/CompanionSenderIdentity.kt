package com.yikiu.roadcast.core

import android.content.Context
import java.util.UUID

/** Persist before accepting a phone so process death cannot silently invalidate a new pairing. */
internal object CompanionSenderIdentity {
    data class Identity(val id: String, val token: String)
    @Synchronized fun load(context: Context): Identity {
        val prefs = context.getSharedPreferences("roadcast-companion-sender", Context.MODE_PRIVATE)
        val id = prefs.getString("id", null) ?: UUID.randomUUID().toString()
        val token = prefs.getString("token", null) ?: UUID.randomUUID().toString().replace("-", "")
        check(prefs.edit().putString("id", id).putString("token", token).commit()) { "Could not save companion pairing identity" }
        return Identity(id, token)
    }
}
