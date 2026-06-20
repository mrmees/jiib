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
    // D-17 (28-04): pool size hardcoded at ThemeResolver.DEFAULT_POOL_MAX_ITEMS=4.
    // Old stored blobs with the deleted field still decode cleanly via kotlinx ignoreUnknownKeys.
    val poolOverrides: Map<String, Long> = emptyMap(),
    val fsChoice: String = "M", // FontScale.name — a SEPARATE setting (D-05), NOT folded into the theme tuple.
    // R7 (26.5-07): per-printer wss/https toggle. Defaults FALSE so every pre-R7 blob (no key present)
    // decodes to today's plain ws/http posture — ignoreUnknownKeys tolerates old blobs carrying any
    // retired key (e.g. the deleted webcamEnabled, removed 2026-06-15 when webcam became app-global).
    val useSecure: Boolean = false,
    // 2026-06-15: set TRUE once a name has been locked (user-typed OR auto-seeded from hostname). Old blobs
    // without this key decode to false via kotlinx ignoreUnknownKeys — safe default: re-evaluates on connect.
    val nameAutoSeeded: Boolean = false,
    // 2026-06-18: optional per-printer accent override ARGB (unsigned-32), null = seed-derived.
    // Old blobs without this key decode to null via kotlinx ignoreUnknownKeys — safe default.
    val accentOverrideArgb: Long? = null,
    // 2026-06-19: optional full URL override for proxied/reverse-proxied Moonraker installs.
    // When set, the connection editor uses this URL instead of building from host/port/useSecure.
    // Old blobs without this key decode to null via kotlinx ignoreUnknownKeys — safe default.
    val advancedUrl: String? = null,
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
            "paletteMode=$paletteMode, poolShift=$poolShift, " +
            "poolOverrides=${poolOverrides.keys}, fsChoice=$fsChoice, useSecure=$useSecure, " +
            "nameAutoSeeded=$nameAutoSeeded)"
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
    // Pool size hardcoded at ThemeResolver.DEFAULT_POOL_MAX_ITEMS (D-17 / 28-04); not a Profile field.
    val seedHex: String = "#3f78ff",
    val dark: Boolean = true,
    val paletteMode: String = "Colorful",
    val poolShift: Int = 0,
    val poolOverrides: Map<String, Long> = emptyMap(),
    // R7 (26.5-07): per-printer wss/https toggle — see [PersistedProfile.useSecure]. Default false.
    val useSecure: Boolean = false,
    // 2026-06-15: mirrors [PersistedProfile.nameAutoSeeded] — true once the name is locked (user-typed
    // or auto-seeded from hostname); prevents re-seeding on reconnect. Default false.
    val nameAutoSeeded: Boolean = false,
    /** Optional per-printer accent override ARGB (unsigned-32), null = seed-derived. */
    val accentOverrideArgb: Long? = null,
    /** Optional full URL override for proxied/reverse-proxied Moonraker installs. null = build from host/port/useSecure. */
    val advancedUrl: String? = null,
) {
    /**
     * The connection projection — host/port/apiKey/useSecure ONLY. This is the value
     * `distinctUntilChanged` keys on in plan 02 (AppContainer.activeConfig): two profiles differing
     * only in name/theme MUST produce an EQUAL [ConnectionConfig] so a name/theme edit does NOT churn
     * the spine (RESEARCH Pitfall 1). A [useSecure] flip DOES change the projection ON PURPOSE — the
     * spine must rebind to pick up the new ws↔wss scheme (R7, 26.5-07).
     */
    fun toConnectionConfig(): ConnectionConfig =
        ConnectionConfig(host = host, port = port, apiKey = apiKey, useSecure = useSecure, advancedUrl = advancedUrl)

    /** The display name (D-10): the optional [name], falling back to the host. */
    fun displayName(): String = name ?: host

    /**
     * Resolve this profile's FULL theme (D-03) from its persisted TUPLE primitives, reusing
     * [ThemePrefs.sanitizeTuple] so corrupt theme data fails safe to the defaults (junk seed → default seed,
     * bad mode → Colorful, out-of-range shift → defaults, one malformed pool override dropped
     * per-entry). NEVER throws, NEVER bakes a [works.mees.dinghy.theme.ThemeTokens].
     */
    fun toThemeTuple(): ThemePrefs.ThemeTuple =
        ThemePrefs.sanitizeTuple(
            rawSeed = seedHex,
            rawDark = dark,
            rawMode = paletteMode,
            rawShift = poolShift,
            rawOverrides = poolOverrides,
            rawAccent = accentOverrideArgb,
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
            poolOverrides = poolOverrides,
            fsChoice = "M", // runtime Profile no longer carries fsChoice; persist a stable default
            useSecure = useSecure,
            nameAutoSeeded = nameAutoSeeded,
            accentOverrideArgb = accentOverrideArgb,
            advancedUrl = advancedUrl,
        )

    override fun toString(): String =
        "Profile(id=$id, name=$name, host=$host, port=$port, " +
            "apiKey=${if (apiKey != null) "***" else "null"}, seedHex=$seedHex, dark=$dark, " +
            "paletteMode=$paletteMode, poolShift=$poolShift, " +
            "poolOverrides=${poolOverrides.keys}, useSecure=$useSecure, nameAutoSeeded=$nameAutoSeeded)"

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
                poolOverrides = p.poolOverrides,
                useSecure = p.useSecure,
                nameAutoSeeded = p.nameAutoSeeded,
                accentOverrideArgb = p.accentOverrideArgb,
                advancedUrl = p.advancedUrl,
            )
    }
}
