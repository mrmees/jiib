package works.mees.dinghy.prompt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PROMPT-01 — the PURE, TOTAL line parser + sub-parsers ([parseAction] / [parseButtonFields] /
 * [normalizeStyle] / [parseImageScale] / [isValidImagePath]). Mirrors the QrPayloadParserTest /
 * ConsoleSeverityTest decision-table idiom: every accept/reject class locked, and a fuzz set proving
 * the parser is total (returns a value, never throws) on adversarial input (T-12-01 / T-12-02).
 */
class PromptParseTest {

    // ---- parseAction: dispatch + the `// ` retained, NOT stripped --------------------------------

    @Test
    fun parsesBeginTitle() {
        assertEquals(
            PromptEvent.Begin("Load Filament"),
            parseAction("// action:prompt_begin Load Filament"),
        )
    }

    @Test
    fun nonPromptLineIsNull() {
        assertNull(parseAction("not a prompt"))
        // The `// ` is required AS-IS — a stripped command form does NOT match (Pitfall 2).
        assertNull(parseAction("action:prompt_begin x"))
        assertNull(parseAction("prompt_begin x"))
    }

    @Test
    fun unknownCommandIsDropped() {
        assertNull(parseAction("// action:prompt_bogus x"))
        assertNull(parseAction("// action:prompt_confetti on"))
    }

    @Test
    fun parsesSimpleCommands() {
        assertEquals(PromptEvent.Show, parseAction("// action:prompt_show"))
        assertEquals(PromptEvent.End, parseAction("// action:prompt_end"))
        assertEquals(PromptEvent.RowStart, parseAction("// action:prompt_row_start"))
        assertEquals(PromptEvent.RowEnd, parseAction("// action:prompt_row_end"))
        assertEquals(PromptEvent.ButtonGroupStart, parseAction("// action:prompt_button_group_start"))
        assertEquals(PromptEvent.ButtonGroupEnd, parseAction("// action:prompt_button_group_end"))
        assertEquals(PromptEvent.Text("hi"), parseAction("// action:prompt_text hi"))
    }

    @Test
    fun parsesTargetLowercasedTrimmedFiltered() {
        assertEquals(
            PromptEvent.Target(listOf("klipperscreen", "touch")),
            parseAction("// action:prompt_target KlipperScreen, touch ,"),
        )
    }

    @Test
    fun parsesSizeCaseInsensitiveUnknownNull() {
        assertEquals(PromptEvent.Size(PromptSize.LARGE), parseAction("// action:prompt_size LARGE"))
        assertEquals(
            PromptEvent.Size(PromptSize.FULL_SCREEN),
            parseAction("// action:prompt_size full-screen"),
        )
        assertEquals(PromptEvent.Size(null), parseAction("// action:prompt_size jumbo"))
        assertEquals(PromptEvent.Size(null), parseAction("// action:prompt_size"))
    }

    @Test
    fun parsesAlignUnknownNull() {
        assertEquals(PromptEvent.Align(PromptAlign.LEFT), parseAction("// action:prompt_align left"))
        assertEquals(PromptEvent.Align(null), parseAction("// action:prompt_align justify"))
        assertEquals(PromptEvent.Align(null), parseAction("// action:prompt_align"))
    }

    @Test
    fun parsesButtonAndDropsEmptyLabel() {
        assertEquals(
            PromptEvent.Button("Home", "G28", PromptStyle.PRIMARY),
            parseAction("// action:prompt_button Home|G28|primary"),
        )
        // gcode defaults to label; style defaults to secondary.
        assertEquals(
            PromptEvent.Button("M117 hello", "M117 hello", PromptStyle.SECONDARY),
            parseAction("// action:prompt_button M117 hello"),
        )
        // Empty label (bare pipes) drops the button entirely.
        assertNull(parseAction("// action:prompt_button ||"))
    }

    @Test
    fun parsesFooterButton() {
        assertEquals(
            PromptEvent.FooterButton("Done", "Done", PromptStyle.SECONDARY),
            parseAction("// action:prompt_footer_button Done||"),
        )
    }

    @Test
    fun parsesImageWithAltAndScale() {
        assertEquals(
            PromptEvent.Image("config/prompt-assets/spool.svg", "Blue PLA spool preview", 0.75),
            parseAction("// action:prompt_image config/prompt-assets/spool.svg|Blue PLA spool preview|0.75"),
        )
        // Missing alt + scale 0.5.
        assertEquals(
            PromptEvent.Image("config/prompt-assets/nozzle.png", "", 0.5),
            parseAction("// action:prompt_image config/prompt-assets/nozzle.png||0.5"),
        )
    }

    @Test
    fun parsesMarkupCarriesPlainText() {
        val ev = parseAction("// action:prompt_markup <b>Hi &amp; bye</b>")
        assertEquals(PromptEvent.Markup("<b>Hi &amp; bye</b>", "Hi & bye"), ev)
    }

    @Test
    fun disconnectEventIsDisconnect() {
        assertEquals(PromptEvent.Disconnect, disconnectEvent())
    }

    // ---- parseButtonFields -----------------------------------------------------------------------

    @Test
    fun buttonFieldsDefaultsGcodeToLabelAndStyleToSecondary() {
        assertEquals(ButtonFields("Cancel", "Cancel", PromptStyle.ERROR), parseButtonFields("Cancel||error"))
        assertEquals(ButtonFields("Save", "Save", PromptStyle.SECONDARY), parseButtonFields("Save"))
        assertNull(parseButtonFields(""))     // empty label → dropped
        assertNull(parseButtonFields("  |x")) // whitespace-only label → dropped
    }

    // ---- normalizeStyle --------------------------------------------------------------------------

    @Test
    fun normalizeStyleCaseInsensitiveElseSecondary() {
        assertEquals(PromptStyle.PRIMARY, normalizeStyle("PRIMARY"))
        assertEquals(PromptStyle.SUCCESS, normalizeStyle(" success "))
        assertEquals(PromptStyle.SECONDARY, normalizeStyle("nonsense"))
        assertEquals(PromptStyle.SECONDARY, normalizeStyle(null))
        assertEquals(PromptStyle.SECONDARY, normalizeStyle(""))
    }

    // ---- parseImageScale -------------------------------------------------------------------------

    @Test
    fun parseImageScaleGrammar() {
        assertEquals(0.75, parseImageScale("0.75"))
        assertEquals(2.0, parseImageScale("2"))
        assertEquals(1.0, parseImageScale("1"))
        assertNull(parseImageScale(null))
        assertNull(parseImageScale(""))
        assertNull(parseImageScale("1,2"))
        assertNull(parseImageScale("-1"))
        assertNull(parseImageScale("abc"))
        assertNull(parseImageScale("Infinity"))
        assertNull(parseImageScale("0"))     // not strictly positive
        assertNull(parseImageScale("1e3"))   // no exponent
    }

    // ---- isValidImagePath (V5 path-traversal allow-list) -----------------------------------------

    @Test
    fun imagePathAllowList() {
        assertTrue(isValidImagePath("config/img.png"))
        assertTrue(isValidImagePath("config/prompt-assets/valid.svg"))
        assertFalse(isValidImagePath("/etc/passwd"))
        assertFalse(isValidImagePath("~/x"))
        assertFalse(isValidImagePath("img.png"))            // not under config/
        assertFalse(isValidImagePath("config/../x"))        // parent traversal
        assertFalse(isValidImagePath("config/a:b/x"))       // colon in a segment
        assertFalse(isValidImagePath("config/x\\y"))        // backslash
        assertFalse(isValidImagePath("config//x"))          // empty segment
        assertFalse(isValidImagePath(""))
    }

    // ---- Totality: never throws on adversarial input ---------------------------------------------

    @Test
    fun parseActionIsTotalOnAdversarialInput() {
        val inputs = listOf(
            "",
            "<",
            "// action:prompt_",                              // truncated (empty cmd, empty arg)
            "// action:prompt_markup <color:#zz>bad</color>", // malformed color → markup still parses
            "// action:prompt_image",                         // no args
            "// action:prompt_button",                        // no label
            "// action:prompt_size 😀",             // emoji arg
            "// action:prompt_markup <unterminated",          // unclosed tag
        )
        for (input in inputs) {
            // Must return (null or a value) and never throw.
            val result: PromptEvent? = parseAction(input)
            // A degraded markup line still produces an event; the rest may be null — either is fine.
            if (input.startsWith("// action:prompt_markup")) {
                assertTrue("markup line should yield a Markup event", result is PromptEvent.Markup)
            }
        }
    }
}
