package works.mees.jiib.command

import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Test
import works.mees.jiib.net.MoonrakerJson
import java.io.File

/**
 * Drift guard for the PUBLISHED human command reference `docs/commands/COMMANDS.md`. The doc is a pure
 * rendering of the tracked `docs/commands/catalog.json` (via [CommandReferenceDoc]); this test fails the
 * build if the committed Markdown drifts from that render — so the code, the catalog, and the published
 * doc stay in lockstep (the catalog↔registry side is guarded by [CommandCatalogDriftTest]).
 *
 * REGENERATE after a legitimate catalog/registry change:
 * ```
 * ./gradlew :app:testDebugUnitTest --tests '*CommandReferenceDocDriftTest' -Djiib.regenerateDocs=true
 * ```
 * That writes the fresh doc (and passes); commit the result. Without the flag the test only compares.
 */
class CommandReferenceDocDriftTest {

    @Test
    fun publishedDocMatchesCatalog() {
        val catalogFile = docsFile("docs/commands/catalog.json")
        val catalog = MoonrakerJson.parseToJsonElement(catalogFile.readText()).jsonObject
        // Authoritative set = the runtime registry (what jiib actually sends), NOT the catalog's
        // drift-prone `registered` flag (Codex review 2026-07-06).
        val sentCatalogIds = CommandRegistry.all.map { it.catalogId }.toSet()
        val expected = CommandReferenceDoc.render(catalog, sentCatalogIds)

        // Sibling of catalog.json — resolves even on first generation (the file need not pre-exist).
        val docFile = File(catalogFile.parentFile, "COMMANDS.md")

        if (System.getProperty("jiib.regenerateDocs") == "true") {
            docFile.writeText(expected)
            return
        }

        assertEquals(
            "docs/commands/COMMANDS.md is stale vs catalog.json — regenerate with " +
                "-Djiib.regenerateDocs=true (see CommandReferenceDocDriftTest).",
            expected,
            docFile.takeIf { it.isFile }?.readText() ?: "<docs/commands/COMMANDS.md missing>",
        )
    }

    /** Walk up from the test working dir to the repo file (mirrors CommandCatalogDriftTest.docsFile). */
    private fun docsFile(path: String): File {
        val userDir = requireNotNull(System.getProperty("user.dir")) { "user.dir system property missing" }
        var dir: File? = File(userDir).canonicalFile
        while (dir != null) {
            val candidate = File(dir, path)
            if (candidate.isFile) return candidate
            dir = dir.parentFile
        }
        error("Fixture $path not found from ${System.getProperty("user.dir")}")
    }
}
