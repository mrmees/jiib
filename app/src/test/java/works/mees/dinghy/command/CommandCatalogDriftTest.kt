package works.mees.dinghy.command

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertTrue
import org.junit.Test
import works.mees.dinghy.net.MoonrakerJson
import java.io.File

/**
 * D-10 guard: the in-code registry must not drift away from the machine-readable command catalog
 * and E5/E3 matrix sidecars. These tests intentionally read JSON sidecars from docs/commands; human
 * Markdown reference files are not an enforcement source.
 */
class CommandCatalogDriftTest {

    @Test
    fun registryCatalogIdsExistInCatalogJson() {
        val catalogIds = catalogCommands()
            .map { it.string("catalog_id") }
            .toSet()

        val registryIds = CommandRegistry.all.map { it.catalogId }

        assertTrue("CommandRegistry.all must not be empty", registryIds.isNotEmpty())
        val missing = registryIds.filterNot { it in catalogIds }
        assertTrue(
            "Registry catalog IDs missing from docs/commands/catalog.json: $missing",
            missing.isEmpty(),
        )
    }

    @Test
    fun predicateReferencesAreBackedByMatrixEvidence() {
        val matrix = printerMatrix()
        val printers = matrix.printers
        val notOn = matrix.notOnPrinters
        val predicates = CommandRegistry.all
            .flatMap { it.availability.leafPredicates() }
            .filterNot { it is AvailabilityPredicate.Always }

        assertTrue("registry must expose at least one non-trivial predicate guard", predicates.isNotEmpty())

        val missing = buildList {
            for (predicate in predicates) {
                for (printer in printers) {
                    if (!printer.hasEvidenceFor(predicate) && !notOn.contains(predicate.matrixKey(), printer.id)) {
                        add("${predicate.matrixKey()} on ${printer.id}")
                    }
                }
            }
        }

        assertTrue(
            "Registry predicates must be backed by printer-matrix evidence or explicit not_on_printers: $missing",
            missing.isEmpty(),
        )
    }

    private fun catalogCommands(): List<JsonObject> =
        docsJson("docs/commands/catalog.json")
            .jsonObject["commands"]!!
            .jsonArray
            .map { it.jsonObject }

    private fun printerMatrix(): Matrix {
        val root = docsJson("docs/commands/printer-matrix.json").jsonObject
        val printers = root["printers"]!!.jsonArray.map { printer ->
            val obj = printer.jsonObject
            MatrixPrinter(
                id = obj.string("id"),
                objects = obj.stringSet("objects"),
                macros = obj.stringSet("macros"),
                components = obj.stringSet("components"),
            )
        }
        val notOn = root["not_on_printers"]?.jsonArray.orEmpty().flatMap { entry ->
            val obj = entry.jsonObject
            val key = obj["predicate"]!!.jsonObject.matrixKey()
            obj["printer_ids"]!!.jsonArray.map { MatrixExclusion(key, it.jsonPrimitive.content) }
        }.toSet()
        return Matrix(printers, notOn)
    }

    private fun AvailabilityPredicate.leafPredicates(): List<AvailabilityPredicate> = when (this) {
        AvailabilityPredicate.Always -> emptyList()
        is AvailabilityPredicate.ObjectPresent -> listOf(this)
        is AvailabilityPredicate.MacroPresent -> listOf(this)
        is AvailabilityPredicate.ComponentPresent -> listOf(this)
        is AvailabilityPredicate.GcodeCommandPresent -> listOf(this)
        is AvailabilityPredicate.NotOnOurPrinters -> listOf(this)
        is AvailabilityPredicate.AnyOf -> predicates.flatMap { it.leafPredicates() }
    }

    private fun AvailabilityPredicate.matrixKey(): String = when (this) {
        AvailabilityPredicate.Always -> "always"
        is AvailabilityPredicate.ObjectPresent -> "object_present:$name"
        is AvailabilityPredicate.MacroPresent -> "macro_present:${name.uppercase()}"
        is AvailabilityPredicate.ComponentPresent -> "component_present:$name"
        is AvailabilityPredicate.GcodeCommandPresent -> "gcode_command_present:${name.uppercase()}"
        is AvailabilityPredicate.NotOnOurPrinters -> "not_on_our_printers:$reason"
        is AvailabilityPredicate.AnyOf -> predicates.joinToString(prefix = "any_of:", separator = "|") { it.matrixKey() }
    }

    private fun MatrixPrinter.hasEvidenceFor(predicate: AvailabilityPredicate): Boolean = when (predicate) {
        AvailabilityPredicate.Always -> true
        is AvailabilityPredicate.ObjectPresent -> predicate.name in objects
        is AvailabilityPredicate.MacroPresent -> macros.any { it.equals(predicate.name, ignoreCase = true) }
        is AvailabilityPredicate.ComponentPresent -> predicate.name in components
        is AvailabilityPredicate.GcodeCommandPresent -> macros.any { it.equals(predicate.name, ignoreCase = true) }
        is AvailabilityPredicate.NotOnOurPrinters -> true
        is AvailabilityPredicate.AnyOf -> predicate.predicates.any { hasEvidenceFor(it) }
    }

    private fun JsonObject.matrixKey(): String {
        val type = string("type")
        return when (type) {
            "object_present" -> "object_present:${string("name")}"
            "macro_present" -> "macro_present:${string("name").uppercase()}"
            "component_present" -> "component_present:${string("name")}"
            "gcode_command_present" -> "gcode_command_present:${string("name").uppercase()}"
            else -> type
        }
    }

    private fun docsJson(path: String): JsonElement {
        val file = docsFile(path)
        return MoonrakerJson.parseToJsonElement(file.readText())
    }

    private fun docsFile(path: String): File {
        var dir: File? = File(System.getProperty("user.dir")).canonicalFile
        while (dir != null) {
            val candidate = File(dir, path)
            if (candidate.isFile) return candidate
            dir = dir.parentFile
        }
        error("Fixture $path not found from ${System.getProperty("user.dir")}")
    }

    private fun JsonObject.string(key: String): String =
        this[key]?.jsonPrimitive?.content ?: error("Missing string key '$key' in $this")

    private fun JsonObject.stringSet(key: String): Set<String> =
        (this[key] as? JsonArray)
            ?.map { it.jsonPrimitive.content }
            ?.toSet()
            ?: emptySet()

    private data class Matrix(val printers: List<MatrixPrinter>, val notOnPrinters: Set<MatrixExclusion>)
    private data class MatrixPrinter(
        val id: String,
        val objects: Set<String>,
        val macros: Set<String>,
        val components: Set<String>,
    )
    private data class MatrixExclusion(val predicateKey: String, val printerId: String)

    private fun Set<MatrixExclusion>.contains(predicateKey: String, printerId: String): Boolean =
        contains(MatrixExclusion(predicateKey, printerId))
}
