package com.yikiu.roadcast.core

import com.yikiu.roadcast.core.glance.CarPlayGlance
import com.yikiu.roadcast.core.media.CarPlayNowPlaying
import org.json.JSONObject

/** Complete snapshots include a session ID; iPhone authentication material and audio are excluded. */
internal object CompanionWire {
    fun snapshot(session: String, sequence: Long, route: CarPlayGlance.Snapshot,
        music: CarPlayNowPlaying, artwork: String): String = JSONObject().apply {
        put("version", 1)
        put("session", session)
        put("sequence", sequence)
        put("connected", route.connected)
        put("navigation", JSONObject().apply {
            if (route.connected && route.maneuverType != null) {
                put("maneuver", route.maneuverType)
                put("drivingSide", route.drivingSide)
                put("distanceMeters", route.distanceMeters)
                put("road", route.road.take(512))
                put("remainingMeters", route.remainingMeters)
                put("remainingSeconds", route.remainingSeconds)
                put("arrivalEpochSeconds", route.arrivalEpochSeconds)
            }
        })
        put("music", JSONObject().apply {
            if (route.connected) {
                put("title", music.title?.take(2048) ?: route.song.orEmpty().take(2048))
                put("artist", music.artist.orEmpty().take(512))
                put("album", music.album.orEmpty().take(512))
                put("playing", music.playing)
                put("elapsedMillis", music.elapsedMillis)
                put("durationMillis", music.durationMillis)
                put("artwork", artwork)
            }
        })
    }.toString()
}
