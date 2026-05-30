# Phase 3: Service, Shell & State-Driven Navigation - Discussion Log

> Human-readable record of the discussion. Not consumed by downstream agents.

**Date:** 2026-05-30
**Mode:** discuss (direct-decision batch — roadmap success criteria already locked most of the WHAT)

## Areas Discussed

### Connection / first-run UX

**Question:** With no saved config, how does the user get connected?
**Options presented:** Manual entry only (recommended) / Manual + mDNS auto-discovery.
**Decision:** **Manual + mDNS auto-discovery.**
**Notes:** Adds `NsdManager` `_moonraker._tcp` discovery with a pick-list, but manual host/port entry
remains the always-available floor (NSD is flaky on old Android; printer may be on another subnet).
Persisted via DataStore, replacing `DevConfig`.

### Shell chrome model

**Question:** KlipperScreen-style top bar + menu hub, or a persistent navigation rail?
**Options presented:** KlipperScreen-style top bar + menu hub (recommended) / Persistent navigation rail.
**Decision:** **Persistent navigation rail (modern Material 3).**
**Notes:** Key steer from Matthew — *"we're just using the KlipperScreen information as an idea of what
type of panels we need to flesh out, it's not a port of KlipperScreen to Android. This is modern and we
want it to feel that way."* So the catalog is a panel **inventory**, and modern feel is a first-class
requirement, not a KlipperScreen clone.

### Print-active routing

**Question:** When a print is active, is Job Status a hard override or a default landing?
**Options presented:** Default landing, freely navigable (recommended) / Hard override.
**Decision:** **Default landing, freely navigable.**
**Notes:** App lands on Job Status when printing but the user can navigate to Temp/Move/etc. to tune or
jog mid-print. (klippy non-ready → splash remains a hard override.)

### Foreground-service type

**Question:** FGS type for the connection-holding service (targetSdk 35 forces a declared type)?
**Options presented:** specialUse (recommended) / connectedDevice / dataSync.
**Decision:** **specialUse.**
**Notes:** No background timeout; honest fit; Play-Store justification moot since we sideload. Declared
for targetSdk 35 / newer devices even though the API-30 test device doesn't enforce it.

## Deferred Ideas

- mDNS as the primary path / multi-printer pick-list — discovery is additive in v1; multi-printer is v2.
- Boot-autostart, full Doze/always-on survival, `FLAG_KEEP_SCREEN_ON`, burn-in screensaver — Phase 8.
- Navigation-Compose adoption — revisit when destination count grows past the v1 state-holder.
- Trusted-client / richer auth UI beyond the optional API-key field.

## Claude's Discretion

- **Navigation mechanism:** state-holder `when` routing (top-level `when(klippyState)` gate + in-shell
  route state), NO Navigation-Compose dep for v1 — the reactive klippy gate doesn't fit a back-stack and
  deps stay lean on 2GB hardware.
- **Service architecture:** started FGS (`START_STICKY`) owning a `serviceScope` that builds the spine and
  runs `session.run()`; Activity observes process-held StateFlows (no binding). Manual service-locator DI,
  no Hilt.
- **Config-change handling:** recreate the `MoonrakerSession` on save (host/key are construction-time
  inputs) rather than relying on `requestReconnectNow()` alone.
- **Shared render/throttle primitive:** the view-side render is a classic-Views custom-`Canvas` graph
  (ADR 0001), established on the dashboard and extended by Phase 4 — the state-layer throttle already
  exists in `PrinterStateStore`.
- **DataStore:** add `androidx.datastore:datastore-preferences` to the version catalog (not currently
  present); must hold the minSdk-23 floor.
