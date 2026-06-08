# Phase 20 — System Information Page: On-Device Cross-SBC UAT

**Plan:** 20-04 (Task 3, `checkpoint:human-verify`, gate=blocking)
**Requirement / SC:** SYS-05 / SC-4
**Build:** debug APK installed on flox (`Nexus 7 - 11`, LineageOS 18.1 / API 30, genuine Adreno 320) via `:app:installDebug` — BUILD SUCCESSFUL.
**Printers:** Ender 5 Plus / RPi 4 = `192.168.1.120:7125` · Ender 3 / RockPro64 = `192.168.1.121:7125`

The cross-SBC walk verifies the read-only System Information page renders host identity / live load /
shape-coded health on BOTH SBCs and that the health chip MEANS THE SAME on each (SC-4). A FAIL spawns a
gap-closure plan, NOT a phase-complete mark.

| # | SBC | Check | Result | Notes |
|---|-----|-------|--------|-------|
| 1 | RPi 4 (E5, .120) | Drawer System Info tile shows the `pulse_alert` glyph; tapping it opens the page. | ⬜ PENDING | |
| 2 | RPi 4 | Focus: host model reads "Raspberry Pi 4 Model B Rev 1.4"; CPU temp = sane whole °C that UPDATES (~1 Hz); uptime = compact days/hours; health chip = healthy (go/shapeless) shape. | ⬜ PENDING | |
| 3 | RPi 4 | Field Host: CPU model + 4 cores; Total RAM ~7.6 GB; Distro "Debian GNU/Linux 12 (bookworm)"; Kernel "6.12.87+rpt-rpi-v8". Live load: CPU % + memory used/total update ~1 Hz. | ⬜ PENDING | |
| 4 | RockPro64 (E3, .121) | Switch the app to the E3; the page re-resolves to the new host. | ⬜ PENDING | |
| 5 | RockPro64 | Focus: host-model row degrades gracefully (model empty → "—" or distro-name label "Armbian 25.11.2 noble"); CPU temp updates; health chip uses the TEMP-FALLBACK and reads the SAME three shape-coded states as the Pi (idle → healthy/go, temp < 70). Field: 6 cores, ~3.8 GB RAM, Armbian distro, rockchip64 kernel. | ⬜ PENDING | |
| 6 | Both | Chip MEANS THE SAME on both hosts (same shapes, same go/warn/caution semantics); nothing crashes; Back returns cleanly; no janky/empty page on either SBC. | ⬜ PENDING | |

**Owner sign-off:** ⬜ PENDING — reply "approved" (or list failing check numbers to spawn gap closure).
