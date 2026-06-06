package works.mees.dinghy.designsystem.icons

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import works.mees.dinghy.designsystem.MaterialSymbol
import works.mees.dinghy.theme.compose.LocalTokens

/**
 * The unifying icon render primitive (RESEARCH Q6). Renders a [DinghyIcon] from EITHER source behind one
 * call: the [IconRef.Ligature] branch delegates to the unchanged [MaterialSymbol] glyph primitive, the
 * [IconRef.Drawable] branch to `Icon(painterResource(...))`. Call sites think in ONE unit — the icon-box
 * size [sizeDp] (Codex LOW-8) — and never pass a raw `sp`.
 *
 * ## Sizing (Amendment 2 — dp↔sp)
 * A ligature glyph is *typographic* (sized in `sp`) while a drawable icon box is *layout* (sized in `dp`).
 * To give callers one mental model we accept [sizeDp] for both and, in the ligature branch, derive the
 * glyph's `sizeSp` via [dpToSp]. That derivation is pixel-correct ONLY because [works.mees.dinghy.theme.compose.DinghyTheme]
 * pins `fontScale = 1f` (DinghyTheme.kt:48) — see [dpToSp]. The drawable branch sizes the painter in `dp`
 * directly; a raw `sp` is NEVER passed to it.
 *
 * ## Accessibility (Amendment 1 — TalkBack)
 * The ligature primitive renders the icon by typing its raw ligature NAME as `Text`, which would otherwise
 * leak that name (e.g. "arrow_back") to TalkBack as the spoken label. We override semantics on the ligature
 * branch: a non-null [contentDescription] becomes the sole semantic label; a null [contentDescription]
 * marks the glyph decorative via [clearAndSetSemantics] (nothing spoken). The drawable branch is wired
 * through `Icon`'s own `contentDescription` parameter, which already gives correct decorative/labelled
 * behaviour on null/non-null.
 */
@Composable
fun DinghyIconView(
    icon: DinghyIcon,
    modifier: Modifier = Modifier,
    tint: Color = LocalTokens.current.text,
    sizeDp: Dp = 32.dp,
    contentDescription: String? = null,
) {
    when (val ref = icon.primary) {
        is IconRef.Ligature -> {
            // Amendment 1: the MaterialSymbol Text would expose the raw ligature name to TalkBack;
            // own the semantics so the spoken label is the caller's cd (or nothing, if decorative).
            val a11y = if (contentDescription != null) {
                Modifier.semantics { this.contentDescription = contentDescription }
            } else {
                Modifier.clearAndSetSemantics {}
            }
            MaterialSymbol(
                name = ref.name,
                modifier = modifier.then(a11y),
                tint = tint,
                sizeSp = dpToSp(sizeDp),
            )
        }

        is IconRef.Drawable -> Icon(
            painter = painterResource(ref.resId),
            contentDescription = contentDescription,
            modifier = modifier.size(sizeDp),
            tint = tint,
        )
    }
}

/**
 * Convert an icon-box [sizeDp] to the `sp` magnitude the ligature glyph fills.
 *
 * Amendment 2: this 1:1 numeric mapping is correct ONLY under the app-wide invariant that OS `fontScale`
 * is pinned to `1f` at the Compose root ([works.mees.dinghy.theme.compose.DinghyTheme] / DinghyTheme.kt:48),
 * where `--fs` is the sole text-size authority. Under that pin a dp value and its sp value share the same
 * physical pixel size, so a 32.dp icon box wants a 32sp glyph. If that pin is ever relaxed this helper is
 * the ONE place to revisit (it would then need a real Density to convert dp→px→sp).
 */
internal fun dpToSp(sizeDp: Dp): Float = sizeDp.value
