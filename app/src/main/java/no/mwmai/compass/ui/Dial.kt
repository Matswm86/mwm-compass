package no.mwmai.compass.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import no.mwmai.compass.Reading
import kotlin.math.hypot
import kotlin.math.min

/**
 * The card turns so that the bearing you face sits under the fixed amber
 * lubber mark at the top, like a baseplate compass held in front of you.
 */
@Composable
fun Dial(heading: Float, mark: Float?, reading: Reading, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val styles = remember {
        DialStyles(
            cardinal = TextStyle(fontFamily = Mono, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Deck.Text),
            north = TextStyle(fontFamily = Mono, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Deck.Amber),
            minor = TextStyle(fontFamily = Mono, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Deck.TextDim),
            number = TextStyle(fontFamily = Mono, fontSize = 11.sp, color = Deck.PhosphorDim),
        )
    }
    Canvas(modifier.semantics { contentDescription = "Compass dial" }) {
        val c = center
        val r = min(size.width, size.height) / 2f
        val ring = r * 0.86f

        drawCircle(Deck.Panel, radius = ring, center = c)
        drawCircle(Deck.Edge, radius = ring, center = c, style = Stroke(width = 2.dp.toPx()))

        rotate(-heading, pivot = c) {
            for (deg in 0 until 360 step 2) {
                val len = when {
                    deg % 90 == 0 -> ring * 0.12f
                    deg % 30 == 0 -> ring * 0.09f
                    deg % 10 == 0 -> ring * 0.065f
                    else -> ring * 0.035f
                }
                val color = when {
                    deg == 0 -> Deck.Amber
                    deg % 10 == 0 -> Deck.Phosphor
                    else -> Deck.PhosphorDim
                }
                rotate(deg.toFloat(), pivot = c) {
                    drawLine(
                        color,
                        start = Offset(c.x, c.y - ring + 3.dp.toPx()),
                        end = Offset(c.x, c.y - ring + 3.dp.toPx() + len),
                        strokeWidth = if (deg % 10 == 0) 2.dp.toPx() else 1.dp.toPx(),
                    )
                }
            }
            val labelRadius = ring * 0.72f
            label(measurer, "N", styles.north, 0f, c, labelRadius)
            label(measurer, "E", styles.cardinal, 90f, c, labelRadius)
            label(measurer, "S", styles.cardinal, 180f, c, labelRadius)
            label(measurer, "W", styles.cardinal, 270f, c, labelRadius)
            for ((deg, text) in listOf(45f to "NE", 135f to "SE", 225f to "SW", 315f to "NW")) {
                label(measurer, text, styles.minor, deg, c, labelRadius)
            }
            for (deg in listOf(30, 60, 120, 150, 210, 240, 300, 330)) {
                label(measurer, deg.toString(), styles.number, deg.toFloat(), c, ring * 0.8f)
            }
            if (mark != null) {
                rotate(mark, pivot = c) {
                    val tip = c.y - ring + 3.dp.toPx()
                    drawPath(
                        Path().apply {
                            moveTo(c.x, tip + ring * 0.13f)
                            lineTo(c.x - ring * 0.05f, tip)
                            lineTo(c.x + ring * 0.05f, tip)
                            close()
                        },
                        Deck.Amber,
                    )
                }
            }
        }

        // Fixed lubber mark: the direction the top of the phone points.
        val top = c.y - ring
        drawPath(
            Path().apply {
                moveTo(c.x, top + 2.dp.toPx())
                lineTo(c.x - r * 0.06f, top - r * 0.12f)
                lineTo(c.x + r * 0.06f, top - r * 0.12f)
                close()
            },
            Deck.Amber,
        )

        // Crosshair and bubble level. The bubble is hidden when the phone is
        // upright, where a flat level means nothing.
        val levelR = ring * 0.22f
        drawLine(Deck.Edge, Offset(c.x - levelR * 1.5f, c.y), Offset(c.x + levelR * 1.5f, c.y), 1.dp.toPx())
        drawLine(Deck.Edge, Offset(c.x, c.y - levelR * 1.5f), Offset(c.x, c.y + levelR * 1.5f), 1.dp.toPx())
        drawCircle(Deck.Edge, radius = levelR, center = c, style = Stroke(width = 1.5f.dp.toPx()))
        if (reading.live && !reading.upright) {
            var bx = reading.levelX
            var by = reading.levelY
            val len = hypot(bx, by)
            if (len > 1f) { bx /= len; by /= len }
            // Full deflection at about 20 degrees of tilt, so small errors show.
            val gain = levelR * 2.9f
            var ox = bx * gain
            var oy = by * gain
            val reach = levelR - 6.dp.toPx()
            val o = hypot(ox, oy)
            if (o > reach) { ox = ox / o * reach; oy = oy / o * reach }
            val level = hypot(bx, by) < 0.035f
            drawCircle(
                if (level) Deck.Phosphor else Deck.PhosphorDim,
                radius = 6.dp.toPx(),
                center = Offset(c.x + ox, c.y + oy),
            )
        }
    }
}

private class DialStyles(
    val cardinal: TextStyle,
    val north: TextStyle,
    val minor: TextStyle,
    val number: TextStyle,
)

private fun DrawScope.label(
    measurer: TextMeasurer,
    text: String,
    style: TextStyle,
    degrees: Float,
    c: Offset,
    radius: Float,
) {
    val layout = measurer.measure(text, style)
    rotate(degrees, pivot = c) {
        drawText(
            layout,
            topLeft = Offset(
                c.x - layout.size.width / 2f,
                c.y - radius - layout.size.height / 2f,
            ),
        )
    }
}
