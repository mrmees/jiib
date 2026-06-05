# Spoolman Planning Brief

This companion note is for product/design planning before the implementation
phase. The API/workflow details live in `docs/view_specific_notes/spoolman.md`.
This file captures the interaction decisions, feature candidates, and edge cases
that are easy to miss when planning a printer-side Spoolman surface.

## Product Frame

Dinghy should treat Spoolman as printer workflow support, not as an inventory
administration clone.

The primary job is:

1. Make the currently loaded spool obvious.
2. Make changing that spool fast.
3. Catch obvious print-start mistakes.
4. Let a QR code remove lookup friction.
5. Degrade cleanly when Spoolman or the camera is unavailable.

Inventory creation, vendor management, bulk import, filament editing, and full
Spoolman CRUD should stay outside the first implementation unless the mounted
device later becomes an inventory station.

## Deep-Pass Additions Before Dispatch

These are the extra product/API points that should be explicit before coding
agents start decomposing the work.

### Active Does Not Always Mean Synced

Moonraker owns the active spool id, but its Spoolman status also reports queued
`pending_reports`. A successful active-spool set means "future usage is assigned
to this id"; it does not prove that all prior usage has reached Spoolman.

Design implication:

- Show an "usage queued" or stale-remaining warning when `pending_reports` is
  non-empty.
- If Spoolman is disconnected but Moonraker has `pending_reports`, phrase this as
  queued usage rather than lost usage.
- Do not manually call Spoolman's `/use` during normal printing to compensate.
- In print-start warnings, treat remaining amount as potentially stale when
  reports are pending.

### QR Labels Have Two Supported Shapes

Spoolman can print both `WEB+SPOOLMAN:S-<id>` labels and full
`http(s)://.../spool/show/<id>` labels. Dinghy should support both in the first
QR parser tests.

Security/product rule: the QR is an id carrier, not a navigation target. Parse
the spool id and fetch it from the configured Moonraker/Spoolman path; never
open the scanned URL or switch to a host encoded in the QR.

Other `web+spoolman:` payloads, if encountered, should be treated as unsupported
Spoolman codes rather than non-Spoolman junk. They are not loadable spools unless
they resolve to `s-<id>`.

### Material Filters Need A Policy

The Spoolman API supports partial case-insensitive string filters, so `PLA`
can match `PLA+`. It also supports exact terms by quoting the value. Dinghy's
first-pass material chips should use family matching:

- `PLA` means `PLA`, `PLA+`, `PLA-CF`, `PLA High Speed Matte`, and similar.
- `ABS/ASA` sends comma-separated family terms such as `ABS,ASA`.
- exact material matching remains an advanced/later affordance if real use shows
  that family matching is too broad.

Use this policy consistently for picker filters and print-start mismatch checks.

### Spool Data Is Messier Than A Swatch

Recent upstream issues and release notes point at multi-color filaments and bad
color values as practical edge cases. Dinghy should display them defensively:

- Support `multi_color_hexes` as a split swatch when present.
- Accept `color_hex` with or without `#`; fall back cleanly on invalid values.
- Treat `extra` as a map of JSON-encoded strings. Parse only agreed local keys
  for badges/warnings, and tolerate invalid JSON values.
- Do not let a bad color field break the active spool card, picker row, or QR
  confirmation.

### Adjacent Features Are Not First-Pass QR

NFC/OpenPrintTag, RFID helpers, manufacturer UPC/EAN intake, and per-spool custom
links are real user requests around Spoolman, but they should stay out of the
initial mounted-printer QR flow. The first pass should only load an existing
Spoolman spool by id.

## User Journeys Worth Designing

### Idle: "What Is Loaded?"

The Status screen should answer this without opening a new page.

Show a compact active-spool card with:

- filament material
- vendor/name
- color swatch
- remaining weight or length
- active/no-active/disconnected state

Useful card states:

- Spoolman unavailable: Moonraker has no `spoolman` component.
- Spoolman disconnected: component exists, but status reports disconnected.
- No active spool: printer has no active spool id.
- Loading details: active id is known, detail fetch pending.
- Active spool: detail fetched.
- Detail stale: detail fetch failed after a prior successful value.

Primary actions:

- `Scan`
- `Change`
- `Clear`

Avoid placing too many inventory fields on the home card. The card is a
confidence indicator and a launch point, not a database row.

### Manual Load: "I Put A Spool On The Printer"

The picker should be optimized for standing at the printer with dirty hands,
not for spreadsheet search.

Good default list:

- unarchived spools
- sorted by material, vendor/name, id
- material chip/filter row
- recent/last-used shortcut if available
- location shortcut for likely printer-adjacent shelves

Recommended first filters:

- material chips: PLA, PETG, TPU, ABS/ASA, Other
- close-enough color swatches: black, white, gray, clear/natural, red, orange,
  yellow, green, blue, purple, pink, brown, metallic/silk, multi-color, other
- "At this printer" or printer-location shortcut
- "No location"
- recent/last-used shortcut
- vendor shortcut as a second-row/refine filter when the list is still large
- archived toggle hidden behind an advanced affordance

Populate secondary filter choices from Spoolman where possible:

- `/v1/material` for exact local material labels
- `/v1/location` for shelf/printer-adjacent shortcuts
- `/v1/vendor` for vendor refine chips

Material matching should use Spoolman's partial case-insensitive material
filter. That gives useful family behavior, for example `PLA` matching `PLA+`,
`PLA High Speed Matte`, and `PLA Meta`. It is not typo-tolerant fuzzy search, so
multi-family chips should send comma-separated terms such as `ABS,ASA`.

Color matching should use Spoolman's own filament color-similarity endpoint,
then fetch spools by the returned filament ids. Do not build a local RGB
distance matcher in Dinghy unless Spoolman's endpoint proves too slow on a real
inventory.

Sort shortcuts are useful filters on a mounted screen:

- recent: `sort=last_used:desc,registered:desc&limit=10`
- low remaining: `sort=remaining_weight:asc`
- least recently used: `sort=last_used:asc,registered:asc`

Lot numbers, article numbers, external ids, and custom `extra` fields should be
later-phase refinements. Spoolman exposes discovery endpoints for them, but they
are not currently populated in the live local install and they are less useful
for the standing-at-printer load flow.

Avoid an alphanumeric search field in the first pass unless there is no other
way to handle real inventories. Dinghy's UI law pushes printer-control surfaces
toward tap/scroll/filter interactions rather than keyboard entry.

### QR Load: "Scan The Label"

The QR flow should be deliberately confirm-first:

1. User taps `Scan`.
2. Camera opens.
3. QR decode finds `web+spoolman:s-<id>` or a full `/spool/show/<id>` URL.
4. Dinghy fetches the spool detail.
5. User sees a confirmation card.
6. User taps `Load`.
7. Dinghy sets Moonraker's active spool id.

Do not auto-load on scan. A bad scan should not change print accounting.

QR confirmation should show only the fields needed for confidence:

- material
- color
- vendor/name
- remaining amount
- location
- archived warning if present

If the scanner sees a manufacturer UPC/EAN or any non-Spoolman barcode, show a
short "Not a Spoolman spool code" state and keep the user in the scan flow. Do
not create inventory records from manufacturer barcodes in the first pass.

Native Dinghy scanning is not subject to the browser HTTPS camera restriction
called out in Spoolman's FAQ, but the same user-facing failures still matter:
camera permission denied, no camera, camera busy, unreadable QR, and unsupported
payload.

### Print Start: "Can I Print This File With This Spool?"

The spool gate belongs in the existing Files print confirmation flow. It should
not become a separate preflight wizard.

Recommended decision table:

| Condition | Behavior |
|---|---|
| no Spoolman component | print normally; no spool gate |
| Spoolman disconnected | warn; allow manual override |
| no active spool | block until pick/scan, or require explicit "print without spool tracking" override |
| active spool archived | warn or block, depending on policy |
| material mismatch | warn with `Print anyway`, `Pick spool`, `Scan` |
| remaining amount too low | warn/block with `Pick spool`, `Scan`, `Print anyway` |
| file lacks weight metadata | skip low-filament check unless a length/density fallback is deliberately implemented |
| pending usage reports | warn that remaining amount may be stale; allow retry/continue policy |
| active spool detail fetch fails | warn; allow retry or override |

For a mounted printer UI, it is usually better to warn than hard-block except
for "no active spool" if the user wants Spoolman accounting to be reliable.
That policy should be explicit before implementation.

### During Print Or Pause: "I Changed Filament"

This should be supported, but kept simple.

Flow:

1. User opens active-spool card.
2. User scans or picks the replacement spool.
3. Dinghy confirms that future usage will be attributed to the new spool.
4. Dinghy sets the active spool id.

Avoid trying to adjust consumption for the old spool. Moonraker should already
be reporting usage against whatever spool was active during prior extrusion.

Do not assume Dinghy is the only mutator. Fluidd/Mainsail, runout flows, or
Klipper macros using Moonraker's active-spool remote method can change the active
spool while Dinghy is open. The status card, picker, and confirmation state must
reconcile `notify_active_spool_set` even when Dinghy did not initiate it.

### Unload: "There Is No Spool Loaded"

Clear active spool via Moonraker. Optional follow-up behavior:

- ask whether to move the old spool's location back to a shelf/dryer
- offer common locations as tap targets
- keep location update optional, never automatic by default

This distinction matters: active state is Moonraker truth; location is inventory
context.

## Extra Features To Consider

These are useful, but should be ranked after the active-spool, picker, QR, and
print-start gate flows.

### Material/Color-Aware Picker From Selected Gcode

When a user starts from Files, Dinghy can pre-filter the spool picker using the
selected file metadata:

- `filament_type[]` -> material filter
- `filament_weight_total` -> enough-filament check
- `filament_colors[]` -> color swatch prefilter through Spoolman's similarity
  endpoint; keep it a warning/hint, not a strict compatibility check

This makes "Pick spool" from a warning modal much faster than opening a generic
inventory list.

### "Use Last Spool For This File"

If Moonraker history or auxiliary data exposes the spool used for a previous run
of the same file, Dinghy could suggest it. Treat this as a later convenience; it
depends on history shape and should be fixture-proven.

### Measured Weight Correction

Good mounted-device workflow when the user has a scale nearby:

- open current spool details
- tap `Measure`
- enter gross weight using the single-setting numeric primitive
- send the measured gross grams to Spoolman

This is a correction workflow, not the primary way Dinghy should track
consumption.

### Drying And Calibration Badges

Spoolman `extra` fields can carry things like dried date, calibrated printer,
or profile name. Dinghy can show badges and warnings if the local convention is
known.

Keep this optional until the actual `extra` field schema is agreed on. Avoid
inventing hidden schema in code, and remember that Spoolman stores each `extra`
value as a JSON-encoded string.

### Per-Tool Or Lane Assignment

Single active spool is enough for the first pass. Multi-material, toolchanger,
AMS/MMU, or lane-based printers need a separate model:

- active spool per extruder/tool/lane
- material checks per `filament_type[]` slot
- QR scan into a selected lane

Do not let this complexity leak into the first single-extruder implementation.

## Layout Guidance

### Status Card

The active-spool card should be compact and glanceable.

Field priority:

1. material
2. color
3. vendor/name
4. remaining amount
5. disconnected/no-active warning

Suggested controls:

- `Scan` as the primary quick action
- `Change` for picker
- `Clear` as a guarded/destructive-ish action

### Picker

Use a dense, Files-like list. Each row should have fixed height and avoid layout
shift.

Row fields:

- color swatch
- material text
- vendor/name
- remaining grams
- location

The focus area can show the selected spool detail, while the field area remains
the scrollable list. In portrait, the selected detail can collapse into a top or
bottom summary band.

### Scanner

The scan surface should be full-screen and task-focused.

Use:

- camera preview as the focus
- a small scan status overlay
- gutter Back
- optional flashlight only if available and cheap to detect

Implementation notes:

- Use ZXing or document a deliberate GMS-free replacement.
- Fixture-prove the actual lens. The intended mounted/front camera may not focus
  on close labels on old Nexus 7 class hardware; use rear/autofocus if that is
  the only reliable scan path.
- Throttle analysis frames, but keep enough resolution to read the real printed
  label at expected distance.
- Release the camera on pause, background, and navigation-away.

Do not add in-app instructions beyond minimal status text. The QR task is
self-explanatory at the printer.

### Print-Start Warning

Reuse the existing confirm-guard language:

- green for safe continue
- amber for "proceed at peril"
- red only for destructive/stop actions

Warning text should name the mismatch concretely:

- "File wants PLA. Active spool is PETG."
- "File needs about 84 g. Active spool reports 42 g remaining."
- "No active spool is selected for Spoolman tracking."

Actions should be direct:

- `Scan`
- `Pick spool`
- `Print anyway`
- `Back`

## Data Ownership Rules

- Moonraker active spool id is the source of truth for what the printer is using.
- Moonraker `pending_reports` is the source of truth for whether usage is fully
  synced to Spoolman.
- Spoolman `location` is inventory context, not active-printer truth.
- Dinghy should not call `/use` during normal printing if Moonraker is already
  reporting usage.
- Direct Spoolman edits should be deliberate user actions, not side effects of
  viewing or scanning.
- All Spoolman response fields should be treated as optional.
- Color fields should be display-normalized and never trusted to be valid.
- External active-spool changes should be accepted and reflected, not overwritten
  by stale picker state.
- Cached spool details may be displayed read-only when disconnected, but set/clear
  actions should be disabled.

## Error And Empty States

Design these states before coding:

- no `spoolman` component
- Spoolman component present but disconnected
- no active spool id
- active id points to a missing Spoolman spool
- active spool archived
- QR payload invalid
- QR payload valid but spool id not found
- QR payload is a valid Spoolman scheme but not a spool id
- QR full URL from a different host; parse id only, never navigate or reconfigure
- camera permission denied
- no usable camera or selected lens cannot focus on the label
- camera busy/unreadable
- camera opens but no QR found
- proxy request fails
- proxy returns a shape different from direct Spoolman REST
- pending usage reports make remaining filament potentially stale
- active spool id is set, but detail fetch returns missing/deleted spool
- active spool changes externally while Dinghy picker/confirmation is open
- selected file lacks enough metadata for material or weight checks
- invalid or multi-color filament data in swatch fields

The user should always have an escape path: Back, manual picker, or print without
tracking when policy allows.

## Open Product Decisions

These are worth settling during design discussion:

1. Should "no active spool" block print start, or warn with override?
2. Should low remaining filament block, or warn with override?
3. What safety margin should be added to `filament_weight_total`?
4. Should material mismatch and picker chips compare exact strings, normalized
   aliases, or partial families like `PLA` vs `PLA+`?
5. Should clearing a spool prompt for a location update?
6. Should scanning an archived spool be blocked or allowed with warning?
7. What warning language should show when Moonraker has pending Spoolman usage
   reports?
8. If `filament_weight_total` is missing, should Dinghy skip the low-filament
   check or implement a length/density fallback?
9. Which physical camera/lens should QR use on target hardware, and what
   resolution/focus proof is required?
10. Should the Spoolman picker become a drawer destination, or remain launched
   from Status/Extrude/Files until there is a full management screen?
11. Should Dinghy store any local spool preferences, or derive everything from
   Moonraker/Spoolman each session?
12. What exact `extra` field keys, if any, should Dinghy understand for drying
    or calibration badges?

## Recommended First Cut

Must have:

- active-spool status card
- manual picker
- set active spool
- clear active spool
- QR parser and scan-to-confirm-to-load
- both official QR scheme and full `/spool/show/<id>` URL parsing
- print-start warnings for no spool, material mismatch, and low remaining amount
- pending-report/stale-remaining warning
- external active-spool change reconciliation
- real-device QR lens/focus proof and camera lifecycle cleanup
- clean disconnected/no-camera fallbacks

Nice to have:

- material-aware picker prefilter from selected gcode
- location shortcuts
- archived-spool warning
- measured gross weight correction

Defer:

- full inventory editing
- filament/vendor creation
- manufacturer barcode intake
- multi-lane spool assignment
- arbitrary custom-field administration
- NFC/OpenPrintTag/RFID integrations
