package no.mwmai.compass.ui

import android.hardware.SensorManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import no.mwmai.compass.Reading
import no.mwmai.compass.cardinal
import no.mwmai.compass.formatDeclination
import no.mwmai.compass.markHint
import no.mwmai.compass.normalize
import no.mwmai.compass.wholeDegrees

@Composable
fun CompassScreen(
    reading: Reading,
    trueNorth: Boolean,
    declination: Float?,
    locating: Boolean,
    mark: Float?,
    onToggleNorth: () -> Unit,
    onMark: () -> Unit,
) {
    // Everything on screen is in the chosen reference. Without a declination
    // yet, true north falls back to magnetic and the display says so.
    val offset = if (trueNorth) declination ?: 0f else 0f
    val usingTrue = trueNorth && declination != null
    val heading = normalize(reading.azimuth + offset)
    val shownMark = mark?.let { normalize(it + offset) }

    Column(
        Modifier
            .fillMaxSize()
            .background(Deck.Ink)
            .systemBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "MWM COMPASS",
                fontFamily = Mono,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 4.sp,
                color = Deck.TextDim,
            )
            Spacer(Modifier.weight(1f))
            Text(
                calibrationLabel(reading.accuracy),
                fontFamily = Mono,
                fontSize = 11.sp,
                letterSpacing = 2.sp,
                color = if (needsCalibration(reading.accuracy)) Deck.Amber else Deck.PhosphorDim,
            )
        }
        Spacer(Modifier.height(12.dp))

        Display(
            reading = reading,
            heading = heading,
            usingTrue = usingTrue,
            trueNorth = trueNorth,
            declination = declination,
            locating = locating,
            shownMark = shownMark,
        )

        BoxWithConstraints(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            val side = if (maxWidth < maxHeight) maxWidth else maxHeight
            Dial(
                heading = heading,
                mark = shownMark,
                reading = reading,
                modifier = Modifier.size(side),
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DeckButton(
                label = if (mark == null) "MARK" else "CLEAR MARK",
                active = mark != null,
                onClick = onMark,
                modifier = Modifier.weight(1f),
            )
            DeckButton(
                label = "TRUE NORTH",
                active = trueNorth,
                onClick = onToggleNorth,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun Display(
    reading: Reading,
    heading: Float,
    usingTrue: Boolean,
    trueNorth: Boolean,
    declination: Float?,
    locating: Boolean,
    shownMark: Float?,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Deck.Lcd)
            .border(BorderStroke(1.dp, Deck.Edge), RoundedCornerShape(16.dp))
            .padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                if (reading.live) "${wholeDegrees(heading)}°" else "---°",
                fontFamily = Mono,
                fontSize = 64.sp,
                fontWeight = FontWeight.Medium,
                color = Deck.Phosphor,
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.padding(bottom = 10.dp)) {
                Text(
                    if (reading.live) cardinal(heading) else "",
                    fontFamily = Mono,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = Deck.Amber,
                )
                Text(
                    if (usingTrue) "TRUE" else "MAGNETIC",
                    fontFamily = Mono,
                    fontSize = 12.sp,
                    letterSpacing = 2.sp,
                    color = Deck.PhosphorDim,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        StatusLine(statusText(reading, usingTrue, trueNorth, declination, locating))
        if (shownMark != null) {
            StatusLine(
                "MARK ${wholeDegrees(shownMark)}°  ·  ${markHint(heading, shownMark)}",
                color = Deck.Amber,
            )
        }
    }
}

@Composable
private fun StatusLine(text: String, color: Color = Deck.PhosphorDim) {
    Text(
        text,
        fontFamily = Mono,
        fontSize = 13.sp,
        letterSpacing = 1.sp,
        color = color,
        modifier = Modifier.padding(top = 2.dp),
    )
}

private fun statusText(
    reading: Reading,
    usingTrue: Boolean,
    trueNorth: Boolean,
    declination: Float?,
    locating: Boolean,
): String = when {
    !reading.hasSensor -> "NO COMPASS SENSOR IN THIS PHONE"
    !reading.live -> "WAITING FOR SENSOR"
    needsCalibration(reading.accuracy) -> "CALIBRATE: WAVE PHONE IN A FIGURE 8"
    trueNorth && declination == null -> if (locating) "FINDING DECLINATION..." else "NO LOCATION: SHOWING MAGNETIC"
    reading.upright -> "UPRIGHT: BEARING OF THE PHONE'S BACK"
    usingTrue -> "DECLINATION ${formatDeclination(declination ?: 0f)}"
    reading.tilt <= 2f -> "LEVEL"
    else -> "TILT ${reading.tilt.toInt()}°"
}

private fun needsCalibration(accuracy: Int): Boolean =
    accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE ||
        accuracy == SensorManager.SENSOR_STATUS_ACCURACY_LOW

private fun calibrationLabel(accuracy: Int): String = when (accuracy) {
    SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> "CAL HIGH"
    SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> "CAL MED"
    SensorManager.SENSOR_STATUS_ACCURACY_LOW -> "CAL LOW"
    SensorManager.SENSOR_STATUS_UNRELIABLE -> "CAL POOR"
    else -> ""
}

@Composable
private fun DeckButton(
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier
            .height(56.dp)
            .clip(shape)
            .background(if (active) Deck.Phosphor else Deck.Raised)
            .border(BorderStroke(1.dp, if (active) Deck.Phosphor else Deck.Edge), shape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            fontFamily = Mono,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp,
            color = if (active) Deck.Ink else Deck.Text,
        )
    }
}
