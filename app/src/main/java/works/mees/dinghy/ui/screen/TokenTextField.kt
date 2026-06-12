package works.mees.dinghy.ui.screen

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import works.mees.dinghy.theme.Geist
import works.mees.dinghy.theme.compose.LocalTokens
import works.mees.dinghy.theme.fsSp

/**
 * The token-aware text field (review #7) — the bridge that keeps the Settings screen's text entry
 * ON the Phase-3 semantic theme instead of drifting onto Material3's own default palette.
 *
 * Material3's [OutlinedTextField] is the only practical way to host the system keyboard (PRIM-02 —
 * the keyboard is allowed ONLY on Settings), but its default colors come from a Material color
 * scheme this app never configures. This wrapper derives EVERY color it hands to
 * [OutlinedTextFieldDefaults.colors] from [LocalTokens] semantic roles — so a theme swap (dark /
 * light / custom accent) recolors these fields exactly like every [works.mees.dinghy.designsystem.
 * control.OutlinedControl] does. There is intentionally NOT a single raw color literal here
 * (the THEME-01 "never raw color" contract).
 *
 * Token → Material mapping:
 *  - unfocused border  → `outline`        (the affordance edge at rest)
 *  - focused border    → `accentLine`     (the accent signature on focus — matches Intent.Accent)
 *  - cursor            → `accent`         (signature blue motion color)
 *  - text + container  → `text` / `surface` roles (strong text on the raised screen body)
 *  - label / muted     → `text2`          (the muted-text role)
 *
 * Type sizes route through [fsSp] + [Geist] so the field honors the S/M/L `--fs` text-size setting.
 *
 * @param value the current field text.
 * @param onValueChange invoked on every edit.
 * @param label the field label (Geist, `--fs`-scaled).
 * @param modifier caller-supplied modifier chain.
 * @param keyboardType the system keyboard variant (Text / Number / Password) — PRIM-02.
 * @param isPassword when true, masks the input with a [PasswordVisualTransformation] (the API key).
 * @param isError when true, paints the border/label with the `stop` role (an inline validation error).
 * @param dense when true, reduces the field's internal vertical content padding to ~6dp (C6 exempt
 *              surfaces — settings-class screens where the standard text-field height is too tall).
 *              Default false — all existing call sites are unaffected. Numeric-keyboard option and
 *              all validation behavior are unchanged.
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
    dense: Boolean = false,             // C6 surfaces pass true for reduced vertical padding
) {
    val t = LocalTokens.current
    // dense=true → C6 exempt: cap field height so the visual vertical padding is ≈6dp less than the
    // default OutlinedTextField height (which is ~56dp). The value-String overload of OutlinedTextField
    // does not expose contentPadding — we apply a sizeIn constraint on the outer modifier instead.
    // dense=false → standard: no height constraint, OutlinedTextField renders at its natural height.
    val resolvedModifier = if (dense) modifier.heightIn(max = 48.dp) else modifier
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = resolvedModifier,
        label = {
            Text(
                text = label,
                fontFamily = Geist,
                fontSize = fsSp(13f, t.fs).sp,
            )
        },
        singleLine = true,
        isError = isError,
        textStyle = TextStyle(
            fontFamily = Geist,
            fontSize = fsSp(18f, t.fs).sp,
        ),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        visualTransformation =
            if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
        // Every color derives from LocalTokens (review #7 / THEME-01) — no raw Color literal.
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = t.accentLine,
            unfocusedBorderColor = t.outline,
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
