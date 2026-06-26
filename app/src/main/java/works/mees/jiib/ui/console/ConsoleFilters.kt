package works.mees.jiib.ui.console

/**
 * The three built-in, opt-in console noise filters (CONS-02 / D-03 / D-04), with the regexes lifted
 * VERBATIM from the Mainsail fork @76fcbd2 (`src/store/gui/console/getters.ts` +
 * `src/store/variables.ts`).
 *
 * D-04 (LOAD-BEARING): filtering is a VIEW-layer concern ONLY. [apply] returns a NEW filtered list
 * and NEVER mutates its input — the raw `ConsoleLine` stream survives a toggle-off (filters are
 * reversible) so the Phase-12 prompt engine and the backfill are never starved by what the console
 * hides. All three filters default OFF (opt-in).
 *
 * Pure — NO I/O, NO Compose; mirrors the `state/DeriveCapabilities.kt` discipline; host-tested by
 * `ConsoleFiltersTest`.
 */
object ConsoleFilters {

    /** Hide the M105 temperature-report echo (e.g. `ok T:210.0 /210.0 B:60.0 /60.0`, `T0:...`). */
    val HIDE_TEMPERATURES = Regex("""^(?:ok\s+)?(B|C|T\d*):""")

    /** Hide Timelapse plugin chatter — the fork's full six-rule set (these are gcode "command" lines). */
    val HIDE_TIMELAPSE = listOf(
        Regex("""^_TIMELAPSE_NEW_FRAME"""),
        Regex("""^TIMELAPSE_TAKE_FRAME"""),
        Regex("""^TIMELAPSE_RENDER"""),
        Regex("""^_SET_TIMELAPSE_SETUP"""),
        Regex("""^HYPERLAPSE ACTION="""),
        Regex("""^SET_GCODE_VARIABLE MACRO=TIMELAPSE_"""),
    )

    /**
     * Hide prompt commands — matches BOTH the raw `// action:prompt…` form AND the already-`// `-
     * stripped `action:prompt…` live form (Phase-12 prompt-protocol lines).
     */
    val HIDE_PROMPT_COMMANDS = Regex("""^(?:// )?action:prompt""")

    /**
     * Return a NEW list with the lines that match any ACTIVE filter removed; [lines] is never
     * mutated (D-04). A line is hidden if any enabled rule's regex matches its [ConsoleLine.rawMessage].
     *
     * @param lines the RAW (unfiltered) console lines from the holder.
     * @param hideTemperatures apply [HIDE_TEMPERATURES] when true.
     * @param hideTimelapse apply any of [HIDE_TIMELAPSE] when true.
     * @param hidePrompt apply [HIDE_PROMPT_COMMANDS] when true.
     * @return a filtered VIEW (a different list instance); with all flags false this equals [lines].
     */
    fun apply(
        lines: List<ConsoleLine>,
        hideTemperatures: Boolean,
        hideTimelapse: Boolean,
        hidePrompt: Boolean,
    ): List<ConsoleLine> = lines.filterNot { line ->
        val msg = line.rawMessage
        (hideTemperatures && HIDE_TEMPERATURES.containsMatchIn(msg)) ||
            (hideTimelapse && HIDE_TIMELAPSE.any { it.containsMatchIn(msg) }) ||
            (hidePrompt && HIDE_PROMPT_COMMANDS.containsMatchIn(msg))
    }
}
