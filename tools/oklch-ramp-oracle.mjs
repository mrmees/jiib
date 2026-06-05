#!/usr/bin/env node
// oklch-ramp-oracle.mjs — INDEPENDENT off-line OKLCH→sRGB oracle for the bed-mesh sequential ramp.
//
// This script is the source-of-truth generator for app/src/test/resources/oklch-ramp-golden.json.
// It is the analog of how color-golden.json derives from ../theme_theory/app/color.js: the Kotlin
// side (plan 15.1-03's Palette.bakeRamp / OklchRamp) asserts AGAINST this fixture bit-for-bit and
// NEVER regenerates it from Kotlin output. If the ramp endpoints ever change, edit the control
// points below and re-run this script to regenerate the fixture.
//
// The OKLCH↔sRGB math here mirrors works.mees.dinghy.theme.Palette VERBATIM (the same OKLab 3×3
// matrices, the same sRGB transfer fns, the same round-to-8-bit quantization, the same 20-iter
// chroma-reduction gamut clamp). Any divergence would defeat the golden test's whole point.
//
// Usage:
//   node tools/oklch-ramp-oracle.mjs            # prints the 32-stop ramp, writes the fixture
//   node tools/oklch-ramp-oracle.mjs --print    # prints only, does not write
//
// Ramp spec (LOCKED — see the plan 15.1-01 Task 2 parameterization):
//   - 32 stops, index 0..31, endpoints INCLUSIVE (stop 0 == low CP, stop 31 == high CP exactly).
//   - THREE OKLCH control points: low oklch(0.45 0.12 255) → mid oklch(0.65 0.13 150)
//     → high oklch(0.85 0.15 95)  (viridis/cividis-style blue→teal→yellow, off pure red/green).
//   - PIECEWISE-LINEAR in OKLCH across TWO segments split at the MID control point, which is
//     anchored at stop index 15: segment A = stops 0..15 lerp low→mid; segment B = stops 15..31
//     lerp mid→high. Hue H along the SHORTER arc within each segment; L and C linear.
//   - Hex format: lowercase #rrggbb, 6 digits, no alpha.

import { writeFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";

// ---------- sRGB <-> OKLCH (mirrors Palette.kt) ----------
const clamp01 = (x) => Math.min(1, Math.max(0, x));

const lToS = (c) =>
  c <= 0.0031308 ? 12.92 * c : 1.055 * Math.pow(c, 1.0 / 2.4) - 0.055;

// OKLab -> linear-sRGB (3×3 matrices copied VERBATIM from color.js / Palette.kt).
function labToLin(L, a, b) {
  const lr = L + 0.3963377774 * a + 0.2158037573 * b;
  const mr = L - 0.1055613458 * a - 0.0638541728 * b;
  const sr = L - 0.0894841775 * a - 1.2914855480 * b;
  const l = lr * lr * lr;
  const m = mr * mr * mr;
  const s = sr * sr * sr;
  return [
    4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s,
    -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s,
    -0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s,
  ];
}

function inGamut(L, C, H) {
  const a = C * Math.cos((H * Math.PI) / 180.0);
  const b = C * Math.sin((H * Math.PI) / 180.0);
  const lin = labToLin(L, a, b);
  return lin.every((v) => v >= -0.0002 && v <= 1.0002);
}

function rgbToHex(r, g, b) {
  const f = (x) =>
    Math.round(clamp01(x) * 255.0)
      .toString(16)
      .padStart(2, "0");
  return "#" + f(r) + f(g) + f(b);
}

// OKLCH -> sRGB hex, reducing chroma (20-iter binary search) until in gamut. Mirrors Palette.oklchToHex.
function oklchToHex(L, C, H) {
  let c = C;
  if (!inGamut(L, c, H)) {
    let lo = 0.0;
    let hi = C;
    for (let i = 0; i < 20; i++) {
      const mid = (lo + hi) / 2.0;
      if (inGamut(L, mid, H)) lo = mid;
      else hi = mid;
    }
    c = lo;
  }
  const a = c * Math.cos((H * Math.PI) / 180.0);
  const b = c * Math.sin((H * Math.PI) / 180.0);
  const lin = labToLin(L, a, b).map(lToS);
  return rgbToHex(lin[0], lin[1], lin[2]);
}

// ---------- Ramp interpolation ----------
// Shorter-arc hue lerp (degrees).
function lerpHueShortArc(h0, h1, t) {
  let d = ((h1 - h0) % 360.0 + 540.0) % 360.0 - 180.0; // signed delta in (-180,180]
  let h = h0 + d * t;
  h = ((h % 360.0) + 360.0) % 360.0;
  return h;
}

function lerp(a, b, t) {
  return a + (b - a) * t;
}

// LOCKED control points.
const LOW = { L: 0.45, C: 0.12, H: 255.0 };
const MID = { L: 0.65, C: 0.13, H: 150.0 };
const HIGH = { L: 0.85, C: 0.15, H: 95.0 };
const STOPS = 32;
const MID_INDEX = 15; // mid anchored at the lower segment's far endpoint

function rampStop(i) {
  // Two piecewise-linear segments split at MID_INDEX.
  let lo, hi, t;
  if (i <= MID_INDEX) {
    lo = LOW;
    hi = MID;
    t = i / MID_INDEX; // 0 at stop 0, 1 at stop 15
  } else {
    lo = MID;
    hi = HIGH;
    t = (i - MID_INDEX) / (STOPS - 1 - MID_INDEX); // 0 at stop 15, 1 at stop 31
  }
  const L = lerp(lo.L, hi.L, t);
  const C = lerp(lo.C, hi.C, t);
  const H = lerpHueShortArc(lo.H, hi.H, t);
  return oklchToHex(L, C, H);
}

const ramp = Array.from({ length: STOPS }, (_, i) => rampStop(i));

const fixture = {
  _meta: {
    oracle: "tools/oklch-ramp-oracle.mjs",
    command: "node tools/oklch-ramp-oracle.mjs",
    controlPoints: {
      low: { L: LOW.L, C: LOW.C, H: LOW.H },
      mid: { L: MID.L, C: MID.C, H: MID.H },
      high: { L: HIGH.L, C: HIGH.C, H: HIGH.H },
    },
    stops: STOPS,
    midIndex: MID_INDEX,
    interpolation:
      "piecewise-linear in OKLCH, two segments split at midIndex (low->mid over stops 0..15, mid->high over stops 15..31); hue along shorter arc per segment; L and C linear",
    hexFormat: "#rrggbb",
    endpointsInclusive: true,
  },
  ramp,
};

const printOnly = process.argv.includes("--print");

ramp.forEach((hex, i) => console.log(`${String(i).padStart(2, " ")}  ${hex}`));

if (!printOnly) {
  const here = dirname(fileURLToPath(import.meta.url));
  const out = join(here, "..", "app", "src", "test", "resources", "oklch-ramp-golden.json");
  writeFileSync(out, JSON.stringify(fixture, null, 2) + "\n");
  console.error(`wrote ${out}`);
}
