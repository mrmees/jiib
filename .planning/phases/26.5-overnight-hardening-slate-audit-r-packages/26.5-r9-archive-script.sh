#!/usr/bin/env bash
# =============================================================================
# 26.5-r9-archive-script.sh — R9 step-4 bulk archive (docs/top-down-audit-roadmap.md §R9 step 4)
#
# *** REVIEW BEFORE RUNNING — emitted by plan 26.5-02, NOT executed overnight ***
#
# This script was written as a REVIEWED ARTIFACT during the Phase 26.5 overnight
# run. Per the phase CONTEXT lock, R9 step 4 (bulk archive/deletions) is OWNER-
# GATED: the overnight executor wrote this script, self-tested its guards, and
# deliberately did NOT execute it and did NOT chmod +x it. Nothing was moved or
# deleted by plan 26.5-02.
#
# WHAT IT DOES (when the owner runs it with --execute):
#   1. DELETES (git rm) every *DISCUSSION-LOG* file under .planning/phases/
#      (31 files at authoring time; each self-declares "audit trail only, do
#      not use as input").
#   2. MOVES (git mv) shipped-phase CONTEXT / VALIDATION / VERIFICATION / REVIEW
#      files from .planning/phases/<phase>/ to .planning/archive/<phase>/.
#      PLAN/SUMMARY pairs are NEVER touched (code comments reference task IDs
#      like "13-05 Task 3").
#   3. SKIPS (with a warning) anything on the PROTECTED list below — the guard
#      function gates EVERY mv/rm in this script.
#
# DEFAULT MODE IS DRY-RUN. Without --execute it only prints what it would do.
#   ./26.5-r9-archive-script.sh              # dry-run (no mutations)
#   bash 26.5-r9-archive-script.sh --self-test  # guard self-test (no mutations)
#   bash 26.5-r9-archive-script.sh --execute    # the real thing (owner only)
#
# ⚠ 15.2-AUDIT.md LINK REMINDER (from §R9 step 4): 15.2-AUDIT.md is cited by
#   docs/ui_design/THEMING.md. It is on the protected list and will not move —
#   but if you ever DO relocate it, update the THEMING.md link in the same
#   commit.
# =============================================================================
set -euo pipefail

cd "$(git rev-parse --show-toplevel)"

MODE="dry-run"
case "${1:-}" in
  --execute)   MODE="execute" ;;
  --self-test) MODE="self-test" ;;
  "")          MODE="dry-run" ;;
  *) echo "usage: $0 [--execute|--self-test]" >&2; exit 2 ;;
esac

# -----------------------------------------------------------------------------
# PROTECTED LIST (§R9 step 4 — never move or delete; verbatim from the roadmap):
#   - phases/01/captures/*  (cited by ADR-0001)
#   - phases/13/captures/*  (cited by the cadence contract)
#   - 15.2-AUDIT.md         (cited by docs/ui_design/THEMING.md)
#   - PATTERNS 02/03/18     (foundational)
#   - docs/commands/spoolman-live-*.json + e3/e5 *.jsonl (consumed by the test
#     suite via FakeSpoolmanClient)
#   - ALL PLAN/SUMMARY pairs (referenced from code comments by task ID)
# Implemented as glob patterns matched against repo-relative paths.
# -----------------------------------------------------------------------------
PROTECTED=(
  ".planning/phases/01-*/captures/*"
  ".planning/phases/13-*/captures/*"
  ".planning/phases/15.2-*/15.2-AUDIT.md"
  ".planning/phases/02-*/02-PATTERNS.md"
  ".planning/phases/03-*/03-PATTERNS.md"
  ".planning/phases/18-preview-harness-tokenization-foundation/18-PATTERNS.md"
  "docs/commands/spoolman-live-*.json"
  "docs/commands/e3-saveconfig-capture.jsonl"
  "docs/commands/e5-saveconfig-capture.jsonl"
  ".planning/phases/*/*-PLAN.md"
  ".planning/phases/*/*-SUMMARY.md"
)

# The in-flight phase is never archived (it is still being executed/verified).
CURRENT_PHASE_DIR=".planning/phases/26.5-overnight-hardening-slate-audit-r-packages"

# guard <path> → returns 0 (protected: SKIP) or 1 (not protected: proceed).
# EVERY rm/mv below must pass its target through this function first.
guard() {
  local path="$1" pat
  for pat in "${PROTECTED[@]}"; do
    # shellcheck disable=SC2254  # intentional glob match
    case "$path" in
      $pat) echo "  [PROTECTED — skipped] $path (matches: $pat)" >&2; return 0 ;;
    esac
  done
  case "$path" in
    "$CURRENT_PHASE_DIR"/*) echo "  [IN-FLIGHT PHASE — skipped] $path" >&2; return 0 ;;
  esac
  return 1
}

# do_rm / do_mv — the ONLY mutation points. Both honor the guard and dry-run.
do_rm() {
  local f="$1"
  guard "$f" && return 0
  if [[ "$MODE" == "execute" ]]; then
    git rm -q "$f"
    echo "  [DELETED] $f"
  else
    echo "  [would delete] $f"
  fi
}

do_mv() {
  local f="$1" dest_dir="$2"
  guard "$f" && return 0
  if [[ "$MODE" == "execute" ]]; then
    mkdir -p "$dest_dir"
    git mv "$f" "$dest_dir/"
    echo "  [ARCHIVED] $f -> $dest_dir/"
  else
    echo "  [would archive] $f -> $dest_dir/"
  fi
}

# -----------------------------------------------------------------------------
# SELF-TEST MODE: assert the guard blocks every protected path that exists on
# disk, and assert ZERO mutations occurred (working tree unchanged). This is
# the "zero-deletions self-test" the plan requires — it proves the guards work
# without performing a single deletion.
# -----------------------------------------------------------------------------
if [[ "$MODE" == "self-test" ]]; then
  echo "== guard self-test =="
  BEFORE=$(git status --porcelain | sha1sum)
  fail=0
  # Expand each protected pattern; every real file it matches MUST be guarded.
  for pat in "${PROTECTED[@]}"; do
    # shellcheck disable=SC2086  # intentional glob expansion
    for f in $pat; do
      [[ -e "$f" ]] || continue
      if guard "$f" >/dev/null 2>&1; then
        echo "  OK   guard blocks: $f"
      else
        echo "  FAIL guard MISSED: $f" >&2
        fail=1
      fi
    done
  done
  AFTER=$(git status --porcelain | sha1sum)
  if [[ "$BEFORE" != "$AFTER" ]]; then
    echo "  FAIL: working tree CHANGED during self-test (must be zero mutations)" >&2
    fail=1
  else
    echo "  OK   zero deletions/mutations (working tree unchanged)"
  fi
  [[ "$fail" -eq 0 ]] && echo "SELF-TEST PASSED" || { echo "SELF-TEST FAILED" >&2; exit 1; }
  exit 0
fi

echo "== R9 step-4 bulk archive — mode: $MODE =="

# -----------------------------------------------------------------------------
# PASS 1: delete DISCUSSION-LOG files (self-declared audit-trail-only).
# -----------------------------------------------------------------------------
echo "-- pass 1: DISCUSSION-LOG deletions --"
while IFS= read -r f; do
  do_rm "$f"
done < <(find .planning/phases -name "*DISCUSSION-LOG*" -type f | sort)

# -----------------------------------------------------------------------------
# PASS 2: archive shipped-phase CONTEXT / VALIDATION / VERIFICATION / REVIEW
# files to .planning/archive/<phase>/. PLAN/SUMMARY stay (protected patterns).
# -----------------------------------------------------------------------------
echo "-- pass 2: shipped-phase doc archival --"
while IFS= read -r f; do
  phase_dir=$(basename "$(dirname "$f")")
  do_mv "$f" ".planning/archive/$phase_dir"
done < <(find .planning/phases -type f \( -name "*-CONTEXT.md" -o -name "*-VALIDATION.md" -o -name "*VERIFICATION*.md" -o -name "*-REVIEW.md" \) | sort)

if [[ "$MODE" == "execute" ]]; then
  echo "== done. Review 'git status' then commit. Reminder: nothing protected moved; =="
  echo "== if you later relocate 15.2-AUDIT.md, update docs/ui_design/THEMING.md's link. =="
else
  echo "== dry-run complete — NOTHING was changed. Re-run with --execute after review. =="
fi
