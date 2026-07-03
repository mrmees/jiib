package works.mees.jiib.ui.screen

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import works.mees.jiib.theme.JiibType
import works.mees.jiib.theme.TextRole
import works.mees.jiib.theme.compose.LocalTokens
import works.mees.jiib.theme.compose.toTextStyle

/**
 * The token-aware text field (review #7) — the bridge that keeps the Settings screen's text entry
 * ON the Phase-3 semantic theme instead of drifting onto Material3's own default palette.
 *
 * Material3's [OutlinedTextField] is the only practical way to host the system keyboard (PRIM-02 —
 * the keyboard is allowed ONLY on Settings), but its default colors come from a Material color
 * scheme this app never configures. This wrapper derives EVERY color it hands to
 * [OutlinedTextFieldDefaults.colors] from [LocalTokens] semantic roles — so a theme swap (dark /
 * light / custom accent) recolors these fields exactly like every [works.mees.jiib.designsystem.
 * control.OutlinedControl] does. There is intentionally NOT a single raw color literal here
 * (the THEME-01 "never raw color" contract).
 *
 * Token → Material mapping:
 *  - unfocused border  → `outline` (or [borderColor] override — caller can pass `t.accent` for a
 *                         more prominent affordance, still a token, never a raw literal)
 *  - focused border    → `accentLine`     (the accent signature on focus — matches Intent.Accent)
 *  - cursor            → `accent`         (signature blue motion color)
 *  - text + container  → `text` / `surface` roles (strong text on the raised screen body)
 *  - label / muted     → `text2`          (the muted-text role)
 *
 * Type routes through [works.mees.jiib.theme.JiibType] roles so the field honors the S/M/L
 * `--fs` text-size setting (label → [labelRole] or caption, input → [textStyle] or dataInline).
 *
 * @param value the current field text.
 * @param onValueChange invoked on every edit.
 * @param label the field label (Geist, `--fs`-scaled).
 * @param modifier caller-supplied modifier chain.
 * @param keyboardType the system keyboard variant (Text / Number / Password) — PRIM-02.
 * @param isPassword when true, masks the input with a [PasswordVisualTransformation] (the API key).
 * @param isError when true, paints the border/label with the `stop` role (an inline validation error).
 * @param textStyle optional override for the value text style; defaults to [JiibType.dataInline].
 *   Must be a token-derived style — callers MUST NOT pass raw font/size literals.
 * @param labelRole optional override for the label [TextRole]; defaults to [JiibType.caption].
 * @param borderColor optional override for the unfocused border color; defaults to `t.outline`.
 *   Callers must pass a token color (e.g. `t.accent`) — never a raw [Color] literal (THEME-01).
 */
@Composable
fun TokenTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    isPassword: Boolean = false,
    isError: Boolean = false,
    textStyle: TextStyle? = null,
    labelRole: TextRole? = null,
    borderColor: Color? = null,
) {
    val t = LocalTokens.current
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = {
            Text(
                text = label,
                style = (labelRole ?: JiibType.caption).toTextStyle(t), // R11 floor
            )
        },
        singleLine = true,
        isError = isError,
        // Value text: caller override (e.g. statValue for the increment editor) or the monospace
        // data role default (connection/API-key DATA).
        textStyle = textStyle ?: JiibType.dataInline.toTextStyle(t),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        visualTransformation =
            if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
        // Every color derives from LocalTokens (review #7 / THEME-01) — no raw Color literal.
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = t.accentLine,
            unfocusedBorderColor = borderColor ?: t.outline,
            errorBorderColor = t.stop,
            cursorColor = t.accent,
            errorCursorColor = t.stop,
            focusedTextColor = t.text,
            unfocusedTextColor = t.text,
            errorTextColor = t.text,
            focusedContainerColor = t.surface,
            unfocusedContainerColor = t.surface,
            errorContainerColor = t.surface,
            focusedLabelColor = t.accent2,
            unfocusedLabelColor = t.text2,
            errorLabelColor = t.stop,
        ),
    )
}
