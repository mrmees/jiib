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

    /** Scan-confirm header glyph (wide-pass R14 registry routing — pre-existing in-use ligature). */
    val QrCodeScanner = DinghyIcon(IconRef.Ligature("qr_code_scanner"), alternate = "qr_code_scanner")

    /** Spool filter-picker Clear glyph (owner-assigned 2026-06-12, closes 23-rev WR-02). */
    val DeleteSweep = DinghyIcon(IconRef.Ligature("delete_sweep"), alternate = "delete_sweep")

    // --- Active-spool card + scan surface (structural-slate registry routing, 2026-06-12 —
    //     pre-existing in-use ligatures flipped from raw MaterialSymbol params; NO new glyph picks) ---
    /** Active-spool card "Change" action (swap to another spool). */
    val SpoolChange = DinghyIcon(IconRef.Ligature("swap_horiz"), alternate = "spool_change")
    /** Active-spool card "Clear" action (post_spool_id {} — unload the active record). */
    val SpoolClear = DinghyIcon(IconRef.Ligature("remove_circle"), alternate = "spool_clear")
    /** Spool storage-location stat row (active-spool card + scan-confirm). */
    val SpoolLocation = DinghyIcon(IconRef.Ligature("location_on"), alternate = "spool_location")
    /** Amber badge: queued usage not yet synced — remaining may be stale. */
    val SpoolUsageStale = DinghyIcon(IconRef.Ligature("sync_problem"), alternate = "spool_usage_stale")
    /** Amber badge: the active spool changed externally (another client). */
    val SpoolChangedExternally = DinghyIcon(IconRef.Ligature("autorenew"), alternate = "spool_changed_externally")
    /** Scan degrade panel — camera permission denied. */
    val ScanNoPermission = DinghyIcon(IconRef.Ligature("no_photography"), alternate = "scan_no_permission")
    /** Scan degrade panel — no camera on this device. */
    val ScanNoCamera = DinghyIcon(IconRef.Ligature("videocam_off"), alternate = "scan_no_camera")
    /** Scan degrade panel — camera busy / unavailable. */
    val ScanCameraBusy = DinghyIcon(IconRef.Ligature("error"), alternate = "scan_camera_busy")
    /** Scan surface camera-flip toggle (front/back). */
    val ScanCameraFlip = DinghyIcon(IconRef.Ligature("cameraswitch"), alternate = "scan_camera_flip")
    /** Scan degrade escape — "Use picker instead" (manual list picker). */
    val ScanUsePicker = DinghyIcon(IconRef.Ligature("list"), alternate = "scan_use_picker")

    // --- Launcher / shortcut glyphs (Standby grid + Printing shortcut row — icon-never-twice) ---
    val LauncherFiles = DinghyIcon(IconRef.Ligature("print_connect"), alternate = "launcher_files")
    val LauncherTemperature = DinghyIcon(IconRef.Ligature("thermostat"), alternate = "launcher_temperature")
    val LauncherMove = DinghyIcon(IconRef.Ligature("open_with"), alternate = "launcher_move")
    val LauncherExtrude = DinghyIcon(IconRef.Ligature("output_circle"), alternate = "launcher_extrude")
    val LauncherCalibration = DinghyIcon(IconRef.Ligature("tune"), alternate = "launcher_calibration")
    // 24-02 (Wave 0): idle-list Webcam row glyph — owner-confirmed "videocam" (DO NOT change without owner consent).
    val LauncherWebcam = DinghyIcon(IconRef.Ligature("videocam"), alternate = "launcher_webcam")
    // 18.3 D-01: the former `database` ligature placeholder is retired for a genuinely-custom side-view
    // filament-spool VectorDrawable (`R.drawable.spool`) — Material Symbols has no spool glyph, so this is
    // a sanctioned hand-authored printer-domain custom (icon-source policy above). The drawable draws the
    // BODY/FLANGES only; the color-reactive filament band is painted at runtime by `SpoolGlyph` (D-02/D-04).
    // Still distinct from `Progress` (donut_large) — they can co-render on PrintStatus (WR-02 still holds).
    val LauncherSpool = DinghyIcon(IconRef.Drawable(R.drawable.spool), alternate = "launcher_spool")
    val LauncherMacros = DinghyIcon(IconRef.Ligature("bolt"), alternate = "launcher_macros")
    val LauncherConsole = DinghyIcon(IconRef.Ligature("terminal"), alternate = "launcher_console")
    val LauncherDrawer = DinghyIcon(IconRef.Ligature("more_horiz"), alternate = "launcher_drawer")

    // --- Morphing-root idle foot-bar glyphs (24-02, OWNER-LOCKED — never invent/substitute, icon law).
    // D-09: the third foot button opens the App Drawer (interim System hub); glyph reads NEUTRAL (not Power/red).
    // Both ligatures owner-confirmed 2026-06-10 and verified present in the bundled v2.944 Material Symbols ttf.
    val FootPreheat = DinghyIcon(IconRef.Ligature("chair_fireplace"), alternate = "home_foot_preheat")
    val FootSystem = DinghyIcon(IconRef.Ligature("bottom_panel_open"), alternate = "home_foot_system")

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

    // FanMode (the PART-COOLING fan, Fine-Tune) renders the OFFICIAL Material Symbols `air` glyph via the
    // bundled font (Ligature). Phase 19 D-08 reassigned it from `mode_fan` → `air` (owner-directed cross-phase
    // side-change): `mode_fan_2` is now the Phase-19 generic-fan OUTPUT glyph (OutputFan), so the part-cooling
    // fan moves to `air` to keep the two fans visually distinct. The bundled ttf carries `air`/`speed`, so no
    // local vector is needed. Consumer: FineTuneScreen.kt (the part-fan Fine-Tune param, D-03).
    val FanMode = DinghyIcon(IconRef.Ligature("air"), alternate = "fan_mode")
    val Speed = DinghyIcon(IconRef.Ligature("speed"), alternate = "speed")

    // --- Phase-19 output-type glyphs (D-01..D-07, OWNER-LOCKED — never invent/substitute, icon law).
    // The Wave-2 detail rows reference these tokens symbolically (never raw ligature strings) so each
    // output family resolves to its owner-chosen glyph behind the verify_ligatures.py resolution gate (D-09). ---
    val OutputHeater = DinghyIcon(IconRef.Ligature("mode_heat"), alternate = "output_heater")
    val OutputFan = DinghyIcon(IconRef.Ligature("mode_fan_2"), alternate = "output_fan")
    // D-03: shared by led / neopixel / dotstar / pca9533 / pca9632 (every LED-class output family).
    val OutputLed = DinghyIcon(IconRef.Ligature("lightbulb_2"), alternate = "output_led")
    val OutputServo = DinghyIcon(IconRef.Ligature("cyclone"), alternate = "output_servo")
    // D-05: both the digital and PWM output_pin variants share this row glyph.
    val OutputPin = DinghyIcon(IconRef.Ligature("check_box"), alternate = "output_pin")
    val OutputPwmTool = DinghyIcon(IconRef.Ligature("vital_signs"), alternate = "output_pwm_tool")
    // D-07: the Outputs section / App-Drawer tile glyph.
    val OutputSection = DinghyIcon(IconRef.Ligature("output"), alternate = "output_section")

    // --- Phase-20 System-Information glyphs (D-01..D-10, OWNER-LOCKED — never invent/substitute, icon
    // law [[dinghy-never-pick-icons-ask]]). The whole slate resolves in the bundled v2.944 Material
    // Symbols ttf (20-RESEARCH Icon Ligature Gate). D-03 (CPU temp) REUSES the existing
    // LauncherTemperature (thermostat) token and D-09 (CPU load) REUSES the existing Speed (speed)
    // token — re-registering those ligatures under a new name would trip the iconRef uniqueness guard,
    // so they are NOT re-added here. ---
    val SysInfoTile = DinghyIcon(IconRef.Ligature("pulse_alert"), alternate = "sysinfo_tile")        // D-01 drawer tile
    val SysInfoHost = DinghyIcon(IconRef.Ligature("dns"), alternate = "sysinfo_host")                 // D-02 hostname/model
    val SysInfoUptime = DinghyIcon(IconRef.Ligature("schedule"), alternate = "sysinfo_uptime")        // D-04 uptime
    val SysInfoCpu = DinghyIcon(IconRef.Ligature("developer_board"), alternate = "sysinfo_cpu")       // D-05 CPU model/cores
    val SysInfoRam = DinghyIcon(IconRef.Ligature("memory"), alternate = "sysinfo_ram")                // D-06 total RAM
    val SysInfoDistro = DinghyIcon(IconRef.Ligature("deployed_code"), alternate = "sysinfo_distro")   // D-07 distro
    val SysInfoKernel = DinghyIcon(IconRef.Ligature("code_blocks"), alternate = "sysinfo_kernel")     // D-08 kernel
    val SysInfoMemUsage = DinghyIcon(IconRef.Ligature("data_usage"), alternate = "sysinfo_mem_usage") // D-10 memory used/total

    // --- FineTune exemplar glyphs (18-06) ---
    // MaxVelocity/MaxAccel render the OFFICIAL Material Symbols glyph via the bundled font (Ligature) —
    // NOT a hand-traced local vector. The font is already a shipped dependency used app-wide (PrintStatus
    // launcher icons etc.), so D-17's "no Material Symbols font dep" rationale does not hold for these.
    // The FineTune glyphs below now ALSO render the OFFICIAL Material Symbols glyph via the bundled font
    // (Ligature) — the 18.1 flip retired their hand-traced local vectors (D-08/D-15).
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

    // --- Phase-23 redesign glyphs (23-03, OWNER-LOCKED — never invent/substitute, icon law).
    // Assignments from owner's 2026-06-09 Spoolman sketch session; all ligatures verified present
    // in the bundled v2.944 Material Symbols ttf (plan 23-01 verify_ligatures.py gate exits 0).
    val Sort = DinghyIcon(IconRef.Ligature("sort"), alternate = "sort")
    val FilterList = DinghyIcon(IconRef.Ligature("filter_list"), alternate = "filter_list")
    // expand_circle_up = Load spool (owner-reassigned from play_circle — see bucket notes).
    val ExpandCircleUp = DinghyIcon(IconRef.Ligature("expand_circle_up"), alternate = "expand_circle_up")
    // expand_circle_down = Unload spool (owner-reassigned from stop_circle — see bucket notes).
    val ExpandCircleDown = DinghyIcon(IconRef.Ligature("expand_circle_down"), alternate = "expand_circle_down")
    val ResetWrench = DinghyIcon(IconRef.Ligature("reset_wrench"), alternate = "reset_wrench")
    val ResetSettings = DinghyIcon(IconRef.Ligature("reset_settings"), alternate = "reset_settings")

    // --- Phase-23 SpoolScreen pilot glyphs (23-06, OWNER-LOCKED — from img/material-icon-bucket.json).
    // All five ligatures verified present in the bundled v2.944 Material Symbols ttf.
    // match_case = sort by name; calendar_clock = sort by date; experiment = filter by filament family.
    // home = navigate to PrintStatus (foot button); qr_code = open QR scan (foot button).
    val MatchCase = DinghyIcon(IconRef.Ligature("match_case"), alternate = "match_case")
    val CalendarClock = DinghyIcon(IconRef.Ligature("calendar_clock"), alternate = "calendar_clock")
    val Experiment = DinghyIcon(IconRef.Ligature("experiment"), alternate = "experiment")
    val Home = DinghyIcon(IconRef.Ligature("home"), alternate = "home")
    val QrCode = DinghyIcon(IconRef.Ligature("qr_code"), alternate = "qr_code")

    // --- Browse screens (Phase 25) — OWNER-LOCKED (D-21). Sources: img/material-icon-bucket.json
    // assignments, existing DinghyIcons tokens (reused), and two owner-approved out-of-bucket entries
    // (play_arrow / radio_button_unchecked, sanctioned 2026-06-10). Never the same glyph twice on one screen.
    // Files actions:
    val Print = DinghyIcon(IconRef.Ligature("print"), alternate = "files_print")                         // bucket: "start a print"
    val Delete = DinghyIcon(IconRef.Ligature("delete"), alternate = "files_delete")                      // bucket entry
    // Console filter toggles (hide-state glyphs — the icon shown when the toggle is in the HIDE state):
    val HideTemps = DinghyIcon(IconRef.Ligature("mode_heat_off"), alternate = "console_hide_temps")      // bucket: "hide temperature messages in console"
    val HideTimelapse = DinghyIcon(IconRef.Ligature("video_camera_back"), alternate = "console_hide_timelapse") // bucket: "webcam menu icon, also show timelapse in console"
    val HidePrompts = DinghyIcon(IconRef.Ligature("chat_error"), alternate = "console_hide_prompts")     // bucket: "hide macro prompt messages in console"
    // Macros screen:
    val MacrosLeader = DinghyIcon(IconRef.Ligature("code"), alternate = "macros_leader")                  // bucket: "macros" (distinct from LauncherMacros=bolt)
    val ManageMacros = DinghyIcon(IconRef.Ligature("bookmark_manager"), alternate = "macros_manage")      // bucket entry (more semantically precise than reusing LauncherCalibration=tune)
    val ExecuteMacro = DinghyIcon(IconRef.Ligature("play_arrow"), alternate = "macros_execute")           // owner-approved 2026-06-10 (not in bucket; sanctioned new entry)
    val UnbookmarkedMacro = DinghyIcon(IconRef.Ligature("radio_button_unchecked"), alternate = "macros_unbookmarked") // owner-approved 2026-06-10 (existing code ligature, out-of-bucket but sanctioned)
    // WR-07 (registry conformance — NOT new icon choices): the Macros ManageMode Show-hidden toggle pair
    // carried the old SystemMacrosScreen glyphs forward as raw ligature strings, and the SpoolWarningGuard
    // kept its pre-existing raw "warning" MaterialSymbol. Registered here so the planned font subset
    // (which iterates this registry) and the verify_ligatures.py resolution gate cover them.
    val Visibility = DinghyIcon(IconRef.Ligature("visibility"), alternate = "macros_show_hidden_on")      // revealed state (pre-existing glyph, preserved)
    val VisibilityOff = DinghyIcon(IconRef.Ligature("visibility_off"), alternate = "macros_show_hidden_off") // hidden state (pre-existing glyph, preserved)
    val Warning = DinghyIcon(IconRef.Ligature("warning"), alternate = "warning")                          // SpoolWarningGuard row glyph (pre-existing, preserved)

    // --- Phase-27 calibration hub routine glyphs (27-01, OWNER-LOCKED — owner confirmed 2026-06-12
    // "Bless all 5 as-is." — the current raw ligatures promoted verbatim to registry tokens; all 5
    // verified present in the bundled v2.944 Material Symbols ttf via verify_ligatures.py. The
    // Move/TESTZ distance-stepper +/- cells REUSE BabystepExpand/BabystepCompress (owner decision:
    // "Reuse expand/compress") — NO new stepper token added.
    // Token names follow the Routine* convention from 27-PATTERNS.md §"Icon Registration Gate".
    val RoutineProbeCalibrate = DinghyIcon(IconRef.Ligature("straighten"), alternate = "routine_probe_calibrate")
    val RoutineBedMesh = DinghyIcon(IconRef.Ligature("grid_on"), alternate = "routine_bed_mesh")
    val RoutineScrewsTilt = DinghyIcon(IconRef.Ligature("architecture"), alternate = "routine_screws_tilt")
    val RoutineZTilt = DinghyIcon(IconRef.Ligature("vertical_align_center"), alternate = "routine_z_tilt")
    val RoutineQgl = DinghyIcon(IconRef.Ligature("crop_square"), alternate = "routine_qgl")

    // --- Phase-27 review fix WR-04 (registry conformance — NOT new icon choices): the rebuilt
    // Move/ScrewsTilt/BedMesh screens still drew these 9 glyphs as raw MaterialSymbol/inline-ligature
    // call sites, bypassing the subset source of truth and the verify_ligatures.py gate. Promoted
    // VERBATIM — the EXISTING shipping glyphs are preserved unchanged (drift-guarding only, per the
    // 27-01 "bless as-is" precedent and [[dinghy-never-pick-icons-ask]]); call sites now route
    // through DinghyIconView.
    val JogXPlus = DinghyIcon(IconRef.Ligature("arrow_forward"), alternate = "jog_x_plus")              // Move: X+ jog cell
    val HomeStateHomed = DinghyIcon(IconRef.Ligature("in_home_mode"), alternate = "home_state_homed")   // Move: XY-home cell, homed
    val HomeStateUnhomed = DinghyIcon(IconRef.Ligature("wifi_home"), alternate = "home_state_unhomed")  // Move: XY-home cell, needs homing
    val ScrewPending = DinghyIcon(IconRef.Ligature("point_scan"), alternate = "screw_pending")          // ScrewsTilt: not yet probed
    val ScrewBase = DinghyIcon(IconRef.Ligature("anchor"), alternate = "screw_base")                    // ScrewsTilt: base/reference screw
    val ScrewInTolerance = DinghyIcon(IconRef.Ligature("commit"), alternate = "screw_in_tolerance")     // ScrewsTilt: within tolerance
    val ScrewTurnCcw = DinghyIcon(IconRef.Ligature("rotate_left"), alternate = "screw_turn_ccw")        // ScrewsTilt: turn counter-clockwise
    val ScrewTurnCw = DinghyIcon(IconRef.Ligature("rotate_right"), alternate = "screw_turn_cw")         // ScrewsTilt: turn clockwise
    val MeshEmpty = DinghyIcon(IconRef.Ligature("grid_off"), alternate = "mesh_empty")                  // BedMesh: empty-state Focus

    // PrintStatus standby-home identity (header law, 2026-06-13). Owner-named "mode_standby".
    // Swapped to the e-stop while printing. Confirmed resolvable in the bundled v2.944 ttf.
    val PrintStatusStandby = DinghyIcon(IconRef.Ligature("mode_standby"), alternate = "print_status_standby")

    // --- Move Hub redesign glyphs (Task D3, OWNER-APPROVED — never invent/substitute, icon law
    // [[dinghy-never-pick-icons-ask]]). The five Field-list mode rows; homing rows reuse
    // HomeStateUnhomed and Microstep reuses FineTune. All five ligatures are unique.
    val MoveTouch = DinghyIcon(IconRef.Ligature("my_location"), alternate = "move_touch")
    val MoveXY = DinghyIcon(IconRef.Ligature("control_camera"), alternate = "move_xy")
    val MoveZ = DinghyIcon(IconRef.Ligature("swap_vert"), alternate = "move_z")
    val SavedLocation = DinghyIcon(IconRef.Ligature("bookmark"), alternate = "saved_location")
    val SaveLocation = DinghyIcon(IconRef.Ligature("bookmark_add"), alternate = "save_location")

    // --- Phase-28 System/Settings cluster glyphs (28-01, OWNER-LOCKED D-21).
    // All ligatures verified resolvable in the bundled v2.944 Material Symbols ttf.
    // `settings` / `info` / `power_settings_new` are new-to-NEEDED (not yet in verify_ligatures.py).
    // `palette` is already registered as Palette (Spool detail pane) — `SystemRowTheme` and `Palette`
    // never co-occur on one screen, so the shared ligature is allow-listed in DinghyIconsTest (WR-02).
    val LauncherFineTune = DinghyIcon(IconRef.Ligature("line_style"), alternate = "launcher_fine_tune")
    val SystemRowPrinters = DinghyIcon(IconRef.Ligature("android_wifi_3_bar_plus"), alternate = "system_row_printers")
    val SystemRowSettings = DinghyIcon(IconRef.Ligature("settings"), alternate = "system_row_settings")
    val SystemRowTheme = DinghyIcon(IconRef.Ligature("palette"), alternate = "system_row_theme")
    val SystemRowAbout = DinghyIcon(IconRef.Ligature("info"), alternate = "system_row_about")
    val SystemRowPower = DinghyIcon(IconRef.Ligature("power_settings_new"), alternate = "system_row_power")

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
        LauncherWebcam, LauncherSpool, LauncherMacros, LauncherConsole, LauncherDrawer,
        FootPreheat, FootSystem,
        BabystepCompress, BabystepExpand, StatusStop, Nozzle, HeatBed, FanMode, Speed,
        KeyboardReturn, OutputCircle, MaxVelocity, MaxAccel, MinCruise, SquareCornerVelocity,
        PressureAdvance, SmoothTime, Decrease, Increase, InputCircle,
        OutputHeater, OutputFan, OutputLed, OutputServo, OutputPin, OutputPwmTool, OutputSection,
        SysInfoTile, SysInfoHost, SysInfoUptime, SysInfoCpu, SysInfoRam, SysInfoDistro,
        SysInfoKernel, SysInfoMemUsage,
        Sort, FilterList, ExpandCircleUp, ExpandCircleDown, ResetWrench, ResetSettings, QrCodeScanner, DeleteSweep,
        SpoolChange, SpoolClear, SpoolLocation, SpoolUsageStale, SpoolChangedExternally,
        ScanNoPermission, ScanNoCamera, ScanCameraBusy, ScanCameraFlip, ScanUsePicker,
        MatchCase, CalendarClock, Experiment, Home, QrCode,
        Print, Delete, HideTemps, HideTimelapse, HidePrompts,
        MacrosLeader, ManageMacros, ExecuteMacro, UnbookmarkedMacro,
        Visibility, VisibilityOff, Warning,
        RoutineProbeCalibrate, RoutineBedMesh, RoutineScrewsTilt, RoutineZTilt, RoutineQgl,
        JogXPlus, HomeStateHomed, HomeStateUnhomed,
        ScrewPending, ScrewBase, ScrewInTolerance, ScrewTurnCcw, ScrewTurnCw,
        MeshEmpty,
        LauncherFineTune, SystemRowPrinters, SystemRowSettings, SystemRowTheme,
        SystemRowAbout, SystemRowPower,
        PrintStatusStandby,
        MoveTouch, MoveXY, MoveZ, SavedLocation, SaveLocation,
    )
}
