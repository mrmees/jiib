package works.mees.jiib.ui.console

/**
 * The severity tier of a console line, derived purely from its raw Klipper prefix (CONS-02 / D-02).
 *
 * This is the project's pure-function discipline (mirrors `state/DeriveCapabilities.kt` /
 * `command/PrinterCommands.kt`): NO I/O, NO coroutines, NO Compose — same input always yields the
 * same output, so it is fully host-testable off-hardware (`ConsoleSeverityTest`).
 *
 * The prefix → tier mapping mirrors the Mainsail fork @76fcbd2 verbatim
 * (`src/components/console/ConsoleTableEntry.vue` + `src/store/server/actions.ts`). The `!! ` / `// `
 * convention is a Klipper OUTPUT convention (not formally in the Moonraker notification spec), so
 * [classify] is a TOTAL function — an absent / empty / odd prefix degrades to [NORMAL] and NEVER
 * throws (T-08-02-T).
 */
enum class ConsoleSeverity {
    /** `!! ` prefix — a Klipper error (→ `t.stop`, red). */
    ERROR,

    /** `// ` prefix (other than `action:` / `debug:`) — an echo / notice (→ `t.heat`, amber). */
    WARNING,

    /** No recognized prefix — a plain command or response (→ `t.text`, or `t.go` for `ok`). */
    NORMAL,

    /** `// action:` prefix — a Klipper action line; the Phase-12 prompt-protocol hook (keep raw). */
    ACTION,

    /** `// debug:` prefix — a Klipper debug line (dimmed). */
    DEBUG;

    companion object {
        /**
         * Classify [rawMessage] into a [ConsoleSeverity] tier from its leading prefix. TOTAL — an
         * absent / empty / non-conforming prefix (e.g. a bare `//nospace` without the trailing space)
         * maps to [NORMAL] and never throws (T-08-02-T; covered by `ConsoleSeverityTest`).
         *
         * Order matters: the more-specific `// action:` / `// debug:` prefixes are tested BEFORE the
         * generic `// ` echo prefix.
         */
        fun classify(rawMessage: String): ConsoleSeverity = when {
            rawMessage.startsWith("!! ") -> ERROR
            rawMessage.startsWith("// action:") -> ACTION
            rawMessage.startsWith("// debug:") -> DEBUG
            rawMessage.startsWith("// ") -> WARNING
            else -> NORMAL
        }
    }
}
