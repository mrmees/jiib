package works.mees.dinghy.theme.compose

import androidx.compose.ui.graphics.Color

/**
 * RED-phase scaffold (quick 260611-cj1, TDD): compiles so the test sourceset stays buildable
 * (the wave-0 lesson — Gradle compiles the WHOLE sourceset before `--tests` filters), but fails
 * at runtime until the GREEN commit implements the luminance gate.
 */
internal fun isDarkBackdrop(bg: Color): Boolean = TODO("260611-cj1 GREEN: luminance gate")
