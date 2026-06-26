package works.mees.jiib.prompt

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

/**
 * Test-classpath loader for the committed Macro Prompt Protocol conformance corpus
 * (`/prompt/fixtures.json` — schema_version 1, 26 fixtures = 8 core + 18 optional).
 *
 * Mirrors [works.mees.jiib.net.GoldenFixtures]: `getResourceAsStream` + `Json.parseToJsonElement`,
 * then exposes the top-level object and the `["fixtures"].jsonArray`. The corpus is the
 * renderer-neutral oracle every prompt frontend validates against (D-14); it is a byte-identical
 * committed copy of the upstream pack (see `fixtures-source.txt`).
 */
object PromptFixtures {

    /** Read the raw `/prompt/<name>` resource string from the test classpath. */
    fun raw(name: String): String =
        requireNotNull(PromptFixtures::class.java.getResourceAsStream("/prompt/$name")) {
            "Fixture /prompt/$name not found on the test classpath"
        }.bufferedReader().use { it.readText() }

    /** The parsed top-level fixtures document object (schema_version, protocol, fixtures, ...). */
    fun document(): JsonObject = Json.parseToJsonElement(raw("fixtures.json")).jsonObject

    /** The ordered `fixtures` array from the corpus document. */
    fun fixtures(): JsonArray = document()["fixtures"]!!.jsonArray

    /** Each fixture as a [JsonObject], in source order. */
    fun fixtureObjects(): List<JsonObject> = fixtures().map { it.jsonObject }
}
