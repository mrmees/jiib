package works.mees.dinghy.designsystem.control

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import works.mees.dinghy.designsystem.MaterialSymbol
import works.mees.dinghy.designsystem.icons.DinghyIcon
import works.mees.dinghy.designsystem.icons.IconRef
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
 *  - [Neutral] — basic setting / secondary follow-up / plain nav (Back, D-10) → `outline`.
 *  - [Accent]  — functional command with a direct physical effect (move, heat, fan) → `accentLine`.
 *  - [Warn]    — proceed at peril (reset, disable, undo, unexpected live change) → `heat` (amber).
 *  - [Danger]  — stop / cancel a pending action / host interruption → `stop` (red).
 *  - [Go]      — accept / done / commit a positive action → `go` (green).
 *
 * Plain navigational Back spends NO safety color — it is [Neutral]/outline app-wide (15.1 D-10;
 * MoveScreen is the reference).
 *
 * **C7 (15.2 D-10) — a discarding/rejecting Back stays [Danger]/red.** Back is [Neutral] ONLY for
 * plain navigation that changes nothing. A Back/Cancel that **DISCARDS pending input** or **REJECTS a
 * pending result** is a cancel-with-loss and stays [Danger] (`stop`). This is why `MeasuredWeightPage`
 * (discards a pending measurement) and `ScanConfirmCard` (rejects a pending scan result) keep red
 * Backs — they are NOT plain navigation. See docs/ui_design/THEMING.md → "Conformance criteria — the
 * C-series" → C7.
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
 * @param contentDescription optional TalkBack label for the [symbol] glyph (WR-05). The MaterialSymbol
 *   renders the icon by typing its raw ligature NAME as Text — without this, an icon-only control
 *   (blank [label]) speaks the ligature name (e.g. "mode_heat_off") or nothing meaningful. A non-null
 *   value overrides the glyph's semantics (the [works.mees.dinghy.designsystem.icons.DinghyIconView]
 *   Amendment-1 precedent); null leaves semantics unchanged (pre-WR-05 behavior for legacy call sites).
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
    contentDescription: String? = null,
) {
    val t = LocalTokens.current
    // WR-05: a non-null contentDescription becomes the glyph's spoken label instead of the raw
    // ligature text. Null is deliberately a no-op so pre-existing call sites are unaffected.
    val symbolA11y = if (contentDescription != null) {
        Modifier.semantics { this.contentDescription = contentDescription }
    } else {
        Modifier
    }
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
            .background(t.surface) // Controls = filled (COMPONENTS.md §2 fill convention).
            .border(BorderStroke(2.dp, intent.outlineColor(t)), shape)
            .then(clickMod),
        contentAlignment = Alignment.Center,
    ) {
        if (symbol != null && label.isBlank()) {
            // Icon-only control (a blank label + a symbol) — the glyph IS the affordance, so it fills the
            // cell at a FONT-SCALE-STABLE dp-equivalent size (~50dp regardless of system font scale).
            // Dividing by fontScale converts the sp value to a fixed-dp equivalent: at fontScale 1.0 the
            // rendering is byte-identical to the old 50sp; at fontScale >1 (e.g. 1.3 on Accessibility) the
            // glyph no longer balloons past the 2px border. UAT-driven fix: the FloatingEStop glyph
            // overflowed its 0.7U border on flox at large system font scale (24-05 UAT).
            MaterialSymbol(
                name = symbol,
                modifier = symbolA11y,
                tint = t.text,
                sizeSp = 50f / LocalDensity.current.fontScale,
            )
        } else if (symbol != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MaterialSymbol(name = symbol, modifier = symbolA11y, tint = t.text, sizeSp = fsSp(22f, t.fs))
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

/**
 * Resolves a [DinghyIcon]'s Material Symbols ligature name for use in an [OutlinedControl].
 *
 * Control glyphs in this design system are always Material Symbols ligatures. A [DinghyIcon]
 * whose [DinghyIcon.primary] is an [IconRef.Drawable] cannot be used in a control symbol slot —
 * that is a programming error and is surfaced loudly via [IllegalArgumentException].
 *
 * @throws IllegalArgumentException if [icon]'s primary source is [IconRef.Drawable] rather
 *   than [IconRef.Ligature].
 */
internal fun ligatureOf(icon: DinghyIcon): String =
    (icon.primary as? IconRef.Ligature)?.name
        ?: throw IllegalArgumentException(
            "OutlinedControl icon must be ligature-backed: ${icon.alternate}"
        )

/**
 * DinghyIcon-aware [OutlinedControl] overload — accepts a registered [DinghyIcon] token so
 * redesign components (SortRow, FilterRow, FootButtonBar, FloatingEStop) pass REGISTERED icons
 * by token, never ad-hoc raw ligature strings (closes the icon-registry control-API gap; 23-03).
 *
 * Redesign components pass a registered [DinghyIcon] (e.g. `DinghyIcons.Sort`), never a raw
 * ligature string; the string-symbol overload is retained only for pre-redesign call sites.
 *
 * Delegates to the existing [symbol: String?] implementation — single rendering path, no
 * duplicated body. The existing raw-[symbol] overload is preserved for back-compat.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OutlinedControl(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    intent: Intent = Intent.Neutral,
    icon: DinghyIcon?,
    onLongClick: (() -> Unit)? = null,
    contentDescription: String? = null,
) {
    OutlinedControl(
        label = label,
        onClick = onClick,
        modifier = modifier,
        intent = intent,
        symbol = icon?.let { ligatureOf(it) },
        onLongClick = onLongClick,
        contentDescription = contentDescription,
    )
}
