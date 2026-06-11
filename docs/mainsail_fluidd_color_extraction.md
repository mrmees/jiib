# Mainsail / Fluidd Theme Color Extraction (via Moonraker API)

How to reliably retrieve the active **theme colors** (and dark/light mode) for Mainsail and
Fluidd by reading Moonraker's internal database over the HTTP API.

Verified against source for both apps and validated empirically against live printers
(192.168.1.120). Last verified: 2026-06.

---

## TL;DR

- Both UIs persist their settings in Moonraker's database, each under its own namespace
  (`fluidd`, `mainsail`).
- **You cannot assume the color field exists.** On a fresh install where the user has never
  touched the theme, the key is **absent (HTTP 404)**. You must fall back to the hardcoded
  default constants below.
- Field names, the dark/light representation, and hex letter-case all **differ between the two
  apps**. Normalize before comparing.

| | Fluidd | Mainsail |
|---|---|---|
| Namespace | `fluidd` | `mainsail` |
| Key path | `uiSettings.theme` | `uiSettings` |
| Primary color field | `value.color` | `value.primary` |
| Logo color field | — (logo is a file `logo.src`, not a color) | `value.logo` |
| Dark/light field | `value.isDark` (boolean) | `value.mode` (`"dark"` / `"light"`) |
| Written on fresh install? | **No** — read-only init | **No** (unless a server-side theme file exists) |
| Hex case when user-set | lowercase (`#ff0000`) | UPPERCASE (`#0000FF`) |

---

## API endpoints

Moonraker default port is **7125**.

List namespaces (sanity check that the apps are installed):
```
GET http://<printer>:7125/server/database/list
```

Read the relevant key:
```
GET http://<printer>:7125/server/database/item?namespace=fluidd&key=uiSettings.theme
GET http://<printer>:7125/server/database/item?namespace=mainsail&key=uiSettings
```

Moonraker supports **dotted key paths** (e.g. `uiSettings.theme`), so you can fetch the nested
Fluidd theme object directly. Response shape:
```json
{ "namespace": "fluidd", "key": "uiSettings.theme", "value": { ... } }
```
A missing key returns **HTTP 404** — this is expected and means "never customized," not an error.

### Sample responses (live, after setting custom values)

Fluidd — light mode, pure red:
```json
{ "isDark": false, "logo": { "src": "logo_fluidd.svg" }, "color": "#ff0000", "backgroundLogo": true }
```

Mainsail — dark mode, pure blue primary, pure green logo:
```json
{ "primary": "#0000FF", "logo": "#00FF00", "mode": "dark" }
```

---

## Default constants (from source)

Used as the fallback when the DB key is absent (fresh install) or to detect whether the stored
value is actually a customization.

| App | Source file | Defaults |
|---|---|---|
| **Fluidd** | `src/store/config/state.ts` | `color = #2196F3`, `isDark = true`, `logo.src = logo_fluidd.svg`, `backgroundLogo = true` |
| **Mainsail** | `src/store/variables.ts` | `primary = #2196f3`, `logo = #D41216`, `mode = dark` |

> Note the case mismatch in the source defaults themselves: Fluidd `#2196F3` (upper) vs
> Mainsail `#2196f3` (lower). Always `.ToUpper()` (or lowercase) both sides before comparing.

---

## Why you can't rely on the field existing — persistence behavior

### Fluidd: read-only init, writes only on change
- `initUiSettings` (`src/store/config/actions.ts`) **only reads** the namespace and merges it
  over the in-memory default state. There is **no** database write in the init path.
- The theme is persisted (`saveByPath` → `uiSettings.theme`) **only when the user changes it**.
- **Consequence:** a never-touched Fluidd has **no** `uiSettings.theme` key → query 404s. The
  blue you see in the UI is the in-memory default `#2196F3`, never written to the DB.

### Mainsail: has an init-writeback, but it does NOT write the color defaults
- `initDb` (`src/store/gui/actions.ts`) is commented *"fill in default values"*, but it sources
  those values from a **server-side theme file**:
  `GET /server/files/config/<themeDir>/default.json` (typically `config/.theme/default.json`).
- On a stock install that file doesn't exist → `404` → `defaults = {}` → the loop writes nothing
  except `initVersion`. The theme colors come from the in-memory constants in `variables.ts`.
- **Consequence:** a fresh Mainsail namespace contains `initVersion` (and other touched keys)
  but **no** `uiSettings.primary/logo/mode` until the user opens theme settings (which fires
  `saveSetting` and POSTs the value).
- **Caveat:** if an admin deploys a `config/.theme/default.json` for org-wide branding, `initDb`
  *will* write those theme values into the namespace on first boot. In that case a present key
  equal to the app default could actually be an admin default. Doesn't matter for simple
  "is it customized vs factory" unless you run a custom default theme.

### Detecting "customized" vs "default"
Three states per app:
1. **Key absent (404)** → never touched → app uses hardcoded default.
2. **Key present, value == default** → touched then reset (e.g. Mainsail reset writes explicit
   defaults rather than deleting the key) → currently default.
3. **Key present, value != default** → genuinely customized.

So: `customized = key_exists AND value != known_default` (case-normalized).

---

## Fork verification

The local Fluidd clone (`E:\claude\personal\github\fluidd`, fork `mrmees/fluidd`) was checked
because it carries customizations (nav links, macro-prompt protocol). `git diff upstream/develop`
for `state.ts`, `actions.ts`, `mutations.ts`, and `vuetify.ts` is **empty** — theming is
byte-identical to upstream `fluidd-core/fluidd`. The upstream `state.ts` default was independently
fetched and confirms `color: '#2196F3'`. No fork-induced theming differences.

(The local Mainsail path is a WSL symlink to `mainsail-crew/mainsail` source; read but not diffed
against upstream — `initDb`/`variables.ts` behavior is standard.)

---

## Reference implementation (PowerShell)

Treats 404/missing as default, normalizes case, flags customization, and normalizes the
dark/light divergence between the two apps.

```powershell
$DEFAULTS = @{
    Fluidd   = @{ color = '#2196F3' }
    Mainsail = @{ primary = '#2196F3'; logo = '#D41216'; mode = 'dark' }
}

function Get-PrinterTheme {
    param([string]$Target, [int]$Port = 7125)
    $base = "http://${Target}:${Port}/server/database/item"
    function Get-Val($ns, $key) {
        try { (Invoke-RestMethod "$base?namespace=$ns&key=$key" -TimeoutSec 8).result.value }
        catch { $null }   # 404 / unreachable -> null (= default / not set)
    }

    $f = Get-Val 'fluidd'   'uiSettings.theme'
    $m = Get-Val 'mainsail' 'uiSettings'

    $fColor = if ($f.color)   { $f.color }   else { $DEFAULTS.Fluidd.color }
    $mColor = if ($m.primary) { $m.primary } else { $DEFAULTS.Mainsail.primary }
    $mLogo  = if ($m.logo)    { $m.logo }    else { $DEFAULTS.Mainsail.logo }

    [pscustomobject]@{
        Target         = $Target
        FluiddColor    = $fColor.ToUpper()
        FluiddIsDark   = if ($null -ne $f.isDark) { [bool]$f.isDark } else { $true }   # default isDark=true
        FluiddCustom   = [bool]$f.color   -and ($f.color.ToUpper()   -ne $DEFAULTS.Fluidd.color.ToUpper())
        MainsailColor  = $mColor.ToUpper()
        MainsailLogo   = $mLogo.ToUpper()
        MainsailIsDark = if ($m.mode) { $m.mode -eq 'dark' } else { $true }            # default mode=dark
        MainsailCustom = [bool]$m.primary -and ($m.primary.ToUpper() -ne $DEFAULTS.Mainsail.primary.ToUpper())
    }
}

# Example:
Get-PrinterTheme -Host '192.168.1.120' | Format-List
```

### Raw extraction (any language / curl)
```
GET /server/database/item?namespace=fluidd&key=uiSettings.theme   -> .result.value.color   (404 = default #2196F3)
GET /server/database/item?namespace=mainsail&key=uiSettings        -> .result.value.primary (404 = default #2196F3)
                                                                   -> .result.value.logo    (404 = default #D41216)
```

---

## Normalization checklist

When aggregating across both apps / multiple printers:

- [ ] Treat HTTP 404 as "default," not an error → substitute the hardcoded constant.
- [ ] `.ToUpper()` every hex value (source defaults and stored values) before comparing.
- [ ] Map dark/light to one concept: Fluidd `isDark` (bool) vs Mainsail `mode` (`"dark"`/`"light"`).
- [ ] Use the right field name per app: Fluidd `color`, Mainsail `primary`.
- [ ] Mainsail has a separate `logo` **color**; Fluidd's `logo` is a **file reference**, not a color.
- [ ] "Customized" = key present AND value != default (case-normalized).
