package works.mees.dinghy.command

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Wave-0 RED scaffold (08-01) — turned GREEN by 08-03 (`MacroInvocation`).
 *
 * REQ-MACRO-02 SECURITY (ASVS V5, T-08-…-T, block_on:high). D-10 allows the alpha keyboard for
 * string macro params — which would break the [PrinterCommands] "never concatenate a user string
 * into a script" invariant. The policy here is REJECT, NOT escape/strip: any forbidden character in
 * a string param value makes [MacroInvocation.build] throw the typed [MacroParamRejected]. A clean
 * value assembles to `MACRO KEY="value"`. NO numeric clamp assertion here — numeric range-clamping
 * is owned by the IME entry field (D-07), not MacroInvocation (S4).
 *
 * Production symbols referenced (NOT YET BUILT → RED): [MacroInvocation.build], [MacroParamRejected].
 */
class MacroInvocationTest {

    @Test
    fun cleanValue_assemblesQuotedKeyValue() {
        val line = MacroInvocation.build("LOAD_FILAMENT", mapOf("MATERIAL" to "PLA"))
        assertEquals("""LOAD_FILAMENT MATERIAL="PLA"""", line)
    }

    @Test
    fun newlineInValue_rejected() {
        // `; SET_HEATER_TEMPERATURE` smuggled behind a newline would split into a second gcode line.
        assertThrows(MacroParamRejected::class.java) {
            MacroInvocation.build("START_PRINT", mapOf("NAME" to "x\n; SET_HEATER_TEMPERATURE HEATER=extruder TARGET=300"))
        }
    }

    @Test
    fun carriageReturnInValue_rejected() {
        assertThrows(MacroParamRejected::class.java) {
            MacroInvocation.build("START_PRINT", mapOf("NAME" to "x\r; M112"))
        }
    }

    @Test
    fun semicolonInValue_rejected() {
        assertThrows(MacroParamRejected::class.java) {
            MacroInvocation.build("START_PRINT", mapOf("NAME" to "; M112"))
        }
    }

    @Test
    fun tabInValue_rejected() {
        assertThrows(MacroParamRejected::class.java) {
            MacroInvocation.build("START_PRINT", mapOf("NAME" to "a\tb"))
        }
    }

    @Test
    fun embeddedDoubleQuoteInValue_rejected() {
        // An embedded quote would break out of the KEY="value" quoting and inject extra tokens.
        assertThrows(MacroParamRejected::class.java) {
            MacroInvocation.build("START_PRINT", mapOf("NAME" to """a" EXTRA=1"""))
        }
    }

    @Test
    fun asciiControlCharInValue_rejected() {
        assertThrows(MacroParamRejected::class.java) {
            MacroInvocation.build("START_PRINT", mapOf("NAME" to "a\u0000b")) // NUL
        }
        assertThrows(MacroParamRejected::class.java) {
            MacroInvocation.build("START_PRINT", mapOf("NAME" to "a\u007Fb")) // DEL
        }
    }

    @Test
    fun multiTokenSplitValue_rejectedNotTruncated() {
        // A malicious value that would split the line into extra KEY=VALUE tokens must be rejected
        // outright, never silently truncated to the first token.
        assertThrows(MacroParamRejected::class.java) {
            MacroInvocation.build("START_PRINT", mapOf("NAME" to "PLA\nM104 S300"))
        }
    }

    // ---- buildTyped coverage (V5 / D-12 / T-25-05-01) ------------------------------------------
    // The ParamEntry Field-takeover in BookmarkedMacrosScreen uses buildTyped, not build. Without
    // these tests the sanitizer is exercised on the old map overload but UNCOVERED on the typed path
    // that the redesigned screen dispatches through. A bypass of buildTyped (e.g. routing params
    // directly to scriptParams) would leave these passing green — the sanitizer MUST stay in the call chain.

    @Test
    fun buildTyped_forbiddenCharInStringParam_rejected() {
        // Newline after a semicolon is a classic command-smuggling attempt; buildTyped must reject it
        // via the same rejectForbidden gate used by build — never produce a partial line.
        assertThrows(MacroParamRejected::class.java) {
            MacroInvocation.buildTyped(
                "START_PRINT",
                listOf(Triple("NAME", "x\n; M112", false)),
            )
        }
    }

    @Test
    fun buildTyped_emptyNumericValue_rejected() {
        // WR-04: a numeric param with no |default(...) seeds "" — emitted unquoted that would be a
        // malformed bare `KEY=` token on the macro line. Must be rejected, never emitted.
        assertThrows(MacroParamRejected::class.java) {
            MacroInvocation.buildTyped(
                "LOAD_FILAMENT",
                listOf(Triple("TEMP", "", true)),
            )
        }
    }

    @Test
    fun buildTyped_numericValueWithSpaces_rejected() {
        // WR-04: MacroParam.default carries the RAW Jinja expression (MacroModels KDoc) — seeded
        // verbatim and emitted unquoted, `printer.extruder.target * 0.5` would token-split into
        // extra KEY=VALUE pairs on the macro line (e.g. overriding another param). Must be rejected.
        assertThrows(MacroParamRejected::class.java) {
            MacroInvocation.buildTyped(
                "LOAD_FILAMENT",
                listOf(Triple("TEMP", "printer.extruder.target * 0.5", true)),
            )
        }
    }

    @Test
    fun buildTyped_paddedNumericValue_rejected() {
        // WR-04 hardening: Double.parseDouble trims whitespace, so " 210" parses fine — but emitted
        // verbatim it still token-splits (`KEY= 210` → bare `KEY=` + stray `210`). Must be rejected.
        assertThrows(MacroParamRejected::class.java) {
            MacroInvocation.buildTyped(
                "LOAD_FILAMENT",
                listOf(Triple("TEMP", " 210", true)),
            )
        }
    }

    @Test
    fun buildTyped_cleanMixedParams_producesCorrectGcodeLine() {
        // A clean string param is quoted; a numeric param is emitted bare (no quotes). The sanitizer
        // must NOT reject a clean value — if it does, this assertion never executes and the test fails.
        // Expected: LOAD_FILAMENT MATERIAL="PLA" TEMP=210
        val line = MacroInvocation.buildTyped(
            "LOAD_FILAMENT",
            listOf(
                Triple("MATERIAL", "PLA", false),   // string → KEY="VALUE"
                Triple("TEMP", "210", true),          // numeric → KEY=VALUE (unquoted)
            ),
        )
        assertEquals("""LOAD_FILAMENT MATERIAL="PLA" TEMP=210""", line)
    }
}
