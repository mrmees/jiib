package works.mees.dinghy.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import works.mees.dinghy.theme.ThemeTokens
import works.mees.dinghy.theme.compose.LocalTokens

/**
 * A filled, color-reactive detail card — the primary focused-item display surface in the
 * jiib redesign grammar (docs/ui_design/COMPONENTS.md §"Component catalog").
 *
 * ## Structure
 *  - Background: [ThemeTokens.surface] (filled surface, NOT transparent — controls convention).
 *  - Corner radius: [ThemeTokens.rCard] (22dp — the card radius token).
 *  - Border: 3dp color-reactive ring — [ringColor] when supplied, [ThemeTokens.accentLine] otherwise.
 *  - Internal padding: 16dp on all edges.
 *
 * ## THEME-01 data carve-out — [ringColor]
 * [ringColor] carries the **filament's actual physical color hex** (e.g. Spoolman's colorSwatches
 * first entry, parsed to a Compose [Color]). This is item DATA, not a chrome token — it must
 * **NOT** be passed through `brandTint` (which would clamp it to the WCAG-3:1 floor and distort
 * the true filament color). The caller is responsible for passing the parsed swatch color directly.
 * The fallback [ThemeTokens.accentLine] is a chrome token and is token-routed normally.
 *
 * All other chrome colors come from [LocalTokens.current] — no raw `Color(0x…)`.
 *
 * @param modifier  caller-supplied modifier (e.g. `Modifier.fillMaxSize()`).
 * @param ringColor optional color-reactive ring — supply the raw filament hex [Color] (data
 *                  carve-out, never brandTint-clamped); leave null to fall back to [ThemeTokens.accentLine].
 * @param content   column content rendered inside the padded card.
 */
@Composable
fun DetailCard(
    modifier: Modifier = Modifier,
    ringColor: Color? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val t = LocalTokens.current
    val shape = RoundedCornerShape(t.rCard)
    val borderColor = ringColor ?: t.accentLine

    Column(
        modifier = modifier
            .clip(shape)
            .border(BorderStroke(3.dp, borderColor), shape)
            .background(t.surface)
            .padding(16.dp),
        content = content,
    )
}

/**
 * A [Modifier] extension that applies the card surface treatment to any composable: clip to
 * [ThemeTokens.rCard] radius, [ThemeTokens.surface] background, and a 1dp [ThemeTokens.hair]
 * decorative hairline border.
 *
 * This is the lightweight variant for call sites that need the card look without the [DetailCard]
 * Column wrapper — for example, embedding an image or a custom layout inside a card-styled container.
 *
 * **THEME-01 compliance:** all colors are role tokens; no raw `Color(0x…)`.
 *
 * @receiver the [Modifier] to extend.
 * @param t the active [ThemeTokens] from [LocalTokens.current].
 * @return a chained modifier with clip + background + hairline border applied.
 */
fun Modifier.cardSurface(t: ThemeTokens): Modifier =
    this
        .clip(RoundedCornerShape(t.rCard))
        .background(t.surface)
        .border(BorderStroke(1.dp, t.hair), RoundedCornerShape(t.rCard))
