package works.mees.jiib.render

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test
import works.mees.jiib.theme.TokensDark

class BedMeshColorResolveTest {
    private val accent = Color(0xFF112233)
    private val stop   = Color(0xFF220011)
    private val heat   = Color(0xFF003300)
    private val go     = Color(0xFF000044)
    private val p0     = Color(0xFF0A0A0A)
    private val p1     = Color(0xFF0B0B0B)
    private val p2     = Color(0xFF0C0C0C)
    private val p3     = Color(0xFF0D0D0D)
    private val t = TokensDark.copy(
        accent = accent,
        stop   = stop,
        heat   = heat,
        go     = go,
        pool   = listOf(p0, p1, p2, p3),
    )

    @Test fun index0isAccent()  = assertEquals(accent, resolveMeshColor(t, 0))
    @Test fun index1isStop()    = assertEquals(stop,   resolveMeshColor(t, 1))
    @Test fun index2isHeat()    = assertEquals(heat,   resolveMeshColor(t, 2))
    @Test fun index3isGo()      = assertEquals(go,     resolveMeshColor(t, 3))
    @Test fun index4isPool0()   = assertEquals(p0,     resolveMeshColor(t, 4))
    @Test fun index7isPool3()   = assertEquals(p3,     resolveMeshColor(t, 7))
    @Test fun outOfRangeFallsBackToAccent() = assertEquals(accent, resolveMeshColor(t, 99))
}
