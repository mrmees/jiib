package works.mees.dinghy.config

import kotlinx.serialization.Serializable
import works.mees.dinghy.theme.ThemePrefs
import java.util.UUID

/**
 * A single named Moonraker printer profile (D-05) — the Phase-14 generalization of the lone persisted
 * [ConnectionConfig] into a managed SET of printers. Each profile carries a stable UUID identity so
 * its name/host/port/apiKey can all change without breaking the active-profile pointer or per-printer
 * prefs (D-05/D-06), an optional display [name] defaulting to its host (D-10), and a FULL per-printer
 * theme (D-08) persisted as PRIMITIVES (never a baked [works.mees.dinghy.theme.ThemeTokens]).
 *
 * Two shapes, the runtime value vs. its persisted wire form:
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
    // FULL theme (D-03/D-08) as PERSISTED PRIMITIVES — the generate-and-cache TUPLE (15-04/15-05), NEVER a
    // resolved ThemeTokens (RESEARCH Pitfall 2 / Anti-Pattern). poolOverrides is String-keyed (poolIndex AS
    // STRING → unsigned-32 ARGB) ON PURPOSE: kotlinx `ignoreUnknownKeys` tolerates unknown KEYS, not malformed
    // VALUES — an Int-typed map would fail the WHOLE decode on a single bad entry; a String→Long map lets the
    // PER-ENTRY sanitize (ThemePrefs.sanitizeTuple) drop one bad slot while the good ones survive (V5).
    val seedHex: String = "#3f78ff",
    val dark: Boolean = true,
    val paletteMode: String = "Colorful",
    val poolShift: Int = 0,
    val maxItems: Int = 4,
    val poolOverrides: Map<String, Long> = emptyMap(),
    val fsChoice: String = "M", // FontScale.name — a SEPARATE setting (D-05), NOT folded into the theme tuple.
    // NOTE (D-05 fresh-start, no migration): old blobs carrying the retired `themeBase`/`themeDeltaArgb`
    // keys still decode cleanly — kotlinx `ignoreUnknownKeys` skips them. The runtime tuple above is the
    // sole source of truth; those old keys are simply ignored (the fields were deleted in 15-06).
) {
    /**
     * Redacts the API key (V7, T-14-01) — never let the key reach a log line, mirrors ConnectionConfig.kt:26.
     * The theme tuple carries NO secrets, so it is NOT redaction surface (V7 — do not widen the masking).
     */
    override fun toString(): String =
        "PersistedProfile(id=$id, name=$name, host=$host, port=$port, " +
            "apiKey=${if (apiKey != null) "***" else "null"}, seedHex=$seedHex, dark=$dark, " +
            "paletteMode=$paletteMode, poolShift=$poolShift, maxItems=$maxItems, " +
            "poolOverrides=${poolOverrides.keys}, fsChoice=$fsChoice)"
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
    // The generate-and-cache theme TUPLE (D-03, 15-05) — String-keyed poolOverrides for per-entry tolerance.
    val seedHex: String = "#3f78ff",
    val dark: Boolean = true,
    val paletteMode: String = "Colorful",
    val poolShift: Int = 0,
    val maxItems: Int = 4,
    val poolOverrides: Map<String, Long> = emptyMap(),
    val fsChoice: String = "M",
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
     * Resolve this profile's FULL theme (D-03) from its persisted TUPLE primitives, reusing
     * [ThemePrefs.sanitizeTuple] so corrupt theme data fails safe to the defaults (junk seed → default seed,
     * bad mode → Colorful, out-of-range shift/maxItems → defaults, one malformed pool override dropped
     * per-entry). NEVER throws, NEVER bakes a [works.mees.dinghy.theme.ThemeTokens].
     */
    fun toThemeTuple(): ThemePrefs.ThemeTuple =
        ThemePrefs.sanitizeTuple(
            rawSeed = seedHex,
            rawDark = dark,
            rawMode = paletteMode,
            rawShift = poolShift,
            rawMaxItems = maxItems,
            rawFs = fsChoice,
            rawOverrides = poolOverrides,
        )

    /** The wire form — for re-encoding when [ProfileStore] writes the blob. */
    fun toPersisted(): PersistedProfile =
        PersistedProfile(
            id = id,
            name = name,
            host = host,
            port = port,
            apiKey = apiKey,
            seedHex = seedHex,
            dark = dark,
            paletteMode = paletteMode,
            poolShift = poolShift,
            maxItems = maxItems,
            poolOverrides = poolOverrides,
            fsChoice = fsChoice,
        )

    override fun toString(): String =
        "Profile(id=$id, name=$name, host=$host, port=$port, " +
            "apiKey=${if (apiKey != null) "***" else "null"}, seedHex=$seedHex, dark=$dark, " +
            "paletteMode=$paletteMode, poolShift=$poolShift, maxItems=$maxItems, " +
            "poolOverrides=${poolOverrides.keys}, fsChoice=$fsChoice)"

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
                seedHex = p.seedHex,
                dark = p.dark,
                paletteMode = p.paletteMode,
                poolShift = p.poolShift,
                maxItems = p.maxItems,
                poolOverrides = p.poolOverrides,
                fsChoice = p.fsChoice,
            )
    }
}
