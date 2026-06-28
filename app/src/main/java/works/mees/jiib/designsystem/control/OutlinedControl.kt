package works.mees.jiib.designsystem.control

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalDensity
import works.mees.jiib.designsystem.layout.LocalUnitDp
import androidx.compose.ui.unit.dp
import works.mees.jiib.designsystem.MaterialSymbol
import works.mees.jiib.designsystem.icons.JiibIcon
import works.mees.jiib.designsystem.icons.IconRef
import works.mees.jiib.theme.JiibType
import works.mees.jiib.theme.ThemeTokens
import works.mees.jiib.theme.compose.LocalTokens
import works.mees.jiib.theme.compose.toTextStyle
import works.mees.jiib.theme.fsSp
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
 * pending result** is a cancel-with-loss and stays [Danger] (`stop`). This is why the measure-weight
 * Field-takeover in `SpoolScreen` (discards a pending measurement) and `ScanConfirmCard` (rejects a
 * pending scan result) keep red Backs — they are NOT plain navigation. See docs/ui_design/THEMING.md →
 * "Conformance criteria — the
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
 * The canonical NEUTRAL soft press-fill — a low-alpha outline tint flattened over the surface.
 * Single-sourced here so both [Intent.softColor] (Neutral arm) and ListRow's press highlight read
 * the SAME value; hardcoding the 0.22f a second time would let them silently drift.
 */
internal fun neutralSoftFill(t: ThemeTokens): Color =
    t.outline.copy(alpha = 0.22f).compositeOver(t.surface)

/**
 * The SOFT pressed-fill tint per intent (owner 2026-06-17): a low-alpha intent color flattened
 * over the surface — the same softness as the IncrementPicker selected tile (`accentSoft` fill +
 * intent-colored border). Subtle enough that the strong text/icon stay readable in dark AND light
 * themes (the tint never lightens the surface past where `t.text` reads), so no content-color flip
 * is needed; the border (still [outlineColor]) carries the intent's full color.
 */
private fun Intent.softColor(t: ThemeTokens): Color = when (this) {
    Intent.Neutral -> neutralSoftFill(t)
    Intent.Accent -> t.accentSoft.compositeOver(t.surface)
    Intent.Warn -> t.heatSoft.compositeOver(t.surface)
    Intent.Danger -> t.stopSoft.compositeOver(t.surface)
    Intent.Go -> t.goSoft.compositeOver(t.surface)
}

/**
 * The outline-led, touch-first control language (UI-02) — the single most-reused interactive
 * primitive. A 2dp outline + intent color on a FILLED `t.surface` base (R4 — fill says "button";
 * the old transparent-fill control language is superseded), with a ≥64dp touch target floor
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
 * @param symbol  optional leading Material-Symbol ligature (design spec: foot-bar buttons keep icon+label).
 * @param onLongClick optional long-press action (e.g. Load-spool's long-press → unload); when set the
 *   control uses `combinedClickable` so a tap fires [onClick] and a hold fires [onLongClick].
 * @param contentDescription optional TalkBack label for the [symbol] glyph (WR-05). The MaterialSymbol
 *   renders the icon by typing its raw ligature NAME as Text — without this, an icon-only control
 *   (blank [label]) speaks the ligature name (e.g. "mode_heat_off") or nothing meaningful. A non-null
 *   value overrides the glyph's semantics (the [works.mees.jiib.designsystem.icons.JiibIconView]
 *   Amendment-1 precedent); null leaves semantics unchanged (pre-WR-05 behavior for legacy call sites).
 * @param enabled R10 (26.5-03) TRUE disablement: when false, NO click modifier is installed at all —
 *   no ripple, no consumed tap (the tap falls through or does nothing visibly-interactive), unlike a
 *   guarded `onClick` lambda which ripples then silently swallows the tap (Part 5 cause #1). Callers
 *   keep their own dim treatment (e.g. the WR-07 alpha 0.38 + semantics-disabled convention).
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
    enabled: Boolean = true,
    fill: Color? = null,
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

    // PRESSED STATE (owner 2026-06-17): on touch-down the button fills with a SOFT tint of its
    // intent color (see [softColor]) — the same softness as the tuning-screen IncrementPicker —
    // instead of the default Material ripple (which on the dark theme just dimmed toward the
    // background and read as "nothing happened"). `indication = null` kills the ripple; the fill is
    // driven straight off the press interaction. The intent-colored border still frames it.
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val backgroundColor = if (pressed) intent.softColor(t) else (fill ?: t.surface)

    val clickMod = when {
        // R10 true disablement: a bare Modifier — no clickable installed, no ripple, no event consumed.
        !enabled -> Modifier
        onLongClick != null -> Modifier.combinedClickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick,
            onLongClick = onLongClick,
        )
        else -> Modifier.clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick,
        )
    }
    // R26 edge frame: inside a U-aware container (FootButtonBar — the only LocalUnitDp provider)
    // the control's height floor is ONE UNIT, not just the 64dp touch floor — the law's
    // "FootButtonBar height = 1U" means the BUTTONS are 1U tall. Without this they wrap at 64dp
    // and float centered inside the 1U row, so their bottom edge misses the 8dp screen frame
    // (the Spool-screen misalignment, 2026-06-12). Null local = legacy 64dp floor unchanged.
    val minHeight = (LocalUnitDp.current ?: 64.dp).coerceAtLeast(64.dp)
    Box(
        modifier = modifier
            .heightIn(min = minHeight) // ≥64dp touch floor (UI-02); 1U inside FootButtonBar (R26).
            .clip(shape)
            // Controls = filled (COMPONENTS.md §2). [fill] overrides the default surface fill for
            // a selected/toggled state (e.g. IncrementPicker's active tile = accentSoft); pressed
            // overrides BOTH with the soft intent tint (see [softColor]). null = standard surface.
            .background(backgroundColor)
            .border(BorderStroke(2.dp, intent.outlineColor(t)), shape)
            .then(clickMod),
        contentAlignment = Alignment.Center,
    ) {
        // R24 (2026-06-12): when a U-aware container (FootButtonBar; screen roots as the wide
        // pass migrates them) provides LocalUnitDp, button glyphs size at the 0.6U icon tier —
        // same ruler as ListRowIcon, U-relative, no growth with the S/M/L text setting. The
        // divide-by-fontScale keeps the sp-rendered ligature at a fixed dp-equivalent (the
        // 24-05 UAT fix). Null local = unmigrated context → legacy sizing unchanged.
        val unitDp = LocalUnitDp.current
        if (symbol != null && label.isBlank()) {
            // Icon-only control (a blank label + a symbol) — the glyph IS the affordance.
            // Legacy fallback: ~50dp fixed equivalent (pre-R24 behavior).
            MaterialSymbol(
                name = symbol,
                modifier = symbolA11y,
                tint = t.text,
                sizeSp = (unitDp?.let { it.value * 0.6f } ?: 50f) / LocalDensity.current.fontScale,
            )
        } else if (symbol != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MaterialSymbol(
                    name = symbol,
                    modifier = symbolA11y,
                    tint = t.text,
                    // 0.6U when U is provided; legacy text-tracked 22sp otherwise.
                    sizeSp = unitDp?.let { (it.value * 0.6f) / LocalDensity.current.fontScale }
                        ?: fsSp(22f, t.fs),
                )
                Text(
                    text = label,
                    color = t.text,
                    style = JiibType.buttonLabel.toTextStyle(t),
                )
            }
        } else {
            Text(
                text = label,
                color = t.text,
                style = JiibType.buttonLabel.toTextStyle(t),
            )
        }
    }
}

/**
 * Resolves a [JiibIcon]'s Material Symbols ligature name for use in an [OutlinedControl].
 *
 * Control glyphs in this design system are always Material Symbols ligatures. A [JiibIcon]
 * whose [JiibIcon.primary] is an [IconRef.Drawable] cannot be used in a control symbol slot —
 * that is a programming error and is surfaced loudly via [IllegalArgumentException].
 *
 * @throws IllegalArgumentException if [icon]'s primary source is [IconRef.Drawable] rather
 *   than [IconRef.Ligature].
 */
internal fun ligatureOf(icon: JiibIcon): String =
    (icon.primary as? IconRef.Ligature)?.name
        ?: throw IllegalArgumentException(
            "OutlinedControl icon must be ligature-backed: ${icon.alternate}"
        )

/**
 * JiibIcon-aware [OutlinedControl] overload — accepts a registered [JiibIcon] token so
 * redesign components (SortRow, FilterRow, FootButtonBar, FloatingEStop) pass REGISTERED icons
 * by token, never ad-hoc raw ligature strings (closes the icon-registry control-API gap; 23-03).
 *
 * Redesign components pass a registered [JiibIcon] (e.g. `JiibIcons.Sort`), never a raw
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
    icon: JiibIcon?,
    onLongClick: (() -> Unit)? = null,
    contentDescription: String? = null,
    enabled: Boolean = true,
    fill: Color? = null,
) {
    OutlinedControl(
        label = label,
        onClick = onClick,
        modifier = modifier,
        intent = intent,
        symbol = icon?.let { ligatureOf(it) },
        onLongClick = onLongClick,
        contentDescription = contentDescription,
        enabled = enabled,
        fill = fill,
    )
}

/**
 * [ControlSpec]-driven [OutlinedControl] overload (control baseline audit, Phase 2 — 2026-06-14). A
 * NAMED control passes its registered [spec] instead of spelling out label/icon/intent at the call site;
 * the spec composes `R.string` + [JiibIcon][works.mees.jiib.designsystem.icons.JiibIcon] +
 * `CommandRegistry` (the catalogId dispatch reference is the CALLER's concern — this overload renders
 * only). [ControlSpec.labelRes] null = icon-only ([ControlSpec.contentDescriptionRes] then supplies the
 * a11y label, enforced by `ControlCatalogDriftTest`).
 *
 * Delegates to the existing `icon:`-overload — single rendering path, no duplicated body. All other
 * overloads are untouched.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OutlinedControl(
    spec: ControlSpec,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    enabled: Boolean = true,
) {
    OutlinedControl(
        label = spec.labelRes?.let { stringResource(it) } ?: "",
        onClick = onClick,
        modifier = modifier,
        intent = spec.intent,
        icon = spec.icon,
        onLongClick = onLongClick,
        contentDescription = spec.contentDescriptionRes?.let { stringResource(it) },
        enabled = enabled,
    )
}
