package works.mees.jiib.designsystem.components

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import works.mees.jiib.R
import works.mees.jiib.designsystem.ConfirmGuard

/**
 * A reusable confirm-before-leaving gate for screens that have a long-running background operation.
 *
 * When [enabled], both the Android system Back gesture AND any UI element that calls [requestBack]
 * (the lambda passed to [content]) will pop a [ConfirmGuard] instead of navigating immediately.
 * The printer keeps working server-side; the guard makes leaving deliberate.
 *
 * When NOT [enabled], [requestBack] calls [onBack] directly with no dialog.
 *
 * Intent: `warn = true` (amber proceed-at-peril) — leaving is allowed but cautionary, not destructive.
 *
 * Usage:
 * ```
 * ConfirmOnBack(enabled = isHoming, onBack = onBack) { requestBack ->
 *     MyScreenContent(onBack = requestBack)
 * }
 * ```
 *
 * @param enabled     when true the guard is armed (system Back + [requestBack] both show the dialog).
 * @param onBack      the real navigation callback invoked after confirmation (or immediately when disabled).
 * @param content     the screen body; receives [requestBack], the guard-aware back trigger.
 */
@Composable
fun ConfirmOnBack(
    enabled: Boolean,
    onBack: () -> Unit,
    content: @Composable (requestBack: () -> Unit) -> Unit,
) {
    var guard by remember { mutableStateOf(false) }
    val request: () -> Unit = { if (enabled) guard = true else onBack() }
    BackHandler(enabled = enabled) { guard = true }
    content(request)
    if (guard) {
        ConfirmGuard(
            title = stringResource(R.string.gating_unknown_title),
            message = stringResource(R.string.gating_back_guard_message),
            confirmLabel = stringResource(R.string.common_back),
            cancelLabel = stringResource(R.string.common_cancel),
            destructive = false,
            warn = true,
            onConfirm = { guard = false; onBack() },
            onCancel = { guard = false },
        )
    }
}
