package works.mees.jiib.designsystem.control

import androidx.annotation.StringRes
import works.mees.jiib.designsystem.icons.JiibIcon

/** The kind of control a [ControlSpec] describes — drives which class renders it. */
enum class ControlType { Button, Selector, Stepper, Toggle, ColorSwatch, EStop, DenseValue }

/** Stable semantic key for a named control (key by ROLE, never label/placement). */
@JvmInline
value class ControlKey(val value: String)

/**
 * Presentation-only identity for a NAMED control (control baseline audit, 2026-06-14). Composes the
 * three existing registries WITHOUT duplicating them: titles/a11y come from `R.string`, glyphs from
 * [JiibIcon] tokens, dispatch from `CommandRegistry` (referenced by [commandCatalogId], never copied).
 * One-off contextual controls stay inline through the existing [OutlinedControl] overloads — they do
 * NOT get a ControlSpec. See design spec §3.
 */
data class ControlSpec(
    val key: ControlKey,
    @StringRes val labelRes: Int? = null,            // null = icon-only; label supplied by context
    @StringRes val contentDescriptionRes: Int? = null,
    val icon: JiibIcon? = null,
    val intent: Intent = Intent.Neutral,
    val type: ControlType = ControlType.Button,
    val commandCatalogId: String? = null,            // REFERENCE to CommandRegistry — never copied dispatch
    val longPressCommandCatalogId: String? = null,
)
