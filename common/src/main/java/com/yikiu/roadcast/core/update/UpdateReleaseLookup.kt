package com.yikiu.roadcast.core.update

import java.net.URL
import java.net.URLConnection

/** This locally signed RoadCast fork has no public release feed. */
internal object UpdateReleaseLookup {
    @Suppress("UNUSED_PARAMETER")
    fun latest(
        installedVersion: String,
        userAgent: String,
        openConnection: (URL) -> URLConnection = { it.openConnection() },
    ): UpdateRelease? = null
}
