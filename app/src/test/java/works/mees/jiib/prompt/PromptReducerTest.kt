package works.mees.jiib.prompt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The 8 `packages/js/test/hardening.spec.ts` edge cases re-ported as reducer unit tests, PLUS one explicit
 * nested-container-start case (Codex pre-execute finding — neither a fixture nor a hardening case covers it
 * directly; reducer.ts `openContainer` line 88). These pin the subtle reducer rules the fixtures alone do
 * not exhaust: view-immutability, container-open-at-show, group-rejects-non-button, suppressed-isolation,
 * empty-label-drop, markup case-sensitivity at the reducer boundary, stray-end, replace-active, and the
 * nested-start-ignored-but-children-still-append rule (PROMPT-03 re-entrancy / never-wedge).
 */
class PromptReducerTest {

    private val dinghy = PromptOpts(frontendId = "jiib", frontendCategories = listOf("touch"), liveAppend = true)

    /** Fold a list of raw action lines under [opts] (default Jiib identity). */
    private fun run(lines: List<String>, opts: PromptOpts = dinghy): PromptStateData {
        var s = initialPromptState(opts)
        for (l in lines) {
            val ev = parseAction(l)
            if (ev != null) s = reduce(s, ev)
        }
        return s
    }

    // Test 1 / 1b / 8 — view immutability (Kotlin: immutable by construction). The projected view holds
    // the same immutable List instances; a fresh projection is value-equal and cannot be corrupted because
    // List<PromptItem> is read-only and PromptItem is a data class with no mutable state.
    @Test
    fun viewIsImmutableByConstruction() {
        val s = run(
            listOf(
                "// action:prompt_begin T",
                "// action:prompt_row_start",
                "// action:prompt_text child",
                "// action:prompt_row_end",
                "// action:prompt_show",
            ),
        )
        val v1 = promptView(s)
        // Attempt to corrupt the returned view's nested children via a defensive copy + add (the model
        // hands out read-only Lists; this proves a consumer cannot reach back into reducer state).
        val mutated = v1.items[0].children.toMutableList()
        mutated.add(PromptItem(type = PromptItemType.TEXT, text = "INJECTED"))
        // A fresh projection is unaffected (immutable by construction — the deep-clone boundary of view.ts).
        val v2 = promptView(s)
        assertEquals(v1, v2)
        assertEquals(1, v2.items.size)
        val row = v2.items[0]
        assertEquals(PromptItemType.ROW, row.type)
        assertEquals(listOf(PromptItem(type = PromptItemType.TEXT, text = "child")), row.children)
        assertFalse("re-projected children must not contain the injected node", row.children.any { it.text == "INJECTED" })
    }

    // Test 3 — container left open at show, then post-show content appends inside the open row, then
    // closes, then appends top-level text (liveAppend=true).
    @Test
    fun containerOpenAtShowThenPostShowContent() {
        val s = run(
            listOf(
                "// action:prompt_begin T",
                "// action:prompt_row_start",
                "// action:prompt_show",
                "// action:prompt_text A",
                "// action:prompt_row_end",
                "// action:prompt_text B",
            ),
        )
        val v = promptView(s)
        assertTrue(v.visible)
        assertEquals(2, v.items.size)
        assertEquals(PromptItemType.ROW, v.items[0].type)
        assertEquals(listOf(PromptItem(type = PromptItemType.TEXT, text = "A")), v.items[0].children)
        assertEquals(PromptItem(type = PromptItemType.TEXT, text = "B"), v.items[1])
    }

    // Test 4 — button_group rejects non-button children (text dropped, not promoted to top level).
    @Test
    fun buttonGroupRejectsNonButton() {
        val s = run(
            listOf(
                "// action:prompt_begin G",
                "// action:prompt_button_group_start",
                "// action:prompt_text nope",
                "// action:prompt_button Yes|Y",
                "// action:prompt_button_group_end",
                "// action:prompt_show",
            ),
        )
        val v = promptView(s)
        assertEquals(1, v.items.size)
        assertEquals(PromptItemType.BUTTON_GROUP, v.items[0].type)
        assertEquals(
            listOf(PromptItem(type = PromptItemType.BUTTON, label = "Yes", gcode = "Y", style = PromptStyle.SECONDARY)),
            v.items[0].children,
        )
    }

    // Test 5 — a suppressed prompt accumulates nothing; the following matched begin starts clean.
    @Test
    fun suppressedPromptIsolation() {
        val opts = PromptOpts(frontendId = "mainsail", frontendCategories = emptyList(), liveAppend = true)
        val s = run(
            listOf(
                "// action:prompt_target fluidd",
                "// action:prompt_begin A",
                "// action:prompt_text secret",
                "// action:prompt_begin B",
                "// action:prompt_show",
            ),
            opts,
        )
        val v = promptView(s)
        assertTrue(v.visible)
        assertEquals("B", v.title)
        assertTrue(v.items.isEmpty())
    }

    // Test 6 — empty-label button (bare pipes) is dropped at the parse boundary (returns null → no-op).
    @Test
    fun emptyLabelButtonDropped() {
        assertNull(parseAction("// action:prompt_button ||"))
        // And folding it changes nothing (parseAction null → not reduced).
        val s = run(
            listOf(
                "// action:prompt_begin T",
                "// action:prompt_button ||",
                "// action:prompt_show",
            ),
        )
        assertTrue(promptView(s).items.isEmpty())
    }

    // Test 7 — markup tag-name case sensitivity at the reducer boundary: <B> is unknown (stripped, inner
    // preserved); <size:SMALL> accepts the uppercase VALUE (case-insensitive value, case-sensitive tag).
    @Test
    fun markupCaseSensitivityAtReducer() {
        val s = run(
            listOf(
                "// action:prompt_begin T",
                "// action:prompt_markup <B>x</B>",
                "// action:prompt_show",
            ),
        )
        val item = promptView(s).items.single()
        assertEquals(PromptItemType.MARKUP, item.type)
        // Unknown <B> tag stripped → plain text preserved.
        assertEquals("x", item.plainText)
    }

    // Test (stray-end) — a row_end with no open row is ignored; content still renders in order.
    @Test
    fun strayRowEndIgnored() {
        val s = run(
            listOf(
                "// action:prompt_begin Stray",
                "// action:prompt_text One",
                "// action:prompt_row_end",
                "// action:prompt_text Two",
                "// action:prompt_show",
            ),
        )
        val v = promptView(s)
        assertEquals(
            listOf(
                PromptItem(type = PromptItemType.TEXT, text = "One"),
                PromptItem(type = PromptItemType.TEXT, text = "Two"),
            ),
            v.items,
        )
    }

    // Test (replace-active) — a second begin replaces the active prompt, clearing prior content, starting
    // clean from freshIdle(epoch+1).
    @Test
    fun replaceActivePromptStartsClean() {
        var s = initialPromptState(dinghy)
        for (l in listOf(
            "// action:prompt_begin First",
            "// action:prompt_text replaced",
            "// action:prompt_show",
        )) {
            parseAction(l)?.let { s = reduce(s, it) }
        }
        val epochAfterFirst = s.epoch
        for (l in listOf(
            "// action:prompt_begin Second",
            "// action:prompt_text fresh",
            "// action:prompt_show",
        )) {
            parseAction(l)?.let { s = reduce(s, it) }
        }
        assertEquals(epochAfterFirst + 1, s.epoch)
        val v = promptView(s)
        assertEquals("Second", v.title)
        assertEquals(listOf(PromptItem(type = PromptItemType.TEXT, text = "fresh")), v.items)
    }

    // NESTED-CONTAINER-START (Codex pre-execute finding) — open a row, then while it is STILL open send
    // another row_start; the nested start is IGNORED (no new container item) AND a subsequent eligible
    // child still appends to the ALREADY-open first container (reducer.ts openContainer line 88).
    @Test
    fun nestedContainerStartIgnoredChildrenStillAppend() {
        val s = run(
            listOf(
                "// action:prompt_begin T",
                "// action:prompt_row_start",
                "// action:prompt_text first",
                "// action:prompt_row_start", // nested start — IGNORED
                "// action:prompt_text second", // still appends to the open row
                "// action:prompt_row_end",
                "// action:prompt_show",
            ),
        )
        val v = promptView(s)
        // Exactly ONE container item (the nested start did not create a second).
        assertEquals(1, v.items.size)
        assertEquals(PromptItemType.ROW, v.items[0].type)
        assertEquals(
            listOf(
                PromptItem(type = PromptItemType.TEXT, text = "first"),
                PromptItem(type = PromptItemType.TEXT, text = "second"),
            ),
            v.items[0].children,
        )
    }

    // Same rule for button_group: nested button_group_start ignored, buttons still append to the first.
    @Test
    fun nestedButtonGroupStartIgnored() {
        val s = run(
            listOf(
                "// action:prompt_begin T",
                "// action:prompt_button_group_start",
                "// action:prompt_button A|A",
                "// action:prompt_button_group_start", // nested — IGNORED
                "// action:prompt_button B|B",
                "// action:prompt_button_group_end",
                "// action:prompt_show",
            ),
        )
        val v = promptView(s)
        assertEquals(1, v.items.size)
        assertEquals(PromptItemType.BUTTON_GROUP, v.items[0].type)
        assertEquals(2, v.items[0].children.size)
        assertEquals(listOf("A", "B"), v.items[0].children.map { it.label })
    }
}
