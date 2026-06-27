package works.mees.jiib.command

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import works.mees.jiib.state.ConnectionState
import works.mees.jiib.state.KlippyState

class GatingStateTest {
    private val homeActive = listOf(CommandDispatcher.ActiveCommand(1, "home_all", GatingMode.HardLock))
    private val jogActive = listOf(CommandDispatcher.ActiveCommand(2, "jog_X", GatingMode.SoftBusy))

    @Test fun empty_isIdle() {
        assertEquals(GatingState.Idle, deriveGatingState(emptyList(), null, ConnectionState.Connected, KlippyState.Ready))
    }

    @Test fun hardLock_healthy_isLocked() {
        assertEquals(GatingState.Locked("home_all"),
            deriveGatingState(homeActive, null, ConnectionState.Connected, KlippyState.Ready))
    }

    @Test fun softBusy_isBusy() {
        assertEquals(GatingState.Busy("jog_X"),
            deriveGatingState(jogActive, null, ConnectionState.Connected, KlippyState.Ready))
    }

    @Test fun unresolvedLatch_isUnknown_evenWhenActiveEmpty() {
        assertEquals(GatingState.Unknown,
            deriveGatingState(emptyList(), "bed_mesh_calibrate", ConnectionState.Disconnected, KlippyState.Disconnected))
    }

    @Test fun hardLock_whileDisconnected_isUnknown() {
        assertTrue(deriveGatingState(homeActive, null, ConnectionState.Disconnected, KlippyState.Ready) is GatingState.Unknown)
    }

    @Test fun hardLock_beatsSoftBusy_whenBothActive() {
        assertEquals(GatingState.Locked("home_all"),
            deriveGatingState(homeActive + jogActive, null, ConnectionState.Connected, KlippyState.Ready))
    }
}
