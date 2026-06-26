package works.mees.jiib.service

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import works.mees.jiib.JiibApp
import works.mees.jiib.MainActivity
import works.mees.jiib.config.ConnectionConfig

/**
 * Instrumented proof of SHELL-03 / D-02 via a CONCRETE CONTINUITY SIGNAL (review #3): the started FGS
 * OWNS the Moonraker spine, so Activity recreation (rotation) does NOT rebuild it — the published
 * [SpineHandle.sessionInstanceId][works.mees.jiib.di.SpineHandle.sessionInstanceId] is UNCHANGED across
 * rotation. A changed id would mean the Activity rebuilt the spine = FAIL. The continuity assertion is
 * robust even if a harness re-collects flows, because a republish would mint a NEW id (review #3).
 *
 * The load-bearing assertion is id continuity, NOT reaching Connected — so the seeded config need only
 * resolve to a valid [ConnectionConfig] (the service publishes the handle the moment it BUILDS the spine,
 * before `run()` reaches Connected). A reachable printer is not required for this gate.
 */
@RunWith(AndroidJUnit4::class)
class ServiceSurvivesRotationTest {

    private val app: JiibApp = ApplicationProvider.getApplicationContext()
    private val container get() = app.container
    private val device: UiDevice =
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    @Test
    fun spineSurvivesRotation_sessionInstanceIdUnchanged() {
        // Seed a connection so the service builds (and publishes) a spine. Host need not be reachable —
        // we assert continuity of the published id, not Connected.
        runBlocking {
            container.connectionStore.save(ConnectionConfig(host = "10.255.255.1", port = 7125))
        }

        // Start the FGS (process-held spine) and bring up MainActivity.
        app.startForegroundService(Intent(app, MoonrakerService::class.java))

        ActivityScenario.launch(MainActivity::class.java).use {
            device.setOrientationNatural()

            // Wait until a handle is published (spine built from the seeded config).
            val published = waitForSpine()
            assertNotNull("service must publish a spine from the seeded config", published)
            val idBefore = published!!.sessionInstanceId

            // Rotate several times; after each, the SAME spine instance must survive (no rebuild).
            repeat(3) {
                device.setOrientationLeft()
                Thread.sleep(SETTLE_MS)
                assertEquals(
                    "spine rebuilt on rotation-to-left — Activity owns the spine (FAIL)",
                    idBefore,
                    currentId(),
                )
                device.setOrientationNatural()
                Thread.sleep(SETTLE_MS)
                assertEquals(
                    "spine rebuilt on rotation-to-natural — Activity owns the spine (FAIL)",
                    idBefore,
                    currentId(),
                )
            }
        }
    }

    private fun currentId(): Long? = container.spine.value?.sessionInstanceId

    private fun waitForSpine(): works.mees.jiib.di.SpineHandle? {
        val deadline = System.currentTimeMillis() + SPINE_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            container.spine.value?.let { return it }
            Thread.sleep(POLL_MS)
        }
        return container.spine.value
    }

    @After
    fun tearDown() {
        device.setOrientationNatural()
        // Stop the service and clear the seeded config so the next test starts clean.
        app.stopService(Intent(app, MoonrakerService::class.java))
        runBlocking { container.connectionStore.clear() }
    }

    private companion object {
        const val SPINE_TIMEOUT_MS = 10_000L
        const val POLL_MS = 50L
        const val SETTLE_MS = 1_500L
    }
}
