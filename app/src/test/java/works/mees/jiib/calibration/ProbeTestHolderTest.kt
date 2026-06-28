package works.mees.jiib.calibration

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import works.mees.jiib.state.PrinterState
import works.mees.jiib.state.PrinterStateStore

/**
 * Host-side proof for [ProbeTestHolder]: the toolkit-agnostic holder that drives the Probe-Test
 * page off [PrinterState.probeLastQuery] / [PrinterState.probeLastZ] and [parseProbeAccuracy] from
 * the raw gcode stream.
 *
 * Mirrors [ProbeCalibrateHolderTest]: a real [PrinterStateStore] seeded synchronously under
 * `runTest` with an [UnconfinedTestDispatcher]; bounded coroutine tests (no hang risk — memory
 * `dinghy-display-gradle-hang-interop`).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProbeTestHolderTest {

    @Test
    fun defaultVmHasNullFields() = runTest(UnconfinedTestDispatcher()) {
        val store = PrinterStateStore(backgroundScope)
        val holder = ProbeTestHolder(backgroundScope, store)

        assertNull(holder.vm.value.triggered)
        assertNull(holder.vm.value.lastZ)
        assertNull(holder.vm.value.accuracy)
    }

    @Test
    fun probeLastQueryTrueFoldsIntoTriggeredTrue() = runTest(UnconfinedTestDispatcher()) {
        val store = PrinterStateStore(backgroundScope)
        val holder = ProbeTestHolder(backgroundScope, store)

        store.seed(PrinterState(probeLastQuery = true))
        runCurrent()

        assertEquals(true, holder.vm.value.triggered)
    }

    @Test
    fun probeLastQueryFalseFoldsIntoTriggeredFalse() = runTest(UnconfinedTestDispatcher()) {
        val store = PrinterStateStore(backgroundScope)
        val holder = ProbeTestHolder(backgroundScope, store)

        store.seed(PrinterState(probeLastQuery = false))
        runCurrent()

        assertEquals(false, holder.vm.value.triggered)
    }

    @Test
    fun probeLastZFoldsIntoLastZ() = runTest(UnconfinedTestDispatcher()) {
        val store = PrinterStateStore(backgroundScope)
        val holder = ProbeTestHolder(backgroundScope, store)

        store.seed(PrinterState(probeLastZ = 1.5))
        runCurrent()

        assertEquals(1.5, holder.vm.value.lastZ!!, 0.0001)
    }

    @Test
    fun accuracyLineFromGcodeResponsesSurfacesInVm() = runTest(UnconfinedTestDispatcher()) {
        val store = PrinterStateStore(backgroundScope)
        val holder = ProbeTestHolder(backgroundScope, store)

        store.onGcodeLine(
            "probe accuracy results: maximum 2.012500, minimum 2.000000, range 0.012500, " +
                "average 2.005000, median 2.005000, standard deviation 0.003536"
        )
        runCurrent()

        val accuracy = holder.vm.value.accuracy
        assertNotNull("accuracy populated from gcode stream", accuracy)
        assertEquals(2.0125, accuracy!!.maximum, 0.0001)
        assertEquals(2.0000, accuracy.minimum, 0.0001)
        assertEquals(0.0125, accuracy.range, 0.0001)
        assertEquals(2.005, accuracy.average, 0.0001)
        assertEquals(2.005, accuracy.median, 0.0001)
        assertEquals(0.003536, accuracy.stdDev, 0.000001)
    }

    @Test
    fun progressLinesAreIgnoredAndLeaveAccuracyNull() = runTest(UnconfinedTestDispatcher()) {
        val store = PrinterStateStore(backgroundScope)
        val holder = ProbeTestHolder(backgroundScope, store)

        store.onGcodeLine("probe at 100.0,100.0 is z=2.005000")
        runCurrent()

        assertNull("per-sample progress line must not populate accuracy", holder.vm.value.accuracy)
    }

    @Test
    fun latestAccuracyLineReplacesPriorResult() = runTest(UnconfinedTestDispatcher()) {
        val store = PrinterStateStore(backgroundScope)
        val holder = ProbeTestHolder(backgroundScope, store)

        store.onGcodeLine(
            "probe accuracy results: maximum 1.000000, minimum 1.000000, range 0.000000, " +
                "average 1.000000, median 1.000000, standard deviation 0.000000"
        )
        runCurrent()
        store.onGcodeLine(
            "probe accuracy results: maximum 2.012500, minimum 2.000000, range 0.012500, " +
                "average 2.005000, median 2.005000, standard deviation 0.003536"
        )
        runCurrent()

        assertEquals("second result replaces first", 2.0125, holder.vm.value.accuracy!!.maximum, 0.0001)
    }

    @Test
    fun printerStateAndAccuracyCombineInOneVm() = runTest(UnconfinedTestDispatcher()) {
        val store = PrinterStateStore(backgroundScope)
        val holder = ProbeTestHolder(backgroundScope, store)

        store.seed(PrinterState(probeLastQuery = true, probeLastZ = 2.005))
        runCurrent()
        store.onGcodeLine(
            "probe accuracy results: maximum 2.012500, minimum 2.000000, range 0.012500, " +
                "average 2.005000, median 2.005000, standard deviation 0.003536"
        )
        runCurrent()

        val vm = holder.vm.value
        assertEquals(true, vm.triggered)
        assertEquals(2.005, vm.lastZ!!, 0.0001)
        assertNotNull("accuracy present alongside printerState fields", vm.accuracy)
    }
}
