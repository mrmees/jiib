package works.mees.dinghy.ui.macros

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import works.mees.dinghy.state.Capabilities
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Wave-0 RED scaffold (08-01) — turned GREEN by 08-06 Task 1 (`MacroHolder`).
 *
 * REQ-MACRO-01/03. The holder combines `Capabilities.macros` (already derived on every reconnect),
 * the parsed macro bodies, and `MacroPrefs` (bookmarks + revealHidden). Underscore-prefixed macros
 * are hidden when revealHidden=false and revealed when true (MACRO-03). The bookmarked list = only
 * the user-selected names (D-07). Empty `Capabilities.macros` → an `unavailable` flag.
 *
 * Production symbols referenced (NOT YET BUILT → RED): [MacroHolder] + its `state: StateFlow`, a stub
 * [MacroPrefs]-shaped source, and [MacroParamParser]/[MacroParam] for the param-population assertion.
 */
class MacroHolderTest {

    /** Stub prefs seam (no DataStore I/O) exposing the two reactive prefs the holder combines. */
    private class FakeMacroPrefsSource(bookmarks: Set<String>, revealHidden: Boolean) {
        val bookmarksFlow = MutableStateFlow(bookmarks)
        val revealHiddenFlow = MutableStateFlow(revealHidden)
        val bookmarks: StateFlow<Set<String>> get() = bookmarksFlow.asStateFlow()
        val revealHidden: StateFlow<Boolean> get() = revealHiddenFlow.asStateFlow()
    }

    private fun caps(vararg macros: String) =
        MutableStateFlow(Capabilities(macros = macros.toList()))

    @Test
    fun allMacrosEnumerated() = runTest {
        val prefs = FakeMacroPrefsSource(emptySet(), revealHidden = true)
        val holder = MacroHolder(this, caps("START_PRINT", "LOAD_FILAMENT", "_CLIENT_EXTRUDE"), prefs.bookmarks, prefs.revealHidden)
        advanceUntilIdle()
        assertEquals(3, holder.state.value.macros.size)
    }

    @Test
    fun underscoreMacrosHiddenByDefault_revealedWhenToggled() = runTest {
        val prefs = FakeMacroPrefsSource(emptySet(), revealHidden = false)
        val holder = MacroHolder(this, caps("START_PRINT", "_CLIENT_EXTRUDE"), prefs.bookmarks, prefs.revealHidden)
        advanceUntilIdle()
        assertEquals(listOf("START_PRINT"), holder.state.value.visibleMacros.map { it.name })
        prefs.revealHiddenFlow.value = true
        advanceUntilIdle()
        assertTrue(holder.state.value.visibleMacros.any { it.name == "_CLIENT_EXTRUDE" })
    }

    @Test
    fun bookmarkedList_isOnlySelectedNames() = runTest {
        val prefs = FakeMacroPrefsSource(setOf("START_PRINT"), revealHidden = false)
        val holder = MacroHolder(this, caps("START_PRINT", "END_PRINT", "LOAD_FILAMENT"), prefs.bookmarks, prefs.revealHidden)
        advanceUntilIdle()
        assertEquals(listOf("START_PRINT"), holder.state.value.bookmarkedMacros.map { it.name })
    }

    @Test
    fun paramsPopulateFromRealBody() = runTest {
        val prefs = FakeMacroPrefsSource(emptySet(), revealHidden = false)
        val holder = MacroHolder(this, caps("START_PRINT"), prefs.bookmarks, prefs.revealHidden)
        // The holder parses the macro body for params; START_PRINT declares BED_TEMP + EXTRUDER_TEMP.
        holder.setMacroBody("START_PRINT", "{% set BED_TEMP = params.BED_TEMP|default(60)|float %}")
        advanceUntilIdle()
        val startPrint = holder.state.value.macros.first { it.name == "START_PRINT" }
        assertTrue(startPrint.params.any { it.name == "BED_TEMP" })
    }

    @Test
    fun emptyCapabilities_setsUnavailable() = runTest {
        val prefs = FakeMacroPrefsSource(emptySet(), revealHidden = false)
        val holder = MacroHolder(this, caps(), prefs.bookmarks, prefs.revealHidden)
        advanceUntilIdle()
        assertTrue(holder.state.value.unavailable)
        assertFalse(holder.state.value.macros.isNotEmpty())
    }

    @Test
    fun descriptionPlumbsThroughToVm() = runTest {
        val prefs = FakeMacroPrefsSource(emptySet(), revealHidden = false)
        val holder = MacroHolder(this, caps("START_PRINT"), prefs.bookmarks, prefs.revealHidden)
        holder.setMacroDescriptions(mapOf("start_print" to "Starts the print"))
        advanceUntilIdle()
        assertEquals(
            "Starts the print",
            holder.state.value.macros.first { it.name == "START_PRINT" }.description,
        )
    }

    @Test
    fun usesRawParams_surfacesFromBody() = runTest {
        val prefs = FakeMacroPrefsSource(emptySet(), revealHidden = false)
        val holder = MacroHolder(this, caps("ECHO"), prefs.bookmarks, prefs.revealHidden)
        holder.setMacroBody("ECHO", """RESPOND MSG="{rawparams}"""")
        advanceUntilIdle()
        assertTrue(holder.state.value.macros.first { it.name == "ECHO" }.usesRawParams)
    }
}
