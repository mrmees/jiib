---
phase: 04-service-shell-settings-print-status-home
plan: 04
subsystem: ui-settings
tags: [settings, connection, theming, mdns, datastore, compose]
requires:
  - "AppContainer.connectionStore / themeResolver / themePrefs (04-03)"
  - "ConnectionStore + ConnectionConfig + MoonrakerDiscovery (04-01)"
  - "ThemeResolver + ThemePrefs + TokenDelta + FontScale (Phase 3)"
  - "OutlinedControl + LocalTokens + Geist/GeistMono + fsSp (Phase 3)"
provides:
  - "ui/screen/SettingsScreen.kt — the conventional Settings screen (SET-01): connection entry + theme + accent picker"
  - "ui/screen/TokenTextField.kt — token-aware OutlinedTextField wrapper (review #7)"
  - "AppContainer.discovery — the FULLY-LAZY MoonrakerDiscovery, injected for the Scan button"
affects:
  - "AppContainer constructor (new required `discovery` param)"
  - "DinghyApp (constructs the Context-bound MoonrakerDiscovery)"
  - "AppContainerTest (newContainer() supplies a throwing-provider lazy discovery)"
tech-stack:
  added: []
  patterns:
    - "TokenTextField bridges Material3 OutlinedTextField chrome to LocalTokens semantic roles (THEME-01)"
    - "Conventional verticalScroll list — the ONE screen exempt from Focus/Field/Gutter (D-15)"
    - "Live + persist dual-write for every theme control (ThemeResolver + ThemePrefs, D-16)"
    - "Lazy mDNS scan collected only on tap with a bounded settle window (D-04, review #5)"
key-files:
  created:
    - "app/src/main/java/works/mees/dinghy/ui/screen/SettingsScreen.kt"
    - "app/src/main/java/works/mees/dinghy/ui/screen/TokenTextField.kt"
  modified:
    - "app/src/main/java/works/mees/dinghy/di/AppContainer.kt"
    - "app/src/main/java/works/mees/dinghy/DinghyApp.kt"
    - "app/src/test/java/works/mees/dinghy/di/AppContainerTest.kt"
decisions:
  - "MoonrakerDiscovery is INJECTED into AppContainer (constructed by DinghyApp, which holds the Context) rather than built in the container — keeps the container Context-free while honoring the laziness contract (review #5)"
  - "The mDNS Scan uses a 6s bounded settle window (withTimeoutOrNull) so a tap can never run discovery forever; an empty scan ends cleanly and manual entry is always the floor"
  - "Client-side Save validation mirrors ConnectionStore.sanitize (blank host / port 1..65535) for inline errors; the store re-sanitizes on read, so the screen never persists invalid input"
  - "The accent-swatch FILL is the single non-token Color in the screen — it materializes the candidate accent being picked (palette DATA, D-16), not rendered chrome; every other color routes through LocalTokens"
metrics:
  duration_min: 14
  completed: 2026-06-01
  tasks: 2
  files: 5
---

# Phase 4 Plan 04: Settings Screen Summary

Conventional token-themed Settings screen (SET-01) that lets the user type a printer host/port/optional
key with the system keyboard and Save it to `ConnectionStore` (seeding the live spine via the service
rebuild, D-03), surface printers via a lazy mDNS scan, and drive Dark/Light + S/M/L + an accent picker
both live (ThemeResolver) and persisted (ThemePrefs, D-16) — built on a `TokenTextField` wrapper so the
Material fields stay on the Phase-3 semantic theme.

## What shipped

**`ui/screen/TokenTextField.kt`** — a thin `@Composable` over Material3 `OutlinedTextField` that derives
every color it hands to `OutlinedTextFieldDefaults.colors(...)` (focused/unfocused/error border, cursor,
text, container, label) from `LocalTokens.current` roles, and sizes type via `fsSp(..)` + Geist. This is
the review-#7 bridge: connection fields recolor with the active theme instead of Material defaults. No raw
color literal.

**`ui/screen/SettingsScreen.kt`** — `SettingsScreen(container, onConnectionSaved)`, a
`Column.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)` (NOT `ScreenScaffold`, D-15;
constructs nothing — receives the container, GalleryScreen discipline). Two sections:

- **Connection** (PRIM-02 system keyboard confined here): `TokenTextField`s for host (Text), port
  (Number, default `7125`), and optional key (Password, masked). Save validates via the same
  blank-host / port-1..65535 parity as `ConnectionStore.sanitize` → inline `FieldError`s, no save on
  invalid (T-04-04-T); on valid it builds a `ConnectionConfig` and calls `connectionStore.save(...)` in a
  coroutine, then `onConnectionSaved()`. Pre-fills host/port from the current saved config — never the key.
  - **Key handling (review #10/#12):** the key field is blank on load; a non-secret "Key saved" indicator
    shows when a key already exists; a blank key on Save PRESERVES the stored key (re-reads it); a distinct
    red **Clear key** action explicitly removes it.
  - **mDNS Scan (review #5, D-04):** an accent `Scan (mDNS)` control collects `container.discovery.discover()`
    ONLY on tap, inside a 6s `withTimeoutOrNull` settle window; results render as tappable rows that fill
    host+port; an empty scan shows "No printers found" and never blocks manual entry.
- **Appearance (D-16):** Dark/Light, S/M/L over `FontScale.entries`, and a single-role accent picker. Each
  control writes BOTH the live `themeResolver` (`setBase`/`setFs`/`setDeltas`) AND the persisted
  `themePrefs`. The picker writes only `TokenDelta.of(Role.Accent to argb)` (a "Default" swatch clears the
  override); no Heat/Go/Stop/Bg writes, no multi-role editor, no feature toggles (D-17). Seeded once from
  `themePrefs.flow` so the screen opens reflecting saved base/size/accent.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] AppContainer did not expose the mDNS scanner the plan's Scan button collects**
- **Found during:** Task 1
- **Issue:** The plan's `<action>` and `<read_first>` say the Scan button collects `container`'s
  `MoonrakerDiscovery.discover()`, but `AppContainer` (04-03) had no `discovery` field and held no Context to
  construct one (its laziness contract needs Context-bound `NsdManager`/`MulticastLock` providers).
- **Fix:** Added an injected `val discovery: MoonrakerDiscovery` param to `AppContainer`; `DinghyApp`
  (which holds the application Context) constructs the `MoonrakerDiscovery` with `getSystemService`-backed
  provider lambdas (acquired only on collect, review #5) and passes it in. Updated `AppContainerTest`'s
  `newContainer()` to supply a throwing-provider lazy discovery (the host tests never collect `discover()`,
  proving holding it pins nothing).
- **Files modified:** `di/AppContainer.kt`, `DinghyApp.kt`, `test/.../AppContainerTest.kt`
- **Commit:** a7dab6f

## Known Stubs

None. The screen wires real data end to end: host/port/key persist to `ConnectionStore` (→ service rebuild),
theme controls drive the real `ThemeResolver` + `ThemePrefs`, and Scan collects the real
`MoonrakerDiscovery`. The full multi-role custom-theme editor is intentionally deferred per D-16 (the
substrate already supports it) and is not a stub blocking this plan's goal.

## Verification

- `:app:compileDebugKotlin` — GREEN
- `:app:testDebugUnitTest` — GREEN (incl. the updated `AppContainerTest`)
- Greps: `verticalScroll` present, no `ScreenScaffold` scaffold usage; `OutlinedTextFieldDefaults` present
  in TokenTextField with zero raw color literals; `TokenTextField` used for host/port/key;
  `connectionStore.save` + `discover` wired; key-saved indicator + Clear-key + no-clobber present; default
  port 7125; `setBase`/`setFs`/`setDeltas` dual-write present; accent picker writes Role.Accent only (no
  Heat/Go/Stop/Bg); no feature-toggle section. Token-purity holds except the five palette swatch ARGB ints
  and the one `Color(fillArgb)` that renders a chosen swatch (palette DATA, not chrome).

## Not in scope (downstream)

- A caller for `SettingsScreen` (the App Drawer / route holder wiring) lands with the shell plan; this plan
  delivers the screen component, not its navigation host.
- On-device manual UAT (keyboard focus, live theme flip, a real mDNS printer appearing) is an end-of-phase
  human-verify item per config (`human_verify_mode: end-of-phase`).

## Self-Check: PASSED
- FOUND: app/src/main/java/works/mees/dinghy/ui/screen/SettingsScreen.kt
- FOUND: app/src/main/java/works/mees/dinghy/ui/screen/TokenTextField.kt
- FOUND commit a7dab6f (Task 1), FOUND commit 042b1f6 (Task 2)
