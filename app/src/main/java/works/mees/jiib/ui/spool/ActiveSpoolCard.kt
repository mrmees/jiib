package works.mees.jiib.ui.spool

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import works.mees.jiib.designsystem.icons.DinghyIconView
import works.mees.jiib.designsystem.icons.DinghyIcons
import works.mees.jiib.designsystem.control.Intent
import works.mees.jiib.designsystem.control.OutlinedControl
import works.mees.jiib.spool.SpoolmanSpool
import works.mees.jiib.spool.SpoolmanStatus
import works.mees.jiib.theme.DinghyType
import works.mees.jiib.theme.ThemeTokens
import works.mees.jiib.theme.compose.LocalTokens
import works.mees.jiib.theme.compose.toTextStyle
import works.mees.jiib.theme.fsSp

/**
 * The D-03 active-spool card states (docs/view_specific_notes/spoolman.md §Suggested UI state). The
 * card renders ONE `when` over these — every variant is covered so a missing-capability / disconnected /
 * no-active / in-flight / stale path NEVER crashes (T-11-06-02).
 */
sealed interface ActiveSpoolCardState {
    /** Moonraker has no `spoolman` component (D-02) — capability absent. */
    data object Unavailable : ActiveSpoolCardState

    /** Component present but Spoolman not connected (`spoolman_connected == false`). */
    data object Disconnected : ActiveSpoolCardState

    /** Connected, no active spool (`spool_id == null`) — prompt to pick/scan (D-13 clear state). */
    data object NoActive : ActiveSpoolCardState

    /** An active id is known; the spool detail fetch is in flight. */
    data class Loading(val spoolId: Int) : ActiveSpoolCardState

    /**
     * The normal filled card: the resolved [spool]. [stale] = Moonraker has queued usage reports
     * (D-11 — remaining may be stale); [changedExternally] = the active id was reconciled to a new id
     * from a server push (D-10) since the last user action.
     */
    data class Loaded(
        val spool: SpoolmanSpool,
        val stale: Boolean = false,
        val changedExternally: Boolean = false,
    ) : ActiveSpoolCardState
}

/**
 * Derive the [ActiveSpoolCardState] from the capability flag + the active status + the resolved detail.
 * Pure (no Android/I/O) so it is host-testable like `derive()`. Order is load-bearing: capability and
 * connection gates win before the id/detail branches.
 *
 * @param spoolmanPresent the D-02 capability gate (`AppContainer.spoolmanPresent`).
 * @param status the live active-spool status (D-10 reconciled), or null when not yet fetched.
 * @param detail the resolved `/v1/spool/{id}` detail for `status.activeSpoolId`, or null while fetching.
 * @param changedExternally true when the id was reconciled to a new value from a server push.
 */
fun deriveActiveSpoolCardState(
    spoolmanPresent: Boolean,
    status: SpoolmanStatus?,
    detail: SpoolmanSpool?,
    changedExternally: Boolean = false,
): ActiveSpoolCardState = when {
    !spoolmanPresent -> ActiveSpoolCardState.Unavailable
    status == null -> ActiveSpoolCardState.Loading(spoolId = -1)
    !status.spoolmanConnected -> ActiveSpoolCardState.Disconnected
    status.activeSpoolId == null -> ActiveSpoolCardState.NoActive
    detail == null -> ActiveSpoolCardState.Loading(spoolId = status.activeSpoolId!!)
    else -> ActiveSpoolCardState.Loaded(
        spool = detail,
        stale = status.hasPendingReports,
        changedExternally = changedExternally,
    )
}

/**
 * The compact active-spool card on Print Status (SPOOL-02, D-03). Copies
 * [works.mees.jiib.ui.printstatus.PrintStatusScreen]'s `LastJobCard` grammar: a token-outlined card
 * with a material / color-swatch / vendor·name / remaining / state read, all via [LocalTokens] (THEME-01),
 * live numbers in GeistMono tabular numerals. The swatch is rendered ONLY from the D-08 normalized
 * `colorSwatches` (multi-color split) — NEVER a raw untrusted hex (T-11-06-01). Quick actions
 * Scan / Change / Clear are [OutlinedControl]s with [Intent] colors (Clear = red Danger, D-13 → clear).
 *
 * Font scale matches the LastJobCard analog (D-16): card title 22sp, the remaining stat is the
 * GeistMono tabular hero at 26sp, row labels 17–18sp, metadata floor 15sp — never smaller.
 *
 * @param state the derived D-03 variant (see [deriveActiveSpoolCardState]).
 * @param onScan/[onChange]/[onClear] the quick-action hooks (the screen wires Change → the picker, Scan →
 *   the 11-07 scan surface, Clear → `post_spool_id {}` via the dispatcher).
 * @param onClick the whole-card nav seam (opens the Spool screen / detail).
 */
@Composable
fun ActiveSpoolCard(
    state: ActiveSpoolCardState,
    onScan: () -> Unit,
    onChange: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    val t = LocalTokens.current
    val shape = RoundedCornerShape(t.rCard)
    val outline = when (state) {
        is ActiveSpoolCardState.Loaded ->
            if (state.spool.archived || state.stale || state.changedExternally) t.heat else t.accentLine
        ActiveSpoolCardState.NoActive -> t.heat
        else -> t.hair
    }
    Column(
        modifier
            .clip(shape)
            .border(BorderStroke(2.dp, outline), shape)
            .background(t.surface)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Header: an inventory glyph + the title line ("Active spool" / the state heading).
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DinghyIconView(icon = DinghyIcons.Inventory, tint = t.text2, sizeDp = fsSp(24f, t.fs).dp, contentDescription = null)
            Text(
                text = cardTitle(state),
                color = t.text,
                style = DinghyType.screenTitle.toTextStyle(t),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        when (state) {
            ActiveSpoolCardState.Unavailable -> CardNotice(
                "This printer has no Spoolman component. Spool tracking is unavailable.", t,
            )
            ActiveSpoolCardState.Disconnected -> CardNotice(
                "Spoolman is not connected. Spool changes are blocked until it reconnects.", t,
            )
            ActiveSpoolCardState.NoActive -> CardNotice(
                "No spool is loaded. Scan a QR label or pick one from inventory.", t,
            )
            is ActiveSpoolCardState.Loading -> CardNotice("Loading spool…", t)
            is ActiveSpoolCardState.Loaded -> LoadedBody(state, t)
        }

        // Quick actions — Scan / Change physical-accent, Clear red Danger (D-13). Disabled when there is
        // nothing to act on (unavailable/disconnected): a no-op tap would be confusing. Pick/Scan still
        // make sense in the no-active state.
        val actionsEnabled = state !is ActiveSpoolCardState.Unavailable && state !is ActiveSpoolCardState.Disconnected
        val hasActiveSpool = state is ActiveSpoolCardState.Loaded || state is ActiveSpoolCardState.Loading
        if (actionsEnabled) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedControl(
                    label = "Scan",
                    onClick = onScan,
                    modifier = Modifier.weight(1f),
                    intent = Intent.Accent,
                    icon = DinghyIcons.QrCodeScanner,
                )
                OutlinedControl(
                    label = "Change",
                    onClick = onChange,
                    modifier = Modifier.weight(1f),
                    intent = Intent.Accent,
                    icon = DinghyIcons.SpoolChange,
                )
                // Clear only makes sense when something is loaded (D-13 → post_spool_id {}).
                if (hasActiveSpool) {
                    OutlinedControl(
                        label = "Clear",
                        onClick = onClear,
                        modifier = Modifier.weight(1f),
                        intent = Intent.Danger,
                        icon = DinghyIcons.SpoolClear,
                    )
                }
            }
        }
    }
}

/** The filled-card body for the [ActiveSpoolCardState.Loaded] variant. */
@Composable
private fun LoadedBody(state: ActiveSpoolCardState.Loaded, t: ThemeTokens) {
    val spool = state.spool
    val filament = spool.filament
    // Material · name + the D-08 normalized split swatch (NEVER a raw hex).
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ColorSwatch(filament?.colorSwatches ?: emptyList(), t)
        Text(
            text = listOfNotNull(filament?.material, filament?.name).joinToString(" · ").ifBlank { "—" },
            color = t.text,
            style = DinghyType.dataInline.toTextStyle(t),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
    // Vendor.
    filament?.vendor?.name?.let { vendor ->
        SpoolStatRow(DinghyIcons.Storefront, "Vendor", vendor, t)
    }
    // Remaining — the GeistMono tabular hero (26sp).
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DinghyIconView(icon = DinghyIcons.Scale, tint = t.text2, sizeDp = fsSp(20f, t.fs).dp, contentDescription = null)
        Text("Remaining", color = t.text2, style = DinghyType.caption.toTextStyle(t))
        Text(
            text = spool.remainingWeight?.let { "${it.roundToInt()} g" } ?: "—",
            color = if (spool.remainingWeight == null) t.text3 else t.text,
            style = DinghyType.statValue.toTextStyle(t),
            maxLines = 1,
        )
    }
    // Location context (inventory only, never the active-spool truth).
    spool.location?.let { SpoolStatRow(DinghyIcons.SpoolLocation, "Location", it, t) }
    // D-09 archived badge.
    if (spool.archived) {
        StateBadge(DinghyIcons.Archive, "Archived spool — verify before loading", t.heat, t)
    }
    // D-11 stale / pending usage.
    if (state.stale) {
        StateBadge(DinghyIcons.SpoolUsageStale, "Usage queued — remaining may be stale", t.heat, t)
    }
    // D-10 reconciled to a new id externally.
    if (state.changedExternally) {
        StateBadge(DinghyIcons.SpoolChangedExternally, "Active spool changed externally", t.heat, t)
    }
}

/** The D-08 split swatch: a small circle per normalized color; empty list → a neutral unknown marker. */
@Composable
private fun ColorSwatch(swatches: List<String>, t: ThemeTokens) {
    val size = fsSp(20f, t.fs).dp
    if (swatches.isEmpty()) {
        // Neutral unknown-color marker — a hairline-outlined empty circle (NEVER a fabricated color).
        Box(Modifier.size(size).clip(CircleShape).background(t.surface2).border(BorderStroke(1.dp, t.hair), CircleShape))
        return
    }
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        // Render up to 3 split colors; each goes through normalizeColorHex AGAIN as a belt-and-braces guard
        // before parsing to a Compose Color (a non-normalizable value falls back to the neutral marker).
        swatches.take(3).forEach { hex ->
            val color = parseNormalizedHex(hex)
            Box(
                Modifier.size(size).clip(CircleShape)
                    .background(color ?: t.surface2)
                    .border(BorderStroke(1.dp, t.hair), CircleShape),
            )
        }
    }
}

/** One icon-led metadata row (15–17sp floor; never smaller — D-16). */
@Composable
private fun SpoolStatRow(icon: works.mees.jiib.designsystem.icons.DinghyIcon, label: String, value: String, t: ThemeTokens) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DinghyIconView(icon, tint = t.text2, sizeDp = fsSp(18f, t.fs).dp)
        Text(label, color = t.text2, style = DinghyType.caption.toTextStyle(t))
        Text(
            value,
            color = t.text,
            style = DinghyType.dataMeta.toTextStyle(t),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** An amber proceed-at-peril state badge (archived / stale / changed-externally). */
@Composable
private fun StateBadge(icon: works.mees.jiib.designsystem.icons.DinghyIcon, text: String, color: Color, t: ThemeTokens) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DinghyIconView(icon, tint = color, sizeDp = fsSp(18f, t.fs).dp)
        Text(text, color = color, style = DinghyType.caption.toTextStyle(t))
    }
}

/** A plain notice line for the non-loaded variants (metadata floor 15sp). */
@Composable
private fun CardNotice(text: String, t: ThemeTokens) {
    Text(text, color = t.text2, style = DinghyType.caption.toTextStyle(t))
}

private fun cardTitle(state: ActiveSpoolCardState): String = when (state) {
    ActiveSpoolCardState.Unavailable -> "Spoolman unavailable"
    ActiveSpoolCardState.Disconnected -> "Spoolman disconnected"
    ActiveSpoolCardState.NoActive -> "No active spool"
    is ActiveSpoolCardState.Loading -> "Active spool"
    is ActiveSpoolCardState.Loaded -> "Active spool"
}

// parseNormalizedHex was promoted to an `internal` top-level helper in SpoolScreen.kt (18.3-01) — the
// same `works.mees.jiib.ui.spool` package, so it resolves here with no import and no duplicated logic.
