package works.mees.jiib.prompt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PromptMarkup grammar (PROMPT-01 / T-12-03) — the PURE, total [parseMarkup] AST builder +
 * [markupToPlainText]. Ports the markup-relevant cases from upstream `hardening.spec.ts`: tag-name
 * case-sensitivity, decode-entities-once, invalid-tag-strip-keep-inner, and the plain-text fallback.
 */
class PromptMarkupTest {

    @Test
    fun boldAndColorBuildAstAndPlainText() {
        val ast = parseMarkup("<b>hi</b> <color:#ff0000>red</color>")
        // A bold node, a literal space text node, and a color node.
        assertEquals(3, ast.size)
        assertTrue(ast[0] is MarkupNode.Emphasis && (ast[0] as MarkupNode.Emphasis).tag == EmphasisTag.B)
        assertTrue(ast[2] is MarkupNode.Color)
        assertEquals("hi red", markupToPlainText("<b>hi</b> <color:#ff0000>red</color>"))
    }

    @Test
    fun unknownTagIsCaseSensitiveStrippedInnerKept() {
        // <B> is unknown (case-sensitive) → tag stripped, inner text preserved.
        val ast = parseMarkup("<B>x</B>")
        assertEquals(listOf<MarkupNode>(MarkupNode.Text("x")), ast)
        assertEquals("x", markupToPlainText("<B>x</B>"))
    }

    @Test
    fun sizeTagValueIsCaseInsensitive() {
        val ast = parseMarkup("<size:SMALL>x</size>")
        assertEquals(1, ast.size)
        val size = ast[0] as MarkupNode.Size
        assertEquals(PromptTextSize.SMALL, size.value)
        assertEquals(listOf<MarkupNode>(MarkupNode.Text("x")), size.children)
    }

    @Test
    fun entitiesDecodeExactlyOnce() {
        // &lt; &gt; &amp; decode once; two-char backslash-n → LF; decoded output is never re-parsed.
        assertEquals("<a&b\n", markupToPlainText("&lt;a&amp;b\\n"))
        // A literal &amp;lt; decodes to &lt; (NOT <) — single pass, no re-scan.
        assertEquals("&lt;", markupToPlainText("&amp;lt;"))
    }

    @Test
    fun invalidColorThreeHexIsStrippedInnerKept() {
        // <color:#fff> (3 hex) is invalid → tag ignored, inner text preserved.
        val ast = parseMarkup("<color:#fff>x</color>")
        assertEquals(listOf<MarkupNode>(MarkupNode.Text("x")), ast)
        assertEquals("x", markupToPlainText("<color:#fff>x</color>"))
    }

    @Test
    fun nestedMarkupFlattensInOrder() {
        val plain = markupToPlainText("<b><color:#22c55e>Ready &amp; safe</color></b>\\n<size:small>Use &lt;care&gt;.</size>")
        assertEquals("Ready & safe\nUse <care>.", plain)
    }

    @Test
    fun unterminatedTagEmitsLiteralLessThan() {
        // A `<` with no matching `>` is emitted literally and never throws.
        assertEquals("a<b", markupToPlainText("a<b"))
    }

    @Test
    fun mismatchedCloseIsIgnored() {
        // An extra/mismatched close tag is ignored; content already flushed at the current level.
        assertEquals("hi", markupToPlainText("hi</b>"))
    }
}
