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
 *
 * **Icon-source policy.** Icons are real Material Symbols by default — [IconRef.Ligature] (rendered
 * from the bundled font) when the glyph is present, or an OFFICIAL Google vector drawable (path data
 * verbatim, never hand-traced) when the bundled font is too old to carry it. Hand-authored custom
 * drawables are ONLY for genuinely-custom printer-domain glyphs Material Symbols lacks (nozzle, bed,
 * bed-tilt, spool). The shape-coded status indicators (octagon/triangle) ARE in the font and are NOT
 * custom. Retires D-17's "no Material Symbols font" stance — the font is already a shipped dependency
 * used app-wide. (The "never the same glyph twice on one screen" rule still holds.)
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

    // --- Spool exemplar glyphs (18-07; the detail-pane Material Symbols) ---
    val Inventory = DinghyIcon(IconRef.Ligature("inventory_2"), alternate = "inventory")
    val Palette = DinghyIcon(IconRef.Ligature("palette"), alternate = "palette")
    val CalendarAddOn = DinghyIcon(IconRef.Ligature("calendar_add_on"), alternate = "calendar_add_on")
    val CheckCircle = DinghyIcon(IconRef.Ligature("check_circle"), alternate = "check_circle")
    val Archive = DinghyIcon(IconRef.Ligature("archive"), alternate = "archive")

    // --- Launcher / shortcut glyphs (Standby grid + Printing shortcut row — icon-never-twice) ---
    val LauncherFiles = DinghyIcon(IconRef.Ligature("print_connect"), alternate = "launcher_files")
    val LauncherTemperature = DinghyIcon(IconRef.Ligature("thermostat"), alternate = "launcher_temperature")
    val LauncherMove = DinghyIcon(IconRef.Ligature("open_with"), alternate = "launcher_move")
    val LauncherExtrude = DinghyIcon(IconRef.Ligature("output_circle"), alternate = "launcher_extrude")
    val LauncherCalibration = DinghyIcon(IconRef.Ligature("tune"), alternate = "launcher_calibration")
    // WR-02: distinct from `Progress` (donut_large). `LauncherSpool` is the Spool launcher tile and
    // `Progress` (the Spoolman print-line glyph) can render on the SAME PrintStatus screen — they must NOT
    // share a glyph (the registry's "icon-never-twice on one screen" rule). `database` reads as a stacked
    // spool/cylinder and is consistent with the Spool feature's inventory framing.
    val LauncherSpool = DinghyIcon(IconRef.Ligature("database"), alternate = "launcher_spool")
    val LauncherMacros = DinghyIcon(IconRef.Ligature("bolt"), alternate = "launcher_macros")
    val LauncherConsole = DinghyIcon(IconRef.Ligature("terminal"), alternate = "launcher_console")
    val LauncherDrawer = DinghyIcon(IconRef.Ligature("more_horiz"), alternate = "launcher_drawer")

    // --- Ligature-backed (Material Symbols), formerly drawable-backed (18.1 flip, D-09/D-15) ---
    val BabystepCompress = DinghyIcon(IconRef.Ligature("compress"), alternate = "babystep_compress")
    val BabystepExpand = DinghyIcon(IconRef.Ligature("expand"), alternate = "babystep_expand")
    /**
     * Stop-status glyph (the former octagon token, renamed in 18.1). The bundled Material Symbols `disabled_by_default`
     * ligature renders a square+✕ silhouette — NOT a literal octagon — so the token is named `StatusStop`
     * (D-06/D-14): safety is carried by the distinct *shape* of the glyph, not the octagon outline.
     */
    val StatusStop =
        DinghyIcon(IconRef.Ligature("disabled_by_default"), alternate = "status_stop")

    // --- Drawable-backed (hand-authored printer-domain customs; the only two D-10 keepers) ---
    val Nozzle = DinghyIcon(IconRef.Drawable(R.drawable.nozzle), alternate = "nozzle")
    val HeatBed = DinghyIcon(IconRef.Drawable(R.drawable.heat_bed), alternate = "heat_bed")

    // FanMode/Speed render the OFFICIAL Material Symbols glyph via the bundled font (Ligature) — the
    // bundled ttf carries `mode_fan`/`speed`, so no local vector is needed (260607-fts).
    val FanMode = DinghyIcon(IconRef.Ligature("mode_fan"), alternate = "fan_mode")
    val Speed = DinghyIcon(IconRef.Ligature("speed"), alternate = "speed")

    // --- FineTune exemplar glyphs (18-06) ---
    // MaxVelocity/MaxAccel render the OFFICIAL Material Symbols glyph via the bundled font (Ligature) —
    // NOT a hand-traced local vector. The font is already a shipped dependency used app-wide (PrintStatus
    // launcher icons etc.), so D-17's "no Material Symbols font dep" rationale does not hold for these.
    // The remaining FineTune glyphs below stay project-local vector drawables (D-17) pending owner review.
    val KeyboardReturn = DinghyIcon(IconRef.Ligature("keyboard_return"), alternate = "keyboard_return")
    val OutputCircle = DinghyIcon(IconRef.Ligature("output_circle"), alternate = "output_circle")
    val MaxVelocity =
        DinghyIcon(IconRef.Ligature("arrow_shape_up_stack_2"), alternate = "max_velocity")
    val MaxAccel = DinghyIcon(IconRef.Ligature("sprint"), alternate = "max_accel")
    val MinCruise = DinghyIcon(IconRef.Ligature("directions_boat"), alternate = "min_cruise")
    val SquareCornerVelocity =
        DinghyIcon(IconRef.Ligature("rounded_corner"), alternate = "square_corner_velocity")
    val PressureAdvance =
        DinghyIcon(IconRef.Ligature("text_select_move_forward_word"), alternate = "pressure_advance")
    val SmoothTime = DinghyIcon(IconRef.Ligature("avg_time"), alternate = "smooth_time")
    val Decrease = DinghyIcon(IconRef.Ligature("remove"), alternate = "decrease")
    val Increase = DinghyIcon(IconRef.Ligature("add"), alternate = "increase")
    val InputCircle = DinghyIcon(IconRef.Ligature("input_circle"), alternate = "input_circle")

    /**
     * Hand-rolled list of every entry above — the Phase-22-readiness handle (the registry-iteration
     * source for `tools/subset-symbols` and the uniqueness test). Add new entries here when you add a
     * `val` above (no reflection — this stays explicit and grep-auditable on the minSdk-23 floor).
     */
    val all: List<DinghyIcon> = listOf(
        Back, Check, Edit, FineTune, Height, Altitude, Layers, Scale, Storefront, TimerDown, TimerUp,
        Progress, PauseCircle,
        Inventory, Palette, CalendarAddOn, CheckCircle, Archive,
        LauncherFiles, LauncherTemperature, LauncherMove, LauncherExtrude, LauncherCalibration,
        LauncherSpool, LauncherMacros, LauncherConsole, LauncherDrawer,
        BabystepCompress, BabystepExpand, StatusStop, Nozzle, HeatBed, FanMode, Speed,
        KeyboardReturn, OutputCircle, MaxVelocity, MaxAccel, MinCruise, SquareCornerVelocity,
        PressureAdvance, SmoothTime, Decrease, Increase, InputCircle,
    )
}
