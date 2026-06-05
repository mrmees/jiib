/*
 * tools/color-golden/dump.js — golden-vector oracle dump (Phase 15, plan 15-01).
 *
 * DEV-TIME REGENERABLE ORACLE — NOT a runtime/build dependency of the app.
 *
 * This script `require`s the sibling generator `../theme_theory/app/color.js`
 * (the owner-frozen color oracle, OUTSIDE this repo) and emits a pinned JSON
 * fixture `app/src/test/resources/color-golden.json`. The Kotlin port's golden
 * conformance tests (PaletteGoldenTest) load that committed fixture from the
 * test classpath and assert the Kotlin `Palette.generate` output matches it
 * bit-for-bit. The fixture is committed so CI never reaches across the sibling
 * boundary — the sibling is only touched here, by hand, at dev time, when the
 * golden vectors are regenerated.
 *
 * Run from the repo root:   node tools/color-golden/dump.js
 *
 * The canonical shipped config is statusFromPool:true (D-01) for every vector
 * (the JS default is false; D-01 ships the from-pool status language).
 */
'use strict';

const fs = require('fs');
const path = require('path');

// repo root = two dirs up from tools/color-golden/
const REPO_ROOT = path.resolve(__dirname, '..', '..');
// sibling oracle lives one level above the repo root (a sibling checkout).
const ORACLE = path.resolve(REPO_ROOT, '..', 'theme_theory', 'app', 'color.js');
const OUT = path.resolve(REPO_ROOT, 'app', 'src', 'test', 'resources', 'color-golden.json');

const Palette = require(ORACLE);

// Each vector echoes the opts it was generated with, plus the slice of the
// generator output the golden test pins. statusFromPool:true is the shipped
// canonical config (D-01) for ALL vectors.
const VECTORS = [
  { label: 'defaultDark', opts: { seedHex: '#3f78ff', dark: true, maxItems: 3, poolShift: 0, statusFromPool: true } },
  { label: 'defaultLight', opts: { seedHex: '#3f78ff', dark: false, maxItems: 3, poolShift: 0, statusFromPool: true } },
  { label: 'simple', opts: { seedHex: '#3f78ff', dark: true, maxItems: 3, poolShift: 0, statusFromPool: true, simple: true } },
  { label: 'highContrast', opts: { seedHex: '#3f78ff', dark: true, maxItems: 3, poolShift: 0, statusFromPool: true, highContrast: true } },
  { label: 'edgeRed', opts: { seedHex: '#e23a3a', dark: true, maxItems: 3, poolShift: 0, statusFromPool: true } },
  { label: 'edgeYellow', opts: { seedHex: '#c8b400', dark: true, maxItems: 3, poolShift: 0, statusFromPool: true } },
  { label: 'shift120', opts: { seedHex: '#3f78ff', dark: true, maxItems: 3, poolShift: 120, statusFromPool: true } },
];

// Project the full generator output down to the fields the golden test pins.
function project(p) {
  return {
    accent: p.theme.primary,
    secondary: p.theme.secondary,
    bg: p.surfaces.bg,
    surface: p.surfaces.surface,
    divider: p.surfaces.divider,
    text: p.surfaces.text,
    muted: p.surfaces.muted,
    pool: p.pool,
    poolHues: p.poolHues,
    poolRoles: p.poolRoles,
    directional: p.directional,
    status: p.status,
    minHueGap: p.minHueGap,
    poolShift: p.poolShift,
  };
}

const fixture = {};
for (const v of VECTORS) {
  const out = Palette.generate(v.opts);
  fixture[v.label] = Object.assign({ opts: v.opts }, project(out));
}

fs.mkdirSync(path.dirname(OUT), { recursive: true });
fs.writeFileSync(OUT, JSON.stringify(fixture, null, 2) + '\n');

console.log('wrote ' + path.relative(REPO_ROOT, OUT) + ' (' + Object.keys(fixture).length + ' vectors)');
