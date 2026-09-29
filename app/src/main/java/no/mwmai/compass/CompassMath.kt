package no.mwmai.compass

import kotlin.math.abs
import kotlin.math.roundToInt

// Plain angle arithmetic, kept free of Android classes so it runs in unit tests.

/** Any angle in degrees, folded into [0, 360). */
fun normalize(degrees: Float): Float {
    val d = degrees % 360f
    return if (d < 0f) d + 360f else d
}

/** The signed turn from [from] to [to], in (-180, 180]. Positive is clockwise. */
fun shortestDelta(from: Float, to: Float): Float {
    val d = normalize(to - from)
    return if (d > 180f) d - 360f else d
}

private val POINTS = listOf(
    "N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE",
    "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW",
)

/** The 16-point compass name, each point covering 22.5 degrees centred on it. */
fun cardinal(degrees: Float): String =
    POINTS[((normalize(degrees) + 11.25f) / 22.5f).toInt() % 16]

/** Whole degrees for the readout, 0 to 359: 359.6 rounds to 0, never to 360. */
fun wholeDegrees(degrees: Float): Int = normalize(degrees).roundToInt() % 360

/** "+3.4° E" or "-1.2° W": east declination is added to a magnetic bearing. */
fun formatDeclination(declination: Float): String {
    val side = if (declination >= 0f) "E" else "W"
    val sign = if (declination >= 0f) "+" else "-"
    return "$sign${"%.1f".format(java.util.Locale.ROOT, abs(declination))}° $side"
}

/** Which way to turn to face the mark, and how far. Within 2 degrees counts as on it. */
fun markHint(heading: Float, mark: Float): String {
    val d = shortestDelta(heading, mark)
    val n = abs(d).roundToInt()
    return when {
        n <= 2 -> "ON MARK"
        d > 0f -> "TURN RIGHT $n°"
        else -> "TURN LEFT $n°"
    }
}

/**
 * A low-pass filter that knows 359 and 1 are two degrees apart. Raw sensor
 * headings jitter by a degree or two; this steadies the needle without the
 * lag of a long average. [alpha] is the share of each new sample, 0 to 1.
 */
class AngleSmoother(private val alpha: Float) {
    private var value = Float.NaN

    fun next(target: Float): Float {
        value = if (value.isNaN()) normalize(target)
        else normalize(value + alpha * shortestDelta(value, target))
        return value
    }
}
