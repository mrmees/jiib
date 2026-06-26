package works.mees.dinghy.prompt

import androidx.compose.runtime.Immutable

/**
 * The Macro Prompt Protocol v1 reducer model (PROMPT-01 / PROMPT-03) — a faithful port of the upstream
 * `packages/js/src/types.ts` state + view shapes. Two layers:
 *
 *  - [PromptStateData] is the INTERNAL state machine the reducer folds events into (12-02). It carries
 *    lifecycle/epoch/pending* bookkeeping the render layer must NEVER see.
 *  - [PromptView] is the EXTERNAL conformance projection — EXACTLY 6 keys (`visible`, `title`, `targets`,
 *    `size`, `items`, `footer_buttons`). It is the renderer-neutral oracle every prompt frontend grades
 *    against (D-14) and the structure the 26 fixtures assert (T-12-07: no internal field leaks).
 *
 * Pure data only — no I/O, no coroutines, no Compose beyond the `@Immutable` skip-hint. Every collection
 * is an immutable [List]; never `MutableList`. Immutability is free here (Kotlin `data class` + `List`),
 * which is the JS `view.ts` deep-clone boundary expressed by construction (RESEARCH: Immutability boundary).
 */

/** The four lifecycle phases of a prompt: idle → building → shown, or building/shown → suppressed at a non-matching begin. */
enum class PromptLifecycle { IDLE, BUILDING, SHOWN, SUPPRESSED }

/**
 * The engine identity + behavior options. Dinghy's identity (D-08) is `frontendId = "jiib"`,
 * `frontendCategories = ["touch"]`, with [liveAppend] true (D-02 — content keeps appending after show).
 */
@Immutable
data class PromptOpts(
    val frontendId: String = "jiib",
    val frontendCategories: List<String> = listOf("touch"),
    val liveAppend: Boolean = true,
)

/**
 * A footer button — the persistent action row at the bottom of a prompt (separate from content buttons).
 * Mirrors `PromptFooterButton` in types.ts.
 */
@Immutable
data class FooterButton(
    val label: String,
    val gcode: String,
    val style: PromptStyle,
)

/**
 * A content item in a prompt's body — the `PromptItem` union from types.ts. Top-level items may carry an
 * optional [PromptItem.align] (left/right; center omits the field). `row`/`button_group` items hold
 * [PromptItem.children]; leaf items leave it empty. The container kinds never appear as children.
 *
 * Modeled as a single immutable data class (not a sealed hierarchy) so the reducer's `items` list is a
 * homogeneous `List<PromptItem>` matching the JS array exactly and the view projection is mechanical.
 */
@Immutable
data class PromptItem(
    val type: PromptItemType,
    val text: String? = null,
    val markup: String? = null,
    val plainText: String? = null,
    val path: String? = null,
    val alt: String? = null,
    val scale: Double? = null,
    val label: String? = null,
    val gcode: String? = null,
    val style: PromptStyle? = null,
    /** Top-level alignment; `null` means center (the default) and is omitted from the conformance view. */
    val align: PromptAlign? = null,
    /** Children of a `row`/`button_group`; empty for leaf items. */
    val children: List<PromptItem> = emptyList(),
)

/** The discriminant for [PromptItem]; mirrors the `type` tag of the JS `PromptItem` union. */
enum class PromptItemType(val wire: String) {
    TEXT("text"),
    MARKUP("markup"),
    IMAGE("image"),
    BUTTON("button"),
    ROW("row"),
    BUTTON_GROUP("button_group"),
}

/**
 * The INTERNAL reducer state — the full state machine. Folded by `reduce` (PromptReducer.kt); projected
 * to the 6-key [PromptView] by `promptView`. Mirrors `PromptStateData` in types.ts.
 *
 * [size] is parsed + tracked even though Dinghy's renderer clamps every prompt to full-screen (D-06): the
 * conformance view must expose the authored size verbatim. [pendingTargets]/[pendingSize] are the
 * next-begin bookkeeping consumed (reset to null) at each begin — even a suppressed one (T-12-06).
 */
@Immutable
data class PromptStateData(
    val lifecycle: PromptLifecycle,
    val epoch: Int,
    val title: String,
    val size: PromptSize?,
    val activeTargets: List<String>,
    val items: List<PromptItem>,
    val footerButtons: List<FooterButton>,
    val activeContainer: PromptItemType?,
    val pendingTargets: List<String>?,
    val pendingSize: PromptSize?,
    val currentAlign: PromptAlign,
    val opts: PromptOpts,
)

/**
 * The CANONICAL semantic view — the conformance / interop / render-source shape. EXACTLY these 6 fields;
 * no internal reducer field (lifecycle, epoch, pending bookkeeping, activeContainer, opts) leaks here (T-12-07). Mirrors
 * `PromptView` in types.ts. Immutable by construction (the deep-clone boundary of `view.ts`, free in Kotlin).
 */
@Immutable
data class PromptView(
    val visible: Boolean,
    val title: String,
    val targets: List<String>,
    val size: PromptSize?,
    val items: List<PromptItem>,
    val footerButtons: List<FooterButton>,
)
