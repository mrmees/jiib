package works.mees.dinghy.designsystem.icons

import works.mees.dinghy.R

/**
 * The semantic icon-token **registry** (RESEARCH Q6). One entry per icon the THREE Phase-18 exemplar
 * screens (PrintStatus / FineTune / Spool) consume — this phase registers ONLY exemplar icons; the
 * other ~50 raw call sites stay un-tokenized until Phase 22 (D-03 / deferred backfill). Each entry pairs
 * an [IconRef] source with a non-blank, UNIQUE [DinghyIcon.alternate] canonical remap handle (D-07).
 *
 * Going forward this object is the single source of truth for `tools/subset-symbols`: iterate [all],
 * collect every [IconRef.Ligature.name], and that is the subset list. No existing artifact breaks —
 * only exemplar icons are registered today.
 */
object DinghyIcons {

    // --- Ligature-backed (Material Symbols) ---
    val Back = DinghyIcon(IconRef.Ligature("arrow_back"), alternate = "back")
    val Check = DinghyIcon(IconRef.Ligature("check"), alternate = "check")
    val Edit = DinghyIcon(IconRef.Ligature("edit"), alternate = "edit")
    val FineTune = DinghyIcon(IconRef.Ligature("instant_mix"), alternate = "fine_tune")
    val Height = DinghyIcon(IconRef.Ligature("height"), alternate = "height")
    val Altitude = DinghyIcon(IconRef.Ligature("altitude"), alternate = "altitude")
    val Layers = DinghyIcon(IconRef.Ligature("layers"), alternate = "layers")
    val Scale = DinghyIcon(IconRef.Ligature("scale"), alternate = "scale")
    val Storefront = DinghyIcon(IconRef.Ligature("storefront"), alternate = "storefront")
    val TimerDown = DinghyIcon(IconRef.Ligature("timer_arrow_down"), alternate = "timer_down")
    val TimerUp = DinghyIcon(IconRef.Ligature("timer_arrow_up"), alternate = "timer_up")
    val Progress = DinghyIcon(IconRef.Ligature("donut_large"), alternate = "progress")
    val PauseCircle = DinghyIcon(IconRef.Ligature("pause_circle"), alternate = "pause_circle")

    // --- Launcher / shortcut glyphs (Standby grid + Printing shortcut row — icon-never-twice) ---
    val LauncherFiles = DinghyIcon(IconRef.Ligature("print_connect"), alternate = "launcher_files")
    val LauncherTemperature = DinghyIcon(IconRef.Ligature("thermostat"), alternate = "launcher_temperature")
    val LauncherMove = DinghyIcon(IconRef.Ligature("open_with"), alternate = "launcher_move")
    val LauncherExtrude = DinghyIcon(IconRef.Ligature("output_circle"), alternate = "launcher_extrude")
    val LauncherCalibration = DinghyIcon(IconRef.Ligature("tune"), alternate = "launcher_calibration")
    val LauncherSpool = DinghyIcon(IconRef.Ligature("donut_large"), alternate = "launcher_spool")
    val LauncherMacros = DinghyIcon(IconRef.Ligature("bolt"), alternate = "launcher_macros")
    val LauncherConsole = DinghyIcon(IconRef.Ligature("terminal"), alternate = "launcher_console")
    val LauncherDrawer = DinghyIcon(IconRef.Ligature("more_horiz"), alternate = "launcher_drawer")

    // --- Drawable-backed (bundled vectors) ---
    val BabystepCompress =
        DinghyIcon(IconRef.Drawable(R.drawable.ic_babystep_compress), alternate = "babystep_compress")
    val BabystepExpand =
        DinghyIcon(IconRef.Drawable(R.drawable.ic_babystep_expand), alternate = "babystep_expand")
    val StatusOctagon =
        DinghyIcon(IconRef.Drawable(R.drawable.ic_status_octagon), alternate = "status_octagon")
    val Nozzle = DinghyIcon(IconRef.Drawable(R.drawable.nozzle), alternate = "nozzle")
    val HeatBed = DinghyIcon(IconRef.Drawable(R.drawable.heat_bed), alternate = "heat_bed")
    val FanMode = DinghyIcon(IconRef.Drawable(R.drawable.mode_fan), alternate = "fan_mode")
    val Speed = DinghyIcon(IconRef.Drawable(R.drawable.speed), alternate = "speed")

    /**
     * Hand-rolled list of every entry above — the Phase-22-readiness handle (the registry-iteration
     * source for `tools/subset-symbols` and the uniqueness test). Add new entries here when you add a
     * `val` above (no reflection — this stays explicit and grep-auditable on the minSdk-23 floor).
     */
    val all: List<DinghyIcon> = listOf(
        Back, Check, Edit, FineTune, Height, Altitude, Layers, Scale, Storefront, TimerDown, TimerUp,
        Progress, PauseCircle,
        LauncherFiles, LauncherTemperature, LauncherMove, LauncherExtrude, LauncherCalibration,
        LauncherSpool, LauncherMacros, LauncherConsole, LauncherDrawer,
        BabystepCompress, BabystepExpand, StatusOctagon, Nozzle, HeatBed, FanMode, Speed,
    )
}
