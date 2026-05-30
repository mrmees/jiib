// =============================================================================
// verify-min-sdk — PRECOMPILED SCRIPT PLUGIN (plugin id: "verify-min-sdk").
// The REAL PKG-02 control (RESEARCH #5, threats T-01-01 / T-01-02).
//
// WHY A PRECOMPILED PLUGIN (not apply(from=)): only a plugin compiled inside the
// `build-logic` included build (with AGP on its compile classpath) can use the
// typed AGP Variant API — SingleArtifact.MERGED_MANIFEST — AND resolve at runtime
// to the SAME AGP classloader the android plugin registered. A plain
// apply(from=) script can do neither.
//
// WHAT IT DOES: pinning gradle/libs.versions.toml is necessary but NOT sufficient
// — a transitive AAR can declare a higher <uses-sdk android:minSdkVersion> and
// AGP's manifest merger raises the MERGED minSdk to the max across app + deps.
// So the delivered control is a build assertion on the MERGED manifest. This
// plugin registers `verifyMinSdk<Variant>` tasks that read
// SingleArtifact.MERGED_MANIFEST, parse the merged minSdkVersion, and FAIL the
// build if it is not EXACTLY 23 (the Nexus 7 2013 floor). `verifyMinSdk` (the
// release alias) is wired into `check` as a CI gate.
//
// ADVERSARIAL PROOF (recorded in 01-01-SUMMARY.md): temporarily add a dependency
// whose published minSdk > 23; AGP's merger raises the merged minSdk and
// verifyMinSdk (and assembleRelease) fail non-zero. Removing it restores exit 0.
// =============================================================================

import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.variant.ApplicationAndroidComponentsExtension

// Locked floor — Nexus 7 2013 = API 23.
val expectedMinSdk = 23

val androidComponents = extensions.getByType(ApplicationAndroidComponentsExtension::class.java)

androidComponents.onVariants { variant ->
    val variantName = variant.name.replaceFirstChar { it.uppercase() }
    val mergedManifest = variant.artifacts.get(SingleArtifact.MERGED_MANIFEST)

    val verifyTask = tasks.register("verifyMinSdk$variantName") {
        group = "verification"
        description =
            "Asserts the MERGED-manifest minSdkVersion of the ${variant.name} variant == $expectedMinSdk (PKG-02)."

        // Declare the merged manifest as an input → AGP produces it first, and the
        // task reruns when it changes.
        inputs.file(mergedManifest).withPropertyName("mergedManifest")

        doLast {
            val manifestFile = mergedManifest.get().asFile
            if (!manifestFile.exists()) {
                throw GradleException("verifyMinSdk: merged manifest not found at ${manifestFile.absolutePath}")
            }
            val text = manifestFile.readText()

            // Parse <uses-sdk android:minSdkVersion="NN"/>; require the numeric floor 23.
            val match = Regex("""android:minSdkVersion="(\d+)"""").find(text)
                ?: throw GradleException(
                    "verifyMinSdk: could not find android:minSdkVersion in the merged manifest " +
                        "(${manifestFile.absolutePath}). PKG-02 cannot be asserted."
                )

            val mergedMinSdk = match.groupValues[1].toInt()
            if (mergedMinSdk != expectedMinSdk) {
                throw GradleException(
                    "verifyMinSdk FAILED (PKG-02): merged-manifest minSdkVersion is $mergedMinSdk, " +
                        "expected exactly $expectedMinSdk. A dependency likely raised the floor above the " +
                        "Nexus 7 2013 target (API 23). Inspect ${manifestFile.absolutePath}."
                )
            }
            logger.lifecycle(
                "verifyMinSdk OK: merged-manifest minSdkVersion == $mergedMinSdk for variant '${variant.name}' (PKG-02)."
            )
        }
    }

    // Release variant is the shipped artifact: expose the plain `verifyMinSdk`
    // alias and make `check` depend on it (CI gate).
    if (variant.name == "release") {
        val alias = tasks.register("verifyMinSdk") { dependsOn(verifyTask) }
        tasks.named("check") { dependsOn(alias) }
    }
}
