package com.yikiu.roadcast.core.transport

import com.yikiu.roadcast.core.network.WirelessStartupException
import com.yikiu.roadcast.core.network.WirelessStartupFailure
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BluetoothRadioCheckTest {
    @Test fun aRadioThatIsOnLetsTheAttemptGoOn() {
        assertNull(BluetoothRadioCheck.blocker(adapterPresent = true, enabled = true, pausedByRoadCast = false))
    }

    // An Allwinner T8 head unit came up with Bluetooth off. A fork build that still offered the
    // local-only hotspot made 94 attempts in under six hours; each started the hotspot and only
    // then failed on the radio.
    @Test fun aRadioThatIsOffStopsTheAttemptBeforeTheHotspot() {
        assertEquals(
            BluetoothRadioCheck.RADIO_OFF,
            BluetoothRadioCheck.blocker(adapterPresent = true, enabled = false, pausedByRoadCast = false),
        )
    }

    // The pause is undone before the handshake; stopping here would never let that happen.
    @Test fun aRadioThatRoadCastPausedDoesNotStopTheAttempt() {
        assertNull(BluetoothRadioCheck.blocker(adapterPresent = true, enabled = false, pausedByRoadCast = true))
    }

    @Test fun aHeadUnitWithoutAnAdapterStopsTheAttemptWhateverElseIsKnown() {
        for (paused in listOf(false, true)) {
            assertEquals(
                BluetoothRadioCheck.NO_ADAPTER,
                BluetoothRadioCheck.blocker(adapterPresent = false, enabled = false, pausedByRoadCast = paused),
            )
        }
    }

    // No retry gives a head unit a Bluetooth adapter; it used to be retried for ever as "Connecting to your iPhone".
    @Test fun aMissingAdapterStopsTheAttemptsWithItsCause() {
        val failure = BluetoothRadioCheck.failure(BluetoothRadioCheck.NO_ADAPTER)
        assertTrue(failure is WirelessStartupException)
        assertEquals(WirelessStartupFailure.BLUETOOTH_UNUSABLE, (failure as WirelessStartupException).reason)
        assertFalse(failure.reason.retryable)
        assertEquals(BluetoothRadioCheck.NO_ADAPTER, failure.message)
        assertTrue(BluetoothRadioCheck.NO_ADAPTER.startsWith("Bluetooth adapter is unavailable"))
        assertTrue(BluetoothRadioCheck.NO_ADAPTER.contains(BluetoothLinkCheck.NO_SOCKETS_MARK))
    }

    // The driver can switch the radio on, so that attempt goes on being retried.
    @Test fun aRadioThatIsOffIsStillRetried() {
        val failure = BluetoothRadioCheck.failure(BluetoothRadioCheck.RADIO_OFF)
        assertFalse(failure is WirelessStartupException)
        assertEquals(BluetoothRadioCheck.RADIO_OFF, failure.message)
    }

    // CarPlayHostActivity shows the request in the app's language by finding these words.
    @Test fun theRequestCarriesTheWordsTheHostScreenLooksFor() {
        assertTrue(BluetoothRadioCheck.RADIO_OFF.contains(BluetoothRadioCheck.RADIO_OFF_MARK))
    }

    @Test fun blockerForStateHandlesAllAdapterStatesAndPauseSemantics() {
        // Bluetooth off -> RADIO_OFF
        assertEquals(
            BluetoothRadioCheck.RADIO_OFF,
            BluetoothRadioCheck.blockerForState(
                adapterPresent = true,
                state = android.bluetooth.BluetoothAdapter.STATE_OFF,
                pausedByRoadCast = false,
            ),
        )

        // Bluetooth turning off -> RADIO_OFF
        assertEquals(
            BluetoothRadioCheck.RADIO_OFF,
            BluetoothRadioCheck.blockerForState(
                adapterPresent = true,
                state = android.bluetooth.BluetoothAdapter.STATE_TURNING_OFF,
                pausedByRoadCast = false,
            ),
        )

        // Bluetooth off or turning off when RoadCast itself paused it -> no blocker
        assertNull(
            BluetoothRadioCheck.blockerForState(
                adapterPresent = true,
                state = android.bluetooth.BluetoothAdapter.STATE_OFF,
                pausedByRoadCast = true,
            ),
        )
        assertNull(
            BluetoothRadioCheck.blockerForState(
                adapterPresent = true,
                state = android.bluetooth.BluetoothAdapter.STATE_TURNING_OFF,
                pausedByRoadCast = true,
            ),
        )

        // Bluetooth turning on -> no blocker
        assertNull(
            BluetoothRadioCheck.blockerForState(
                adapterPresent = true,
                state = android.bluetooth.BluetoothAdapter.STATE_TURNING_ON,
                pausedByRoadCast = false,
            ),
        )

        // Bluetooth on -> no blocker
        assertNull(
            BluetoothRadioCheck.blockerForState(
                adapterPresent = true,
                state = android.bluetooth.BluetoothAdapter.STATE_ON,
                pausedByRoadCast = false,
            ),
        )

        // No Bluetooth adapter -> NO_ADAPTER
        assertEquals(
            BluetoothRadioCheck.NO_ADAPTER,
            BluetoothRadioCheck.blockerForState(
                adapterPresent = false,
                state = null,
                pausedByRoadCast = false,
            ),
        )
        assertEquals(
            BluetoothRadioCheck.NO_ADAPTER,
            BluetoothRadioCheck.blockerForState(
                adapterPresent = false,
                state = null,
                pausedByRoadCast = true,
            ),
        )
    }
}
