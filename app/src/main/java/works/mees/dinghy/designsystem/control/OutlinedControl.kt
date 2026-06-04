package works.mees.dinghy.designsystem.control

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import works.mees.dinghy.designsystem.MaterialSymbol
import works.mees.dinghy.theme.Geist
import works.mees.dinghy.theme.ThemeTokens
import works.mees.dinghy.theme.compose.LocalTokens
import works.mees.dinghy.theme.fsSp
import androidx.compose.material3.Text

/**
 * The button intent → role-token mapping (docs/ui_design/THEMING.md "Button intent = color";
 * docs/ui_design/CLAUDE.md). Intent IS the affordance signal — color is never decorative here.
 * Each intent resolves its outline color from [LocalTokens] (THEME-01 — never a raw color):
 *
 *  - [Neutral] — basic setting / secondary follow-up → `outline`.
 *  - [Accent]  — functional command with a direct physical effect (move, heat, fan) → `accentLine`.
 *  - [Warn]    — proceed at peril (reset, disable, undo, unexpected live change) → `heat` (amber).
 *  - [Danger]  — stop / cancel / back / host interruption → `stop` (red).
 *  - [Go]      — accept / done / commit a positive action → `go` (green).
 */
enum class Intent {
    Neutral,
    Accent,
    Warn,
    Danger,
    Go,
}

/** Resolve the intent's outline color from the active tokens (no raw color literal — THEME-01). */
private fun Intent.outlineColor(t: ThemeTokens): Color = when (this) {
    Intent.Neutral -> t.outline
    Intent.Accent -> t.accentLine
    Intent.Warn -> t.heat
    Intent.Danger -> t.stop
    Intent.Go -> t.go
}

/**
 * The outline-led, touch-first control language (UI-02) — the single most-reused interactive
 * primitive. A 2px outline + intent color on a TRANSPARENT fill, with a ≥64dp touch target floor
 * built for gloved, greasy, fat-fingered taps at arm's length (docs/ui_design/CLAUDE.md, hifi.css
 * `.ctl`). Every value comes from [LocalTokens] — no raw color, no raw px beyond the touch floor and
 * the `--fs`-scaled label (the only sanctioned fixed values, LAYOUT.md NON-NEGOTIABLE 3).
 *
 * The glow in the design language is a STATIC layer (D-13) — there is intentionally NO looping/
 * infinite Compose transition here. On the Adreno-320 floor a continuous "breathing"/sheen animation
 * burns fill rate we cannot spare; the static outline+color carries the affordance. One-shot press
 * feedback may be added later but never a continuous loop.
 *
 * @param label   the control's text (Geist, `--fs`-scaled).
 * @param onClick invoked on tap.
 * @param intent  the semantic color role (default [Intent.Neutral]).
 * @param symbol  optional leading Material-Symbol ligature (design spec: gutter buttons keep icon+label).
 * @param onLongClick optional long-press action (e.g. Load-spool's long-press → unload); when set the
 *   control uses `combinedClickable` so a tap fires [onClick] and a hold fires [onLongClick].
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OutlinedControl(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    intent: Intent = Intent.Neutral,
    symbol: String? = null,
    onLongClick: (() -> Unit)? = null,
) {
    val t = LocalTokens.current
    val shape = RoundedCornerShape(t.rCtrl)
    val clickMod = if (onLongClick != null) {
        Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
    } else {
        Modifier.clickable(onClick = onClick)
    }
    Box(
        modifier = modifier
            .heightIn(min = 64.dp) // ≥64dp touch floor (UI-02) — a sanctioned fixed value.
            .clip(shape)
            .border(BorderStroke(2.dp, intent.outlineColor(t)), shape)
            .then(clickMod),
        contentAlignment = Alignment.Center,
    ) {
        if (symbol != null && label.isBlank()) {
            // Icon-only control (a blank label + a symbol) — the glyph IS the affordance, so it fills the
            // cell: ~78% of the 64dp touch-floor (NOT --fs-scaled, since the cell height is a fixed dp).
            MaterialSymbol(name = symbol, tint = t.text, sizeSp = 50f)
        } else if (symbol != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MaterialSymbol(name = symbol, tint = t.text, sizeSp = fsSp(22f, t.fs))
                Text(
                    text = label,
                    color = t.text,
                    fontFamily = Geist,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = fsSp(18f, t.fs).sp,
                )
            }
        } else {
            Text(
                text = label,
                color = t.text,
                fontFamily = Geist,
                fontWeight = FontWeight.SemiBold,
                fontSize = fsSp(18f, t.fs).sp,
            )
        }
    }
}
