package works.mees.jiib.theme

import org.junit.Test
import java.io.File

/**
 * Font conformance guard (THEMING.md §R11 / 2026-06-15 design). Text family & size must come from
 * DinghyType roles, never inline. Allowlisted files own the role plumbing / are non-shipping.
 *
 * REPORTING MODE: prints offenders, does not fail — flip [ENFORCE] to true once the sweep completes
 * (final task). Keep this file in sync with the allowlist in the plan.
 */
class FontConformanceTest {

    private val enforce = ENFORCE

    private val allowlist = listOf(
        "theme/DinghyType.kt",
        "theme/compose/DinghyTextStyle.kt",
        "designsystem/MaterialSymbol.kt",
        "ui/console/ConsoleRowsAdapter.kt",
        "ui/files/FileRowsAdapter.kt",
        "render/GraphView.kt",
        "render/WebcamView.kt",
        // The macro-author `<size:…>` markup ladder is DATA (the size analog of the documented
        // `<color:#hex>` author-hex carve-out, THEMING.md §D-03) — a SpanStyle fontSize the author
        // chooses, not chrome. The base run is role-routed (DinghyType.body); only the sanctioned
        // size-span carve-out remains inline here.
        "ui/prompt/PromptMarkupText.kt",
        "/preview/",   // NOT /bench/ — BenchActivity ships (manifest), gets swept
    )

    private val patterns = listOf(
        Regex("""\bfontFamily\s*="""),
        Regex("""\bfontSize\s*="""),
    )

    @Test
    fun textFamilyAndSizeComeFromRoles() {
        val mainDir = mainSrcDir()
        val offenders = mainDir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { f -> allowlist.none { f.invariantPath().contains(it) } }
            .flatMap { f ->
                f.readLines().withIndex().filter { (_, line) ->
                    patterns.any { it.containsMatchIn(line) }
                }.map { (i, line) -> "${f.invariantPath()}:${i + 1}: ${line.trim()}" }
            }
            .toList()

        if (offenders.isNotEmpty()) {
            println("FONT CONFORMANCE: ${offenders.size} inline font sites remaining:")
            offenders.forEach { println("  $it") }
        }
        if (enforce) {
            assert(offenders.isEmpty()) {
                "Inline font sites must use DinghyType roles:\n${offenders.joinToString("\n")}"
            }
        }
    }

    private fun File.invariantPath(): String = path.replace('\\', '/')

    private fun mainSrcDir(): File {
        val userDir = requireNotNull(System.getProperty("user.dir"))
        var dir: File? = File(userDir).canonicalFile
        while (dir != null) {
            val candidate = File(dir, "app/src/main/java/works/mees/jiib")
            if (candidate.isDirectory) return candidate
            dir = dir.parentFile
        }
        error("main source dir not found from $userDir")
    }

    private companion object { const val ENFORCE = true }
}
