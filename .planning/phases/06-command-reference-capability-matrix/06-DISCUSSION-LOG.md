# Phase 6: Command Reference & Capability Matrix - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-06-01
**Phase:** 6-Command Reference & Capability Matrix
**Areas discussed:** Catalog breadth, Registry shape, Matrix form, Drift prevention

---

## Catalog breadth

### How wide should the docs command catalog go?
| Option | Description | Selected |
|--------|-------------|----------|
| Roadmap-scoped (Recommended) | Document only the v1-roadmap subset (used set + phases 7-14) | |
| Comprehensive reference | Enumerate the broader Klipper/Moonraker surface even where unused | ✓ |
| Used-only, minimal | Only commands the app already sends through Phase 5 | |

**User's choice:** Comprehensive reference.
**Notes:** Claude flagged that "comprehensive Klipper config" is an infinite read; bounded in the next question.

### Comprehensive across which surfaces? (where's the edge)
| Option | Description | Selected |
|--------|-------------|----------|
| The 3 named APIs, full (Recommended) | Full Klipper G-Code command ref + full Moonraker API + full Spoolman API; NOT config-module options | ✓ |
| + Klipper config modules too | Above plus every Klipper `[section]` and its parameters | |
| G-Code + Moonraker only | Defer Spoolman to Phase 11 | |

**User's choice:** The 3 named APIs, full.
**Notes:** Bounds "comprehensive" to the universe of commands you can actually SEND — finishable.

### What depth does each catalog entry get?
| Option | Description | Selected |
|--------|-------------|----------|
| Tiered depth (Recommended) | Full semantics for the sent subset; light (purpose+params+URL) for the rest | ✓ |
| Uniform full detail | Every command gets full purpose/params/semantics | |
| Uniform light | Every entry purpose+params+URL only | |

**User's choice:** Tiered depth.

### How should the catalog be organized on disk?
| Option | Description | Selected |
|--------|-------------|----------|
| Per-API files + stable IDs (Recommended) | Split into `docs/commands/` per API; each command gets a stable catalog ID the registry references | ✓ |
| One big catalog file | Single `docs/command-catalog.md` | |
| You decide | Let planner pick layout | |

**User's choice:** Per-API files + stable IDs.

---

## Registry shape

### What is the canonical in-code registry, structurally?
| Option | Description | Selected |
|--------|-------------|----------|
| Data-driven Command model (Recommended) | One Command data class per command (ID, transport, builder, doc-cited semantics); JsonRpcMethods + PrinterCommands fold in | ✓ |
| Consolidated constants + builders | Keep the two homes, add a thin lookup; no Command model | |
| You decide | Let research/planning pick | |

**User's choice:** Data-driven Command model.

### How aggressively should Phase 1-5 code migrate onto the registry?
| Option | Description | Selected |
|--------|-------------|----------|
| Re-point, don't rewrite (Recommended) | Registry is canonical but Phase 1-5 call sites + typed builders stay intact | |
| Full refactor of call sites | Rewrite Phase 1-5 call sites to dispatch via the registry uniformly | ✓ |
| Registry-forward only | Create registry, don't touch Phase 1-5 (two sources of truth) | |

**User's choice:** Full refactor of call sites.
**Notes:** Claude flagged this touches the on-device-verified spine → on-device flox re-verification is now mandatory (captured as D-05/D-12).

### Where do the Phase-5 clamps + mode-safety live after the full refactor?
| Option | Description | Selected |
|--------|-------------|----------|
| Registry wraps the typed builders (Recommended) | Command entries reference the existing clamped builders verbatim; safety provably unchanged | ✓ |
| Clamps become entry metadata | Move bounds into the Command entry; generic builder re-implements clamping | |
| You decide | Planning chooses with a byte-identical-gcode constraint | |

**User's choice:** Registry wraps the typed builders.
**Notes:** Preserves the ASVS-V5 clamping + SAVE/RESTORE discipline; byte-identical-gcode tests prove it (D-11).

---

## Matrix form

### Is the availability matrix a doc, runtime data, or both?
| Option | Description | Selected |
|--------|-------------|----------|
| Doc reference; runtime stays live (Recommended) | Committed doc is dev-facing; runtime gating stays live via deriveCapabilities; extend the live surface | ✓ |
| Baked matrix shipped in-app | Ship static per-printer data (only knows the two test rigs) | |
| Both: live primary, doc as fixture | Live gating + committed captures as test fixtures | |

**User's choice:** Doc reference; runtime stays live.
**Notes:** Keeps the "point it at any Moonraker" promise — gating works on any user's printer.

### How does a catalog command connect to a live availability check?
| Option | Description | Selected |
|--------|-------------|----------|
| Each command carries a gating predicate (Recommended) | Printer-conditional commands record what proves them available (object/macro presence) | ✓ |
| Matrix is a presence table only | Yes/no grid; later phases re-invent the command→object logic | |
| You decide | Let research determine the mapping | |

**User's choice:** Each command carries a gating predicate.

### How should the live Capabilities surface grow to support arbitrary predicates?
| Option | Description | Selected |
|--------|-------------|----------|
| Retain raw object-name set + helpers (Recommended) | Keep the full objects.list Set<String> + hasObject() so any predicate is evaluable without pre-modeling | ✓ |
| Add typed fields per capability | Add explicit hasQGL/hasZTilt/… fields per gated command | |
| You decide | Let planning pick | |

**User's choice:** Retain raw object-name set + helpers.

---

## Drift prevention

### How should catalog ↔ registry ↔ matrix consistency be enforced?
| Option | Description | Selected |
|--------|-------------|----------|
| Host test asserts registry↔catalog (Recommended) | CI host test: every registry command has a catalog ID; every predicate names an object in the live captures (or marked not-on-our-printers) | ✓ |
| Test + live re-introspection check | Above plus a re-runnable real-printer diff (not CI-friendly) | |
| Documentation discipline only | No enforcing test | |

**User's choice:** Host test asserts registry↔catalog.
**Notes:** Direct answer to the project's twice-logged mock-vs-reality bug class. Live re-introspection kept as a manual tool (deferred).

### What's the done-definition / verification gate for this phase?
| Option | Description | Selected |
|--------|-------------|----------|
| Artifacts + green CI + flox regression (Recommended) | Three artifacts + drift/byte-identical tests green + on-device flox pass that refactored Phase-5 panels behave identically | ✓ |
| Artifacts + green CI only | Trust host tests, skip on-device | |
| You decide | Let verify-phase derive it | |

**User's choice:** Artifacts + green CI + flox regression.

---

## Claude's Discretion
- Exact `Command` data-class field shape and the registry's "register into" API (honoring D-04/D-06).
- The precise live-introspection method for enumerating available gcode commands (config-object
  presence is the practical signal — research to confirm).
- Catalog file naming/anchoring details beyond per-API files + stable IDs.

## Deferred Ideas
- Live re-introspection diff as a scheduled/manual real-printer check (not the CI gate).
- Klipper config-module ([section]) parameter reference — explicitly out of scope for the catalog.
- Building Spoolman-backed spool management — catalogued now (reference-only), built in Phase 11.
