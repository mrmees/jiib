#!/usr/bin/env bash
set -euo pipefail

repo_root=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
cd "$repo_root"

mode=all
case "${1:-}" in
  "") ;;
  --docs-only) mode=docs ;;
  --gradle-only) mode=gradle ;;
  -h|--help)
    printf '%s\n' \
      'Usage: ./scripts/check.sh [--docs-only|--gradle-only]' \
      '  no option      Run documentation hygiene and the Gradle CI gate.' \
      '  --docs-only    Check tracked Markdown links, stale phrases, and instruction pointers.' \
      '  --gradle-only  Run unit tests, lint, and the debug build.'
    exit 0
    ;;
  *)
    printf 'Unknown option: %s\n' "$1" >&2
    exit 2
    ;;
esac

check_docs() {
  local stale_pattern='compileSdk 35|Architecture not yet mapped'
  local dead_links
  local src dir target
  local -a sources instruction_files

  mapfile -t sources < <(git ls-files '*.md')
  if ((${#sources[@]} == 0)); then
    printf '%s\n' 'FAIL: no tracked Markdown files found' >&2
    return 1
  fi

  if grep -En "$stale_pattern" \
    AGENTS.md README.md docs/maintainer/*.md docs/ui_design/*.md; then
    printf '%s\n' 'FAIL: stale phrase found in authoritative documentation' >&2
    return 1
  fi

  dead_links=$(mktemp)
  trap 'rm -f "$dead_links"' RETURN
  for src in "${sources[@]}"; do
    [[ -f "$src" ]] || continue
    dir=$(dirname "$src")
    while IFS= read -r target; do
      case "$target" in
        http://*|https://*|*://*|/*|[A-Za-z]:/*|*\\*) continue ;;
      esac
      if [[ ! -e "$dir/$target" && ! -e "$target" ]]; then
        printf 'DEAD LINK in %s -> %s\n' "$src" "$target" | tee -a "$dead_links"
      fi
    done < <(
      { grep -oE '\]\([^)# ]+\.md(#[^)]*)?\)' "$src" || true; } \
        | sed -E 's/^\]\(//; s/#[^)]*//; s/\)$//' \
        | sort -u
    )
  done
  if [[ -s "$dead_links" ]]; then
    printf '%s\n' 'FAIL: dead internal Markdown links found' >&2
    return 1
  fi

  mapfile -t instruction_files < <(git ls-files 'CLAUDE.md' ':(glob)**/CLAUDE.md')
  for src in "${instruction_files[@]}"; do
    if (( $(wc -l < "$src") > 10 )) || ! grep -q 'AGENTS\.md' "$src"; then
      printf 'FAIL: %s must remain a short pointer to its AGENTS.md\n' "$src" >&2
      return 1
    fi
  done

  printf '%s\n' 'OK: documentation hygiene'
}

check_gradle() {
  local -a tasks=(
    :app:testDebugUnitTest
    :app:lintDebug
    :app:assembleDebug
    --no-daemon
  )

  if [[ -r /proc/version ]] \
    && grep -qi microsoft /proc/version \
    && [[ -x /mnt/c/Windows/System32/cmd.exe ]] \
    && [[ -f /mnt/e/Android/gw.bat ]]; then
    /mnt/c/Windows/System32/cmd.exe /c \
      'E:\Android\gw.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --no-daemon'
  else
    ./gradlew "${tasks[@]}"
  fi
}

if [[ "$mode" == all || "$mode" == docs ]]; then
  check_docs
fi
if [[ "$mode" == all || "$mode" == gradle ]]; then
  check_gradle
fi
