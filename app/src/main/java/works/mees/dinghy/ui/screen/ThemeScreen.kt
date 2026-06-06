package works.mees.dinghy.ui.screen

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import works.mees.dinghy.di.AppContainer

/**
 * The **Theme** drawer destination (15.2-04, D-03) — the per-printer "look" surface, promoted to its own
 * top-level [works.mees.dinghy.ui.route.Dest] in the Settings-IA dissolve (D-01). Theme controls the
 * active printer's APPEARANCE (seed color / palette / pool overrides / status colors); functional
 * per-profile feature toggles live on the sibling **Settings** dest, app-global items on **About**.
 *
 * ## Promotion, not a fork (15.2-04 Task 1)
 * The full theme editor content (color wheel + presets + per-slot pool-override grid + status overrides +
 * Randomize/Reset/Done) was authored as [ThemeEditorScreen] in Phase 15 as a Settings SUB-PAGE. This
 * screen PROMOTES that content verbatim to its own Dest by hosting [ThemeEditorScreen] directly — there is
 * no second divergent copy of the editor body. The Settings "Edit theme…" forward-entry that used to push
 * the editor in-place is GONE (Settings no longer carries theme content); the App Drawer's Theme tile is
 * now the single entry.
 *
 * ## Per-printer writes (D-03)
 * Every theme write inside [ThemeEditorScreen] already routes through the per-profile `setActive*`
 * intents on the process-lifetime writeScope ([[dinghy-compose-write-scope-cancellation]]), so changing
 * the theme changes ONLY the active printer's saved look — exactly the per-printer behavior the
 * on-device checkpoint verifies. Nothing about that contract changes by promoting it to a Dest.
 *
 * ## FFG-exempt (THEMING.md / LAYOUT.md)
 * Like Settings/About, the theme editor is a scrollable conventional surface exempt from the
 * Focus/Field/Gutter grammar; the editor's green "Done" is its explicit exit and AppShell suppresses the
 * swipe-up drawer gesture for this Dest (with an explicit gutter-equivalent Back: the Done control).
 *
 * @param container the process-scoped service-locator (constructs nothing here).
 * @param onBack invoked when the user exits (the editor's Done) — the shell pops to the caller.
 */
@Composable
fun ThemeScreen(
    container: AppContainer,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // The promoted editor content IS the Theme screen. ThemeEditorScreen already owns the whole canvas,
    // its own sub-pickers (pool/status slot wheels), and a green "Done" that calls onBack — the exact
    // Dest contract. Hosting it here makes it the navigable target without forking the editor body.
    ThemeEditorScreen(container = container, onBack = onBack, modifier = modifier)
}
