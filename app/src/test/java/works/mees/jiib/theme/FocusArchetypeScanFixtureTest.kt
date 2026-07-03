package works.mees.jiib.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * TDD fixtures for the Focus-archetype body scanner ([scanFocusBody]) and the two guards layered on
 * top of it in the real tests: the `content =` named-arg guard (F2) and the Layer-1 containment
 * word-boundary regex (which must NOT flag `DigestRow.Line(` for the `DigestLine` identifier).
 *
 * Semantics (see FocusScan.kt SEMANTICS NOTE + task-29-report.md): FORBIDDEN layout/content
 * primitives + `.padding(` at the visible Focus-body level are offenders; lambda/slot interiors
 * (F3) and registry arg spans are masked; control-flow (`when`/`if`/`else`) stays visible; value
 * constructors / framework factories / component classes are permitted.
 */
class FocusArchetypeScanFixtureTest {

    private val registry = setOf("FocusExplainer", "FocusStage")

    @Test
    fun single_archetype_call_passes() {
        val body = """ FocusExplainer(text = status) """
        assertEquals(emptyList<String>(), scanFocusBody(body, registry, "f.kt", 1))
    }

    @Test
    fun when_dispatch_to_archetypes_passes() {
        // `when`-branches are VISIBLE, but they dispatch only to archetypes; the FocusStage slot
        // (`body = { Column … }`) is masked, so its interior Column/Text is not scanned.
        val body = """
            when (mode) {
                Mode.A -> FocusExplainer(text = a)
                else -> FocusStage(body = { Column { Text("inside slot is fine") } })
            }
        """
        assertEquals(emptyList<String>(), scanFocusBody(body, registry, "f.kt", 1))
    }

    @Test
    fun raw_column_at_body_level_is_an_offender() {
        val body = """ Column(Modifier.fillMaxSize()) { FocusExplainer(text = x) } """
        assertTrue(scanFocusBody(body, registry, "f.kt", 1).isNotEmpty())
    }

    @Test
    fun forbidden_inside_a_when_branch_is_still_caught() {
        // Teeth: control-flow is NOT a slot — a hand-rolled Column in a when-branch is an offender.
        val body = """
            when (mode) {
                Mode.A -> FocusExplainer(text = a)
                else -> Column(Modifier) { Text("hand-rolled") }
            }
        """
        assertTrue(scanFocusBody(body, registry, "f.kt", 1).isNotEmpty())
    }

    @Test
    fun parenless_column_at_body_level_is_an_offender() {
        // Bypass fix: `Column { }` has NO `(` — the CALL detector never sees it and step-1 masking
        // hides the interior; the BLOCK detector must catch the opener itself.
        val body = """ Column { Text("hi") } """
        assertTrue(scanFocusBody(body, registry, "f.kt", 1).isNotEmpty())
    }

    @Test
    fun parenless_box_inside_a_when_branch_is_an_offender() {
        val body = """
            when (mode) {
                Mode.A -> FocusExplainer(text = a)
                else -> Box{}
            }
        """
        assertTrue(scanFocusBody(body, registry, "f.kt", 1).isNotEmpty())
    }

    @Test
    fun parenless_registry_archetype_trailing_lambda_is_not_flagged() {
        // BLOCK skips registry names; control-flow (`when {`) is lowercase and never matches.
        val body = """ FocusStage { FocusExplainer(text = x) } """
        assertEquals(emptyList<String>(), scanFocusBody(body, registry, "f.kt", 1))
    }

    @Test
    fun forbidden_inside_a_slot_lambda_is_not_scanned() {
        // F3: DigestRow.Custom / builder / any non-registry lambda is slot content — not scanned.
        val body = """ FocusStage(body = { Row { Icon() ; Text("slot") } }) """
        assertEquals(emptyList<String>(), scanFocusBody(body, registry, "f.kt", 1))
    }

    @Test
    fun value_construction_and_component_classes_are_permitted() {
        // Uppercase calls that are not FORBIDDEN (value constructors, component classes, framework
        // factories) are allowed at Focus-body level — they are the archetype's data contract.
        val body = """
            val stat = InfoStat(icon, label, value)
            LaunchedEffect(Unit) { load() }
            FocusExplainer(text = stat.value)
        """
        assertEquals(emptyList<String>(), scanFocusBody(body, registry, "f.kt", 1))
    }

    @Test
    fun trailing_padding_at_body_level_is_an_offender() {
        val body = """ FocusExplainer(text = x).padding(8.dp) """
        assertTrue(scanFocusBody(body, registry, "f.kt", 1).isNotEmpty())
    }

    // --- Item 1: masking is registry-only; generic lambdas are now SCANNED --------------------

    @Test
    fun forbidden_inside_a_generic_nonregistry_lambda_is_now_caught() {
        // `remember { }` / `LaunchedEffect { }` are NOT registry slots — their interiors are scanned.
        val body = """ LaunchedEffect(Unit) { Column { Text("nope") } } """
        assertTrue(scanFocusBody(body, registry, "f.kt", 1).isNotEmpty())
    }

    @Test
    fun forbidden_inside_a_registry_parenless_slot_is_still_masked() {
        // Registry parenless trailing lambda IS a slot → interior masked (F3).
        val body = """ FocusStage { Column { Text("slot") } } """
        assertEquals(emptyList<String>(), scanFocusBody(body, registry, "f.kt", 1))
    }

    // --- Item 2: fully-qualified + alias evasion defenses --------------------------------------

    @Test
    fun fully_qualified_forbidden_call_is_flagged() {
        val body = """ androidx.compose.foundation.layout.Column(Modifier) { } """
        assertTrue(scanFocusBody(body, registry, "f.kt", 1).isNotEmpty())
    }

    @Test
    fun fully_qualified_forbidden_block_is_flagged() {
        val body = """ some.pkg.Column { } """
        assertTrue(scanFocusBody(body, registry, "f.kt", 1).isNotEmpty())
    }

    @Test
    fun member_access_non_forbidden_value_constructor_still_passes() {
        // `DigestRow.Line(` — Line ∉ FORBIDDEN — must NOT be flagged by the dotted-form detector,
        // even bare at the visible body level (value construction is permitted).
        val body = """ val r = DigestRow.Line(label = a, value = b) """
        assertEquals(emptyList<String>(), scanFocusBody(body, registry, "f.kt", 1))
    }

    @Test
    fun import_aliased_forbidden_primitive_is_flagged() {
        // `import …Column as Grid` → `Grid(` is forbidden in that file.
        val src = "import androidx.compose.foundation.layout.Column as Grid\nfun f() {}"
        val forbidden = focusForbiddenFor(src)
        assertTrue("Grid" in forbidden)
        val body = """ Grid(Modifier) { FocusExplainer(text = x) } """
        assertTrue(scanFocusBody(body, registry, "f.kt", 1, forbidden).isNotEmpty())
    }

    @Test
    fun import_alias_of_non_forbidden_symbol_is_not_added() {
        val src = "import works.mees.jiib.designsystem.layout.ListBlock as DesignListBlock"
        assertTrue("DesignListBlock" !in focusForbiddenFor(src))
    }

    // --- Item 3: FocusFrame with no trailing lambda and no `content =` arg (F2b) ---------------

    @Test
    fun focusFrame_without_trailing_lambda_has_null_body_span() {
        val src = "FocusFrame(title = t, icon = i)"
        val stripped = stripKotlin(src)
        val call = findCalls(stripped, Regex("""(^|[^.\w])FocusFrame\s*\(""")).single()
        // Neither a `content =` arg nor a trailing lambda → the real test flags it as an offender.
        assertTrue("content" !in topLevelArgNames(call.argSpan))
        assertEquals(null, lambdaBodySpan(stripped, call.endOffset))
    }

    // --- F2: the `content =` named-arg guard (applied at the FocusFrame-call level) -------------

    @Test
    fun focusFrame_content_named_arg_is_flagged() {
        // The Focus body MUST be a trailing lambda so conformance can scan it. Passing it as
        // `content = body` hides it from the scanner → the real test flags this outright.
        val src = "FocusFrame(title = t, content = body)"
        val call = findCalls(stripKotlin(src), Regex("""(^|[^.\w])FocusFrame\s*\(""")).single()
        assertTrue("content" in topLevelArgNames(call.argSpan))
    }

    @Test
    fun focusFrame_trailing_lambda_has_no_content_arg() {
        val src = "FocusFrame(title = t) { FocusDigest(rows) }"
        val call = findCalls(stripKotlin(src), Regex("""(^|[^.\w])FocusFrame\s*\(""")).single()
        assertTrue("content" !in topLevelArgNames(call.argSpan))
    }

    // --- Layer-1 containment regex word-boundary (DigestRow.Line must NOT match DigestLine) -----

    @Test
    fun containment_regex_matches_bare_layer1_call() {
        val stripped = stripKotlin("DigestLine(text = x)")
        assertEquals(1, Regex("""(^|[^.\w])DigestLine\s*\(""").findAll(stripped).count())
    }

    @Test
    fun containment_regex_ignores_digestRow_Line_value_constructor() {
        // DigestRow.Line(...) is a VALUE constructor (member access), not the Layer-1 composable.
        val stripped = stripKotlin("val r = DigestRow.Line(label = a, value = b)")
        assertEquals(0, Regex("""(^|[^.\w])DigestLine\s*\(""").findAll(stripped).count())
    }
}
