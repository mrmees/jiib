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
 * THREE SEPARATE preference files (PATTERNS DECIDE): `connection.preferences_pb`, `theme.preferences_pb`,
 * and `macros.preferences_pb`. Keeping the API-key-bearing connection store in its own file gives a
 * cleaner redaction boundary (T-04-01-I); the macro-prefs file is independent so bookmarks/revealHidden
 * settle on their own connection-independent lifecycle (08-07 B1). Each file is created ONCE here via
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

        container = AppContainer(
            themeDataStore = themeDataStore,
            connectionDataStore = connectionDataStore,
            macroDataStore = macroDataStore,
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
    }
}

/** Resolve a file under the app's `datastore/` dir for [PreferenceDataStoreFactory.create]. */
private fun android.content.Context.preferencesDataStoreFile(name: String): java.io.File =
    java.io.File(applicationContext.filesDir, "datastore/$name")
