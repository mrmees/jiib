package works.mees.jiib.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusSourceScanTest {

    @Test
    fun top_level_args_are_parsed_by_name() {
        val span = """text = "hi", maxLines = 1, overflow = TextOverflow.Ellipsis"""
        assertEquals(setOf("text", "maxLines", "overflow"), topLevelArgNames(span))
    }

    @Test
    fun nested_lambda_args_do_not_leak_to_the_top_level() {
        // THE false-pass hole: maxLines only inside a nested call must NOT count.
        val span = """modifier = Modifier.weight(1f), content = { Text("x", maxLines = 1) }"""
        val names = topLevelArgNames(span)
        assertTrue("maxLines" !in names)
        assertEquals(setOf("modifier", "content"), names)
    }

    @Test
    fun positional_args_produce_no_names() {
        assertEquals(setOf("style"), topLevelArgNames(""""hello", style = someStyle"""))
    }

    @Test
    fun char_literals_are_stripped() {
        // A ')' char literal used to unbalance the paren walk.
        val src = "fun f() { if (c == ')') g(maxLines = 1) }"
        val stripped = stripKotlin(src)
        assertTrue(!stripped.contains("')'"))
        assertEquals(src.length, stripped.length)   // strip is length-preserving
    }

    @Test
    fun find_calls_returns_arg_spans_and_lines() {
        val src = "val a = 1\nText(x, maxLines = 1)\n"
        val calls = findCalls(stripKotlin(src), Regex("""(^|[^.\w])(Text|BasicText)\s*\("""))
        assertEquals(1, calls.size)
        assertEquals(2, calls[0].line)
        assertTrue("maxLines" in topLevelArgNames(calls[0].argSpan))
    }

    @Test
    fun trailing_lambda_span_is_located_after_the_call() {
        val src = "FocusFrame(title = t) {\n  FocusDigest(rows)\n}\n"
        val stripped = stripKotlin(src)
        val call = findCalls(stripped, Regex("""(^|[^.\w])FocusFrame\s*\(""")).single()
        val (start, end) = lambdaBodySpan(stripped, call.endOffset)!!
        val body = stripped.substring(start, end)
        assertTrue(body.contains("FocusDigest"))
    }
}
