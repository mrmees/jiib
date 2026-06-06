package works.mees.dinghy.ui.printstatus

/**
 * The PURE spool-aware Preheat-selection decision (Phase 16 / D-01, RESEARCH §5). Extracted to a
 * host-testable function so the direct-temps-vs-selector branch is unit-gated (`PreheatTest`)
 * rather than buried inline in the Compose screen.
 *
 * 16-06 reads the resolved spool's `settings_extruder_temp` / `settings_bed_temp` (both nullable
 * `Int`), calls [selectPreheatPath], and then:
 *  - on [PreheatPath.DirectTemps], dispatches a per-temp `setHeater` ONLY for each non-null temp,
 *    each gated on that heater's capability — a `null` temp fires NOTHING for that heater;
 *  - on [PreheatPath.OpenSelector], opens the Phase-5 `PresetSelector`.
 *
 * This fn decides the PATH only — no Compose, no spool fetch, no command dispatch.
 */
sealed interface PreheatPath {
    /**
     * Heat directly to the spool's temps. Each field is NULLABLE and carried AS-IS: a missing temp
     * stays `null` and is NEVER coerced to `0` (a `0` target would silently command a cooldown of
     * the un-set heater). The caller fires `setHeater` only for the non-null temp(s).
     */
    data class DirectTemps(val nozzle: Int?, val bed: Int?) : PreheatPath

    /** No usable spool temps — fall back to the Phase-5 PresetSelector flow. */
    data object OpenSelector : PreheatPath
}

/**
 * Decide the Preheat path (D-01 / RESEARCH §5):
 *  - Spoolman present AND at least one of [nozzleTemp]/[bedTemp] non-null -> [PreheatPath.DirectTemps]
 *    carrying each temp as-is (a null temp stays null, never 0 — per-temp guard).
 *  - No Spoolman, OR both temps null -> [PreheatPath.OpenSelector].
 */
fun selectPreheatPath(spoolmanPresent: Boolean, nozzleTemp: Int?, bedTemp: Int?): PreheatPath =
    if (spoolmanPresent && (nozzleTemp != null || bedTemp != null)) {
        PreheatPath.DirectTemps(nozzle = nozzleTemp, bed = bedTemp)
    } else {
        PreheatPath.OpenSelector
    }
