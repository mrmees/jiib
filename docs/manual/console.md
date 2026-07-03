# Console

<img src="../screenshots/v0.1.0/console-flox-landscape-light_2026-07-03.png" width="640"/>
<img src="../screenshots/v0.1.0/console-moto-portrait-dark_2026-07-03.png" width="220"/>

A live read-only view of the raw printer conversation: every gcode command, Moonraker
response, and Klipper notice, color-coded by severity. Pre-connect history is backfilled
from the printer's gcode store so you can see what happened before you opened the screen.

**Getting there:** Home → Console (row in the home list).

## The screen

The screen is a single pane in both portrait and landscape — it does not split into the
standard two-region Focus / list layout. The Focus card's entire content area is the
console scrollback: a RecyclerView that grows newest-line-at-the-bottom and auto-scrolls
with new output whenever you are already at the bottom. Scroll up to browse history; new
lines continue appending, and scrolling back to the bottom resumes auto-scroll.

The foot bar holds Back and three filter toggles. The screen is read-only: there is no
command entry field and no keyboard affordance.

Back is the only exit.

## Options & controls

### Scrollback feed

**Severity coloring** — each line is assigned a tier from its Klipper prefix and rendered
in the corresponding color:

| Prefix | Tier | Color | Leading glyph |
|--------|------|-------|---------------|
| `!! ` | Error | Red | Octagon (stop) |
| `// ` (not action/debug) | Warning | Amber | Triangle (caution) |
| `// action:` | Action | Dimmed | None |
| `// debug:` | Debug | Dimmed | None |
| `ok` / `ok …` | Normal (ok) | Green | None |
| *(none of the above)* | Normal | Primary text | None |

The `!! ` and `// ` prefixes are stripped before display; action lines retain the
`action:` label and debug lines retain the `debug:` label after the leading `// ` is
removed. The leading glyph on error and warning rows is the redundant shape signal — the
tier reads in grayscale and under color vision deficiencies without relying on color alone.

**Scrollback limit** — the most recent 1000 lines are kept in memory. Older lines are
evicted as new ones arrive.

> Capacity matches Moonraker's `gcode_store_size` default (1000). Raise `gcode_store_size`
> in `moonraker.conf` to retain more history server-side; jiib's in-memory ring stays at 1000.

**Backfill** — on every connection and reconnect, the app fetches the full gcode store
snapshot from Moonraker and replaces the ring, so lines that arrived during a disconnect
window are recovered without manual refresh.

> Uses Moonraker's `server/gcode_store` endpoint. No extra Klipper config is needed.

**"Console is quiet" overlay** — shown over an empty feed on a fresh connection, before
any lines have arrived (either from the live stream or the backfill). Disappears as soon
as the first line lands.

**"History unavailable" banner** — shown at the top of the feed when the backfill request
fails. Live lines continue to appear normally; the banner informs you that the pre-connect
history could not be loaded. The existing feed is not cleared.

**Font and text size** — all rows use the Data face (default: Geist Mono) and honor the
app-wide S / M / L text size setting.

### Foot bar

**Back** (accent) — leaves the Console screen and returns to Home.

**Hide temperature messages** (toggle) — hides the M105 temperature-report echoes that
Klipper emits on every poll cycle (e.g. `ok T:210.0 /210.0 B:60.0 /60.0`). Default: off.

> Filters lines whose text (after prefix stripping) begins with `B:`, `C:`, or `T<n>:`,
> with or without a leading `ok `.

**Hide timelapse messages** (toggle) — hides Timelapse plugin gcode commands that
otherwise fill the log during a time-lapse recording:
`_TIMELAPSE_NEW_FRAME`, `TIMELAPSE_TAKE_FRAME`, `TIMELAPSE_RENDER`,
`_SET_TIMELAPSE_SETUP`, `HYPERLAPSE ACTION=`, and
`SET_GCODE_VARIABLE MACRO=TIMELAPSE_…`. Default: off. Has no effect if you do not use
the Timelapse plugin.

**Hide macro prompt messages** (toggle) — hides `action:prompt` protocol lines that
Klipper emits during macro prompt sequences. Default: off. Has no effect if your macros
do not use the prompt protocol.

All three toggles are independent and opt-in. An active toggle is rendered in accent
intent (filled outline); an inactive toggle is neutral. The raw history is never modified
by a toggle — turning a filter off immediately reveals the previously hidden lines from
the same session.

## Related

[Concepts](concepts.md) · [Home](home.md) · [Macros](macros.md)
