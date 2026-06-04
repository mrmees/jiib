package works.mees.dinghy.spool

import org.junit.Assert.assertNotNull
import org.junit.Assert.fail
import org.junit.Test
import works.mees.dinghy.net.GoldenFixtures

/**
 * RED scaffold (SPOOL-02) — null-safe spool/filament/vendor model parser.
 *
 * Wave-0 compile-safety: pure `fail()` bodies, no reference to the unbuilt `SpoolmanSpool` /
 * `SpoolmanFilament` / `SpoolmanVendor` model symbols (built in Wave 1).
 *
 * Goldens: `spoolman-live-ender5-proxy-spool3.json` (envelope-wrapped single spool) and
 * `spoolman-live-direct-spool3-before.json` (bare spool object — the direct REST shape).
 *
 * Target assertions (Wave 1 turns these green):
 *  - Omitted fields degrade to null/empty, never throw (spool 6 in the pla golden omits the vendor
 *    `external_id`; spool 3 omits `first_used`/`last_used`).
 *  - `color_hex` normalizes consistently with/without a leading `#`, lowercased (D-08): `"F6FA00"`,
 *    `"64794b"`, and `"ff0000"` all normalize to a 6-hex form.
 *  - A `multi_color_hexes`-bearing filament splits into the per-color list (multi-color split, D-08).
 */
class SpoolmanModelParserTest {

    @Test
    fun parsesEnvelopeWrappedSpoolDetail() {
        val raw = GoldenFixtures.raw("spoolman-live-ender5-proxy-spool3.json")
        assertNotNull("spool3 envelope golden must load", raw)
        fail("RED: null-safe spool detail parser (envelope-wrapped) not yet implemented")
    }

    @Test
    fun parsesBareDirectSpoolObject() {
        val raw = GoldenFixtures.raw("spoolman-live-direct-spool3-before.json")
        assertNotNull("direct-spool3 bare-object golden must load", raw)
        fail("RED: null-safe spool detail parser (bare REST object) not yet implemented")
    }

    @Test
    fun normalizesColorHexAndSplitsMultiColor() {
        fail("RED: color_hex normalize (D-08) + multi_color_hexes split not yet implemented")
    }
}
