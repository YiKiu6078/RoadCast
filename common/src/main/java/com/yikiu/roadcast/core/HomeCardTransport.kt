package com.yikiu.roadcast.core

/** The home card's session is the one RoadCast opened with the saved connection choice, saved before it opens. */
internal object HomeCardTransport {
    fun wired(sessionRunning: Boolean, wirelessSelected: Boolean): Boolean = sessionRunning && !wirelessSelected
}
