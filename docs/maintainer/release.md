# Maintainer Release Runbook

This is the manual release checklist for jiib. It records the current safe procedure; it does not
automate release publishing.

## Version Fields

Release identity is set manually in `app/build.gradle.kts`:

```kotlin
val versionCandidate = "ALPHA"
val versionYear = 26
val versionMonth = 7
val versionDay = 3
val versionRelease = 1
```

The version name is:

```text
<CANDIDATE>-<yy>.<m>.<d>.<n>
```

The version code is derived from the date and same-day release number, with per-ABI offsets assigned
for release split APKs. Do not derive the version from the build clock; reproducible release metadata
depends on explicit values.

Base `versionCode` formula:

```text
((versionYear * 10000 + versionMonth * 100 + versionDay) * 100) + (versionRelease * 10)
```

Release split offsets:

- `armeabi-v7a`: base version code
- `arm64-v8a`: base version code + 1

The same-day release number is 1-based and should stay at or below 9 so the per-ABI offsets cannot
collide with adjacent releases.

## Signing

Release signing is configured from gitignored `local.properties` keys:

```properties
jiib.releaseStoreFile=<path-to-keystore>
jiib.releaseStorePassword=
jiib.releaseKeyAlias=jiib
jiib.releaseKeyPassword=
```

The password entries are intentionally blank in this example. Put real values only in the gitignored
`local.properties` file on the release machine.

Current maintainer-machine keystore location, if present:

```text
E:/Android/keystores/jiib-release.jks
```

Never commit the keystore, passwords, or credential-bearing `local.properties`.

## Keystore Backup Warning

The release keystore signs public APKs. Losing the keystore or its credentials orphans installed copies:
users would need to uninstall and reinstall instead of updating in place.

Before publishing a release, confirm:

- The release keystore exists at the expected path or the path in `local.properties`.
- A backup exists outside the working machine.
- The backup can be accessed by the project owner.
- No password or key material is committed to git.

## Build Release APKs

Run the release build from the repository root. The release machine must have `E:\Android\gw.bat`
available.

```bash
/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:assembleRelease --no-daemon"
```

Expected outputs are per-ABI APKs under:

```text
app/build/outputs/apk/release/
```

The app ships split APKs for:

- `armeabi-v7a`
- `arm64-v8a`

There is no universal APK.

## Verify Release Artifacts

`assembleRelease` can succeed without signing when release signing keys are absent, so artifact checks
are mandatory before publishing.

Before uploading release assets:

1. Confirm all four release signing keys in `local.properties` are present and non-empty.
2. Verify both split APKs are signed:

```bash
for apk in app/build/outputs/apk/release/*.apk; do
  apksigner verify --print-certs "$apk"
done
```

3. Inspect both APKs and confirm:
   - `applicationId` is `works.mees.jiib`.
   - `versionName` matches the intended release tag.
   - `armeabi-v7a` uses the base `versionCode`.
   - `arm64-v8a` uses base `versionCode + 1`.
   - Each APK contains only its expected native ABI.
   - No universal APK is present.
4. Record the tested source commit SHA:

```bash
git rev-parse HEAD
```

5. Generate SHA-256 checksums for the APKs and keep them with the release notes:

```bash
sha256sum app/build/outputs/apk/release/*.apk
```

## Smoke Before Publishing

Before uploading release assets:

1. Install and smoke both ABI APKs when hardware is available.
2. If only one ABI can be device-tested, inspect the other APK with the artifact checks above before publishing.
3. Launch the app.
4. Confirm the splash/startup path renders.
5. Confirm an existing or test printer profile can reach the expected connection state.
6. Check `README.md`, `docs/manual/README.md`, and `docs/screenshots/README.md` do not advertise a stale release tag or stale screenshot set.

## GitHub Release Checklist

1. Confirm working tree is clean.
2. Confirm version fields in `app/build.gradle.kts` match the intended tag.
3. Build release APKs.
4. Verify both release artifacts are signed and match the expected package, version, ABI, and checksum.
5. Install/smoke both split APKs when hardware is available.
6. Create and push the GitHub release tag from the tested commit SHA, or explicitly select that SHA in GitHub.
7. Upload both per-ABI APKs with clear names.
8. Include compatibility notes: Android 6.0+, no Play Services required, choose APK by CPU ABI.
9. Link the user manual and screenshot gallery.

## What Not To Do

- Do not publish unsigned release APKs as official release assets.
- Do not upload the keystore or signing credentials.
- Do not change package/application ID after public releases unless intentionally planning a reinstall-only break.
