package works.mees.dinghy.ui.route

import androidx.annotation.StringRes
import works.mees.dinghy.R
import works.mees.dinghy.designsystem.icons.DinghyIcon
import works.mees.dinghy.designsystem.icons.DinghyIcons

/**
 * Typed idle-action list model for the [NavDest.WaterfallHome] Standby Field content (Phase 24, D-05).
 *
 * v1 ships a data-driven ordered [List<HomeAction>] rendered by the Phase-23 `ListBlock`/`ListRow`
 * component classes. The model is intentionally extensible for v2:
 *
 * ## v2 extension points (D-05 — deferred, NOT implemented here)
 * - `data class InlineControl(val controlId: String) : HomeAction` — extruder/bed/etc. controls
 *   living inline in a row (not a navigation destination, a direct command row)
 * - A customization editor + per-user reorder/persist layer
 *
 * These are intentionally NOT built this phase. The sealed interface shape makes them additive (a
 * new variant + a `when` branch in the renderer) rather than a re-architect.
 *
 * ## Icon law
 * Every [Destination.icon] MUST reference a [DinghyIcons] registry token. Raw [IconRef.Ligature]
 * strings are FORBIDDEN inside [buildIdleActions] — per the icon-never-invent rule
 * ([[dinghy-never-pick-icons-ask]]). Using a registered token guarantees the glyph was owner-approved
 * and the ligature resolves in the bundled font (verified by the `DinghyIconsTest` drift guard).
 */
sealed interface HomeAction {

    /**
     * A navigation destination row — tap navigates to [dest].
     *
     * @param dest the [NavDest] this row navigates to
     * @param labelRes a `@StringRes` label displayed in the row (from the string resource table)
     * @param icon a [DinghyIcon] token from [DinghyIcons] — NEVER a raw ligature string
     */
    data class Destination(
        val dest: NavDest,
        @StringRes val labelRes: Int,
        val icon: DinghyIcon,
    ) : HomeAction

    // v2: data class InlineControl(val controlId: String) : HomeAction
    // v2: data class UserCustomAction(…)  — editor + persist layer deferred to the v2 milestone
}

// ---------------------------------------------------------------------------
// v1 idle action list builder (D-06/D-08)
// ---------------------------------------------------------------------------

/**
 * Builds the v1 idle action list in owner-specified D-05/D-06 order:
 * `Spool → Files → Move → Extrude → Macros → Calibration → Temperature → Console → Fine-Tune → Outputs → Webcam`
 *
 * Capability-absent rows **drop out** (D-08 — hide, not grey):
 * - [spoolmanPresent] = false → Spool row absent
 * - [outputsPresent]  = false → Outputs row absent
 * - [webcamEnabled]   = false → Webcam row absent
 *
 * Macros is ALWAYS present (no bookmark gate): it is the only entry point to the Macros screen, so
 * the row must show even with zero bookmarked macros (the Macros screen handles the no-macros case).
 *
 * With all capabilities absent the list contains 8 rows: Files, Move, Extrude, Macros, Calibration,
 * Temperature, Console, Fine-Tune (the always-present set). Temperature, Console, and Fine-Tune
 * are unconditionally present per D-05 (rehomed from drawer-only to idle list in Plan 28-03;
 * the P24 D-07 "printing-only" posture for these three is revised).
 *
 * ## Icon assignment (FIX-5, resolved in 24-04)
 * Every row's [HomeAction.Destination.icon] references a [DinghyIcons] registry token confirmed by
 * the owner (icon-never-invent law). The Webcam row uses [DinghyIcons.LauncherWebcam] ("videocam"),
 * registered and owner-confirmed in Plan 24-02.
 *
 * @param spoolmanPresent true if a Spoolman instance is configured and reachable
 * @param outputsPresent  true if the printer exposes controllable outputs
 * @param webcamEnabled   true if at least one webcam is configured and not toggled off
 * @return ordered [List<HomeAction>] for the Standby Field
 */
fun buildIdleActions(
    spoolmanPresent: Boolean,
    outputsPresent: Boolean,
    webcamEnabled: Boolean,
    isPrinting: Boolean = false,
): List<HomeAction> = buildList {
    // D-05/D-06 order: Spool → Files → Move → Extrude → Macros → Calibration → Temperature → Console → Fine-Tune → Outputs → Webcam

    if (spoolmanPresent) {
        add(HomeAction.Destination(
            dest     = NavDest.Spool,
            labelRes = R.string.cd_launcher_spool,
            icon     = DinghyIcons.LauncherSpool,
        ))
    }

    add(HomeAction.Destination(
        dest     = NavDest.Files,
        labelRes = R.string.cd_launcher_files,
        icon     = DinghyIcons.LauncherFiles,
    ))

    add(HomeAction.Destination(
        dest     = NavDest.Move,
        labelRes = R.string.cd_launcher_move,
        icon     = DinghyIcons.LauncherMove,
    ))

    add(HomeAction.Destination(
        dest     = NavDest.Extrude,
        labelRes = R.string.cd_launcher_extrude,
        icon     = DinghyIcons.LauncherExtrude,
    ))

    // Macros is ALWAYS present — the only way to reach the Macros screen (no bookmark gate).
    add(HomeAction.Destination(
        dest     = NavDest.Macros,
        labelRes = R.string.cd_launcher_macros,
        icon     = DinghyIcons.LauncherMacros,
    ))

    add(HomeAction.Destination(
        dest     = NavDest.CalibrationHub,
        labelRes = R.string.cd_launcher_calibration,
        icon     = DinghyIcons.LauncherCalibration,
    ))

    // D-05 (28-03): Temperature, Console, Fine-Tune rehomed from drawer-only to idle list.
    // These are unconditionally present — no capability gate (always-available destinations).
    add(HomeAction.Destination(
        dest     = NavDest.Temperature,
        labelRes = R.string.cd_launcher_temperature,
        icon     = DinghyIcons.LauncherTemperature,
    ))

    add(HomeAction.Destination(
        dest     = NavDest.Console,
        labelRes = R.string.cd_launcher_console,
        icon     = DinghyIcons.LauncherConsole,
    ))

    add(HomeAction.Destination(
        dest     = NavDest.FineTune,
        labelRes = R.string.cd_launcher_fine_tune,
        icon     = DinghyIcons.LauncherFineTune,
    ))

    if (outputsPresent) {
        add(HomeAction.Destination(
            dest     = NavDest.Outputs,
            labelRes = R.string.outputs_title,
            icon     = DinghyIcons.OutputSection,
        ))
    }

    if (webcamEnabled) {
        // FIX-5 (24-04): owner-confirmed Webcam glyph DinghyIcons.LauncherWebcam ("videocam")
        // and label R.string.cd_launcher_webcam, registered by Plan 24-02. The Wave-0 isolation
        // stub (LauncherDrawer + cd_launcher_drawer) is replaced here now that 24-02 ships on HEAD.
        add(HomeAction.Destination(
            dest     = NavDest.Webcam,
            labelRes = R.string.cd_launcher_webcam,
            icon     = DinghyIcons.LauncherWebcam,
        ))
    }

    // While a print runs, System leaves the foot bar (replaced by Pause/Cancel) and lands here so it
    // stays reachable. Reuses the SAME registered System glyph + label (icon-never-invent: same
    // function, same token object — no new registry entry, no iconRef_isUnique duplicate).
    if (isPrinting) {
        add(HomeAction.Destination(
            dest     = NavDest.System,
            labelRes = R.string.home_foot_system,
            icon     = DinghyIcons.FootSystem,
        ))
    }
}
