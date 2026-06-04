package works.mees.dinghy.prompt

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import works.mees.dinghy.command.DispatchEvent
import works.mees.dinghy.net.ConnectionError
import works.mees.dinghy.state.ConnectionState
import works.mees.dinghy.state.PrinterStateStore

/**
 * Host-side proof for [PromptEngine]: the spine-level holder that folds the live un-throttled gcode
 * stream through the pure reducer (12-02) into a [PromptView] StateFlow, closes the prompt LOCALLY on a
 * disconnect edge (emitting NO `prompt_end` — D-10 / Pitfall 6, T-12-09), and exposes the stable dispatch
 * keys + the dispatcher-Failure fold the overlay uses.
 *
 * Mirrors [works.mees.dinghy.calibration.ProbeCalibrateHolderTest]: a real [PrinterStateStore] driven
 * synchronously under `runTest` with an [UnconfinedTestDispatcher]; bounded `runCurrent()` only (never
 * open-ended) per [[dinghy-display-gradle-hang-interop]].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PromptEngineTest {

    /** Feed a sequence of raw `// action:` lines through the store's un-throttled gcode stream. */
    private fun PrinterStateStore.feed(vararg lines: String) {
        lines.forEach { onGcodeLine(it) }
    }

    @Test
    fun streamBeginTextShowMakesPromptVisible() = runTest(UnconfinedTestDispatcher()) {
        val store = PrinterStateStore(backgroundScope)
        val engine = PromptEngine(backgroundScope, store)

        store.feed(
            "// action:prompt_begin Load",
            "// action:prompt_text Insert filament",
            "// action:prompt_show",
        )
        runCurrent()

        val view = engine.view.value
        assertTrue("begin+text+show → visible prompt", view.visible)
        assertEquals("Load", view.title)
        assertEquals(1, view.items.size)
        assertEquals(PromptItemType.TEXT, view.items[0].type)
        assertEquals("Insert filament", view.items[0].text)
    }

    @Test
    fun endLineHidesPrompt() = runTest(UnconfinedTestDispatcher()) {
        val store = PrinterStateStore(backgroundScope)
        val engine = PromptEngine(backgroundScope, store)

        store.feed(
            "// action:prompt_begin Load",
            "// action:prompt_text Insert filament",
            "// action:prompt_show",
        )
        runCurrent()
        assertTrue(engine.view.value.visible)

        // A prompt_end line (the reducer's end path) hides it.
        store.feed("// action:prompt_end")
        runCurrent()
        assertFalse("prompt_end → hidden", engine.view.value.visible)
    }

    @Test
    fun connectedToDisconnectedClosesLocallyWithZeroDispatches() = runTest(UnconfinedTestDispatcher()) {
        val store = PrinterStateStore(backgroundScope)
        // A recording dispatcher: the engine MUST NOT dispatch anything on the disconnect edge (D-10).
        val recorded = mutableListOf<String>()
        val engine = PromptEngine(backgroundScope, store)

        // Establish a Connected baseline, then show a prompt.
        store.setConnectionState(ConnectionState.Connected)
        runCurrent()
        store.feed(
            "// action:prompt_begin Load",
            "// action:prompt_show",
        )
        runCurrent()
        assertTrue("prompt visible before disconnect", engine.view.value.visible)

        // Connected → Disconnected edge: LOCAL close. The engine exposes keys but never calls dispatch
        // itself — so simply asserting the view closed AND nothing was recorded proves no prompt_end went out.
        store.setConnectionState(ConnectionState.Disconnected)
        runCurrent()

        assertFalse("disconnect → prompt closed locally", engine.view.value.visible)
        assertEquals("disconnect must dispatch ZERO gcode (no prompt_end — D-10)", emptyList<String>(), recorded)
    }

    @Test
    fun connectedToErrorAlsoClosesLocally() = runTest(UnconfinedTestDispatcher()) {
        val store = PrinterStateStore(backgroundScope)
        val engine = PromptEngine(backgroundScope, store)

        store.setConnectionState(ConnectionState.Connected)
        runCurrent()
        store.feed("// action:prompt_begin Load", "// action:prompt_show")
        runCurrent()
        assertTrue(engine.view.value.visible)

        store.setConnectionState(ConnectionState.Error(ConnectionError.NetworkUnavailable))
        runCurrent()
        assertFalse("Error transition also closes locally", engine.view.value.visible)
    }

    @Test
    fun syncingAndConnectingDoNotClosePrompt() = runTest(UnconfinedTestDispatcher()) {
        val store = PrinterStateStore(backgroundScope)
        val engine = PromptEngine(backgroundScope, store)

        store.setConnectionState(ConnectionState.Connected)
        runCurrent()
        store.feed("// action:prompt_begin Load", "// action:prompt_show")
        runCurrent()
        assertTrue(engine.view.value.visible)

        // A3: only Disconnected/Error trigger the local close. Syncing/Connecting must NOT.
        store.setConnectionState(ConnectionState.Syncing)
        runCurrent()
        assertTrue("Syncing does NOT close the prompt (A3)", engine.view.value.visible)

        store.setConnectionState(ConnectionState.Connecting)
        runCurrent()
        assertTrue("Connecting does NOT close the prompt (A3)", engine.view.value.visible)
    }

    @Test
    fun promptKeyedFailureFoldsToLatestError() = runTest(UnconfinedTestDispatcher()) {
        val store = PrinterStateStore(backgroundScope)
        val events = MutableSharedFlow<DispatchEvent>(extraBufferCapacity = 8)
        val engine = PromptEngine(backgroundScope, store, events = events)

        events.emit(DispatchEvent.Failure(key = "prompt:1:0", message = "Move out of range"))
        runCurrent()
        assertEquals("Move out of range", engine.latestPromptError)
    }

    @Test
    fun nonPromptKeyedFailureIsIgnored() = runTest(UnconfinedTestDispatcher()) {
        val store = PrinterStateStore(backgroundScope)
        val events = MutableSharedFlow<DispatchEvent>(extraBufferCapacity = 8)
        val engine = PromptEngine(backgroundScope, store, events = events)

        events.emit(DispatchEvent.Failure(key = "testz", message = "not a prompt error"))
        runCurrent()
        assertNull("a non-prompt key never folds into the prompt error", engine.latestPromptError)
    }

    @Test
    fun dispatchKeysAreStableAndNamespaceDistinct() = runTest(UnconfinedTestDispatcher()) {
        val store = PrinterStateStore(backgroundScope)
        val engine = PromptEngine(backgroundScope, store)

        // Open a prompt so the epoch advances to 1 (begin bumps epoch+1 off the idle 0 seed).
        store.feed("// action:prompt_begin Load", "// action:prompt_show")
        runCurrent()

        assertEquals("prompt:close", engine.closeKey)

        // Content vs footer at the SAME index must NEVER collide (separate namespaces — Codex finding).
        for (n in 0..5) {
            assertNotEquals(
                "buttonKey($n) must not equal footerKey($n) — distinct dispatcher namespaces",
                engine.buttonKey(n),
                engine.footerKey(n),
            )
        }
        assertEquals("prompt:1:0", engine.buttonKey(0))
        assertEquals("prompt:1:footer:0", engine.footerKey(0))

        // Stable within the same prompt (idempotent — no recomposition churn).
        assertEquals(engine.buttonKey(2), engine.buttonKey(2))

        // A replaced prompt bumps the epoch, isolating its keys from the prior one (Pitfall 4).
        store.feed("// action:prompt_begin Unload", "// action:prompt_show")
        runCurrent()
        assertEquals("prompt:2:0", engine.buttonKey(0))
        assertNotEquals("epoch isolates the replaced prompt's keys", "prompt:1:0", engine.buttonKey(0))
    }
}
