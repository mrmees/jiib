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
    # --- owner-curated additions ---
    "aldrich_regular",
    "arvo_regular", "arvo_bold",
    "asimovian_regular",
    "bakbak_one_regular",
    "carlito_regular", "carlito_bold",
    "faustina_regular", "faustina_medium", "faustina_semibold", "faustina_bold",
    "genos_regular", "genos_medium", "genos_semibold", "genos_bold",
    "goldman_regular", "goldman_bold",
    "kanit_regular", "kanit_medium", "kanit_semibold", "kanit_bold",
    "noto_sans_regular", "noto_sans_medium", "noto_sans_semibold", "noto_sans_bold",
    "oxanium_regular", "oxanium_medium", "oxanium_semibold", "oxanium_bold",
    "rasa_regular", "rasa_medium", "rasa_semibold", "rasa_bold",
    "roboto_regular", "roboto_medium", "roboto_bold",
    "sarpanch_regular", "sarpanch_bold",
    "zilla_slab_regular", "zilla_slab_medium", "zilla_slab_semibold", "zilla_slab_bold",
]
DATA_TTFS = [
    "geist_mono_medium", "geist_mono_semibold",
    # --- owner-curated additions ---
    "anonymous_pro_medium", "anonymous_pro_semibold",
    "cascadia_code_medium", "cascadia_code_semibold",
    "courier_prime_medium", "courier_prime_semibold",
    "datatype_medium", "datatype_semibold",
    "kode_mono_medium", "kode_mono_semibold",
    "m_plus_1_code_medium", "m_plus_1_code_semibold",
    "nova_mono_medium",
    "share_tech_mono_medium",
    "space_mono_medium", "space_mono_semibold",
]

DIGITS = "0123456789"
DATA_GLYPHS = DIGITS + ".-:/ °"
UI_GLYPHS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789 .,:-%"

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
