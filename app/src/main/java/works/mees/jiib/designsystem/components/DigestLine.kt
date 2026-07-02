package works.mees.jiib.designsystem.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import works.mees.jiib.designsystem.icons.JiibIcon
import works.mees.jiib.designsystem.layout.LocalUnitDp
import works.mees.jiib.theme.JiibType
import works.mees.jiib.theme.TextRole
import works.mees.jiib.theme.compose.LocalTokens
import works.mees.jiib.theme.compose.toTextStyle
import works.mees.jiib.theme.fsSp

/** Visual weight of one digest line. Standard = body/dataInline; Strong = listLabel/statValue; Meta = caption/dataMeta. */
enum class DigestEmphasis { Standard, Strong, Meta }

fun digestLineRoles(emphasis: DigestEmphasis): Pair<TextRole, TextRole> = when (emphasis) {
    DigestEmphasis.Standard -> JiibType.body to JiibType.dataInline
    DigestEmphasis.Strong   -> JiibType.listLabel to JiibType.statValue
    DigestEmphasis.Meta     -> JiibType.caption to JiibType.dataMeta
}

/** LAW 5 helper — a DigestColumn-driven scale applied to a role's base sp, floored at the 15sp ramp floor. */
fun digestScaledSp(baseSp: Float, scale: Float): Float = (baseSp * scale).coerceAtLeast(15f)

/**
 * One tabular data line of a Focus digest (LAW 2): leading 0.6U icon (optional), start-aligned
 * label (WEIGHTED — long labels ellipsize so the value never loses width; HomeDigest precedent,
 * Codex F4), end-aligned value at intrinsic width, Geist Mono tabular value via the Data roles.
 * Text never wraps; the DigestColumn scale shrinks it instead (15sp floor).
 */
@Composable
fun DigestLine(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    icon: JiibIcon? = null,
    iconTint: Color? = null,
    emphasis: DigestEmphasis = DigestEmphasis.Standard,
    valueColor: Color? = null,
    labelColor: Color? = null,
    scale: Float = 1f,
) {
    val t = LocalTokens.current
    val uDp = LocalUnitDp.current ?: 64.dp
    val (labelRole, valueRole) = digestLineRoles(emphasis)
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            ListRowIcon(icon = icon, uDp = uDp, tint = iconTint ?: t.text2)
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = label,
            style = labelRole.toTextStyle(t, sizeSp = fsSp(digestScaledSp(labelRole.baseSp, scale), t.fs)),
            color = labelColor ?: t.text2,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = value,
            style = valueRole.toTextStyle(t, sizeSp = fsSp(digestScaledSp(valueRole.baseSp, scale), t.fs)),
            color = valueColor ?: t.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
