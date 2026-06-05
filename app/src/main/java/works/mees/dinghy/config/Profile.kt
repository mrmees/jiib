package works.mees.dinghy.config

import kotlinx.serialization.Serializable
import works.mees.dinghy.theme.ThemePrefs
import works.mees.dinghy.theme.TokenDelta
import java.util.UUID

/**
 * A single named Moonraker printer profile (D-05) — the Phase-14 generalization of the lone persisted
 * [ConnectionConfig] into a managed SET of printers. Each profile carries a stable UUID identity so
 * its name/host/port/apiKey can all change without breaking the active-profile pointer or per-printer
 * prefs (D-05/D-06), an optional display [name] defaulting to its host (D-10), and a FULL per-printer
 * theme (D-08) persisted as PRIMITIVES (never a baked [works.mees.dinghy.theme.ThemeTokens]).
 *
 * Two shapes, mirroring [ThemePrefs.Resolved]-vs-persisted-keys:
 *  - [PersistedProfile] — the `@Serializable` on-disk wire form (the JSON blob `ProfileStore` stores).
 *  - [Profile] — the runtime value the rest of the app consumes; produced by sanitizing the persisted
 *    form, so a malformed entry never reaches the spine.
 *
 * SECURITY (V7, T-14-01 — mirroring [ConnectionConfig.toString], ConnectionConfig.kt:26): the API key
 * now lives in the profile blob, so [PersistedProfile.toString] redacts [apiKey] to `***`. The blob is
 * NOT encrypted-at-rest (same posture as `ConnectionStore`) so the key must never reach a log line, the
 * FGS notification, or a crash dump.
 */
@Serializable
data class PersistedProfile(
    val id: String,
    val name: String? = null,
    val host: String,
    val port: Int = 7125,
    val apiKey: String? = null,
    // FULL theme (D-08) as PERSISTED PRIMITIVES — the exact shape ThemePrefs persists (ThemePrefs.kt:84-91),
    // NEVER a resolved ThemeTokens (RESEARCH Pitfall 2 / Anti-Pattern):
    val themeBase: String = "Dark", // ThemeBase.name
    val fsChoice: String = "M", // FontScale.name
    val themeDeltaArgb: Map<String, Long> = emptyMap(), // TokenDelta.Role.name -> unsigned-32 ARGB
) {
    /** Redacts the API key (V7, T-14-01) — never let the key reach a log line, mirrors ConnectionConfig.kt:26. */
    override fun toString(): String =
        "PersistedProfile(id=$id, name=$name, host=$host, port=$port, " +
            "apiKey=${if (apiKey != null) "***" else "null"}, themeBase=$themeBase, fsChoice=$fsChoice, " +
            "themeDeltaArgb=${themeDeltaArgb.keys})"
}

/**
 * The runtime profile value the app consumes. Carries the SAME primitives as [PersistedProfile] (so it
 * round-trips cleanly) plus the derived projections the rest of the phase keys on.
 *
 * SECURITY (V7): redacting [toString] — same masking as [PersistedProfile]/[ConnectionConfig].
 */
data class Profile(
    val id: String,
    val name: String? = null,
    val host: String,
    val port: Int = 7125,
    val apiKey: String? = null,
    val themeBase: String = "Dark",
    val fsChoice: String = "M",
    val themeDeltaArgb: Map<String, Long> = emptyMap(),
) {
    /**
     * The connection projection — host/port/apiKey ONLY. This is the value `distinctUntilChanged` keys
     * on in plan 02 (AppContainer.activeConfig): two profiles differing only in name/theme MUST produce
     * an EQUAL [ConnectionConfig] so a name/theme edit does NOT churn the spine (RESEARCH Pitfall 1).
     */
    fun toConnectionConfig(): ConnectionConfig = ConnectionConfig(host = host, port = port, apiKey = apiKey)

    /** The display name (D-10): the optional [name], falling back to the host. */
    fun displayName(): String = name ?: host

    /**
     * Resolve this profile's FULL theme (D-08) from its persisted primitives, reusing [ThemePrefs.sanitize]
     * parity so corrupt theme data fails safe to the defaults (unknown base → Dark, unknown fs → M, junk
     * delta entries dropped). NEVER throws, NEVER bakes a [works.mees.dinghy.theme.ThemeTokens].
     */
    fun toThemeResolved(): ThemePrefs.Resolved =
        ThemePrefs.sanitize(
            rawBase = themeBase,
            rawFs = fsChoice,
            rawRoleKeys = themeDeltaArgb.keys,
            readArgb = { role -> themeDeltaArgb[role] },
        )

    /** The wire form — for re-encoding when [ProfileStore] writes the blob. */
    fun toPersisted(): PersistedProfile =
        PersistedProfile(
            id = id,
            name = name,
            host = host,
            port = port,
            apiKey = apiKey,
            themeBase = themeBase,
            fsChoice = fsChoice,
            themeDeltaArgb = themeDeltaArgb,
        )

    override fun toString(): String =
        "Profile(id=$id, name=$name, host=$host, port=$port, " +
            "apiKey=${if (apiKey != null) "***" else "null"}, themeBase=$themeBase, fsChoice=$fsChoice, " +
            "themeDeltaArgb=${themeDeltaArgb.keys})"

    companion object {
        /** A stable, collision-safe profile identity (D-05). UUID is available since API 1. */
        fun newId(): String = UUID.randomUUID().toString()

        /** Lift a sanitized [PersistedProfile] into its runtime [Profile]. */
        fun fromPersisted(p: PersistedProfile): Profile =
            Profile(
                id = p.id,
                name = p.name,
                host = p.host,
                port = p.port,
                apiKey = p.apiKey,
                themeBase = p.themeBase,
                fsChoice = p.fsChoice,
                themeDeltaArgb = p.themeDeltaArgb,
            )
    }
}
