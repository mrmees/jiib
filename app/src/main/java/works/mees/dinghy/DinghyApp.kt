package works.mees.dinghy

import android.app.Application
import android.content.Context
import android.net.nsd.NsdManager
import android.net.wifi.WifiManager
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import works.mees.dinghy.config.MoonrakerDiscovery
import works.mees.dinghy.di.AppContainer

/**
 * The process [Application] — the ONE owner of the [AppContainer] service-locator and the real
 * DataStore files (D-02, NO Hilt). Registered as `android:name=".DinghyApp"` (04-03 manifest).
 *
 * SEPARATE preference files (PATTERNS DECIDE): `connection.preferences_pb`, `theme.preferences_pb`,
 * `macros.preferences_pb`, `webcam.preferences_pb`, (Phase-14) `profiles.preferences_pb`, (Phase-16)
 * `babystep.preferences_pb`, (Phase-26) `tracestyle.preferences_pb`, and (Phase-26.5 §R2)
 * `display.preferences_pb`. Keeping the
 * API-key-bearing stores (connection + profiles) in their own files gives a cleaner redaction boundary
 * (T-04-01-I); the macro/webcam files are independent so their prefs settle on their own
 * connection-independent lifecycle (08-07 B1 / 10-06 D-10). Each file is created ONCE here via
 * [PreferenceDataStoreFactory.create] (one instance per process — the single-writer invariant DataStore
 * needs) on its own IO-backed scope.
 */
class DinghyApp : Application() {

    /** The process-scoped service-locator the Activity and the service both resolve via this app. */
    lateinit var container: AppContainer
        private set

    // App-lifetime scope for the DataStore actors + the one-shot theme seed (not Activity/Service-tied).
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()

        // SEPARATE files: theme.preferences_pb and connection.preferences_pb (cleaner key redaction).
        val themeDataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
            scope = appScope,
            produceFile = { applicationContext.preferencesDataStoreFile("theme.preferences_pb") },
        )
        val connectionDataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
            scope = appScope,
            produceFile = { applicationContext.preferencesDataStoreFile("connection.preferences_pb") },
        )
        // A THIRD, INDEPENDENT file: macros.preferences_pb (08-07 B1). It carries no secrets, but it is
        // kept on its own lifecycle (separate from connection/theme) per the established separate-file
        // discipline — it backs the process-scoped, connection-independent macro bookmarks/revealHidden
        // store (MacroPrefs). One instance per process (the single-writer invariant DataStore needs).
        val macroDataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
            scope = appScope,
            produceFile = { applicationContext.preferencesDataStoreFile("macros.preferences_pb") },
        )
        // A FOURTH, INDEPENDENT file: webcam.preferences_pb (10-06 D-10). It carries no secrets, and is
        // kept on its own lifecycle (separate from connection/theme/macros) per the established
        // separate-file discipline — it backs the process-scoped, connection-independent per-printer
        // preferred-cam store (WebcamPrefs, keyed `preferred_cam_<host>`). One instance per process (the
        // single-writer invariant DataStore needs).
        val webcamDataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
            scope = appScope,
            produceFile = { applicationContext.preferencesDataStoreFile("webcam.preferences_pb") },
        )
        // A FIFTH, INDEPENDENT file: profiles.preferences_pb (MULTI-01, Phase 14). It DOES carry the
        // per-profile API key, so it inherits the same redaction discipline as connection.preferences_pb
        // (Profile/PersistedProfile.toString() mask the key). Kept on its own file/lifecycle per the
        // separate-file discipline — it backs the managed profile SET + active-profile id (ProfileStore),
        // the Phase-14 generalization of the single connection store. Created ONCE here (the single-writer
        // invariant DataStore needs — RESEARCH Pitfall 3) and never elsewhere.
        val profileDataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
            scope = appScope,
            produceFile = { applicationContext.preferencesDataStoreFile("profiles.preferences_pb") },
        )
        // A SIXTH, INDEPENDENT file: babystep.preferences_pb (D-06, Phase 16). It carries no secrets (like
        // macros/webcam), so it is kept on its own connection-independent lifecycle per the separate-file
        // discipline — it backs the process-scoped babystep app setting (BabystepPrefs: enable toggle +
        // first-layer-window layer-count). One instance per process (the single-writer invariant DataStore
        // needs — RESEARCH Pitfall 3) and never elsewhere.
        val babystepDataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
            scope = appScope,
            produceFile = { applicationContext.preferencesDataStoreFile("babystep.preferences_pb") },
        )
        // A SEVENTH, INDEPENDENT file: tracestyle.preferences_pb (D-14, Phase 26). It carries no secrets
        // (like macros/webcam/babystep), so it is kept on its own connection-independent lifecycle per the
        // separate-file discipline — it backs the process-scoped per-sensor trace color + visibility settings
        // (TraceStylePrefs: flat key-map of ARGB-Int colors + Boolean visibility, keyed by sensor name).
        // One instance per process (the single-writer invariant DataStore needs — RESEARCH Pitfall 3).
        val traceStyleDataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
            scope = appScope,
            produceFile = { applicationContext.preferencesDataStoreFile("tracestyle.preferences_pb") },
        )
        // An EIGHTH, INDEPENDENT file: display.preferences_pb (§R2, Phase 26.5-05). It carries no secrets
        // (like macros/webcam/babystep/tracestyle), so it is kept on its own connection-independent
        // lifecycle per the separate-file discipline — it backs the process-scoped display settings
        // (DisplayPrefs: the keep-screen-on toggle, default ON for the dedicated-display use case, plus
        // the app-global webcam-enabled toggle added 2026-06-15). One
        // instance per process (the single-writer invariant DataStore needs — RESEARCH Pitfall 3).
        val displayDataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
            scope = appScope,
            produceFile = { applicationContext.preferencesDataStoreFile("display.preferences_pb") },
        )
        // A NINTH, INDEPENDENT file: savedlocations.preferences_pb (Move hub, feat/move-hub-redesign).
        // It carries no secrets (like macros/webcam/babystep/tracestyle/display), so it is kept on its
        // own connection-independent lifecycle per the separate-file discipline — it backs the
        // process-scoped named toolhead-position store (SavedLocationPrefs: ordered list of SavedLocation,
        // identity = name). One instance per process (the single-writer invariant DataStore needs —
        // RESEARCH Pitfall 3).
        val savedLocationDataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
            scope = appScope,
            produceFile = { applicationContext.preferencesDataStoreFile("savedlocations.preferences_pb") },
        )
        // A TENTH, INDEPENDENT file: fontscale.preferences_pb (App/Printer Settings Split, Task 1.2).
        // It carries no secrets (like macros/webcam/babystep/tracestyle/display/savedlocations), so it
        // is kept on its own connection-independent lifecycle per the separate-file discipline — it backs
        // the process-scoped app-global font-scale setting (FontScalePrefs: S/M/L FontScale choice).
        // Replaces the retired per-printer Profile.fsChoice as the SOLE source of --fs. One instance
        // per process (the single-writer invariant DataStore needs — RESEARCH Pitfall 3).
        val fontScaleDataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
            scope = appScope,
            produceFile = { applicationContext.preferencesDataStoreFile("fontscale.preferences_pb") },
        )

        container = AppContainer(
            themeDataStore = themeDataStore,
            connectionDataStore = connectionDataStore,
            macroDataStore = macroDataStore,
            webcamDataStore = webcamDataStore,
            profileDataStore = profileDataStore,
            babystepDataStore = babystepDataStore,
            traceStyleDataStore = traceStyleDataStore,
            displayDataStore = displayDataStore,
            savedLocationDataStore = savedLocationDataStore,
            fontScaleDataStore = fontScaleDataStore,
            // FULLY-LAZY mDNS scanner (04-01, review #5): the provider lambdas acquire the NsdManager
            // and a fresh multicast lock ONLY when discover() is collected — holding the instance pins
            // no radio. The lock is needed to receive mDNS multicast on Wi-Fi on many devices.
            discovery = MoonrakerDiscovery(
                nsdProvider = {
                    applicationContext.getSystemService(Context.NSD_SERVICE) as NsdManager
                },
                multicastLockProvider = {
                    (applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager)
                        ?.createMulticastLock("dinghy-mdns")
                },
            ),
        )
        // Seed the resolver from persisted theme prefs (one re-emit on first load, live thereafter).
        container.seedTheme(appScope)

        // R5a (§R5a step 5): StrictMode detect-all in DEBUG builds only — the main thread is
        // currently clean (efficiency audit); this keeps it that way for free. Log-only penalty
        // (never crash): violations surface in logcat. Release builds unaffected.
        if (BuildConfig.DEBUG) {
            android.os.StrictMode.setThreadPolicy(
                android.os.StrictMode.ThreadPolicy.Builder()
                    .detectAll()
                    .penaltyLog()
                    .build()
            )
            android.os.StrictMode.setVmPolicy(
                android.os.StrictMode.VmPolicy.Builder()
                    .detectAll()
                    .penaltyLog()
                    .build()
            )
        }
    }
}

/** Resolve a file under the app's `datastore/` dir for [PreferenceDataStoreFactory.create]. */
private fun android.content.Context.preferencesDataStoreFile(name: String): java.io.File =
    java.io.File(applicationContext.filesDir, "datastore/$name")
