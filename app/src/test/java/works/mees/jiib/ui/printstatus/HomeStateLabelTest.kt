package works.mees.jiib.ui.printstatus

import org.junit.Assert.assertEquals
import org.junit.Test
import works.mees.jiib.R
import works.mees.jiib.state.KlippyState
import works.mees.jiib.state.PrintState

class HomeStateLabelTest {

    @Test
    fun klippyShutdown_winsOverPrintState() {
        assertEquals(
            R.string.printstatus_status_shutdown,
            homeStateLabelRes(PrintState.Printing, KlippyState.Shutdown),
        )
    }

    @Test
    fun klippyError_winsOverPrintState() {
        assertEquals(
            R.string.printstatus_status_error,
            homeStateLabelRes(PrintState.Standby, KlippyState.Error),
        )
    }

    @Test
    fun klippyReady_fallsThroughToPrintState() {
        assertEquals(R.string.printstatus_status_printing, homeStateLabelRes(PrintState.Printing, KlippyState.Ready))
        assertEquals(R.string.printstatus_status_standby, homeStateLabelRes(PrintState.Standby, KlippyState.Ready))
        assertEquals(R.string.printstatus_status_complete, homeStateLabelRes(PrintState.Complete, KlippyState.Ready))
        assertEquals(R.string.printstatus_status_cancelled, homeStateLabelRes(PrintState.Cancelled, KlippyState.Ready))
    }
}
