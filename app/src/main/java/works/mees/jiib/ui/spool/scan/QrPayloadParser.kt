package works.mees.jiib.ui.spool.scan

/**
 * PURE, host-tested QR payload parser (SPOOL-05 / D-12 — the load-bearing Security V5
 * input-validation boundary). A scanned label is UNTRUSTED input; this is an allow-list parser that
 * extracts an `Int` spool id ONLY and NEVER trusts, returns, or acts on a URL host. Mirrors the
 * pure-predicate idiom of [works.mees.jiib.ui.files.deleteAllowed] — no Android, no I/O, fully
 * unit-testable ([works.mees.jiib.spool.QrPayloadParserTest]).
 *
 * EXHAUSTIVE CONTRACT (every accept/reject class is locked by the Wave-0 decision table):
 *
 *  ACCEPT → [SpoolQrResult.Spool]:
 *   - `web+spoolman:s-<digits>`               (the canonical Spoolman spool URI)
 *   - `WEB+SPOOLMAN:S-<digits>`               (case-INSENSITIVE — uppercase is the same id)
 *   - `http(s)://<any-host>/spool/show/<digits>`  (parse the TRAILING id ONLY; the host —
 *     including a hostile `evil.example` — is NEVER read, returned, navigated, or used as a
 *     Spoolman base URL: D-12 "id carrier, not a navigation target")
 *
 *  REJECT → [SpoolQrResult.UnsupportedSpoolmanCode]:
 *   - `web+spoolman:f-<id>`                    (the FILAMENT scheme — a Spoolman code we don't load)
 *
 *  REJECT → [SpoolQrResult.NotASpoolCode]:
 *   - bare UPC/EAN (`012345678905`) and any non-numeric / arbitrary text (`abc`)
 *   - `web+spoolman:s-` with no digits, or a non-numeric id
 *   - any other scheme / a URL WITHOUT a `/spool/show/<digits>` segment (`https://x/other/5`)
 *   - an OVERLONG digit run that overflows `Int` → [SpoolQrResult.NotASpoolCode] via `toIntOrNull`
 *     (NEVER an overflow throw — T-11-03-02)
 *
 * The id is `Int`-validated with [String.toIntOrNull]; a downstream 404 ("spool not found") is the
 * UI's problem (11-08), not this parser's — its only job is to let nothing through in the wrong shape.
 */
sealed interface SpoolQrResult {
    /** A valid, `Int`-validated Spoolman spool id. The host (if any) is deliberately absent here. */
    data class Spool(val id: Int) : SpoolQrResult

    /** A recognized Spoolman code we don't load (e.g. the `f-` filament scheme). */
    data object UnsupportedSpoolmanCode : SpoolQrResult

    /** Not a Spoolman spool code at all (UPC/EAN, non-numeric, foreign scheme, missing id). */
    data object NotASpoolCode : SpoolQrResult
}

private const val SPOOL_URI_PREFIX = "web+spoolman:s-"
private const val FILAMENT_URI_PREFIX = "web+spoolman:f-"

/** Matches `http(s)://<host>/spool/show/<digits>` and captures ONLY the trailing digit run. */
private val SPOOL_SHOW_URL = Regex(
    "^https?://[^/]+/spool/show/(\\d+)/?$",
    RegexOption.IGNORE_CASE,
)

/**
 * Parse an untrusted scanned [payload] into a [SpoolQrResult], following the D-12 allow-list contract
 * documented on [SpoolQrResult]. Pure: same input always yields the same output. The host of a URL
 * payload is NEVER carried into the result — only the `Int` id.
 */
fun parseSpoolId(payload: String): SpoolQrResult {
    val trimmed = payload.trim()
    val lower = trimmed.lowercase()

    // 1) Canonical spool URI (case-insensitive): web+spoolman:s-<digits>
    if (lower.startsWith(SPOOL_URI_PREFIX)) {
        val idPart = trimmed.substring(SPOOL_URI_PREFIX.length)
        return idPart.toIntOrNull()?.let { SpoolQrResult.Spool(it) } ?: SpoolQrResult.NotASpoolCode
    }

    // 2) The filament scheme is a recognized-but-unsupported Spoolman code.
    if (lower.startsWith(FILAMENT_URI_PREFIX)) {
        return SpoolQrResult.UnsupportedSpoolmanCode
    }

    // 3) A Spoolman web URL — parse the trailing id ONLY; the host is ignored, never trusted (D-12).
    SPOOL_SHOW_URL.matchEntire(trimmed)?.let { match ->
        val idPart = match.groupValues[1]
        return idPart.toIntOrNull()?.let { SpoolQrResult.Spool(it) } ?: SpoolQrResult.NotASpoolCode
    }

    // 4) Anything else — UPC/EAN, non-numeric, a foreign scheme, a non-spool URL — is not a spool code.
    return SpoolQrResult.NotASpoolCode
}
