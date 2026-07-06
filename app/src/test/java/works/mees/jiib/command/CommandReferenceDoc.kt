package works.mees.jiib.command

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Renders the human-facing **published** command reference (`docs/commands/COMMANDS.md`) from the
 * tracked machine-readable [catalog][docs/commands/catalog.json]. It is the SINGLE render source; the
 * committed Markdown is a pure function of the catalog, and [CommandReferenceDocDriftTest] fails the
 * build if the checked-in file drifts from this output (regenerate with `-Djiib.regenerateDocs=true`).
 *
 * Scope (owner decision 2026-07-05): only the commands jiib ACTUALLY sends/calls. The AUTHORITATIVE
 * set is the runtime [CommandRegistry.all] (the catalog_ids jiib dispatches) — NOT the catalog's
 * `runtime_registry.registered` flag, which [CommandCatalogDriftTest] does not bind to the registry and
 * can drift (Codex review 2026-07-06: the flag marked 65 while the registry sends ~86, so a flag-based
 * doc silently omitted ~21 real commands). Each sent command is joined to its catalog.json row for the
 * display metadata (purpose/params/availability/upstream). The broad reference surface jiib never
 * touches stays in catalog.json only. Because it is generated from the registry, the doc cannot rot.
 *
 * Lives in the TEST sourceset: it is a dev/CI documentation tool, not app runtime code.
 */
object CommandReferenceDoc {

    /**
     * Render the Markdown document. [sentCatalogIds] is the authoritative set of catalog_ids jiib
     * sends (`CommandRegistry.all.map { it.catalogId }`); catalog entries are filtered to it and joined
     * for metadata. (`CommandCatalogDriftTest` already guarantees every sent id has a catalog row.)
     */
    fun render(catalog: JsonObject, sentCatalogIds: Set<String>): String {
        val commands = catalog["commands"]!!.jsonArray.map { it.jsonObject }
        val sent = commands.filter { it.str("catalog_id") in sentCatalogIds }
        val byCategory = sent
            .groupBy { it.str("category") }
            .toSortedMap()

        val sb = StringBuilder()
        sb.append(header(totalCatalog = commands.size, registeredCount = sent.size))
        for ((category, cmds) in byCategory) {
            sb.append("### ").append(categoryTitle(category)).append("\n\n")
            val ordered = cmds.sortedWith(compareBy({ it.str("name") }, { it.str("catalog_id") }))
            for (c in ordered) sb.append(renderCommand(c))
        }
        // Deterministic single trailing newline.
        return sb.toString().trimEnd('\n') + "\n"
    }

    private fun header(totalCatalog: Int, registeredCount: Int): String =
        """
        |<!--
        |  GENERATED FILE — DO NOT EDIT BY HAND.
        |  Rendered from docs/commands/catalog.json by CommandReferenceDoc (test sourceset).
        |  To update: change the CommandRegistry / catalog.json, then regenerate:
        |    ./gradlew :app:testDebugUnitTest --tests '*CommandReferenceDocDriftTest' -Djiib.regenerateDocs=true
        |  A stale file fails the build (CommandReferenceDocDriftTest).
        |-->
        |
        |# jiib — Moonraker & Printer Command Reference
        |
        |Every G-code command and Moonraker API call **jiib actually sends** to your printer — the
        |complete surface of how the app talks to Moonraker/Klipper. It is generated from jiib's runtime
        |command registry, so it can never fall out of sync with the code.
        |
        |This lists the **$registeredCount** commands jiib sends. It intentionally omits the broad
        |Klipper/Moonraker reference surface jiib does *not* use; the full $totalCatalog-entry catalog
        |(including planned and reference-only rows) lives in
        |[`docs/commands/catalog.json`](./catalog.json).
        |
        |## How jiib talks to the printer
        |
        |jiib holds a single WebSocket to Moonraker and speaks **JSON-RPC** over it. On connect it runs a
        |fixed handshake — `server.connection.identify` → `server.info` → `printer.objects.list` (discover
        |what this printer exposes) → `printer.objects.query` for the subset jiib needs → then
        |`printer.objects.subscribe` for that same subset, after which Moonraker pushes
        |`notify_status_update` diffs that jiib merges into its live state. Printer motion and macros are
        |sent as G-code through `printer.gcode.script`; everything else is a direct JSON-RPC method call.
        |Which commands are offered on a given printer is gated at runtime by the **Available when**
        |predicate shown below (evidence lives in [`printer-matrix.json`](./printer-matrix.json)).
        |
        |## Commands jiib sends ($registeredCount)
        |
        |
        """.trimMargin()

    private fun renderCommand(c: JsonObject): String {
        val sb = StringBuilder()
        sb.append("#### `").append(c.str("name")).append("` · ").append(transportLabel(c.str("transport")))
            .append('\n')
        c.strOrNull("purpose")?.let { sb.append(it).append('\n') }
        sb.append("- **Available when:** ").append(availability(c["availability"]?.jsonObject)).append('\n')
        paramList(c).takeIf { it.isNotEmpty() }?.let { params ->
            sb.append("- **Parameters:** ").append(params.joinToString(", ") { "`$it`" }).append('\n')
        }
        c.strOrNull("upstream_url")?.let { sb.append("- **Reference:** ").append(it).append('\n') }
        sb.append('\n')
        return sb.toString()
    }

    /** Prefer the curated `key_params`; fall back to the full `params`. Both are string lists. */
    private fun paramList(c: JsonObject): List<String> {
        val key = c["key_params"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }.orEmpty()
        if (key.isNotEmpty()) return key
        return c["params"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }.orEmpty()
    }

    private fun transportLabel(transport: String): String = when (transport) {
        "gcode_script" -> "G-code (via `printer.gcode.script`)"
        "json_rpc" -> "Moonraker JSON-RPC"
        "spoolman_rest" -> "Spoolman REST"
        else -> transport
    }

    private fun availability(a: JsonObject?): String {
        if (a == null) return "Always"
        return when (a.strOrNull("type")) {
            null, "always" -> "Always"
            "object_present" -> "Klipper object `${a.str("name")}` present"
            "any_object_present" -> {
                val names = a["names"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }.orEmpty()
                "any of " + names.joinToString(", ") { "`$it`" } + " present"
            }
            "macro_present" -> "macro `${a.str("name").uppercase()}` present"
            "component_present" -> "Moonraker component `${a.str("name")}` present"
            "gcode_command_present" -> "G-code `${a.str("name").uppercase()}` present"
            else -> a.str("type")
        }
    }

    /** `heater_generic chamber` → "Heater Generic Chamber"; `print_control` → "Print Control". */
    private fun categoryTitle(raw: String): String =
        raw.split(Regex("[\\s_]+"))
            .filter { it.isNotEmpty() }
            .joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }

    private fun JsonObject.str(key: String): String =
        this[key]?.jsonPrimitive?.contentOrNull ?: error("Missing string key '$key' in catalog entry $this")

    private fun JsonObject.strOrNull(key: String): String? =
        this[key]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
}
