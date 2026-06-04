package works.mees.dinghy.ui.spool

import works.mees.dinghy.spool.SpoolmanSpool
import works.mees.dinghy.spool.SpoolmanStatus
import works.mees.dinghy.state.FilePreviewMetadata

/**
 * PURE, host-tested warn-only print-start gate (SPOOL-07 / D-01). The EXACT analog of
 * [works.mees.dinghy.ui.files.deleteAllowed] — same directory, same pure-predicate role — except this
 * one NEVER blocks: it returns an ORDERED list of amber "proceed-at-peril" warnings the Print confirm
 * flow taps past in one action. A clean pass returns an EMPTY list (the normal `Print file` confirm
 * shows unchanged). No Android, no I/O, fully unit-testable ([works.mees.dinghy.spool.PrintStartGateTest]).
 *
 * THE WARN-ONLY CONTRACT IS LOAD-BEARING (D-01): there is no "block" return path anywhere in this
 * function. Every condition below appends a [SpoolWarning] and execution continues; nothing short-circuits
 * the user's print.
 *
 * D-01 condition table (each → one appended warning):
 *  - no active spool (status has no `spool_id`, OR the active spool detail is absent) → [SpoolWarning.NoActiveSpool]
 *  - material family mismatch (file wants PLA, active is PETG; family-partial + case-insensitive per
 *    D-05; a multi-family file matches if ANY family matches) → [SpoolWarning.MaterialMismatch]
 *  - remaining_weight < filament_weight_total + [marginGrams]-derived buffer → [SpoolWarning.LowFilament].
 *    SKIPPED ENTIRELY when `file.filamentWeightTotal == null` (no length/density fallback this phase).
 *  - active spool archived (D-09 allow-with-warning) → [SpoolWarning.ArchivedSpool]
 *  - non-empty `pending_reports` (D-11 stale-not-lost) → [SpoolWarning.PendingReports]
 *  - active-spool detail fetch failed → [SpoolWarning.FetchFailed]
 *
 * The discretionary low-remaining safety buffer is `max(total * 1.05, total + 10g)` per the RESEARCH
 * recommendation (a small percentage with a 10g floor) — documented as [LOW_FILAMENT_MARGIN_FRACTION] /
 * [LOW_FILAMENT_MARGIN_FLOOR_GRAMS].
 */
sealed interface SpoolWarning {
    /** Stable display text the amber confirm surface renders. */
    val message: String

    /** No active spool selected for Spoolman tracking — the user can `Pick spool`/`Scan`/`Print anyway`. */
    data object NoActiveSpool : SpoolWarning {
        override val message: String = "No active spool selected for Spoolman tracking"
    }

    /** File material family vs the active spool's material disagree (D-05 family match). */
    data class MaterialMismatch(val fileMaterials: List<String>, val spoolMaterial: String) : SpoolWarning {
        override val message: String =
            "Material mismatch: file uses ${fileMaterials.joinToString(", ")}, active spool is $spoolMaterial"
    }

    /** The active spool may not have enough filament for this file (remaining < needed + margin). */
    data class LowFilament(val neededGrams: Double, val remainingGrams: Double) : SpoolWarning {
        override val message: String =
            "File needs ~${neededGrams.toInt()}g. Spool reports ${remainingGrams.toInt()}g."
    }

    /** The active spool is archived (D-09 — allowed with a warning, never blocked). */
    data object ArchivedSpool : SpoolWarning {
        override val message: String = "Active spool is archived — usage will still be tracked"
    }

    /** Spoolman has queued, un-flushed usage reports — remaining may be stale (D-11). */
    data object PendingReports : SpoolWarning {
        override val message: String = "Usage queued — reported remaining may be stale"
    }

    /** The active-spool detail could not be fetched; the gate could not fully verify the spool. */
    data object FetchFailed : SpoolWarning {
        override val message: String = "Could not verify the active spool — proceed or retry"
    }
}

/** Low-remaining buffer: 5% of the file's weight need… */
const val LOW_FILAMENT_MARGIN_FRACTION: Double = 1.05

/** …or a 10g floor, whichever is larger (RESEARCH `max(total*1.05, total+10g)`). */
const val LOW_FILAMENT_MARGIN_FLOOR_GRAMS: Double = 10.0

/**
 * Evaluate the D-01 warn-only gate. Returns the ordered list of [SpoolWarning]s (possibly empty); NEVER
 * a block. The order is the D-01 condition order (no-spool → mismatch → low → archived → pending → fetch).
 *
 * @param activeSpool the resolved active-spool detail; `null` = absent (no spool, or fetch produced none).
 * @param status the `server.spoolman.status` reply (active id + pending_reports).
 * @param fetchFailed true when the active-spool detail fetch errored (distinct from "no spool selected").
 * @param file the gcode file's preview metadata (filament arrays + `filament_weight_total`).
 */
fun evaluatePrintStartGate(
    activeSpool: SpoolmanSpool?,
    status: SpoolmanStatus,
    fetchFailed: Boolean,
    file: FilePreviewMetadata,
): List<SpoolWarning> {
    val warnings = mutableListOf<SpoolWarning>()

    val hasActiveId = status.activeSpoolId != null

    // No active spool selected at all → warn (still never blocks).
    if (!hasActiveId || (activeSpool == null && !fetchFailed)) {
        warnings += SpoolWarning.NoActiveSpool
        // Without a spool there is nothing further to compare against; the no-spool warning stands alone.
        return warnings
    }

    // An id IS set but the detail couldn't be fetched → warn (retry/override), can't compare further.
    if (fetchFailed || activeSpool == null) {
        warnings += SpoolWarning.FetchFailed
        return warnings
    }

    // --- We have a resolved active spool: run the comparison checks. ---

    val spoolMaterial = activeSpool.filament?.material
    if (!spoolMaterial.isNullOrBlank() && file.filamentType.isNotEmpty()) {
        val anyFamilyMatches = file.filamentType.any { fileMat ->
            materialFamilyMatches(fileMat, spoolMaterial)
        }
        if (!anyFamilyMatches) {
            warnings += SpoolWarning.MaterialMismatch(file.filamentType, spoolMaterial)
        }
    }

    // Low-filament check — SKIPPED entirely when the file omits filament_weight_total (no fallback).
    val needed = file.filamentWeightTotal
    val remaining = activeSpool.remainingWeight
    if (needed != null && remaining != null) {
        val threshold = maxOf(needed * LOW_FILAMENT_MARGIN_FRACTION, needed + LOW_FILAMENT_MARGIN_FLOOR_GRAMS)
        if (remaining < threshold) {
            warnings += SpoolWarning.LowFilament(neededGrams = needed, remainingGrams = remaining)
        }
    }

    if (activeSpool.archived) {
        warnings += SpoolWarning.ArchivedSpool
    }

    if (status.hasPendingReports) {
        warnings += SpoolWarning.PendingReports
    }

    return warnings
}

/**
 * D-05 material family match: partial + case-insensitive. The active spool material "matches" a file
 * material when either name CONTAINS the other's normalized family token (so file `PLA` matches spool
 * `PLA+`, and file `PLA+` matches spool `PLA`). NOT typo-tolerant fuzzy — pure substring on the
 * trimmed/uppercased values.
 */
internal fun materialFamilyMatches(fileMaterial: String, spoolMaterial: String): Boolean {
    val f = fileMaterial.trim().uppercase()
    val s = spoolMaterial.trim().uppercase()
    if (f.isEmpty() || s.isEmpty()) return false
    return f.contains(s) || s.contains(f)
}
