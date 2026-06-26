package works.mees.jiib.prompt

/**
 * The Macro Prompt Protocol v1 model + event hierarchy (PROMPT-01). A faithful port of the upstream
 * `packages/js/src/types.ts` event union: the discrete, immutable outputs of [parseAction] /
 * [disconnectEvent] that the reducer (12-02) folds into prompt state.
 *
 * Pure data only — no Android, no Compose, no I/O — so the parse layer is host-unit-testable
 * ([works.mees.jiib.prompt] tests). Every collection is an immutable [List]; never `MutableList`.
 */

/** The 6 semantic button styles; anything else normalizes to [SECONDARY] (see [normalizeStyle]). */
enum class PromptStyle {
    PRIMARY, SECONDARY, INFO, WARNING, ERROR, SUCCESS;

    companion object {
        /** The wire token (lowercase) for this style, e.g. [PRIMARY] → `"primary"`. */
        fun fromToken(token: String): PromptStyle? =
            entries.firstOrNull { it.name.equals(token, ignoreCase = true) }
    }
}

/** The text-size ladder ([PromptSize] minus `full-screen`); used by the `<size:...>` markup tag. */
enum class PromptTextSize { SMALL, NORMAL, LARGE, X_LARGE }

/**
 * The per-prompt size hint: the text-size ladder plus the distinct viewport-filling `full-screen`.
 * An empty/unknown `prompt_size` argument yields a `null` size (frontend default), not a member here.
 */
enum class PromptSize(val token: String) {
    SMALL("small"),
    NORMAL("normal"),
    LARGE("large"),
    X_LARGE("x-large"),
    FULL_SCREEN("full-screen");

    companion object {
        /** Case-insensitive token match (`"LARGE"` → [LARGE]); unknown/empty → `null`. */
        fun fromToken(token: String): PromptSize? =
            entries.firstOrNull { it.token == token.trim().lowercase() }
    }
}

/** Item alignment. `center` is the default and is omitted from item state (only left/right are stamped). */
enum class PromptAlign(val token: String) {
    LEFT("left"),
    CENTER("center"),
    RIGHT("right");

    companion object {
        /** Case-insensitive token match; unknown/empty → `null` (alignment unchanged). */
        fun fromToken(token: String): PromptAlign? =
            entries.firstOrNull { it.token == token.trim().lowercase() }
    }
}

/** The fields shared by content/footer buttons (label, gcode defaulting to label, normalized style). */
data class ButtonFields(
    val label: String,
    val gcode: String,
    val style: PromptStyle,
)

/**
 * A PromptMarkup AST node — the renderer-neutral tree produced by [parseMarkup]. Stored for
 * conformance (plain_text) only; the AnnotatedString translation lives in 12-04. Mirrors the
 * `MarkupNode` union in `types.ts`.
 */
sealed interface MarkupNode {
    /** A run of decoded plain text (entities already resolved once). */
    data class Text(val text: String) : MarkupNode

    /** A `<b>`/`<i>`/`<u>` emphasis tag wrapping [children]. */
    data class Emphasis(val tag: EmphasisTag, val children: List<MarkupNode>) : MarkupNode

    /** A `<color:#rrggbb>` or `<bgcolor:#rrggbb>` tag; [value] is the validated `#rrggbb` string. */
    data class Color(val background: Boolean, val value: String, val children: List<MarkupNode>) :
        MarkupNode

    /** A `<size:small|normal|large|x-large>` tag; [value] is the normalized text size. */
    data class Size(val value: PromptTextSize, val children: List<MarkupNode>) : MarkupNode
}

/** The three emphasis tags (`b`/`i`/`u`) — case-sensitive in the grammar. */
enum class EmphasisTag(val tag: String) { B("b"), I("i"), U("u") }

/**
 * A discrete protocol event — the output of [parseAction] (or [disconnectEvent]). Mirrors the
 * `PromptEvent` union in `types.ts`. The reducer (12-02) folds a stream of these into prompt state.
 */
sealed interface PromptEvent {
    data class Begin(val title: String) : PromptEvent
    data class Text(val text: String) : PromptEvent
    data class Markup(val markup: String, val plainText: String) : PromptEvent
    data class Image(val path: String, val alt: String, val scale: Double?) : PromptEvent
    data class Button(val label: String, val gcode: String, val style: PromptStyle) : PromptEvent
    data class FooterButton(val label: String, val gcode: String, val style: PromptStyle) :
        PromptEvent

    data object RowStart : PromptEvent
    data object RowEnd : PromptEvent
    data object ButtonGroupStart : PromptEvent
    data object ButtonGroupEnd : PromptEvent

    data class Target(val targets: List<String>) : PromptEvent
    data class Size(val size: PromptSize?) : PromptEvent
    data class Align(val align: PromptAlign?) : PromptEvent

    data object Show : PromptEvent
    data object End : PromptEvent
    data object Disconnect : PromptEvent
}
