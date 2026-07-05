# Maintainer Testing

This document describes the verification surface for current jiib maintenance work.

## CI Gate

GitHub Actions runs on pushes to `main` and `gsd/**`, and on pull requests to `main`. The build job runs:

```bash
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --no-daemon
```

The hygiene job checks `README.md`, top-level tracked `docs/*.md`, and tracked
`docs/maintainer/*.md` for stale phrases and dead internal Markdown links. It intentionally excludes
local-only ignored trees such as `.planning/`, `docs/commands/`, and `docs/superpowers/`.

If `.github/workflows/ci.yml` does not target `main`, fix CI before treating this gate as active.

## Local Build Environment

The project is developed under WSL, but local Android builds normally run through the Windows-side
helper so adb can reach physical devices.

Use this command shape from the repo root:

```bash
/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat <gradle args>"
```

Examples:

```bash
/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --no-daemon"
/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:lintDebug --no-daemon"
/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:assembleDebug --no-daemon"
```

CI uses Linux and the checked-in Gradle wrapper directly.

## Host Unit Tests

Host tests live under `app/src/test/java/works/mees/jiib/`. They cover most pure logic, reducers,
holders, command building, parser behavior, fixtures, and protocol edge cases.

Common targeted run:

```bash
/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --tests works.mees.jiib.state.PrinterStateReducerTest --no-daemon"
```

Run the full host suite when changing shared state, command contracts, parser behavior, or holder logic:

```bash
/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --no-daemon"
```

## Instrumented And Device Tests

Instrumented tests live under `app/src/androidTest/java/works/mees/jiib/`. They cover lifecycle,
service survival, live/reconnect smoke, camera/webcam surfaces, and UI behavior that cannot be proven
reliably on the JVM.

Run device tests only when the change affects Android lifecycle, camera, foreground service behavior,
View interop, or device-specific UI behavior:

```bash
/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:connectedAndroidTest --no-daemon"
```

Some device checks require a real printer or specific local hardware and are not autonomous CI gates.

## Fixtures And Goldens

Test resources live under `app/src/test/resources/`. Captured JSON and binary fixtures are used to keep
protocol and parser tests deterministic. `docs/commands/catalog.json` and `docs/commands/printer-matrix.json`
are tracked because `CommandCatalogDriftTest` reads them; the rest of `docs/commands/` is local-only
command evidence and capture material, gitignored and possibly absent from public clones.

When adding or changing a command contract, check:

- `app/src/main/java/works/mees/jiib/command/CommandRegistry.kt`
- `app/src/test/java/works/mees/jiib/command/CommandCatalogDriftTest.kt`
- the tracked `docs/commands/catalog.json` and `docs/commands/printer-matrix.json` fixtures
- additional local-only `docs/commands/` evidence, if available

## Docs-Only Changes

For changes limited to `docs/maintainer/*.md`, local-only `.planning/codebase/*.md` archive banners,
or README-style documentation:

1. Run internal-link checks for the files touched.
2. Run stale-term scans for old package/navigation names.
3. Do not run the full Android suite unless build files or source files changed.

Keep literal stale-term regexes in implementation plans or scripts that CI does not scan. Do not paste
old package names, old branch names, retired drawer class names, or old route enum member names into
tracked maintainer docs just to document the scan; that would make the docs fail the scan themselves.
Negative historical facts may name retired concepts when needed, but stale-term scans should either
allowlist those lines explicitly or the docs should avoid the exact tokens.

Suggested stale-phrase dry run:

```bash
mapfile -t sources < <(
  git ls-files README.md ':(glob)docs/*.md' ':(glob)docs/maintainer/*.md' \
    ':!:docs/top-down-audit-roadmap.md' \
    ':!:docs/superpowers/**' \
    ':!:docs/commands/**'
)
pattern=$(sed -n 's/.*grep -rn "\([^"]*\)" .*/\1/p' .github/workflows/ci.yml | head -1)
if [ -z "$pattern" ]; then
  echo "Unable to extract stale-phrase pattern from CI workflow"
  exit 1
fi
if ((${#sources[@]})) && grep -rn "$pattern" "${sources[@]}"; then
  exit 1
fi
echo OK
```

Expected: `OK`.

Suggested dead internal Markdown-link dry run:

```bash
rm -f /tmp/dead-links
mapfile -t sources < <(
  git ls-files README.md ':(glob)docs/*.md' ':(glob)docs/maintainer/*.md' \
    ':!:docs/top-down-audit-roadmap.md' \
    ':!:docs/superpowers/**' \
    ':!:docs/commands/**'
)
for src in "${sources[@]}"; do
  [ -f "$src" ] || continue
  dir=$(dirname "$src")
  { grep -oE '\]\([^)# ]+\.md(#[^)]*)?\)' "$src" 2>/dev/null || true; } \
    | sed -E 's/^\]\(//; s/#[^)]*//; s/\)$//' \
    | sort -u \
    | while read -r target; do
        case "$target" in
          http://*|https://*|*://*|/*|[A-Za-z]:/*|*'\'*) continue ;;
        esac
        if [ ! -e "$dir/$target" ] && [ ! -e "$target" ]; then
          echo "DEAD LINK in $src -> $target" | tee -a /tmp/dead-links
        fi
      done
done
if [ -s /tmp/dead-links ]; then
  exit 1
fi
echo OK
```

Expected: `OK`.

## Manual Review Still Matters

The app has device- and printer-dependent behavior that tests cannot fully model. For user-visible screen
changes, compare the behavior against [the manual](../manual/README.md), [the UI design system](../ui_design/README.md),
and relevant screenshots before shipping.
