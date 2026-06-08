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
    # current IconRef.Ligature entries
    "arrow_back", "check", "edit", "instant_mix", "height", "altitude", "layers", "scale",
    "storefront", "timer_arrow_down", "timer_arrow_up", "donut_large", "pause_circle",
    "inventory_2", "palette", "calendar_add_on", "check_circle", "archive", "print_connect",
    "thermostat", "open_with", "output_circle", "tune", "database", "bolt", "terminal",
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
}


def main():
    have = resolvable_ligatures(FONT)
    missing = sorted(NEEDED - have)
    print(f"{len(NEEDED)} needed, {len(have)} ligatures in font, missing: {missing}")
    sys.exit(1 if missing else 0)


if __name__ == "__main__":
    main()
