package works.mees.jiib.designsystem.icons

import works.mees.jiib.R

/**
 * The semantic icon-token **registry** (RESEARCH Q6). One entry per icon the THREE Phase-18 exemplar
 * screens (PrintStatus / FineTune / Spool) consume — this phase registers ONLY exemplar icons; the
 * other ~50 raw call sites stay un-tokenized until Phase 22 (D-03 / deferred backfill). Each entry pairs
 * an [IconRef] source with a non-blank, UNIQUE [JiibIcon.alternate] canonical remap handle (D-07).
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
object JiibIcons {

    // --- Ligature-backed (Material Symbols) ---
    val Back = JiibIcon(IconRef.Ligature("arrow_back"), alternate = "back")
    val Check = JiibIcon(IconRef.Ligature("check"), alternate = "check")
    val Edit = JiibIcon(IconRef.Ligature("edit"), alternate = "edit")
    val FineTune = JiibIcon(IconRef.Ligature("instant_mix"), alternate = "fine_tune")
    val Height = JiibIcon(IconRef.Ligature("height"), alternate = "height")
    val Altitude = JiibIcon(IconRef.Ligature("altitude"), alternate = "altitude")
    val Layers = JiibIcon(IconRef.Ligature("layers"), alternate = "layers")
    val Scale = JiibIcon(IconRef.Ligature("scale"), alternate = "scale")
    val Storefront = JiibIcon(IconRef.Ligature("storefront"), alternate = "storefront")
    val TimerDown = JiibIcon(IconRef.Ligature("timer_arrow_down"), alternate = "timer_down")
    val TimerUp = JiibIcon(IconRef.Ligature("timer_arrow_up"), alternate = "timer_up")
    val Progress = JiibIcon(IconRef.Ligature("donut_large"), alternate = "progress")
    val PauseCircle = JiibIcon(IconRef.Ligature("pause_circle"), alternate = "pause_circle")
    val Serif = JiibIcon(IconRef.Ligature("serif"), alternate = "serif")
    val DataFont = JiibIcon(IconRef.Ligature("format_letter_spacing_standard"), alternate = "data_font")

    // --- Spool exemplar glyphs (18-07; the detail-pane Material Symbols) ---
    val Inventory = JiibIcon(IconRef.Ligature("inventory_2"), alternate = "inventory")
    val Palette = JiibIcon(IconRef.Ligature("palette"), alternate = "palette")
    val CalendarAddOn = JiibIcon(IconRef.Ligature("calendar_add_on"), alternate = "calendar_add_on")
    val CheckCircle = JiibIcon(IconRef.Ligature("check_circle"), alternate = "check_circle")
    val Archive = JiibIcon(IconRef.Ligature("archive"), alternate = "archive")

    /** Scan-confirm header glyph (wide-pass R14 registry routing — pre-existing in-use ligature). */
    val QrCodeScanner = JiibIcon(IconRef.Ligature("qr_code_scanner"), alternate = "qr_code_scanner")

    /** Spool filter-picker Clear glyph (owner-assigned 2026-06-12, closes 23-rev WR-02). */
    val DeleteSweep = JiibIcon(IconRef.Ligature("delete_sweep"), alternate = "delete_sweep")

    // --- Active-spool card + scan surface (structural-slate registry routing, 2026-06-12 —
    //     pre-existing in-use ligatures flipped from raw MaterialSymbol params; NO new glyph picks) ---
    /** Active-spool card "Change" action (swap to another spool). */
    val SpoolChange = JiibIcon(IconRef.Ligature("swap_horiz"), alternate = "spool_change")
    /** Active-spool card "Clear" action (post_spool_id {} — unload the active record).
     *  Owner 2026-06-17: `do_not_disturb_on` (visual twin of remove_circle, which is absent
     *  from the pinned v2.944 bundled font — it rendered as literal text). */
    val SpoolClear = JiibIcon(IconRef.Ligature("do_not_disturb_on"), alternate = "spool_clear")
    /** Spool storage-location stat row (active-spool card + scan-confirm).
     *  Owner 2026-06-17: `place` (visual twin of location_on, absent from the v2.944 font). */
    val SpoolLocation = JiibIcon(IconRef.Ligature("place"), alternate = "spool_location")
    /** Amber badge: queued usage not yet synced — remaining may be stale. */
    val SpoolUsageStale = JiibIcon(IconRef.Ligature("sync_problem"), alternate = "spool_usage_stale")
    /** Amber badge: the active spool changed externally (another client). */
    val SpoolChangedExternally = JiibIcon(IconRef.Ligature("autorenew"), alternate = "spool_changed_externally")
    /** Scan degrade panel — camera permission denied. */
    val ScanNoPermission = JiibIcon(IconRef.Ligature("no_photography"), alternate = "scan_no_permission")
    /** Scan degrade panel — no camera on this device. */
    val ScanNoCamera = JiibIcon(IconRef.Ligature("videocam_off"), alternate = "scan_no_camera")
    /** Scan degrade panel — camera busy / unavailable. */
    val ScanCameraBusy = JiibIcon(IconRef.Ligature("error"), alternate = "scan_camera_busy")
    /** Scan surface camera-flip toggle (front/back). */
    val ScanCameraFlip = JiibIcon(IconRef.Ligature("cameraswitch"), alternate = "scan_camera_flip")
    /** Scan degrade escape — "Use picker instead" (manual list picker). */
    val ScanUsePicker = JiibIcon(IconRef.Ligature("list"), alternate = "scan_use_picker")

    // --- Launcher / shortcut glyphs (Standby grid + Printing shortcut row — icon-never-twice) ---
    val LauncherFiles = JiibIcon(IconRef.Ligature("print_connect"), alternate = "launcher_files")
    val LauncherTemperature = JiibIcon(IconRef.Ligature("thermostat"), alternate = "launcher_temperature")
    val LauncherMove = JiibIcon(IconRef.Ligature("open_with"), alternate = "launcher_move")
    val LauncherExtrude = JiibIcon(IconRef.Ligature("output_circle"), alternate = "launcher_extrude")
    val LauncherCalibration = JiibIcon(IconRef.Ligature("tune"), alternate = "launcher_calibration")
    // 24-02 (Wave 0): idle-list Webcam row glyph — owner-confirmed "videocam" (DO NOT change without owner consent).
    val LauncherWebcam = JiibIcon(IconRef.Ligature("videocam"), alternate = "launcher_webcam")
    // 18.3 D-01: the former `database` ligature placeholder is retired for a genuinely-custom side-view
    // filament-spool VectorDrawable (`R.drawable.spool`) — Material Symbols has no spool glyph, so this is
    // a sanctioned hand-authored printer-domain custom (icon-source policy above). The drawable draws the
    // BODY/FLANGES only; the color-reactive filament band is painted at runtime by `SpoolGlyph` (D-02/D-04).
    // Still distinct from `Progress` (donut_large) — they can co-render on PrintStatus (WR-02 still holds).
    val LauncherSpool = JiibIcon(IconRef.Drawable(R.drawable.spool), alternate = "launcher_spool")

    // Spool-screen identity glyph (owner-chosen 2026-06-13, icon law [[dinghy-never-pick-icons-ask]]).
    // SpoolScreen's detail Focus header + empty state render this Material Symbol tinted to the spool's
    // filament color (THEME-01 data carve-out), replacing the custom side-view spool drawable on THAT
    // screen only (LauncherSpool stays for PrintStatus / idle list). `ev_shadow` is verified resolvable
    // in the bundled v2.944 Material Symbols ttf (tools/verify_ligatures.py).
    val SpoolFilament = JiibIcon(IconRef.Ligature("ev_shadow"), alternate = "spool_filament")
    val LauncherMacros = JiibIcon(IconRef.Ligature("bolt"), alternate = "launcher_macros")
    val LauncherConsole = JiibIcon(IconRef.Ligature("terminal"), alternate = "launcher_console")
    val LauncherDrawer = JiibIcon(IconRef.Ligature("more_horiz"), alternate = "launcher_drawer")

    // --- Morphing-root idle foot-bar glyphs (24-02, OWNER-LOCKED — never invent/substitute, icon law).
    // D-09: the third foot button opens the App Drawer (interim System hub); glyph reads NEUTRAL (not Power/red).
    // Ligature owner-confirmed 2026-06-10 and verified present in the bundled v2.944 Material Symbols ttf.
    val FootSystem = JiibIcon(IconRef.Ligature("bottom_panel_open"), alternate = "home_foot_system")

    // Active-print foot-bar glyphs (owner-confirmed 2026-06-16; icon-never-invent law). Pause reuses
    // the registered PauseCircle ("pause_circle"). "resume"/"cancel" verified on-device at UAT.
    val FootResume = JiibIcon(IconRef.Ligature("resume"), alternate = "printstatus_foot_resume")
    val FootCancel = JiibIcon(IconRef.Ligature("cancel"), alternate = "printstatus_foot_cancel")
    // Complete-screen Dismiss foot button (owner 2026-06-16; clears the finished job → standby).
    val FootDismiss = JiibIcon(IconRef.Ligature("clear_all"), alternate = "printstatus_foot_dismiss")

    // --- Ligature-backed (Material Symbols), formerly drawable-backed (18.1 flip, D-09/D-15) ---
    val BabystepCompress = JiibIcon(IconRef.Ligature("compress"), alternate = "babystep_compress")
    val BabystepExpand = JiibIcon(IconRef.Ligature("expand"), alternate = "babystep_expand")
    /**
     * Stop-status glyph (the former octagon token, renamed in 18.1). The bundled Material Symbols `disabled_by_default`
     * ligature renders a square+✕ silhouette — NOT a literal octagon — so the token is named `StatusStop`
     * (D-06/D-14): safety is carried by the distinct *shape* of the glyph, not the octagon outline.
     */
    val StatusStop =
        JiibIcon(IconRef.Ligature("disabled_by_default"), alternate = "status_stop")

    // --- Drawable-backed (hand-authored printer-domain customs; the only two D-10 keepers) ---
    val Nozzle = JiibIcon(IconRef.Drawable(R.drawable.nozzle), alternate = "nozzle")
    val HeatBed = JiibIcon(IconRef.Drawable(R.drawable.heat_bed), alternate = "heat_bed")

    // FanMode (the PART-COOLING fan, Fine-Tune) renders the OFFICIAL Material Symbols `air` glyph via the
    // bundled font (Ligature). Phase 19 D-08 reassigned it from `mode_fan` → `air` (owner-directed cross-phase
    // side-change): `mode_fan_2` is now the Phase-19 generic-fan OUTPUT glyph (OutputFan), so the part-cooling
    // fan moves to `air` to keep the two fans visually distinct. The bundled ttf carries `air`/`speed`, so no
    // local vector is needed. Consumer: FineTuneScreen.kt (the part-fan Fine-Tune param, D-03).
    val FanMode = JiibIcon(IconRef.Ligature("air"), alternate = "fan_mode")
    val Speed = JiibIcon(IconRef.Ligature("speed"), alternate = "speed")

    // --- Phase-19 output-type glyphs (D-01..D-07, OWNER-LOCKED — never invent/substitute, icon law).
    // The Wave-2 detail rows reference these tokens symbolically (never raw ligature strings) so each
    // output family resolves to its owner-chosen glyph behind the verify_ligatures.py resolution gate (D-09). ---
    val OutputHeater = JiibIcon(IconRef.Ligature("mode_heat"), alternate = "output_heater")
    val OutputFan = JiibIcon(IconRef.Ligature("mode_fan_2"), alternate = "output_fan")
    // D-03: shared by led / neopixel / dotstar / pca9533 / pca9632 (every LED-class output family).
    val OutputLed = JiibIcon(IconRef.Ligature("lightbulb_2"), alternate = "output_led")
    val OutputServo = JiibIcon(IconRef.Ligature("cyclone"), alternate = "output_servo")
    // D-05: both the digital and PWM output_pin variants share this row glyph.
    val OutputPin = JiibIcon(IconRef.Ligature("check_box"), alternate = "output_pin")
    val OutputPwmTool = JiibIcon(IconRef.Ligature("vital_signs"), alternate = "output_pwm_tool")
    // D-07: the Outputs section / App-Drawer tile glyph.
    val OutputSection = JiibIcon(IconRef.Ligature("output"), alternate = "output_section")

    // --- Phase-20 System-Information glyphs (D-01..D-10, OWNER-LOCKED — never invent/substitute, icon
    // law [[dinghy-never-pick-icons-ask]]). The whole slate resolves in the bundled v2.944 Material
    // Symbols ttf (20-RESEARCH Icon Ligature Gate). D-03 (CPU temp) REUSES the existing
    // LauncherTemperature (thermostat) token and D-09 (CPU load) REUSES the existing Speed (speed)
    // token — re-registering those ligatures under a new name would trip the iconRef uniqueness guard,
    // so they are NOT re-added here. ---
    val SysInfoTile = JiibIcon(IconRef.Ligature("pulse_alert"), alternate = "sysinfo_tile")        // D-01 drawer tile
    val SysInfoHost = JiibIcon(IconRef.Ligature("dns"), alternate = "sysinfo_host")                 // D-02 hostname/model
    val SysInfoUptime = JiibIcon(IconRef.Ligature("schedule"), alternate = "sysinfo_uptime")        // D-04 uptime
    val SysInfoCpu = JiibIcon(IconRef.Ligature("developer_board"), alternate = "sysinfo_cpu")       // D-05 CPU model/cores
    val SysInfoRam = JiibIcon(IconRef.Ligature("memory"), alternate = "sysinfo_ram")                // D-06 total RAM
    val SysInfoDistro = JiibIcon(IconRef.Ligature("deployed_code"), alternate = "sysinfo_distro")   // D-07 distro
    val SysInfoKernel = JiibIcon(IconRef.Ligature("code_blocks"), alternate = "sysinfo_kernel")     // D-08 kernel
    val SysInfoMemUsage = JiibIcon(IconRef.Ligature("data_usage"), alternate = "sysinfo_mem_usage") // D-10 memory used/total

    // ── System Info device browser (Part 2, 2026-06-21; owner-selected glyphs) ──────────────
    /** MCU/mainboard device row + Focus header. Owner pick: a distinct chip glyph (NOT developer_board,
     *  which is the host CPU detail row shown simultaneously). */
    val McuDevice = JiibIcon(IconRef.Ligature("memory_alt"), alternate = "mcu_device")
    /** Host Reboot action (machine.reboot, destructive). */
    val HostReboot = JiibIcon(IconRef.Ligature("restart_alt"), alternate = "host_reboot")
    /** Restart Moonraker service action (machine.services.restart, hazardous-in-process). */
    val RestartService = JiibIcon(IconRef.Ligature("sync_alt"), alternate = "restart_service")
    /** MCU Firmware Restart (printer.firmware_restart, "restarts all boards"). Shares the `memory`
     *  ligature with SysInfoRam by owner choice — see JiibIconsTest allow-list. */
    val McuFirmwareRestart = JiibIcon(IconRef.Ligature("memory"), alternate = "mcu_firmware_restart")
    /** Restart Klipper (printer.restart soft restart). Shares the `refresh` ligature with Revert by
     *  owner choice — see JiibIconsTest allow-list. */
    val RestartKlipper = JiibIcon(IconRef.Ligature("refresh"), alternate = "restart_klipper")

    // --- FineTune exemplar glyphs (18-06) ---
    // MaxVelocity/MaxAccel render the OFFICIAL Material Symbols glyph via the bundled font (Ligature) —
    // NOT a hand-traced local vector. The font is already a shipped dependency used app-wide (PrintStatus
    // launcher icons etc.), so D-17's "no Material Symbols font dep" rationale does not hold for these.
    // The FineTune glyphs below now ALSO render the OFFICIAL Material Symbols glyph via the bundled font
    // (Ligature) — the 18.1 flip retired their hand-traced local vectors (D-08/D-15).
    val KeyboardReturn = JiibIcon(IconRef.Ligature("keyboard_return"), alternate = "keyboard_return")
    val OutputCircle = JiibIcon(IconRef.Ligature("output_circle"), alternate = "output_circle")
    val MaxVelocity =
        JiibIcon(IconRef.Ligature("arrow_shape_up_stack_2"), alternate = "max_velocity")
    val MaxAccel = JiibIcon(IconRef.Ligature("sprint"), alternate = "max_accel")
    val MinCruise = JiibIcon(IconRef.Ligature("directions_boat"), alternate = "min_cruise")
    val SquareCornerVelocity =
        JiibIcon(IconRef.Ligature("rounded_corner"), alternate = "square_corner_velocity")
    val PressureAdvance =
        JiibIcon(IconRef.Ligature("text_select_move_forward_word"), alternate = "pressure_advance")
    val SmoothTime = JiibIcon(IconRef.Ligature("avg_time"), alternate = "smooth_time")
    val Decrease = JiibIcon(IconRef.Ligature("remove"), alternate = "decrease")
    val Increase = JiibIcon(IconRef.Ligature("add"), alternate = "increase")
    // Increment-SELECTION step −/+ (owner 2026-06-17). USE WHEREVER AN INCREMENT SELECTION IS
    // ADJUSTED (the Move Microstep step-size cycler) — distinct from the generic value ± (Decrease/
    // Increase = remove/add). Both ligatures verified present in the bundled v2.944 ttf.
    val StatMinus1 = JiibIcon(IconRef.Ligature("stat_minus_1"), alternate = "decrement_one")
    val StatPlus1 = JiibIcon(IconRef.Ligature("stat_1"), alternate = "increment_one")
    val InputCircle = JiibIcon(IconRef.Ligature("input_circle"), alternate = "input_circle")
    // Revert-to-default header action (setting-adjustment compliance, 2026-06-13; owner-chosen
    // `refresh` ligature, img/material-icon-bucket.json). Bare end-aligned glyph in the Focus header.
    val Revert = JiibIcon(IconRef.Ligature("refresh"), alternate = "revert")

    // --- Phase-23 redesign glyphs (23-03, OWNER-LOCKED — never invent/substitute, icon law).
    // Assignments from owner's 2026-06-09 Spoolman sketch session; all ligatures verified present
    // in the bundled v2.944 Material Symbols ttf (plan 23-01 verify_ligatures.py gate exits 0).
    val Sort = JiibIcon(IconRef.Ligature("sort"), alternate = "sort")
    val FilterList = JiibIcon(IconRef.Ligature("filter_list"), alternate = "filter_list")
    // expand_circle_up = Load spool (owner-reassigned from play_circle — see bucket notes).
    val ExpandCircleUp = JiibIcon(IconRef.Ligature("expand_circle_up"), alternate = "expand_circle_up")
    // expand_circle_down = Unload spool (owner-reassigned from stop_circle — see bucket notes).
    val ExpandCircleDown = JiibIcon(IconRef.Ligature("expand_circle_down"), alternate = "expand_circle_down")
    val ResetWrench = JiibIcon(IconRef.Ligature("reset_wrench"), alternate = "reset_wrench")
    val ResetSettings = JiibIcon(IconRef.Ligature("reset_settings"), alternate = "reset_settings")

    // --- Phase-23 SpoolScreen pilot glyphs (23-06, OWNER-LOCKED — from img/material-icon-bucket.json).
    // All five ligatures verified present in the bundled v2.944 Material Symbols ttf.
    // match_case = sort by name; calendar_clock = sort by date; experiment = filter by filament family.
    // home = navigate to PrintStatus (foot button); qr_code = open QR scan (foot button).
    val MatchCase = JiibIcon(IconRef.Ligature("match_case"), alternate = "match_case")
    val CalendarClock = JiibIcon(IconRef.Ligature("calendar_clock"), alternate = "calendar_clock")
    val Experiment = JiibIcon(IconRef.Ligature("experiment"), alternate = "experiment")
    val Home = JiibIcon(IconRef.Ligature("home"), alternate = "home")
    val QrCode = JiibIcon(IconRef.Ligature("qr_code"), alternate = "qr_code")

    // --- Browse screens (Phase 25) — OWNER-LOCKED (D-21). Sources: img/material-icon-bucket.json
    // assignments, existing JiibIcons tokens (reused), and two owner-approved out-of-bucket entries
    // (play_arrow / radio_button_unchecked, sanctioned 2026-06-10). Never the same glyph twice on one screen.
    // Files actions:
    val Print = JiibIcon(IconRef.Ligature("print"), alternate = "files_print")                         // bucket: "start a print"
    val Delete = JiibIcon(IconRef.Ligature("delete"), alternate = "files_delete")                      // bucket entry
    // Files sort-by-size (owner-selected `line_weight`, 2026-06-13). Full Material Symbols font
    // bundled so it resolves; new ligature, no uniqueness conflict.
    val LineWeight = JiibIcon(IconRef.Ligature("line_weight"), alternate = "files_sort_size")
    // Console filter toggles (hide-state glyphs — the icon shown when the toggle is in the HIDE state):
    val HideTemps = JiibIcon(IconRef.Ligature("mode_heat_off"), alternate = "console_hide_temps")      // bucket: "hide temperature messages in console"
    val HideTimelapse = JiibIcon(IconRef.Ligature("video_camera_back"), alternate = "console_hide_timelapse") // bucket: "webcam menu icon, also show timelapse in console"
    val HidePrompts = JiibIcon(IconRef.Ligature("chat_error"), alternate = "console_hide_prompts")     // bucket: "hide macro prompt messages in console"
    // Macros screen:
    val MacrosLeader = JiibIcon(IconRef.Ligature("code"), alternate = "macros_leader")                  // bucket: "macros" (distinct from LauncherMacros=bolt)
    val ManageMacros = JiibIcon(IconRef.Ligature("bookmark_manager"), alternate = "macros_manage")      // bucket entry (more semantically precise than reusing LauncherCalibration=tune)
    val ExecuteMacro = JiibIcon(IconRef.Ligature("play_arrow"), alternate = "macros_execute")           // owner-approved 2026-06-10 (not in bucket; sanctioned new entry)
    val UnbookmarkedMacro = JiibIcon(IconRef.Ligature("radio_button_unchecked"), alternate = "macros_unbookmarked") // owner-approved 2026-06-10 (existing code ligature, out-of-bucket but sanctioned)
    // WR-07 (registry conformance — NOT new icon choices): the Macros ManageMode Show-hidden toggle pair
    // carried the old SystemMacrosScreen glyphs forward as raw ligature strings, and the SpoolWarningGuard
    // kept its pre-existing raw "warning" MaterialSymbol. Registered here so the planned font subset
    // (which iterates this registry) and the verify_ligatures.py resolution gate cover them.
    val Visibility = JiibIcon(IconRef.Ligature("visibility"), alternate = "macros_show_hidden_on")      // revealed state (pre-existing glyph, preserved)
    val VisibilityOff = JiibIcon(IconRef.Ligature("visibility_off"), alternate = "macros_show_hidden_off") // hidden state (pre-existing glyph, preserved)
    val Warning = JiibIcon(IconRef.Ligature("warning"), alternate = "warning")                          // SpoolWarningGuard row glyph (pre-existing, preserved)

    // --- Phase-27 calibration hub routine glyphs (27-01; ligatures RE-ASSIGNED 2026-06-13 to the
    // owner's icon-bucket selections in img/material-icon-bucket.json — superseding the 27-01
    // placeholders. The full Material Symbols Outlined font is bundled, so every ligature renders.)
    // Token names follow the Routine* convention from 27-PATTERNS.md §"Icon Registration Gate".
    val RoutineProbeCalibrate = JiibIcon(IconRef.Ligature("detector"), alternate = "routine_probe_calibrate")
    val RoutineBedMesh = JiibIcon(IconRef.Ligature("blur_linear"), alternate = "routine_bed_mesh")
    val RoutineScrewsTilt = JiibIcon(IconRef.Ligature("rule_settings"), alternate = "routine_screws_tilt")
    val RoutineZTilt = JiibIcon(IconRef.Ligature("linear_scale"), alternate = "routine_z_tilt")
    val RoutineQgl = JiibIcon(IconRef.Ligature("linked_services"), alternate = "routine_qgl")
    val RoutineOpen = JiibIcon(IconRef.Ligature("play_arrow"), alternate = "routine_open")          // owner-approved 2026-06-23: Calibration hub Open foot button (play_arrow, font-present via ExecuteMacro)

    // --- Phase-27 review fix WR-04 (registry conformance — NOT new icon choices): the rebuilt
    // Move/ScrewsTilt/BedMesh screens still drew these 9 glyphs as raw MaterialSymbol/inline-ligature
    // call sites, bypassing the subset source of truth and the verify_ligatures.py gate. Promoted
    // VERBATIM — the EXISTING shipping glyphs are preserved unchanged (drift-guarding only, per the
    // 27-01 "bless as-is" precedent and [[dinghy-never-pick-icons-ask]]); call sites now route
    // through JiibIconView.
    val JogXPlus = JiibIcon(IconRef.Ligature("arrow_forward"), alternate = "jog_x_plus")              // Move: X+ jog cell
    val HomeStateHomed = JiibIcon(IconRef.Ligature("in_home_mode"), alternate = "home_state_homed")   // Move: XY-home cell, homed
    val HomeStateUnhomed = JiibIcon(IconRef.Ligature("wifi_home"), alternate = "home_state_unhomed")  // Move: XY-home cell, needs homing
    val ScrewPending = JiibIcon(IconRef.Ligature("point_scan"), alternate = "screw_pending")          // ScrewsTilt: not yet probed
    val ScrewBase = JiibIcon(IconRef.Ligature("anchor"), alternate = "screw_base")                    // ScrewsTilt: base/reference screw
    val ScrewInTolerance = JiibIcon(IconRef.Ligature("commit"), alternate = "screw_in_tolerance")     // ScrewsTilt: within tolerance
    val ScrewTurnCcw = JiibIcon(IconRef.Ligature("rotate_left"), alternate = "screw_turn_ccw")        // ScrewsTilt: turn counter-clockwise
    val ScrewTurnCw = JiibIcon(IconRef.Ligature("rotate_right"), alternate = "screw_turn_cw")         // ScrewsTilt: turn clockwise
    val MeshEmpty = JiibIcon(IconRef.Ligature("grid_off"), alternate = "mesh_empty")                  // BedMesh: empty-state Focus
    /** BedMesh Field — Clear Mesh row leading glyph (owner-assigned 2026-06-22). */
    val BlurOff = JiibIcon(IconRef.Ligature("blur_off"), alternate = "blur_off")

    // PrintStatus standby-home identity (header law, 2026-06-13). Owner-named "mode_standby".
    // Swapped to the e-stop while printing. Confirmed resolvable in the bundled v2.944 ttf.
    val PrintStatusStandby = JiibIcon(IconRef.Ligature("mode_standby"), alternate = "print_status_standby")

    // --- Move Hub redesign glyphs (Task D3, OWNER-APPROVED — never invent/substitute, icon law
    // [[dinghy-never-pick-icons-ask]]). The five Field-list mode rows; homing rows reuse
    // HomeStateUnhomed and Microstep reuses FineTune. All five ligatures are unique.
    val MoveTouch = JiibIcon(IconRef.Ligature("my_location"), alternate = "move_touch")
    val MoveXY = JiibIcon(IconRef.Ligature("control_camera"), alternate = "move_xy")
    val MoveZ = JiibIcon(IconRef.Ligature("swap_vert"), alternate = "move_z")
    val SavedLocation = JiibIcon(IconRef.Ligature("bookmark"), alternate = "saved_location")
    val SaveLocation = JiibIcon(IconRef.Ligature("bookmark_add"), alternate = "save_location")
    val MoveHomeAll = JiibIcon(IconRef.Ligature("home_app_logo"), alternate = "move_home_all")
    val MoveDisableMotors = JiibIcon(IconRef.Ligature("stack_off"), alternate = "move_disable_motors")
    val MoveToBookmark = JiibIcon(IconRef.Ligature("place"), alternate = "move_to_bookmark")

    /** Move → Endstops: inactive/open endstop state (owner-assigned 2026-06-18). */
    val CropFree = JiibIcon(IconRef.Ligature("crop_free"), alternate = "crop_free")
    /** Move → Endstops: triggered/active endstop state AND the Endstops list-row icon
     *  (owner-assigned 2026-06-18). */
    val CenterFocusStrong = JiibIcon(IconRef.Ligature("center_focus_strong"), alternate = "center_focus_strong")

    // --- Phase-28 System/Settings cluster glyphs (28-01, OWNER-LOCKED D-21).
    // All ligatures verified resolvable in the bundled v2.944 Material Symbols ttf.
    // `settings` / `info` / `power_settings_new` are new-to-NEEDED (not yet in verify_ligatures.py).
    // `palette` is already registered as Palette (Spool detail pane) — `SystemRowTheme` and `Palette`
    // never co-occur on one screen, so the shared ligature is allow-listed in JiibIconsTest (WR-02).
    val LauncherFineTune = JiibIcon(IconRef.Ligature("line_style"), alternate = "launcher_fine_tune")
    val SystemRowPrinters = JiibIcon(IconRef.Ligature("android_wifi_3_bar_plus"), alternate = "system_row_printers")
    val SystemRowSettings = JiibIcon(IconRef.Ligature("settings"), alternate = "system_row_settings")

    // --- Temperature screen glyphs (Task 8, OWNER-CHOSEN — never invent/substitute, icon law
    // [[dinghy-never-pick-icons-ask]]).
    // `format_list_bulleted` = return to Monitoring mode (sensor-list view); owner-selected.
    // `settings` = Temperature Settings (sensor-display picker); owner-selected. Reuses the system
    // settings glyph — Temperature and System pages never co-render, so the shared ligature is
    // allow-listed in JiibIconsTest (WR-02 precedent, same as palette/output_circle).
    /** Temperature: return to Monitoring mode (owner-chosen; monitoring == the sensor list view). */
    val MonitorMode = JiibIcon(IconRef.Ligature("format_list_bulleted"), alternate = "temp_monitor_mode")
    /** Temperature: Settings (sensor-display picker). Reuses the system settings glyph. */
    val TempSettings = JiibIcon(IconRef.Ligature("settings"), alternate = "temp_settings")
    val SystemRowTheme = JiibIcon(IconRef.Ligature("palette"), alternate = "system_row_theme")
    val SystemRowAbout = JiibIcon(IconRef.Ligature("info"), alternate = "system_row_about")
    val SystemRowPower = JiibIcon(IconRef.Ligature("power_settings_new"), alternate = "system_row_power")

    // --- Control baseline audit (2026-06-14, OWNER-LOCKED master-list §f #1/#2) — sort-direction
    // arrow pair + the shared close glyph. Registered now; call sites (SortFilterControlRow's raw
    // arrow_upward/arrow_downward and PromptDialog's raw "close") migrate in later audit phases.
    val SortAsc = JiibIcon(IconRef.Ligature("arrow_drop_up"), alternate = "sort_asc")
    val SortDesc = JiibIcon(IconRef.Ligature("arrow_drop_down"), alternate = "sort_desc")
    val Close = JiibIcon(IconRef.Ligature("close"), alternate = "close")

    // --- Outputs foot-bar actions (owner-chosen 2026-06-21; icon law). On = power, Off = power_off.
    /** Outputs switch/scrubber/LED foot: turn the output ON. */
    val Power = JiibIcon(IconRef.Ligature("power"), alternate = "power")
    /** Outputs switch/scrubber/LED foot: turn the output OFF. */
    val PowerOff = JiibIcon(IconRef.Ligature("power_off"), alternate = "power_off")

    // --- App/Printer Settings split glyphs (Task 0.1, OWNER-LOCKED — never invent/substitute, icon law
    // [[dinghy-never-pick-icons-ask]]). All five ligatures verified resolvable in the bundled Material
    // Symbols ttf (verify_ligatures.py gate). Groundwork for the Settings split feature.
    val AppSettings     = JiibIcon(IconRef.Ligature("mobile_gear"),               alternate = "app_settings")
    val PrinterSettings = JiibIcon(IconRef.Ligature("print"),                     alternate = "printer_settings")
    val ManagePrinters  = JiibIcon(IconRef.Ligature("format_list_numbered"),      alternate = "manage_printers")
    val TextSize        = JiibIcon(IconRef.Ligature("format_size"),               alternate = "text_size")
    val Rename          = JiibIcon(IconRef.Ligature("drive_file_rename_outline"), alternate = "rename")
    // Owner 2026-06-17: `wb_iridescent` — the original `fluorescent` ligature is NOT in the pinned
    // v2.944 bundled font (it's a newer Material Symbol), so it rendered as literal text. Labels the
    // "Keep Screen On" app setting. Token name kept to avoid churn; alternate updated to match.
    val Fluorescent     = JiibIcon(IconRef.Ligature("wb_iridescent"),             alternate = "keep_screen_on")
    val ShieldLock      = JiibIcon(IconRef.Ligature("shield_lock"),               alternate = "shield_lock")

    // --- Foot-bar button conformance (2026-06-17, OWNER-LOCKED — icon law [[dinghy-never-pick-icons-ask]]).
    // play_circle / stop_circle are from img/material-icon-bucket.json; hourglass / print_add / save /
    // tab_close are new — ALL gated by tools/verify_ligatures.py (Task 2 step 4). mode_heat_off is shared
    // with HideTemps (Temp vs Console never co-render) → allow-listed in JiibIconsTest.
    val CalibrationRun   = JiibIcon(IconRef.Ligature("play_circle"),   alternate = "calibration_run")
    val CalibrationWait  = JiibIcon(IconRef.Ligature("hourglass"),     alternate = "calibration_wait")
    val CalibrationAbort = JiibIcon(IconRef.Ligature("stop_circle"),   alternate = "calibration_abort")
    val PrinterAdd       = JiibIcon(IconRef.Ligature("print_add"),     alternate = "printer_add")
    val Save             = JiibIcon(IconRef.Ligature("save"),          alternate = "save")
    val DialogClose      = JiibIcon(IconRef.Ligature("tab_close"),     alternate = "dialog_close")
    /** Temperature: Presets — owner-chosen ligature `thermostat_auto` (2026-06-17; pre-verified in
     *  bundled v2.944 font; distinct from `thermostat`=LauncherTemperature,
     *  `format_list_bulleted`=MonitorMode). */
    val TempPresets      = JiibIcon(IconRef.Ligature("thermostat_auto"), alternate = "temp_presets")

    // --- Heat Presets (owner-chosen glyphs 2026-06-17). Presets themselves use TempPresets
    // (`thermostat_auto`) in lists/headers; these are the action glyphs. ---
    /** Heat-preset Add foot action — owner-chosen `thermometer_gain`. */
    val HeatPresetAdd    = JiibIcon(IconRef.Ligature("thermometer_gain"), alternate = "heat_preset_add")
    /** Heat-preset Edit Focus action — owner-chosen `edit_square`. */
    val HeatPresetEdit   = JiibIcon(IconRef.Ligature("edit_square"), alternate = "heat_preset_edit")
    /** Heat-preset delete REUSES the real Files-delete `delete` glyph. */
    val HeatPresetDelete = Delete

    // --- Connection Editor glyphs (feat/connection-editor-redesign, OWNER-LOCKED — never
    // invent/substitute, icon law [[dinghy-never-pick-icons-ask]]). All verified resolvable in the
    // refreshed Material Symbols ttf (verify_ligatures.py gate). Font refreshed on this branch to
    // include the `123` glyph for the Port row (glyph name `_123` in the GSUB output set).
    /** Connection editor Focus header / screen identity glyph. Reuses SystemRowPrinters. */
    // Focus header → android_wifi_3_bar_plus → JiibIcons.SystemRowPrinters (existing)
    /** Name row label glyph — owner-chosen `text_fields`. */
    val TextFields   = JiibIcon(IconRef.Ligature("text_fields"),  alternate = "conn_name_row")
    /** Host row + Discovered-printer row glyph. Reuses SysInfoCpu (developer_board). */
    // Host/Discovered row → developer_board → JiibIcons.SysInfoCpu (existing)
    /** API key row glyph — owner-chosen `vpn_key`. */
    val VpnKey       = JiibIcon(IconRef.Ligature("vpn_key"),      alternate = "conn_api_key_row")
    /** Find-on-network row glyph — owner-chosen `search`. */
    val Search       = JiibIcon(IconRef.Ligature("search"),       alternate = "conn_find_row")
    /** Advanced row glyph. Reuses LauncherCalibration (tune). */
    // Advanced row → tune → JiibIcons.LauncherCalibration (existing)
    /** Test connection foot action glyph — owner-chosen `network_ping`. */
    val NetworkPing  = JiibIcon(IconRef.Ligature("network_ping"), alternate = "conn_test_action")
    /** Probe pass indicator glyph. Reuses CheckCircle (check_circle). */
    // Probe pass → check_circle → JiibIcons.CheckCircle (existing)
    /** Probe fail indicator glyph — owner-chosen `x_circle`. */
    val XCircle      = JiibIcon(IconRef.Ligature("x_circle"),     alternate = "conn_probe_fail")
    /** Port row label glyph — owner-chosen `123` (GSUB output glyph `_123`). Font was refreshed
     *  on this branch (feat/connection-editor-redesign) to include this glyph. */
    val Numbers      = JiibIcon(IconRef.Ligature("123"),          alternate = "123")

    /** Temperature screen Monitor→Adjust toggle — owner-chosen `fire_check`. */
    val FireCheck  = JiibIcon(IconRef.Ligature("fire_check"),  alternate = "temp_enter_adjust")
    /** The built-in "OFF" row in the Heaters list — owner-chosen `thermometer`. */
    val HeatersOff = JiibIcon(IconRef.Ligature("thermometer"), alternate = "heaters_off")

    /** Z-babystep increment row — owner-chosen `stacks` glyph (layered-Z reading). */
    val Babystep = JiibIcon(IconRef.Ligature("stacks"), alternate = "babystep")

    // --- Theme screen glyphs (Task 7, OWNER-LOCKED — never invent/substitute, icon law
    // [[dinghy-never-pick-icons-ask]]). All five ligatures selected by owner for the theme-screen rework.
    val Contrast     = JiibIcon(IconRef.Ligature("contrast"),      alternate = "contrast")
    val InvertColors = JiibIcon(IconRef.Ligature("invert_colors"), alternate = "invert_colors")
    val Colors       = JiibIcon(IconRef.Ligature("colors"),        alternate = "colors")
    val Star         = JiibIcon(IconRef.Ligature("star"),          alternate = "star")
    val Shuffle      = JiibIcon(IconRef.Ligature("shuffle"),       alternate = "shuffle")
    val HumidityHigh = JiibIcon(IconRef.Ligature("humidity_high"), alternate = "humidity_high")
    val WaterDrop    = JiibIcon(IconRef.Ligature("water_drop"),    alternate = "water_drop")

    // --- Bed Mesh Config subpage row glyphs (Task 9, OWNER-DECIDED — never invent/substitute, icon law
    // [[dinghy-never-pick-icons-ask]]). All four ligatures confirmed present in the bundled Material
    // Symbols ttf (verify_ligatures.py gate). Assigned by owner 2026-06-22.
    /** Mesh Config → View Type row glyph. */
    val BlurCircular = JiibIcon(IconRef.Ligature("blur_circular"), alternate = "blur_circular")
    /** Mesh Config → High Color row glyph. */
    val HdrStrong    = JiibIcon(IconRef.Ligature("hdr_strong"),    alternate = "hdr_strong")
    /** Mesh Config → Low Color row glyph. */
    val HdrWeak      = JiibIcon(IconRef.Ligature("hdr_weak"),      alternate = "hdr_weak")
    /** Mesh Config → Preview row glyph. */
    val Preview      = JiibIcon(IconRef.Ligature("preview"),       alternate = "preview")
    /** Mesh Config → View Type selector: 2D Heatmap option glyph. */
    val MeshView2D   = JiibIcon(IconRef.Ligature("contrast_square"), alternate = "contrast_square")
    /** Mesh Config → View Type selector: 3D Mesh option glyph. */
    val MeshViewIso  = JiibIcon(IconRef.Ligature("ssid_chart"),    alternate = "ssid_chart")
    /** Mesh Config → View Type selector: Probe Points option glyph. */
    val MeshViewProbe = JiibIcon(IconRef.Ligature("transition_dissolve"), alternate = "transition_dissolve")

    // --- Probe Section glyphs (probe-section Task 0, OWNER-ASSIGNED 2026-06-27 — FINAL.
    // Never substitute; hard owner law [[dinghy-never-pick-icons-ask]].
    // All six ligatures registered in img/material-icon-bucket.json and verified by
    // tools/verify_ligatures.py (Task-0 gate).
    // REUSE (no new tokens): RoutineProbeCalibrate=detector (Z-Offset Calibrate header + Probe hub
    // row + Calibration hub row), Babystep=stacks (Apply Babystepping row/header),
    // CalibrationRun=play_circle (Run Accuracy action). )
    /** Probe Section hub tile + Probe Test screen header/row (lab_research). */
    val ProbeTestTool    = JiibIcon(IconRef.Ligature("lab_research"),              alternate = "probe_test_tool")
    /** Probe Test — Query the probe status action glyph (indeterminate_question_box). */
    val ProbeQuery       = JiibIcon(IconRef.Ligature("indeterminate_question_box"), alternate = "probe_query")
    /** Probe Test — Probe Once action glyph (labs). */
    val ProbeOnce        = JiibIcon(IconRef.Ligature("labs"),                      alternate = "probe_once")
    /** Single Probe (one PROBE) — owner-picked glyph. */
    val ProbeSingle      = JiibIcon(IconRef.Ligature("1x_mobiledata"),             alternate = "probe_single")
    /** Probe Accuracy (PROBE_ACCURACY stats) — owner-picked glyph. */
    val ProbeAccuracy    = JiibIcon(IconRef.Ligature("bar_chart_4_bars"),          alternate = "probe_accuracy")
    /** Eddy Current sensor: Calibrate screen header + hub row glyph (detector_status). */
    val EddyCalibrate    = JiibIcon(IconRef.Ligature("detector_status"),           alternate = "eddy_calibrate")
    /** Eddy Current sensor: Tap Threshold screen header + hub row glyph (detector_offline). */
    val EddyTap          = JiibIcon(IconRef.Ligature("detector_offline"),          alternate = "eddy_tap")
    /** Eddy Current sensor: Drive Current screen header + hub row glyph (detector_smoke). */
    val EddyDriveCurrent = JiibIcon(IconRef.Ligature("detector_smoke"),            alternate = "eddy_drive_current")

    /**
     * Hand-rolled list of every entry above — the Phase-22-readiness handle (the registry-iteration
     * source for `tools/subset-symbols` and the uniqueness test). Add new entries here when you add a
     * `val` above (no reflection — this stays explicit and grep-auditable on the minSdk-23 floor).
     */
    val all: List<JiibIcon> = listOf(
        Back, Check, Edit, FineTune, Height, Altitude, Layers, Scale, Storefront, TimerDown, TimerUp,
        Progress, PauseCircle, Serif, DataFont,
        Inventory, Palette, CalendarAddOn, CheckCircle, Archive,
        LauncherFiles, LauncherTemperature, LauncherMove, LauncherExtrude, LauncherCalibration,
        LauncherWebcam, LauncherSpool, LauncherMacros, LauncherConsole, LauncherDrawer,
        FootSystem, FootResume, FootCancel, FootDismiss,
        BabystepCompress, BabystepExpand, StatusStop, Nozzle, HeatBed, FanMode, Speed,
        KeyboardReturn, OutputCircle, MaxVelocity, MaxAccel, MinCruise, SquareCornerVelocity,
        PressureAdvance, SmoothTime, Decrease, Increase, InputCircle, Revert,
        OutputHeater, OutputFan, OutputLed, OutputServo, OutputPin, OutputPwmTool, OutputSection,
        SysInfoTile, SysInfoHost, SysInfoUptime, SysInfoCpu, SysInfoRam, SysInfoDistro,
        SysInfoKernel, SysInfoMemUsage,
        McuDevice, HostReboot, RestartService, McuFirmwareRestart, RestartKlipper,
        Sort, FilterList, ExpandCircleUp, ExpandCircleDown, ResetWrench, ResetSettings, QrCodeScanner, DeleteSweep,
        SpoolChange, SpoolClear, SpoolLocation, SpoolUsageStale, SpoolChangedExternally, SpoolFilament,
        ScanNoPermission, ScanNoCamera, ScanCameraBusy, ScanCameraFlip, ScanUsePicker,
        MatchCase, CalendarClock, Experiment, Home, QrCode,
        Print, Delete, LineWeight, HideTemps, HideTimelapse, HidePrompts,
        MacrosLeader, ManageMacros, ExecuteMacro, UnbookmarkedMacro,
        Visibility, VisibilityOff, Warning, StatMinus1, StatPlus1,
        RoutineProbeCalibrate, RoutineBedMesh, RoutineScrewsTilt, RoutineZTilt, RoutineQgl, RoutineOpen,
        JogXPlus, HomeStateHomed, HomeStateUnhomed, CropFree, CenterFocusStrong,
        ScrewPending, ScrewBase, ScrewInTolerance, ScrewTurnCcw, ScrewTurnCw,
        MeshEmpty, BlurOff,
        LauncherFineTune, SystemRowPrinters, SystemRowSettings, SystemRowTheme,
        SystemRowAbout, SystemRowPower,
        PrintStatusStandby,
        MoveTouch, MoveXY, MoveZ, SavedLocation, SaveLocation,
        MoveHomeAll, MoveDisableMotors,
        MonitorMode, TempSettings,
        SortAsc, SortDesc, Close,
        Power, PowerOff,
        AppSettings, PrinterSettings, ManagePrinters, TextSize, Rename, Fluorescent, ShieldLock,
        CalibrationRun, CalibrationWait, CalibrationAbort, PrinterAdd, Save, DialogClose,
        TempPresets,
        HeatPresetAdd, HeatPresetEdit, FireCheck, HeatersOff,
        TextFields, VpnKey, Search, NetworkPing, XCircle, Numbers,
        Babystep,
        Contrast, InvertColors, Colors, Star, Shuffle, HumidityHigh, WaterDrop,
        BlurCircular, HdrStrong, HdrWeak, Preview, MeshView2D, MeshViewIso, MeshViewProbe,
        ProbeTestTool, ProbeQuery, ProbeOnce, ProbeSingle, ProbeAccuracy, EddyCalibrate, EddyTap, EddyDriveCurrent,
    )
}
