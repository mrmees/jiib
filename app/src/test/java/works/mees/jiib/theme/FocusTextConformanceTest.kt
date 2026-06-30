package works.mees.jiib.theme

import org.junit.Test
import java.io.File

/**
 * Focus-text conformance guard (Focus-text law, 2026-06-29). In the Focus-screen files, Focus-body
 * text must route through FocusText / FocusHeroText / FocusHeroValueText — raw Text(/BasicText( is
 * banned unless tagged `// focus-text-exempt: <reason>` (Field/list/dialog text in the same file).
 *
 * REPORTING MODE until the sweep completes (Task 9 flips [ENFORCE]). Mirrors FontConformanceTest.
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
            strip(f.readText()).lines().withIndex()
                .filter { (i, stripped) ->
                    // Match raw Text(/BasicText( in CODE (comments+strings already blanked), and
                    // skip lines the author tagged exempt (check the ORIGINAL line — strip blanks comments).
                    rawText.containsMatchIn(stripped) &&
                        !(original.getOrNull(i)?.contains(exempt) ?: false)
                }
                .map { (i, _) -> "$rel:${i + 1}" }
        }

        if (offenders.isNotEmpty()) {
            println("FOCUS-TEXT CONFORMANCE: ${offenders.size} raw Text/BasicText in Focus files:")
            offenders.forEach { println("  $it") }
        }
        if (enforce) assert(offenders.isEmpty()) {
            "Focus-body text must use FocusText/FocusHeroText/FocusHeroValueText (or tag " +
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
