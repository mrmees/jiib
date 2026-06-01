package works.mees.dinghy

import android.app.Application
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import works.mees.dinghy.di.AppContainer

/**
 * The process [Application] — the ONE owner of the [AppContainer] service-locator and the real
 * DataStore files (D-02, NO Hilt). Registered as `android:name=".DinghyApp"` (04-03 manifest).
 *
 * Two SEPARATE preference files (PATTERNS DECIDE): `connection.preferences_pb` and `theme.preferences_pb`.
 * Keeping the API-key-bearing connection store in its own file gives a cleaner redaction boundary
 * (T-04-01-I) and lets the two settle on independent lifecycles. Each file is created ONCE here via
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

        container = AppContainer(
            themeDataStore = themeDataStore,
            connectionDataStore = connectionDataStore,
        )
        // Seed the resolver from persisted theme prefs (one re-emit on first load, live thereafter).
        container.seedTheme(appScope)
    }
}

/** Resolve a file under the app's `datastore/` dir for [PreferenceDataStoreFactory.create]. */
private fun android.content.Context.preferencesDataStoreFile(name: String): java.io.File =
    java.io.File(applicationContext.filesDir, "datastore/$name")
