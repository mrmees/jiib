package works.mees.dinghy.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import works.mees.dinghy.theme.Geist
import works.mees.dinghy.theme.ThemeTokens
import works.mees.dinghy.theme.compose.LocalTokens
import works.mees.dinghy.theme.fsSp

/**
 * The four message severities (PRIM-04). Each maps to a role token from [LocalTokens] AND a
 * distinct text glyph — severity is NEVER conveyed by color alone (an accessibility floor: a
 * red/green-blind user, or anyone glancing past the printer, still reads info/success/warning/
 * error from the icon + the text). Mapping per docs/ui_design/THEMING.md:
 *
 *  - [Info]    → `--accent` (signature blue) — neutral informational.
 *  - [Success] → `--go` (green) — a positive outcome.
 *  - [Warning] → `--heat` (amber) — proceed-at-peril / attention.
 *  - [Error]   → `--stop` (red) — a failure.
 */
enum class Severity { Info, Success, Warning, Error }

private fun Severity.color(t: ThemeTokens): Color = when (this) {
    Severity.Info -> t.accent
    Severity.Success -> t.go
    Severity.Warning -> t.heat
    Severity.Error -> t.stop
}

private fun Severity.softTint(t: ThemeTokens): Color = when (this) {
    Severity.Info -> t.accentSoft
    Severity.Success -> t.goSoft
    Severity.Warning -> t.heatSoft
    Severity.Error -> t.stopSoft
}

/**
 * A distinct mark per severity (the "icon" half of color + icon + text). Kept as text glyphs so
 * the component carries no icon-font/vector dependency and stays a leaf primitive; each severity
 * gets a DIFFERENT mark so no glyph repeats (CLAUDE.md "never the same glyph twice").
 */
private fun Severity.mark(): String = when (this) {
    Severity.Info -> "i"
    Severity.Success -> "✓"   // ✓
    Severity.Warning -> "!"
    Severity.Error -> "×"     // ×
}

/**
 * A transient severity toast (PRIM-04): a token-tinted pill carrying a per-severity icon AND the
 * message text — color is reinforced by, never a substitute for, the icon and copy. Every visual
 * value comes from [LocalTokens] (THEME-01 — no raw color literal): the border + icon take the
 * severity's strong token, the fill takes its soft tint.
 *
 * Static styling only (D-13): no entrance loop / breathing. The host owns show/hide timing; a
 * single cheap one-shot fade on appear is the only motion ever permitted and is left to the caller.
 *
 * @param severity the message class (drives color + icon).
 * @param text     the message copy (always rendered — never icon-only).
 */
@Composable
fun SeverityToast(
    severity: Severity,
    text: String,
    modifier: Modifier = Modifier,
) {
    val t = LocalTokens.current
    val accent = severity.color(t)
    val shape = RoundedCornerShape(t.rCtrl)
    Row(
        modifier = modifier
            .clip(shape)
            .background(severity.softTint(t))
            .border(BorderStroke(2.dp, accent), shape)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Icon half — a token-colored mark in a roundel so it reads as a badge, not stray text.
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(t.rPill))
                .border(BorderStroke(2.dp, accent), RoundedCornerShape(t.rPill)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = severity.mark(),
                color = accent,
                fontFamily = Geist,
                fontWeight = FontWeight.Bold,
                fontSize = fsSp(16f, t.fs).sp,
            )
        }
        // Text half — always present.
        Text(
            text = text,
            color = t.text,
            fontFamily = Geist,
            fontWeight = FontWeight.Medium,
            fontSize = fsSp(16f, t.fs).sp,
        )
    }
}
