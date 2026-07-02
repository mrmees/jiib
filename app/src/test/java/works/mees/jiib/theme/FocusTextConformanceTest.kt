package works.mees.jiib.theme

import org.junit.Test
import java.io.File

/**
 * Focus-text conformance guard (Focus-text law, 2026-06-29). In the Focus-screen files, UNBOUNDED
 * raw Text(/BasicText( is banned — calls that carry neither `maxLines` nor `autoSize` as a
 * TOP-LEVEL named argument must route through FocusText / FocusHeroText / FocusHeroValueText.
 *
 * Already-bounded raw Text( (has `maxLines` and/or `autoSize` as a top-level arg) passes untouched
 * — these are Field/list/dialog sites where overflow is already controlled.  Callers tagged with
 * `// focus-text-exempt: <reason>` on the opening-paren line are also skipped.
 *
 * Detection uses [findCalls] (balanced-paren scan over the STRIPPED source from [stripKotlin]) and
 * [topLevelArgNames] so that `maxLines` buried inside a nested lambda/call does NOT count — that
 * was the substring false-pass hole in the original implementation.
 *
 * ENFORCE mode: build-fails on any unbounded Focus text (neither maxLines nor autoSize).  Mirrors FontConformanceTest.
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
            val stripped = stripKotlin(f.readText())

            val fileOffenders = mutableListOf<String>()
            for (call in findCalls(stripped, rawText)) {
                // Honor the exempt marker on the ORIGINAL source line (strip blanks comments).
                if (original.getOrNull(call.line - 1)?.contains(exempt) == true) continue

                // Flag ONLY if the call is UNBOUNDED: neither `maxLines` nor `autoSize` found as
                // a top-level named argument. Uses topLevelArgNames so that `maxLines` buried
                // inside a nested lambda { Text("x", maxLines = 1) } does NOT count.
                val names = topLevelArgNames(call.argSpan)
                if ("maxLines" !in names && "autoSize" !in names) {
                    fileOffenders += "$rel:${call.line}"
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

    private companion object { const val ENFORCE = true }
}
