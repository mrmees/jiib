package works.mees.jiib.theme

import org.junit.Test
import java.io.File

/**
 * Focus-text conformance guard (Focus-text law, 2026-06-29). In the Focus-screen files, UNBOUNDED
 * raw Text(/BasicText( is banned — calls that carry neither `maxLines` nor `autoSize` in their
 * argument span must route through FocusText / FocusHeroText / FocusHeroValueText.
 *
 * Already-bounded raw Text( (has `maxLines` and/or `autoSize`) passes untouched — these are
 * Field/list/dialog sites where overflow is already controlled.  Callers tagged with
 * `// focus-text-exempt: <reason>` on the opening-paren line are also skipped.
 *
 * Detection uses a balanced-paren scan over the STRIPPED source (comments + string literals
 * blanked) so `maxLines` inside a comment or `Text(` inside a string literal don't count.
 *
 * REPORTING MODE until the sweep completes (Task 9 flips [ENFORCE]).  Mirrors FontConformanceTest.
 */
class FocusTextConformanceTest {
    private val enforce = ENFORCE

    // Curated Focus-screen files (those that build a Focus via FocusFrame/ScreenScaffold). Field/
    // list/dialog text inside them uses the inline `// focus-text-exempt:` marker, not a file skip.
    private val focusFiles = listOf(
        "ui/calibration/BedMeshScreen.kt", "ui/calibration/CalibrationHubScreen.kt",
        "ui/calibration/ProbeScreen.kt", "ui/calibration/ScrewsTiltScreen.kt",
        "ui/calibration/TiltScreen.kt", "ui/console/ConsoleScreen.kt",
        "ui/extrude/ExtrudeScreen.kt", "ui/heaters/HeatersList.kt", "ui/files/FilesScreen.kt",
        "ui/finetune/FineTuneScreen.kt", "ui/heatpresets/HeatPresetsScreen.kt",
        "ui/heatpresets/HeatPresetWizard.kt", "ui/increments/IncrementValuesScreen.kt",
        "ui/macros/BookmarkedMacrosScreen.kt", "ui/move/MoveScreen.kt",
        "ui/outputs/OutputsScreen.kt", "ui/outputs/OutputFocusControl.kt",
        "ui/outputs/OutputToggleControl.kt", "ui/printstatus/PrintStatusScreen.kt",
        "ui/printstatus/PrintStatusFocus.kt", "ui/printstatus/PrintStatusField.kt",
        "ui/printstatus/HomeDigest.kt", "ui/screen/AppSettingsScreen.kt",
        "ui/screen/FontPickerScreen.kt", "ui/screen/PowerResetScreen.kt",
        "ui/screen/PrinterConnectionEditor.kt", "ui/screen/PrinterFindScreen.kt",
        "ui/screen/PrinterSettingsScreen.kt", "ui/screen/PrintersScreen.kt",
        "ui/screen/SystemPageScreen.kt", "ui/screen/ThemeScreen.kt", "ui/spool/SpoolScreen.kt",
        "ui/systeminfo/SystemInformationScreen.kt", "ui/systeminfo/DeviceFocus.kt",
        "ui/temperature/TemperatureScreen.kt", "designsystem/components/AdjusterPanel.kt",
    )

    private val rawText = Regex("""(^|[^.\w])(Text|BasicText)\s*\(""")
    private val exempt = "focus-text-exempt:"

    @Test fun focusBodyTextUsesSanctionedHelpers() {
        val base = mainSrcDir()
        val offenders = focusFiles.flatMap { rel ->
            val f = File(base, rel)
            if (!f.exists()) return@flatMap emptyList<String>()
            val original = f.readLines()
            val stripped = strip(f.readText())

            val fileOffenders = mutableListOf<String>()
            for (match in rawText.findAll(stripped)) {
                // The last character of the regex match is always `(`.
                val openParen = match.range.last

                // Line index (0-based) of the Text(/BasicText( token.
                val lineIdx = stripped.substring(0, openParen).count { it == '\n' }

                // Honor the exempt marker on the ORIGINAL source line (strip blanks comments).
                if (original.getOrNull(lineIdx)?.contains(exempt) == true) continue

                // Balanced-paren scan over the STRIPPED text to find the matching `)`.
                // Strings are already blanked so stray `)` inside them can't unbalance the count.
                var depth = 1
                var pos = openParen + 1
                while (pos < stripped.length && depth > 0) {
                    when (stripped[pos]) {
                        '(' -> depth++
                        ')' -> depth--
                    }
                    if (depth > 0) pos++
                }
                // stripped[pos] is now the closing `)` (or pos == length if source is malformed).
                val argSpan = stripped.substring(openParen + 1, pos)

                // Flag ONLY if the call is UNBOUNDED: neither `maxLines` nor `autoSize` found in
                // the argument span.  Either keyword in the span means overflow is already controlled.
                if (!argSpan.contains("maxLines") && !argSpan.contains("autoSize")) {
                    fileOffenders += "$rel:${lineIdx + 1}"
                }
            }
            fileOffenders
        }

        if (offenders.isNotEmpty()) {
            println("FOCUS-TEXT CONFORMANCE (unbounded): ${offenders.size} raw Text/BasicText in Focus files:")
            offenders.forEach { println("  $it") }
        }
        if (enforce) assert(offenders.isEmpty()) {
            "Unbounded Focus-body text must use FocusText/FocusHeroText/FocusHeroValueText (or tag " +
                "`// focus-text-exempt:`):\n${offenders.joinToString("\n")}"
        }
    }

    private fun mainSrcDir(): File {
        val userDir = requireNotNull(System.getProperty("user.dir"))
        var dir: File? = File(userDir).canonicalFile
        while (dir != null) {
            val c = File(dir, "app/src/main/java/works/mees/jiib")
            if (c.isDirectory) return c
            dir = dir.parentFile
        }
        error("main source dir not found from $userDir")
    }

    private companion object { const val ENFORCE = false }
}

/**
 * Blank out `//` line comments, `/* */` block comments, and the CONTENTS of `"..."` / `"""..."""`
 * string literals (replace each consumed char with a space), leaving newlines intact so line numbers
 * are preserved. So the conformance regex matches only real code, never `Text(` inside a comment/string.
 */
private fun strip(src: String): String {
    val out = StringBuilder(src.length)
    var i = 0
    val n = src.length
    while (i < n) {
        val c = src[i]
        val c2 = if (i + 1 < n) src[i + 1] else ' '
        when {
            c == '/' && c2 == '/' -> { // line comment → blank to EOL
                while (i < n && src[i] != '\n') { out.append(' '); i++ }
            }
            c == '/' && c2 == '*' -> { // block comment → blank, keep newlines
                out.append("  "); i += 2
                while (i < n && !(src[i] == '*' && i + 1 < n && src[i + 1] == '/')) {
                    out.append(if (src[i] == '\n') '\n' else ' '); i++
                }
                if (i < n) { out.append("  "); i += 2 }
            }
            c == '"' && c2 == '"' && i + 2 < n && src[i + 2] == '"' -> { // triple-quoted
                out.append("   "); i += 3
                while (i < n && !(src[i] == '"' && i + 1 < n && src[i + 1] == '"' && i + 2 < n && src[i + 2] == '"')) {
                    out.append(if (src[i] == '\n') '\n' else ' '); i++
                }
                if (i < n) { out.append("   "); i += 3 }
            }
            c == '"' -> { // normal string, honor \" escapes
                out.append(' '); i++
                while (i < n && src[i] != '"') {
                    if (src[i] == '\\' && i + 1 < n) { out.append("  "); i += 2 }
                    else { out.append(if (src[i] == '\n') '\n' else ' '); i++ }
                }
                if (i < n) { out.append(' '); i++ }
            }
            else -> { out.append(c); i++ }
        }
    }
    return out.toString()
}
