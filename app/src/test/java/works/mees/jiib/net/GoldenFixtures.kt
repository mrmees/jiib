package works.mees.jiib.net

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

/**
 * Loads the golden fixture corpus (the `/golden/` resources) from the test classpath and parses it with the same
 * [MoonrakerJson] posture the production inbound path uses. Wave 2/3 tests prefer the captured live
 * frames (`objects_list.json`, ...) but never break when only the synthetic `fallback_*` siblings
 * exist — [resolve] falls back automatically, so an `autonomous: true` wave needs no live hardware.
 */
object GoldenFixtures {

    /** Read a raw resource string from `/golden/<name>` (name without the leading slash). */
    fun raw(name: String): String =
        requireNotNull(GoldenFixtures::class.java.getResourceAsStream("/golden/$name")) {
            "Fixture /golden/$name not found on the test classpath"
        }.bufferedReader().use { it.readText() }

    /** Parse a `/golden/<name>` resource into a [JsonElement]. */
    fun load(name: String): JsonElement = MoonrakerJson.parseToJsonElement(raw(name))

    /** Parse and return the top-level object. */
    fun loadObject(name: String): JsonObject = load(name).jsonObject

    /**
     * Resolve a logical golden name to the live capture if present on the classpath, else the
     * `fallback_` sibling. Pass the live name (e.g. `objects_list.json`); returns the parsed element.
     */
    fun resolve(liveName: String): JsonElement {
        val live = GoldenFixtures::class.java.getResourceAsStream("/golden/$liveName")
        return if (live != null) {
            live.bufferedReader().use { MoonrakerJson.parseToJsonElement(it.readText()) }
        } else {
            load("fallback_$liveName")
        }
    }

    /** Extract the ordered `frames` array (notification/stream fixtures) as raw JSON strings. */
    fun frames(name: String): List<String> =
        loadObject(name)["frames"]!!.jsonArray.map { it.toString() }

    /** The `result.objects` capability array from an objects.list fixture. */
    fun objectsList(name: String): JsonArray =
        loadObject(name)["result"]!!.jsonObject["objects"]!!.jsonArray
}
