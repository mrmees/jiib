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
UI_TTFS = ["geist_regular", "geist_medium", "geist_semibold", "geist_bold"]
DATA_TTFS = ["geist_mono_medium", "geist_mono_semibold"]

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
