package works.mees.jiib.theme.views

import android.content.Context
import android.graphics.Typeface
import androidx.compose.ui.text.font.FontWeight
import androidx.core.content.res.ResourcesCompat
import works.mees.jiib.R
import works.mees.jiib.theme.TextRole
import works.mees.jiib.theme.TypeRole

/** Resolve the `res/font` [Typeface] for a role on a classic-Views surface (mirrors the Compose family map). */
fun TextRole.typeface(context: Context): Typeface {
    val resId = when (role) {
        TypeRole.Data -> if (weight >= FontWeight.SemiBold) R.font.geist_mono_semibold else R.font.geist_mono_medium
        TypeRole.Ui -> when {
            weight >= FontWeight.Bold -> R.font.geist_bold
            weight >= FontWeight.SemiBold -> R.font.geist_semibold
            weight >= FontWeight.Medium -> R.font.geist_medium
            else -> R.font.geist_regular
        }
    }
    return runCatching { ResourcesCompat.getFont(context, resId) }.getOrNull()
        ?: if (role == TypeRole.Data) Typeface.MONOSPACE else Typeface.DEFAULT
}
