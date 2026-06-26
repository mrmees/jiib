package works.mees.jiib.designsystem.icons

import androidx.annotation.DrawableRes

/**
 * One-of-N source for a semantic icon (RESEARCH Q6 / D-07 / D-08). An icon renders from EXACTLY one
 * source — a Material Symbols **ligature** OR an `ic_*`/vector **drawable** — and the sealed shape makes
 * that a compile-time guarantee (the nullable-pair alternative would let both-null / both-set illegal
 * states exist). Both arms are `@JvmInline value class`es: zero allocation, the wrapped primitive is the
 * only field.
 */
sealed interface IconRef {
    /** A Material Symbols glyph addressed by its ligature [name] (e.g. `"arrow_back"`, `"check"`). */
    @JvmInline
    value class Ligature(val name: String) : IconRef

    /** A bundled vector/raster drawable addressed by its `R.drawable.*` [resId]. */
    @JvmInline
    value class Drawable(@DrawableRes val resId: Int) : IconRef
}

/**
 * One semantic icon token. [primary] is what renders today (a [IconRef.Ligature] or [IconRef.Drawable]);
 * [alternate] is the **canonical semantic name** — the D-07 one-place remap handle a fork edits to swap
 * icon sets without find-replacing scattered ligature spellings (e.g. `"back"`, `"save"`, `"lock_open"`).
 *
 * **D-08 (separability):** a [JiibIcon] carries NO label. The user-facing label is a SEPARATE
 * `stringResource(...)` at the call site; a future combo-mode composes `JiibIconView(icon) + Text(label)`
 * without re-plumbing this type.
 *
 * **Invariant:** [alternate] MUST be non-blank and UNIQUE across [JiibIcons] — a duplicate would make a
 * fork's remap ambiguous (enforced by `JiibIconsTest`).
 */
data class JiibIcon(val primary: IconRef, val alternate: String)
