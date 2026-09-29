package no.mwmai.compass

import org.junit.Assert.assertEquals
import org.junit.Test

class CompassMathTest {
    @Test
    fun normalizeFoldsIntoOneTurn() {
        assertEquals(0f, normalize(360f), 0f)
        assertEquals(350f, normalize(-10f), 0.0001f)
        assertEquals(10f, normalize(730f), 0.0001f)
    }

    @Test
    fun shortestDeltaCrossesNorth() {
        assertEquals(2f, shortestDelta(359f, 1f), 0.0001f)
        assertEquals(-2f, shortestDelta(1f, 359f), 0.0001f)
        assertEquals(180f, shortestDelta(0f, 180f), 0.0001f)
        assertEquals(-90f, shortestDelta(90f, 0f), 0.0001f)
    }

    @Test
    fun cardinalPointsSplitAtHalfSteps() {
        assertEquals("N", cardinal(0f))
        assertEquals("N", cardinal(11.2f))
        assertEquals("NNE", cardinal(11.3f))
        assertEquals("E", cardinal(90f))
        assertEquals("SW", cardinal(225f))
        assertEquals("NNW", cardinal(348f))
        assertEquals("N", cardinal(349f))
    }

    @Test
    fun wholeDegreesNeverShows360() {
        assertEquals(0, wholeDegrees(359.6f))
        assertEquals(359, wholeDegrees(359.4f))
        assertEquals(274, wholeDegrees(273.5f))
    }

    @Test
    fun declinationCarriesSignAndSide() {
        assertEquals("+3.4° E", formatDeclination(3.44f))
        assertEquals("-1.2° W", formatDeclination(-1.2f))
    }

    @Test
    fun markHintPicksTheShortTurn() {
        assertEquals("ON MARK", markHint(100f, 101f))
        assertEquals("TURN RIGHT 20°", markHint(350f, 10f))
        assertEquals("TURN LEFT 30°", markHint(10f, 340f))
    }

    @Test
    fun smootherWrapsThroughNorthInsteadOfSwingingRound() {
        val s = AngleSmoother(0.5f)
        assertEquals(350f, s.next(350f), 0.0001f)
        // Half way from 350 to 10 is 0, not 180.
        assertEquals(0f, s.next(10f), 0.0001f)
        assertEquals(5f, s.next(10f), 0.0001f)
    }
}
