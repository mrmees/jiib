package works.mees.dinghy.ui.console

/**
 * A single raw console line as it arrived from Moonraker — the headless, immutable model the whole
 * console layer carries (CONS-02). It mirrors the `state/Capabilities.kt` plain-immutable-model
 * discipline: NO Compose, NO I/O, fully host-testable.
 *
 * D-04 (LOAD-BEARING): [rawMessage] keeps the ORIGINAL Klipper prefix (`!! ` / `// ` / `// action:`
 * / `// debug:`) intact. Severity is derived from that original prefix ONCE (via
 * [ConsoleSeverity.classify]) and stored alongside; the display may later strip the prefix for
 * rendering, but the raw form survives so the view-layer filters ([ConsoleFilters]) are reversible
 * toggles and the Phase-12 prompt engine / backfill are never starved by what the console hides.
 *
 * @param rawMessage the line verbatim from `notify_gcode_response` / `server.gcode_store.message`,
 *   prefix-preserved.
 * @param severity the tier derived from [rawMessage]'s prefix (see [ConsoleSeverity.classify]).
 * @param timeEpoch optional Unix epoch seconds from a `server.gcode_store` entry's `time` field;
 *   `null` for a live `notify_gcode_response` line (which carries no timestamp).
 */
data class ConsoleLine(
    val rawMessage: String,
    val severity: ConsoleSeverity,
    val timeEpoch: Double?,
)
