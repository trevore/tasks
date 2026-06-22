package org.tasks.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoMarkerTest {

    @Test
    fun parsesFullLine() {
        val marker = parseGeoMarker(
            "Pick up prescription\n" +
                "@gomu-geo v1 lat=37.4220 lng=-122.0841 r=150 arrive=on depart=off place=\"Walgreens\""
        )!!
        assertEquals(37.4220, marker.lat, 0.0)
        assertEquals(-122.0841, marker.lng, 0.0)
        assertEquals(150, marker.radius)
        assertEquals(true, marker.arrive)
        assertEquals(false, marker.depart)
        assertEquals("Walgreens", marker.place)
    }

    @Test
    fun parsesLatLngOnly() {
        val marker = parseGeoMarker("@gomu-geo v1 lat=37.4220 lng=-122.0841")!!
        assertEquals(37.4220, marker.lat, 0.0)
        assertEquals(-122.0841, marker.lng, 0.0)
        assertNull(marker.radius)
        assertNull(marker.arrive)
        assertNull(marker.depart)
        assertNull(marker.place)
    }

    @Test
    fun rejectsOutOfRange() {
        assertNull(parseGeoMarker("@gomu-geo v1 lat=91.0 lng=-122.0841"))
        assertNull(parseGeoMarker("@gomu-geo v1 lat=37.4220 lng=-181.0"))
    }

    @Test
    fun rejectsMissingLatOrLng() {
        assertNull(parseGeoMarker("@gomu-geo v1 lng=-122.0841"))
        assertNull(parseGeoMarker("@gomu-geo v1 lat=37.4220"))
    }

    @Test
    fun rejectsNonNumericCoords() {
        assertNull(parseGeoMarker("@gomu-geo v1 lat=here lng=-122.0841"))
    }

    @Test
    fun rejectsNonFiniteCoords() {
        // NaN/Infinity parse as Double but slip past a bare min/max range check.
        assertNull(parseGeoMarker("@gomu-geo v1 lat=NaN lng=2.0"))
        assertNull(parseGeoMarker("@gomu-geo v1 lat=1.0 lng=NaN"))
        assertNull(parseGeoMarker("@gomu-geo v1 lat=Infinity lng=2.0"))
        assertNull(parseGeoMarker("@gomu-geo v1 lat=1.0 lng=-Infinity"))
    }

    @Test
    fun dropsNonPositiveRadius() {
        // A non-positive radius is rejected by the geofence registrar, so fall back
        // to the app default instead of carrying it through.
        assertNull(parseGeoMarker("@gomu-geo v1 lat=1.0 lng=2.0 r=0")!!.radius)
        assertNull(parseGeoMarker("@gomu-geo v1 lat=1.0 lng=2.0 r=-50")!!.radius)
    }

    @Test
    fun parsesQuotedPlaceWithSpaces() {
        val marker = parseGeoMarker("@gomu-geo v1 lat=1.0 lng=2.0 place=\"Walgreens Pharmacy\"")!!
        assertEquals("Walgreens Pharmacy", marker.place)
    }

    @Test
    fun ignoresUnknownKeys() {
        val marker = parseGeoMarker("@gomu-geo v1 lat=1.0 lng=2.0 color=blue foo=bar")!!
        assertEquals(1.0, marker.lat, 0.0)
        assertEquals(2.0, marker.lng, 0.0)
    }

    @Test
    fun keyOrderIsFree() {
        val marker = parseGeoMarker("@gomu-geo v1 place=Home lng=2.0 r=300 lat=1.0")!!
        assertEquals(1.0, marker.lat, 0.0)
        assertEquals(2.0, marker.lng, 0.0)
        assertEquals(300, marker.radius)
        assertEquals("Home", marker.place)
    }

    @Test
    fun toleratesExtraWhitespace() {
        val marker = parseGeoMarker("   @gomu-geo   v1    lat=1.0     lng=2.0   ")!!
        assertEquals(1.0, marker.lat, 0.0)
        assertEquals(2.0, marker.lng, 0.0)
    }

    @Test
    fun arriveOnlyTreatsDepartAsOff() {
        val marker = parseGeoMarker("@gomu-geo v1 lat=1.0 lng=2.0 arrive=on")!!
        assertEquals(true, marker.arrive)
        assertNull(marker.depart)
        assertTrue(marker.hasFlags)
        // the worker resolves an absent flag to off whenever either flag is present
        assertFalse(marker.depart ?: false)
    }

    @Test
    fun firstMarkerWins() {
        val marker = parseGeoMarker(
            "@gomu-geo v1 lat=1.0 lng=2.0\n@gomu-geo v1 lat=3.0 lng=4.0"
        )!!
        assertEquals(1.0, marker.lat, 0.0)
        assertEquals(2.0, marker.lng, 0.0)
    }

    @Test
    fun rejectsUnknownVersion() {
        assertNull(parseGeoMarker("@gomu-geo v2 lat=1.0 lng=2.0"))
    }

    @Test
    fun noMarkerReturnsNull() {
        assertNull(parseGeoMarker(null))
        assertNull(parseGeoMarker(""))
        assertNull(parseGeoMarker("Just a normal note"))
    }

    @Test
    fun stripsMarkerLineKeepingOtherText() {
        assertEquals(
            "Pick up prescription",
            stripGeoMarker("Pick up prescription\n@gomu-geo v1 lat=1.0 lng=2.0 place=Walgreens")
        )
    }

    @Test
    fun stripReturnsNullWhenOnlyMarker() {
        assertNull(stripGeoMarker("@gomu-geo v1 lat=1.0 lng=2.0"))
    }

    @Test
    fun stripLeavesNotesWithoutMarkerUnchanged() {
        assertEquals("Just a normal note", stripGeoMarker("Just a normal note"))
    }
}
