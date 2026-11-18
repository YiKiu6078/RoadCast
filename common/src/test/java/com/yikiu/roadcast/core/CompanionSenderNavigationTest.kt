package com.yikiu.roadcast.core

import android.os.Looper
import java.time.Duration
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class CompanionSenderNavigationTest {
    @Test fun newConnectionReturnsToRoadCastAndExistingConnectionStillAllowsSettings() {
        CompanionSenderService.receiverConnected = false
        val screen = Robolectric.buildActivity(CompanionSenderActivity::class.java).setup()
        try {
            CompanionSenderService.receiverConnected = true
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
            assertTrue(screen.get().isFinishing)
            assertEquals(RoadCastActivity::class.java.name, shadowOf(screen.get()).nextStartedActivity.component?.className)
        } finally { screen.pause().stop().destroy(); CompanionSenderService.receiverConnected = false }
        CompanionSenderService.receiverConnected = true
        val settings = Robolectric.buildActivity(CompanionSenderActivity::class.java).setup()
        try {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
            assertFalse(settings.get().isFinishing)
        } finally { settings.pause().stop().destroy(); CompanionSenderService.receiverConnected = false }
    }
}
