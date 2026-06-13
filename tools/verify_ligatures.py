#!/usr/bin/env python3
"""
Dinghy Display — Material Symbols ligature-resolution gate (D-04 / Phase 18.1).

WHY THIS EXISTS
---------------
Phase 18.1 (icon-system conformance) flips a set of hand-traced / redundant local vector
drawables to their canonical Material Symbol, rendered as a FONT LIGATURE from the bundled
`app/src/main/res/font/material_symbols_outlined.ttf`. A drawable→ligature flip is only safe
if the bundled font actually resolves the glyph by name — otherwise the call site renders
"tofu" (an empty box) with NO build error and NO crash. This script is the device-free
precondition for every such flip: it traverses the font's GSUB table and proves that every
Material Symbol name the app needs (every current `IconRef.Ligature(name)` in DinghyIcons
PLUS the 13 D-08 conversion targets) resolves as a real ligature.

It is the load-bearing gate run BEFORE any drawable is deleted in later 18.1 waves. The
glyph-name-present check (cmap) is necessary but the ligature-output set is the authoritative
"renders by typing the name" test, so we traverse GSUB LigatureSubst (LookupType 4),
unwrapping LookupType 7 (Extension) subtables.

D-13 NOTE: the bundled font (v2.944) is RETAINED, not refreshed — it already carries every
needed glyph (verified). This script is also the re-run gate IF the binary is ever swapped:
a non-empty `missing` list blocks the swap.

CRITICAL: the Pressure-Advance glyph is `text_select_move_forward_word` — WITH the `_word`
suffix. The font carries `_word`; the icon-bucket bookmark that drops it is the stale/wrong
name (RESEARCH A2 / D-15). Use the `_word` form.

USAGE (WSL python 3.13 — fonttools 4.63.0 already installed):
    python tools/verify_ligatures.py        # exit 0 = all needed names resolve; exit 1 = missing
"""

import sys

from fontTools.ttLib import TTFont

FONT = "app/src/main/res/font/material_symbols_outlined.ttf"


def resolvable_ligatures(path):
    """Return the set of glyph names reachable as a GSUB LigatureSubst output (LookupType 4)."""
    f = TTFont(path)
    gsub = f["GSUB"].table
    out = set()

    def subtables(lk):
        for st in lk.SubTable:
            if lk.LookupType == 7:  # Extension — unwrap to the real lookup type + subtable
                yield st.ExtensionLookupType, st.ExtSubTable
            else:
                yield lk.LookupType, st

    for lk in gsub.LookupList.Lookup:
        for lt, st in subtables(lk):
            if lt == 4:  # LigatureSubst
                for _first, ligs in st.ligatures.items():
                    for lg in ligs:
                        out.add(lg.LigGlyph)
    return out


# Every IconRef.Ligature(...) name currently in DinghyIcons + every D-08 conversion target.
NEEDED = {
    # Spool filter-picker Clear (owner-assigned, 2026-06-12 wide pass):
    "delete_sweep",
    # current IconRef.Ligature entries
    "arrow_back", "check", "edit", "instant_mix", "height", "altitude", "layers", "scale",
    "storefront", "timer_arrow_down", "timer_arrow_up", "donut_large", "pause_circle",
    "inventory_2", "palette", "calendar_add_on", "check_circle", "archive", "print_connect",
    "thermostat", "open_with", "output_circle", "tune", "bolt", "terminal",
    "more_horiz", "speed", "keyboard_return", "arrow_shape_up_stack_2", "sprint",
    "directions_boat", "rounded_corner", "avg_time", "input_circle",
    # D-08 conversion targets (drawables flipping to ligatures in later 18.1 waves)
    "disabled_by_default", "warning", "compress", "expand", "detector", "lock",
    "lock_open_right", "add", "remove", "arrow_upward", "arrow_downward",
    "text_select_move_forward_word",
    # Phase-19 D-09 gate: the 7 owner-locked output glyphs (D-01..D-07) + `air` (the D-08
    # part-cooling-fan reassignment target). `mode_fan` was removed above — no IconRef.Ligature
    # references it after the D-08 reassignment. All 8 verified resolvable in the v2.944 ttf (19-RESEARCH).
    "mode_heat", "mode_fan_2", "lightbulb_2", "cyclone", "check_box", "vital_signs", "output", "air",
    # Phase-20 (System Information page) — the owner-locked D-01..D-10 slate. `thermostat` (D-03)
    # and `speed` (D-09) are ALREADY in NEEDED above (LauncherTemperature / the speed entry) — the
    # gate de-dups on the ligature string, so they are NOT re-added here. The 8 below are new-to-NEEDED.
    # All 10 confirmed resolvable in the v2.944 bundled ttf (20-RESEARCH Icon Gate, D-11).
    "pulse_alert", "dns", "schedule", "developer_board", "memory",
    "deployed_code", "code_blocks", "data_usage",
    # Phase-23 (Design-Language Foundation) — the 6 new owner-assigned redesign ligatures
    # (icon-assignments-redesign.md / 23-RESEARCH Icon Registry Changes). A1 gate discharged here.
    # sort=sort-control-row leader, filter_list=filter-row leader,
    # expand_circle_up=Load spool (replaces old play_circle bucket note),
    # expand_circle_down=Unload spool (replaces old stop_circle bucket note),
    # reset_wrench=reset-single-setting, reset_settings=reset-all-settings.
    # All 6 verified resolvable in the v2.944 bundled ttf (23-01 Wave-0 gate).
    "sort", "filter_list", "expand_circle_up", "expand_circle_down",
    "reset_wrench", "reset_settings",
    # Phase-23 plan 06 (SpoolScreen pilot) — 5 owner-assigned glyphs from img/material-icon-bucket.json.
    # match_case=sort-by-name, calendar_clock=sort-by-date, experiment=filter-by-material-type,
    # home=Home foot button (navigate to PrintStatus), qr_code=Scan foot button.
    # All 5 verified resolvable in the v2.944 bundled ttf (23-06 Task-1 gate).
    "match_case", "calendar_clock", "experiment", "home", "qr_code",
    # Phase-24 plan 02 (morphing-root idle glyph registry) — 3 owner-confirmed glyphs.
    # videocam=idle Webcam row (LauncherWebcam), chair_fireplace=idle Preheat foot button (FootPreheat),
    # bottom_panel_open=idle System foot button (FootSystem, D-09 neutral — NOT Power/red).
    # All 3 owner-confirmed 2026-06-10 and verified resolvable in the v2.944 bundled ttf (24-02 Task-2 gate).
    "videocam", "chair_fireplace", "bottom_panel_open",
    # Phase-25 plan 02 (browse-screen token registry, D-20/D-21) — 9 owner-assigned glyphs.
    # Files: print=start-a-print (bucket), delete=delete-file (bucket).
    # Console filters (hide-state): mode_heat_off=hide-temps (bucket), video_camera_back=hide-timelapse (bucket),
    # chat_error=hide-prompts (bucket).
    # Macros: code=section-leader (bucket "macros"), bookmark_manager=manage-mode foot button (bucket).
    # play_arrow=Execute macro foot button (owner-approved new entry 2026-06-10, not in bucket).
    # radio_button_unchecked=unbookmarked trailing affordance (owner-approved 2026-06-10, existing code ligature).
    # All 9 verified resolvable in the v2.944 bundled ttf (25-02 Task-2 gate).
    "print", "delete", "mode_heat_off", "video_camera_back", "chat_error",
    "code", "bookmark_manager", "play_arrow", "radio_button_unchecked",
    # Phase-25 review fix WR-07: the Macros Show-hidden toggle pair, carried forward verbatim from
    # the old SystemMacrosScreen and now registered in DinghyIcons (Visibility/VisibilityOff).
    # ("warning" — the SpoolWarningGuard glyph, now also registered — is already in the D-08 list above.)
    "visibility", "visibility_off",
    # Phase-27 plan 01 (calibration hub routine tokens) — 5 owner-blessed ligatures promoted from raw
    # MaterialSymbol(name=...) usage in CalibrationHubScreen to registry tokens. Owner confirmed
    # 2026-06-12: "Bless all 5 as-is." All 5 already present in the v2.944 bundled ttf (P18.1 gate).
    # RoutineProbeCalibrate=straighten, RoutineBedMesh=grid_on, RoutineScrewsTilt=architecture,
    # RoutineZTilt=vertical_align_center, RoutineQgl=crop_square.
    "straighten", "grid_on", "architecture", "vertical_align_center", "crop_square",
    # Phase-27 review fix WR-04: the 9 glyphs the rebuilt Move/ScrewsTilt/BedMesh screens were
    # drawing as raw, un-registered ligatures — promoted VERBATIM to DinghyIcons tokens
    # (JogXPlus / HomeStateHomed / HomeStateUnhomed / Screw* / MeshEmpty). Shipping glyphs
    # preserved unchanged; this is drift-guarding, not icon selection.
    "arrow_forward", "in_home_mode", "wifi_home", "point_scan", "anchor", "commit",
    "rotate_left", "rotate_right", "grid_off",
    # Phase-28 plan 01 (System/Settings cluster icon-registry foundation, D-21 OWNER-LOCKED).
    # Six new tokens for the System page rows and the WaterfallHome idle-list Fine-Tune row.
    # `palette` is ALREADY in NEEDED above (Spool detail Palette token) — de-dup applies, skip.
    # All 5 below verified resolvable in the v2.944 bundled ttf (28-01 Task-1 gate).
    "line_style", "android_wifi_3_bar_plus", "settings", "info", "power_settings_new",
}


def main():
    have = resolvable_ligatures(FONT)
    missing = sorted(NEEDED - have)
    print(f"{len(NEEDED)} needed, {len(have)} ligatures in font, missing: {missing}")
    sys.exit(1 if missing else 0)


if __name__ == "__main__":
    main()
