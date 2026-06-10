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

    /**
     * Opens the App Drawer (the interim "System" hub, D-09/D-10). This is NOT a navigation
     * destination — it is a contextual action that surfaces the drawer over the current screen.
     */
    data object OpenDrawer : HomeAction

    // v2: data class InlineControl(val controlId: String) : HomeAction
    // v2: data class UserCustomAction(…)  — editor + persist layer deferred to the v2 milestone
}

// ---------------------------------------------------------------------------
// v1 idle action list builder (D-06/D-08)
// ---------------------------------------------------------------------------

/**
 * Builds the v1 idle action list in owner-specified D-06 order:
 * `Spool → Files → Move → Extrude → Macros → Calibration → Outputs → Webcam`
 *
 * Capability-absent rows **drop out** (D-08 — hide, not grey):
 * - [spoolmanPresent] = false → Spool row absent
 * - [bookmarksExist]  = false → Macros row absent (macros only shown when bookmarks exist)
 * - [outputsPresent]  = false → Outputs row absent
 * - [webcamEnabled]   = false → Webcam row absent
 *
 * With all capabilities absent the list contains 4 rows: Files, Move, Extrude, Calibration
 * (the always-present set). Temperature and Console are intentionally OFF the idle list (D-07):
 * they are reachable while printing, not idle. Fine-Tune is likewise printing-only.
 *
 * ## FIX-5 / Wave-0 isolation note
 * The Webcam row uses a PLACEHOLDER icon and label pending the owner-confirmed webcam glyph from
 * Plan 24-02 (the ASK-OWNER gate). The placeholder reuses an already-registered [DinghyIcons] token
 * so this plan compiles in ISOLATION with no cross-Wave-0 dependency. Plan 24-04 (which depends on
 * BOTH 24-01 and 24-02) swaps `// PLACEHOLDER(24-04)` with the real `DinghyIcons.LauncherWebcam`
 * + `R.string.cd_launcher_webcam`.
 *
 * @param spoolmanPresent true if a Spoolman instance is configured and reachable
 * @param bookmarksExist  true if the user has at least one bookmarked macro
 * @param outputsPresent  true if the printer exposes controllable outputs
 * @param webcamEnabled   true if at least one webcam is configured and not toggled off
 * @return ordered [List<HomeAction>] for the Standby Field
 */
fun buildIdleActions(
    spoolmanPresent: Boolean,
    bookmarksExist: Boolean,
    outputsPresent: Boolean,
    webcamEnabled: Boolean,
): List<HomeAction> = buildList {
    // D-06 order: Spool → Files → Move → Extrude → Macros → Calibration → Outputs → Webcam

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

    if (bookmarksExist) {
        add(HomeAction.Destination(
            dest     = NavDest.Macros,
            labelRes = R.string.cd_launcher_macros,
            icon     = DinghyIcons.LauncherMacros,
        ))
    }

    add(HomeAction.Destination(
        dest     = NavDest.Calibration,
        labelRes = R.string.cd_launcher_calibration,
        icon     = DinghyIcons.LauncherCalibration,
    ))

    if (outputsPresent) {
        add(HomeAction.Destination(
            dest     = NavDest.Outputs,
            labelRes = R.string.outputs_title,
            icon     = DinghyIcons.OutputSection,
        ))
    }

    if (webcamEnabled) {
        // PLACEHOLDER(24-04): owner-confirmed Webcam glyph DinghyIcons.LauncherWebcam + label
        // R.string.cd_launcher_webcam wired in 24-04 (after 24-02 registers the owner-confirmed
        // glyph). Using LauncherDrawer (already-registered) here so 24-01 compiles in isolation
        // with no cross-Wave-0 dependency on the 24-02 ASK-OWNER plan.
        add(HomeAction.Destination(
            dest     = NavDest.Webcam,
            labelRes = R.string.cd_launcher_drawer, // PLACEHOLDER(24-04) → cd_launcher_webcam
            icon     = DinghyIcons.LauncherDrawer,  // PLACEHOLDER(24-04) → DinghyIcons.LauncherWebcam
        ))
    }
}
