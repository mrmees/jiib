#!/usr/bin/env python3
"""
jiib — bundled-font gate. Device-free precondition for shipping a selectable font (companion to
verify_ligatures.py). DATA faces MUST be monospaced (uniform digit advance — the tabular-numerics
guarantee) and carry the data glyph set; UI faces MUST carry basic Latin. A non-empty failure list
exits 1 and blocks the bundle. Keep DATA_TTFS/UI_TTFS in sync with FontCatalog.kt.

USAGE (WSL python 3.13, fonttools installed):  python tools/verify_fonts.py
"""
import sys
from fontTools.ttLib import TTFont

FONT_DIR = "app/src/main/res/font"

# Keep in sync with FontCatalog.kt (dual-update, like docs/commands/catalog.json).
UI_TTFS = [
    "geist_regular", "geist_medium", "geist_semibold", "geist_bold",
    "inter_regular", "inter_medium", "inter_semibold", "inter_bold",
    "ibm_plex_sans_regular", "ibm_plex_sans_medium", "ibm_plex_sans_semibold", "ibm_plex_sans_bold",
    "source_sans_3_regular", "source_sans_3_semibold", "source_sans_3_bold",
    "rubik_regular", "rubik_medium", "rubik_semibold", "rubik_bold",
    "work_sans_regular", "work_sans_medium", "work_sans_semibold", "work_sans_bold",
    "dm_sans_regular", "dm_sans_medium", "dm_sans_bold",
    "manrope_regular", "manrope_medium", "manrope_semibold", "manrope_bold",
    "nunito_sans_regular", "nunito_sans_semibold", "nunito_sans_bold",
    "open_sans_regular", "open_sans_semibold", "open_sans_bold",
    "atkinson_hyperlegible_regular", "atkinson_hyperlegible_bold",
    "lexend_regular", "lexend_medium", "lexend_semibold", "lexend_bold",
]
DATA_TTFS = [
    "geist_mono_medium", "geist_mono_semibold",
    "jetbrains_mono_medium", "jetbrains_mono_semibold",
    "ibm_plex_mono_medium", "ibm_plex_mono_semibold",
    "source_code_pro_medium", "source_code_pro_semibold",
    "roboto_mono_medium", "roboto_mono_semibold",
    "space_mono_medium", "space_mono_semibold",
    "inconsolata_medium", "inconsolata_semibold",
    "dejavu_sans_mono_medium", "dejavu_sans_mono_semibold",
    "overpass_mono_medium", "overpass_mono_semibold",
]

DIGITS = "0123456789"
DATA_GLYPHS = DIGITS + ".-:/ °"
UI_GLYPHS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789 .,:-%°"

def cmap(path):
    return TTFont(path).getBestCmap()

def advances(path, chars):
    f = TTFont(path)
    cm = f.getBestCmap()
    hmtx = f["hmtx"]
    out = {}
    for ch in chars:
        gid = cm.get(ord(ch))
        if gid is None:
            return None  # missing glyph
        out[ch] = hmtx[gid][0]
    return out

def missing(path, glyphs):
    cm = cmap(path)
    return [c for c in glyphs if ord(c) not in cm]

def main():
    fails = []
    for base in DATA_TTFS:
        p = f"{FONT_DIR}/{base}.ttf"
        miss = missing(p, DATA_GLYPHS)
        if miss:
            fails.append(f"{base}: missing data glyphs {miss!r}")
        adv = advances(p, DIGITS)
        if adv is None:
            fails.append(f"{base}: missing a digit glyph")
        elif len(set(adv.values())) != 1:
            fails.append(f"{base}: NOT monospaced across digits {adv}")
    for base in UI_TTFS:
        p = f"{FONT_DIR}/{base}.ttf"
        miss = missing(p, UI_GLYPHS)
        if miss:
            fails.append(f"{base}: missing UI glyphs {miss!r}")
    if fails:
        print("FONT GATE FAILED:")
        for f in fails:
            print("  " + f)
        sys.exit(1)
    print(f"OK: {len(UI_TTFS)} UI + {len(DATA_TTFS)} data faces pass.")

if __name__ == "__main__":
    main()
