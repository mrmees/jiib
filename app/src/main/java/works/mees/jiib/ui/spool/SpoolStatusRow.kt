package works.mees.jiib.ui.spool

import androidx.compose.foundation.basicMarquee
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import works.mees.jiib.R
import works.mees.jiib.designsystem.components.ListRow
import works.mees.jiib.designsystem.components.ListRowIcon
import works.mees.jiib.designsystem.icons.DinghyIcons
import works.mees.jiib.spool.SpoolmanFilament
import works.mees.jiib.spool.SpoolmanSpool
import works.mees.jiib.theme.DinghyType
import works.mees.jiib.theme.compose.LocalTokens
import works.mees.jiib.theme.compose.toTextStyle

/**
 * The Spool row's identity text: the loaded filament's `name / material / vendor` (color / type /
 * mfg), non-null/non-blank fields joined with " / ". Null when no usable field exists (the caller
 * then shows "No Spool Loaded" or the generic "Spool" label). Pure — unit-tested.
 */
internal fun spoolRowText(filament: SpoolmanFilament?): String? =
    listOfNotNull(filament?.name, filament?.material, filament?.vendor?.name)
        .filter { it.isNotBlank() }
        .joinToString(" / ")
        .ifBlank { null }

/**
 * The canonical Spool list row (data-rich, 2026-06-16): leading [DinghyIcons.SpoolFilament]
 * (ev_shadow) tinted to the loaded filament's color (THEME-01 data carve-out — same derivation as
 * the Spool screen header), and the `name / material / vendor` identity text scrolling on overflow.
 * Uncolored icon + "No Spool Loaded" when nothing is loaded; "Spool" when a spool is loaded but
 * carries none of the identity fields. [onClick] taps through to the Spool manager.
 *
 * Shared by the Home idle list and the Extrude filament hub so both present identically.
 */
@Composable
internal fun SpoolStatusRow(
    spool: SpoolmanSpool?,
    uDp: Dp,
    onClick: () -> Unit,
) {
    val t = LocalTokens.current
    val spoolColor = spool?.filament?.colorSwatches?.firstNotNullOfOrNull { parseNormalizedHex(it) }
    val text = spoolRowText(spool?.filament)
    val label = when {
        text != null -> text
        spool != null -> stringResource(R.string.cd_launcher_spool) // loaded but no identity fields
        else -> stringResource(R.string.printstatus_spool_none)     // nothing loaded
    }
    ListRow(
        selected = false,
        onClick = onClick,
        uDp = uDp,
        leadingContent = {
            ListRowIcon(
                icon = DinghyIcons.SpoolFilament,
                uDp = uDp,
                tint = spoolColor ?: t.accent,
            )
        },
    ) {
        Text(
            text = label,
            color = t.text,
            style = DinghyType.listLabel.toTextStyle(t),
            maxLines = 1,
            modifier = Modifier.basicMarquee(),
        )
    }
}
