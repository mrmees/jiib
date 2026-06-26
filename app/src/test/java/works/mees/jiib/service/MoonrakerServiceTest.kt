package works.mees.jiib.service

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import works.mees.jiib.config.ConnectionConfig

/**
 * JVM virtual-time proof of [MoonrakerService]'s config-rebuild SEAM ([MoonrakerService.runConfigLoop])
 * — the `collectLatest` + `cancelAndJoin()`-before-relaunch ordering and the monotonic atomic-publish
 * invariant — WITHOUT the Android Service lifecycle or a real socket. The loop body is extracted so it
 * can be driven with a controllable config flow + fake build/publish hooks (Nyquist seam). The
 * production `onStartCommand`/notification half is exercised by the instrumented test (Task 4) and the
 * on-device checkpoint (Task 5).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MoonrakerServiceTest {

    private val cfgA = ConnectionConfig(host = "10.0.0.1", port = 7125)
    private val cfgB = ConnectionConfig(host = "10.0.0.2", port = 7125)

    @Test
    fun nullConfig_publishesIdle_andLaunchesNoSession() = runTest(UnconfinedTestDispatcher()) {
        val configFlow = MutableStateFlow<ConnectionConfig?>(null)
        var builds = 0
        var idlePublishes = 0
        var lastPublishedId: Long? = -1L

        val loop = launch {
            MoonrakerService.runConfigLoop(
                configFlow = configFlow,
                buildAndPublish = { _ ->
                    builds++
                    // A real session job would be launched here; for the null path it must NEVER run.
                    val job = launch { while (isActive) delay(1_000) }
                    lastPublishedId = builds.toLong()
                    job to builds.toLong()
                },
                publishIdle = {
                    idlePublishes++
                    lastPublishedId = null
                },
            )
        }
        runCurrent()

        // Review #12: a null config builds NO session and publishes idle (handle null).
        assertEquals("no session built on null config", 0, builds)
        assertEquals("idle published once", 1, idlePublishes)
        assertNull("published handle id is null (idle)", lastPublishedId)

        loop.cancel()
    }

    @Test
    fun configChange_cancelsPriorJobBeforeRelaunch_andPublishesStrictlyGreaterId() =
        runTest(UnconfinedTestDispatcher()) {
            val configFlow = MutableStateFlow<ConnectionConfig?>(cfgA)
            val jobs = mutableListOf<Job>()
            val publishedIds = mutableListOf<Long>()
            var idCounter = 0L
            // Records the ORDER of lifecycle events to assert cancel-before-build.
            val events = mutableListOf<String>()

            val loop = launch {
                MoonrakerService.runConfigLoop(
                    configFlow = configFlow,
                    buildAndPublish = { _ ->
                        val id = ++idCounter
                        events += "build#$id"
                        val job = launch {
                            try {
                                while (isActive) delay(1_000)
                            } finally {
                                events += "cancelled#$id"
                            }
                        }
                        jobs += job
                        publishedIds += id
                        job to id
                    },
                    publishIdle = { events += "idle" },
                )
            }
            runCurrent()

            // Config A built exactly one session.
            assertEquals(1, jobs.size)
            assertTrue(jobs[0].isActive)
            assertEquals(listOf(1L), publishedIds)

            // Now change the config — the loop must cancelAndJoin the prior job BEFORE building B.
            configFlow.value = cfgB
            runCurrent()

            assertEquals("prior job torn down before relaunch (D-03)", 2, jobs.size)
            assertFalse("session A was cancelled", jobs[0].isActive)
            assertTrue("session B is the live one", jobs[1].isActive)

            // Monotonic atomic publish: B's id is strictly greater than A's.
            assertEquals(listOf(1L, 2L), publishedIds)
            assertTrue("published id strictly increased", publishedIds[1] > publishedIds[0])

            // Ordering proof: A cancelled BEFORE B built (cancelAndJoin precedes buildAndPublish).
            assertEquals(
                listOf("build#1", "cancelled#1", "build#2"),
                events,
            )

            loop.cancel()
        }
}
