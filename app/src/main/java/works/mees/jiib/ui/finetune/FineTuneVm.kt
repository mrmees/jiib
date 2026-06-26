package works.mees.jiib.ui.finetune

/**
 * Per-tuner config-reset baselines (TUNE-05 / D-16), folded from the store's one-shot handshake
 * StateFlows. Each is NULLABLE: a null baseline means the printer never reported that config section
 * (e.g. firmware_retraction on the dev printers, or any absent config key) — the reset for that tuner
 * MUST be a NO-OP (REVIEW #3), enforced where the screen wires `onReset`.
 *
 * Motion limits + PA/smooth are RAW values (mm/s, mm/s², seconds, ratio). [minCruise] is the RAW ratio
 * (0.0..1.0); the holder converts to a display percent and the reset sends the ratio on the wire.
 *
 * Speed %, flow %, and the part-cooling fan deliberately have NO baseline here: speed/flow reset is the
 * protocol-neutral `M220 S100` / `M221 S100` (no config read needed), and Klipper `[fan]` has no
 * persistent configured speed (RESEARCH A2 / Open-Q1) — the part-fan tile gets no reset affordance.
 */
data class FineTuneBaselines(
    val maxVelocity: Double? = null,
    val maxAccel: Double? = null,
    /** RAW ratio 0.0..1.0 (the wire form for SET_VELOCITY_LIMIT MINIMUM_CRUISE_RATIO). */
    val minCruise: Double? = null,
    val scv: Double? = null,
    val pressureAdvance: Double? = null,
    /** From the CONFIG key `pressure_advance_smooth_time` (NOT the live status field), Pitfall 2. */
    val smoothTime: Double? = null,
    val retractLength: Double? = null,
    val retractSpeed: Double? = null,
    val unretractExtraLength: Double? = null,
    val unretractSpeed: Double? = null,
)

/**
 * The Fine-Tune live-adjust view-model. Plain Kotlin (NO Compose annotations) so it is host-testable.
 *
 * Carries DISPLAY-SCALED current values (the holder does ratio→% and 0..1→% at the display boundary,
 * RESEARCH Pitfall 1 — never the reducer). Values are nullable where the printer has not reported them
 * (`null` → the tile shows "—", never a fabricated 0, D-20).
 *
 *  - [speedPct] / [flowPct] — `speed_factor` / `extrude_factor` ratios ×100 (D-03 / D-08).
 *  - [maxVelocity] / [maxAccel] / [scv] — RAW motion limits (mm/s, mm/s², mm/s) for display (D-04/05/07).
 *  - [minCruisePct] — `minimum_cruise_ratio` shown as a PERCENT (ratio ×100, REVIEW #9 / D-06).
 *  - [pressureAdvance] / [smoothTime] — RAW seconds (D-09 / D-10).
 *  - [partFanPct] — `fan.speed` (0..1) ×100 as a percent (D-11).
 *  - [hasGcodeMove] / [hasToolhead] / [hasExtruder] / [hasFan] / [hasFwRetraction] — capability gates;
 *    absent tunables are HIDDEN, not disabled (SC-2 / D-02).
 *  - [retractLength] / [unretractExtraLength] / [retractSpeed] / [unretractSpeed] — live FW-retraction
 *    sub-values (RAW; null when the object is absent or unreported).
 *  - [baselines] — the per-tuner config reset baselines (nullable; null → reset is a no-op, REVIEW #3).
 */
data class FineTuneVm(
    // --- Motion (display-scaled / raw) ---
    val speedPct: Int? = null,
    val maxVelocity: Double? = null,
    val maxAccel: Double? = null,
    val minCruisePct: Int? = null,
    val scv: Double? = null,
    // --- Extrusion (display-scaled / raw) ---
    val flowPct: Int? = null,
    val pressureAdvance: Double? = null,
    val smoothTime: Double? = null,
    val partFanPct: Int? = null,
    // --- FW-retraction sub-values (raw) ---
    val retractLength: Double? = null,
    val unretractExtraLength: Double? = null,
    val retractSpeed: Double? = null,
    val unretractSpeed: Double? = null,
    // --- capability gates (absent → HIDDEN, SC-2) ---
    val hasGcodeMove: Boolean = false,
    val hasToolhead: Boolean = false,
    val hasExtruder: Boolean = false,
    val hasFan: Boolean = false,
    val hasFwRetraction: Boolean = false,
    // --- reset baselines (nullable per-tuner; null → no-op reset, REVIEW #3) ---
    val baselines: FineTuneBaselines = FineTuneBaselines(),
    // --- D-15 whole-group state-flip busy lock (REVIEW #2) ---
    val groupBusy: Boolean = false,
)
