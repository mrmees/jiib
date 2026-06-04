package works.mees.dinghy.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import works.mees.dinghy.command.CommandDispatcher
import works.mees.dinghy.net.MoonrakerSession
import works.mees.dinghy.state.Capabilities
import works.mees.dinghy.state.ConnectionState
import works.mees.dinghy.ui.files.FileBrowserClient
import works.mees.dinghy.state.PrinterState

/**
 * Host-side proof of the [AppContainer] publication contract (the parts provable without an Android
 * runtime — DataStore reads are not exercised here, only the spine-publication seam and the
 * [SessionControl] narrowness invariant). Per the plan's `<behavior>`:
 *  - initial [AppContainer.spine] is null before any session is published;
 *  - publishing handle B replaces A in ONE assignment (review #6) — every field read off `spine.value`
 *    after publish is B's, never a mix of A and B;
 *  - [SessionControl] has NO member returning a [MoonrakerSession] (review #1).
 */
class AppContainerTest {

    /** Minimal in-memory DataStore — AppContainer only constructs ThemePrefs/ConnectionStore over it. */
    private class FakeDataStore : DataStore<Preferences> {
        override val data: Flow<Preferences> = flowOf(emptyPreferences())
        override suspend fun updateData(
            transform: suspend (t: Preferences) -> Preferences,
        ): Preferences = emptyPreferences()
    }

    // A discovery whose providers throw if touched — these tests never collect discover(), proving the
    // container holds it lazily (review #5: merely constructing/holding it pins nothing).
    private fun lazyDiscovery() = works.mees.dinghy.config.MoonrakerDiscovery(
        nsdProvider = { error("nsd must not be acquired in host tests (laziness, review #5)") },
        multicastLockProvider = { error("multicast lock must not be acquired in host tests") },
    )

    private fun newContainer() =
        AppContainer(FakeDataStore(), FakeDataStore(), FakeDataStore(), FakeDataStore(), lazyDiscovery())

    private fun handle(id: Long): SpineHandle {
        val store = works.mees.dinghy.state.PrinterStateStore(
            scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob()),
        )
        return SpineHandle(
            printerState = MutableStateFlow(PrinterState()),
            connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected),
            capabilities = MutableStateFlow(Capabilities()),
            dispatcher = CommandDispatcher(
                request = { _, _, _ -> kotlinx.serialization.json.JsonNull },
                scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob()),
            ),
            store = store,
            minExtrudeTemp = store.minExtrudeTemp,
            maxExtrudeDistance = store.maxExtrudeDistance,
            temperatureBackfill = store.temperatureBackfill,
            httpBase = "http://test:7125",
            metadata = MutableStateFlow(null),
            lastJob = MutableStateFlow(null),
            webcams = MutableStateFlow(emptyList()),
            activeSpool = MutableStateFlow(null),
            fileBrowser = object : FileBrowserClient {},
            sessionInstanceId = id,
        )
    }

    @Test
    fun spineIsNullBeforeAnyPublish() {
        val container = newContainer()
        assertNull("spine must start idle (null) before the service publishes", container.spine.value)
    }

    @Test
    fun publishingNullIdlesTheSpine() {
        val container = newContainer()
        container.publishSpine(handle(1))
        container.publishSpine(null)
        assertNull("publishing null returns the spine to idle (review #12)", container.spine.value)
    }

    @Test
    fun atomicSwap_everyFieldReadsAsBAfterPublishingB() {
        val container = newContainer()
        val a = handle(1)
        val b = handle(2)
        container.publishSpine(a)
        container.publishSpine(b)

        val published = container.spine.value!!
        // ONE assignment swapped the WHOLE handle — no field is still A's (review #6).
        assertEquals(2L, published.sessionInstanceId)
        assertSame(b.printerState, published.printerState)
        assertSame(b.connectionState, published.connectionState)
        assertSame(b.capabilities, published.capabilities)
        assertSame(b.dispatcher, published.dispatcher)
        assertSame(b.fileBrowser, published.fileBrowser)
    }

    @Test
    fun fileBrowserFlowIsNullWhenIdleAndSwapsWithSpine() {
        val container = newContainer()
        assertNull(container.currentFileBrowser)

        val handle = handle(3)
        container.publishSpine(handle)

        assertSame(handle.fileBrowser, container.currentFileBrowser)
    }

    @Test
    fun sessionControl_exposesNoRawMoonrakerSession() {
        // Review #1: NO SessionControl member may return (or expose) a MoonrakerSession.
        val leaks = SessionControl::class.java.declaredMethods.any { m ->
            m.returnType == MoonrakerSession::class.java
        }
        assertFalse("SessionControl must not expose a raw MoonrakerSession (review #1)", leaks)
    }
}
