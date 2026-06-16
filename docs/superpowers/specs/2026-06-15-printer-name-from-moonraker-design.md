# Seed printer-profile name from the Moonraker hostname

**Date:** 2026-06-15
**Status:** design approved, pre-plan
**Owner:** Matthew

## Problem

A printer profile is identified to the user by `displayName()` = `name ?: host` — i.e. when the
user hasn't typed a name, the profile is labeled by its raw IP (`192.168.1.120`). Moonraker knows
the printer's hostname (a readable machine name), so an un-named profile could show `ender5plus`
instead of an IP with zero user effort.

**Live data (both owner printers, 2026-06-15):**
- `GET /printer/info` → `result.hostname` = `"ender5plus"` (.120) / `"ender3"` (.121). **This is the
  reliable source.**
- `GET /machine/system_info` (which the app already fetches) → has `network`/`distribution`/
  `service_state` but **NO hostname field**. So the existing system-info fetch can't supply it.

## Goal

On a successful connect, if the active profile has no manual name, seed its `name` field with the
Moonraker hostname — **once**. After that it is a normal user-editable name (rename still wins;
clearing it stays blank).

## Non-goals

- No prettifying/transformation of the hostname (`ender5plus`, not "Ender 5 Plus" — the user can
  rename if they want pretty).
- No change to profile IDENTITY — the stable `id` UUID and the connection (`host`/`port`) are
  untouched. This only changes the user-facing display label.
- No change to `displayName()` itself (it already does `name ?: host`; once `name` is seeded it
  just shows the hostname). No display-layer code changes.
- Not deriving the name from `machine/system_info`, `server.info`, or the `identify` response —
  none carries the hostname.

## Behavior

### The seed rule

Seed iff **`name.isNullOrBlank() && !nameAutoSeeded`**, using the live hostname; on seed, set
`name = hostname` AND `nameAutoSeeded = true`, atomically.

### Why a `nameAutoSeeded` sentinel flag (the one nuance)

Connects happen on every app launch / reconnect. "Seed when name is blank" alone would re-seed a
name the user **deliberately cleared**. The owner's intent: clearing the name leaves it blank
permanently. So a per-profile `nameAutoSeeded: Boolean` (default `false`) makes the seed strictly
one-time:

- Fresh/un-named profile, `nameAutoSeeded = false` → seeds on first connect, flag flips `true`.
- User later clears the name → `name` blank but `nameAutoSeeded = true` → never re-seeds.
- User-renamed profile → `name` non-blank → never seeds (flag value irrelevant).

**Migration freebie:** existing profiles decode with `nameAutoSeeded = false`, so any currently
IP-labeled (un-named) profile auto-names on its next connect. No explicit migration step.

### The flag must ALSO be set on manual naming (closes the clear-after-naming hole)

The seed collector setting the flag isn't enough on its own. Consider: a user TYPES a name on a new
profile (flag still `false`, since name is non-blank the seed never runs), then later CLEARS it →
name blank + flag `false` → next connect would re-seed and overwrite their deliberate blank. So the
**connection editor's save path** must set `nameAutoSeeded = true` whenever it saves a NON-blank
name (new or edited); when the saved name is blank it PRESERVES the existing flag (so a brand-new
IP-only profile keeps `false` and still auto-seeds). Rule:
- New profile, blank name → flag `false` (default) → auto-seeds on first connect.
- New/edited profile, user typed a name → flag `true` → never auto-seeds.
- Later cleared (was named, or was auto-seeded) → flag already `true` → stays blank, no re-seed.

This means BOTH the seed collector AND the editor save flip the flag; the seed predicate
(`!nameAutoSeeded && name.isBlank() && hostname present`) reads it.

### Edge cases

- **Hostname blank/absent in the response** → no seed (predicate requires a non-blank hostname);
  the flag is NOT set, so a later connect that does return a hostname can still seed.
- **Blank/whitespace hostname** → treated as absent (trim; blank → no seed).
- **Multiple profiles / profile switch** → the seed targets only the ACTIVE profile, and only when
  the freshly-decoded active profile still matches the `sessionKey` the hostname was read from (see
  the B-2 guard). A switch mid-handshake mismatches → no mis-seed. Other profiles are untouched.
- **Re-handshake / reconnect within a session** → idempotent: after the first seed `name` is
  non-blank (or the flag is set), so subsequent connects no-op.

## Mechanism (data flow)

1. **Fetch** — add a `printer.info` one-shot to the handshake in `MoonrakerSession.runHandshake()`,
   alongside the existing `machine.system_info` best-effort read. Parse `result.hostname` (trim;
   blank → null).
   - New `CommandSpec` `printerInfo` (`MR-printer.info`, method `printer.info`) in
     `CommandRegistry`.
   - ⚠ **Command-catalog-drift (corrected per Codex):** `catalog.json` ALREADY contains an
     `MR-printer.info` entry, currently `reference_only` — do NOT add a duplicate; flip it to a
     registered status. The genuinely missing piece is a `docs/commands/printer-matrix.json`
     `command_availability` row for `MR-printer.info` (`predicate {"type":"always"}` /
     `predicate_key "always"`, `registry_status "registered"`, `printer_status` for BOTH
     `ender5plus` + `ender3` — `/printer/info` is already proven on both in the matrix). The
     `CommandCatalogDriftTest` asserts: every runtime registry catalogId is unique+non-empty and
     exists in `catalog.json`, and every runtime registry command has a `printer-matrix.json`
     `command_availability` row — so the matrix row is mandatory or the build fails.
2. **Expose** — store the parsed hostname (tagged with the session's connection identity — see B-2
   below) and surface it on the spine, mirroring the existing `SystemInfo` one-shot path
   (`SystemInfo` lives in `works.mees.dinghy.systeminfo`; `SpineHandle` in `works.mees.dinghy.di`):
   `store.setHostname(...)` on the printer-state store → `SpineHandle.hostname` →
   `AppContainer.hostname`. Null when idle / not yet read.
3. **Seed** — `AppContainer` observes the session hostname and performs a one-time durable write on
   the process-lifetime `writeScope` (never a composition scope — the recurring write-scope trap).
   The session layer stays decoupled: it only REPORTS the hostname; `AppContainer` (which already
   owns both the spine and the `ProfileStore`) owns the profile mutation.

   **The predicate lives INSIDE the transform, NOT in `mutateActive` (Codex B-1).**
   `ProfileStore.mutateActive` only decodes the fresh active profile and applies the caller's
   `transform` — it has no predicate knowledge. So the gate + the session-match guard run in the
   transform lambda, against the freshly-decoded profile, atomically inside the one `dataStore.edit`:
   ```
   mutateActiveProfile { p ->
       if (p.matchesSession(sessionKey) && shouldSeedName(p.name, p.nameAutoSeeded, hostname))
           p.copy(name = hostname.trim(), nameAutoSeeded = true)
       else p   // no-op: wrong profile active, already named, or already seeded
   }
   ```
   - **B-2 active-profile/session race guard (`sessionKey`):** `activeProfile` (from `ProfileStore`,
     `AppContainer`) can update BEFORE the service republishes the spine (`MoonrakerService` cancels
     + rebuilds on `activeConfig` change, then `publishSpine`), so naively pairing the session
     hostname with the current active profile can momentarily MISMATCH during a profile switch. Tag
     the hostname with the connection identity it was read from (the session's `host:port`, or the
     active `profileId` if more readily threaded) and have the transform seed ONLY if the
     freshly-decoded active profile still matches that identity. A switch makes it mismatch → no
     mis-seed.
   - **Double-fire / idempotency:** because the predicate is re-evaluated against the fresh profile
     INSIDE `edit`, a second emission before `activeProfile` reflects the new name simply no-ops
     (already named or already seeded). `distinctUntilChanged` on the gating key avoids redundant
     `writeScope` launches.

## Model change

Add `nameAutoSeeded: Boolean = false` to BOTH `Profile` and `PersistedProfile`
(`config/Profile.kt`), threaded through `toPersisted()` / `fromPersisted()`. Default `false`;
decode-safe for old blobs (kotlinx `ignoreUnknownKeys` + the Kotlin default cover missing/extra
keys, the same dual mechanism used elsewhere).

## The pure predicate (the testable core)

```kotlin
/** One-time hostname-seed gate: seed only an un-named, not-yet-seeded profile with a real hostname. */
fun shouldSeedName(currentName: String?, nameAutoSeeded: Boolean, hostname: String?): Boolean =
    !nameAutoSeeded && currentName.isNullOrBlank() && !hostname.isNullOrBlank()
```

The seeded value is the trimmed hostname. This predicate is **applied inside the
`mutateActiveProfile` transform lambda** (against the freshly-decoded profile), alongside the
B-2 `sessionKey` match — it is NOT a parameter of `mutateActive`.

## Testing

- **Host/unit:**
  - `shouldSeedName` truth table — un-named+unseeded+hostname → true; named → false; seeded → false;
    blank hostname → false; already-named → false.
  - `printer.info` hostname parse — extracts `result.hostname`; missing/blank → null; malformed JSON
    → null (no crash), mirroring `SystemInfo.from`'s `runCatching` fail-safe.
  - `nameAutoSeeded` persistence round-trips through `toPersisted`/`fromPersisted` (BOTH directions —
    Codex S-2); old JSON without the key decodes to `false`.
  - `CommandCatalogDriftTest` stays green after the `MR-printer.info` catalog entry is flipped to
    registered + the `printer-matrix.json` `command_availability` row is added.
  - **Handshake tests (Codex S-1):** `HandshakeTest` asserts the EXACT ordered handshake method list
    — adding `printer.info` requires updating that expected sequence; and `SessionTestHarness` must
    gain a canned `printer.info` reply containing a `hostname` (its unknown-method fallthrough
    returns `{}`, which would yield a null hostname and a misleading green test).
- **On-device UAT (flox + moto, two-device rule):** add a profile by IP with NO name → connect →
  the label becomes the hostname (`ender5plus` / `ender3`); rename it → rename sticks across
  reconnect; clear the name → stays blank across reconnect (no re-seed); the `host:port` line still
  shows the IP throughout.

## Files (anticipated)

- `app/.../command/CommandRegistry.kt` — `printerInfo` CommandSpec (catalogId `MR-printer.info`).
- `docs/commands/catalog.json` — flip the existing `MR-printer.info` entry from `reference_only` to
  registered (do NOT duplicate). `docs/commands/printer-matrix.json` — add the `MR-printer.info`
  `command_availability` row (`always` predicate, registered, both printers).
- `app/.../net/MoonrakerSession.kt` — fetch `printer.info` in `runHandshake` (best-effort, beside
  `machine.system_info`); parse `result.hostname`; tag with the session identity.
- the printer-state store + `SpineHandle` (`works.mees.dinghy.di`) + `AppContainer` — `hostname`
  flow (mirror the `SystemInfo` one-shot in `works.mees.dinghy.systeminfo`).
- `app/.../config/Profile.kt` — `nameAutoSeeded: Boolean = false` on BOTH `Profile` and
  `PersistedProfile` + both `toPersisted()`/`fromPersisted()` directions.
- `app/.../di/AppContainer.kt` — `shouldSeedName` predicate + the one-time seed collector (predicate
  applied INSIDE the `mutateActiveProfile` transform, with the `sessionKey` match).
- `app/.../net/` handshake parse helper for `printer.info.hostname`.
- Test updates: `HandshakeTest` (method sequence), `SessionTestHarness` (canned `printer.info`
  reply with hostname), plus new host tests for `shouldSeedName`, the hostname parse, and the
  `nameAutoSeeded` round-trip.
