package works.mees.jiib.theme.views

import android.content.Context
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import works.mees.jiib.theme.FontCatalog
import works.mees.jiib.theme.TextRole
import works.mees.jiib.theme.ThemeTokens
import works.mees.jiib.theme.TypeRole

/** PURE: which `R.font.*` a role draws from, given the selected faces (or catalog defaults). Unit-tested. */
@androidx.annotation.FontRes
fun fontResFor(role: TextRole, tokens: ThemeTokens?): Int {
    val font = when (role.role) {
        TypeRole.Ui -> tokens?.uiFont ?: FontCatalog.DEFAULT_UI
        TypeRole.Data -> tokens?.dataFont ?: FontCatalog.DEFAULT_DATA
    }
    return font.fontRes(role.weight)
}

/**
 * Resolve the classic-Views [Typeface] for a role under the selected faces (mirrors the Compose family
 * map). Pass the live [tokens] for the user's chosen font; omit (null) at View-construction time to get
 * the catalog default — `applyTokens`/palette re-apply then swaps in the selection (Task 10).
 */
fun TextRole.typeface(context: Context, tokens: ThemeTokens? = null): Typeface =
    runCatching { ResourcesCompat.getFont(context, fontResFor(this, tokens)) }.getOrNull()
        ?: if (role == TypeRole.Data) Typeface.MONOSPACE else Typeface.DEFAULT
