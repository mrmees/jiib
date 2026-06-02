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
 * is owned by NumpadPage (08-06), not MacroInvocation (S4).
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
}
