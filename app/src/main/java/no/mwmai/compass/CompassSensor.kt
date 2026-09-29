package no.mwmai.compass

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.abs
import kotlin.math.acos

data class Reading(
    /** Magnetic heading in degrees, smoothed. */
    val azimuth: Float = 0f,
    /** True once the first sensor sample has arrived. */
    val live: Boolean = false,
    /** Held upright like a camera: the bearing is where the back of the phone points. */
    val upright: Boolean = false,
    /** Bubble offset for the level, -1 to 1 on each axis, screen right and down positive. */
    val levelX: Float = 0f,
    val levelY: Float = 0f,
    /** Degrees the screen is tilted away from lying flat, face up. */
    val tilt: Float = 0f,
    /** Magnetometer accuracy, a SensorManager.SENSOR_STATUS_* value, or -1 before Android reports one. */
    val accuracy: Int = -1,
    val hasSensor: Boolean = true,
)

/**
 * Turns the phone's sensors into a heading. The fused rotation-vector sensor
 * (accelerometer, magnetometer and gyroscope together) is preferred because it
 * stays steady while you walk; phones without one fall back to accelerometer
 * plus magnetometer. Both give a tilt-compensated heading, so the phone does
 * not have to be held perfectly flat.
 */
class CompassSensor(context: Context) : SensorEventListener {
    private val sm = context.getSystemService(SensorManager::class.java)
    private val rotation: Sensor? = sm?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val accel: Sensor? = sm?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val magnet: Sensor? = sm?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
    private val available = magnet != null && (rotation != null || accel != null)

    private val _state = MutableStateFlow(Reading(hasSensor = available))
    val state: StateFlow<Reading> = _state

    private val matrix = FloatArray(9)
    private val remapped = FloatArray(9)
    private val orientation = FloatArray(3)
    private val gravity = FloatArray(3)
    private val geomagnetic = FloatArray(3)
    private var haveGravity = false
    private var haveGeomagnetic = false
    private var upright = false
    private var accuracy = -1
    private val smoother = AngleSmoother(0.15f)

    fun start() {
        val manager = sm ?: return
        if (!available) return
        if (rotation != null) {
            manager.registerListener(this, rotation, SensorManager.SENSOR_DELAY_GAME)
            // Only for its accuracy reports: the fused sensor hides whether the
            // magnetometer needs a figure-8.
            manager.registerListener(this, magnet, SensorManager.SENSOR_DELAY_UI)
        } else {
            manager.registerListener(this, accel, SensorManager.SENSOR_DELAY_GAME)
            manager.registerListener(this, magnet, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    fun stop() {
        sm?.unregisterListener(this)
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
        if (sensor.type == Sensor.TYPE_MAGNETIC_FIELD) {
            this.accuracy = accuracy
            _state.value = _state.value.copy(accuracy = accuracy)
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ROTATION_VECTOR -> {
                // Some phones hand over a fifth value that the matrix call rejects.
                val v = if (event.values.size > 4) event.values.copyOf(4) else event.values
                SensorManager.getRotationMatrixFromVector(matrix, v)
                publish()
            }
            Sensor.TYPE_ACCELEROMETER -> {
                lowPass(event.values, gravity)
                haveGravity = true
                fromRaw()
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                accuracy = event.accuracy
                if (rotation == null) {
                    lowPass(event.values, geomagnetic)
                    haveGeomagnetic = true
                    fromRaw()
                }
            }
        }
    }

    private fun lowPass(input: FloatArray, output: FloatArray) {
        for (i in 0..2) output[i] = output[i] + 0.2f * (input[i] - output[i])
    }

    private fun fromRaw() {
        if (!haveGravity || !haveGeomagnetic) return
        if (SensorManager.getRotationMatrix(matrix, null, gravity, geomagnetic)) publish()
    }

    private fun publish() {
        // Row three of the matrix is "up" seen from the phone: its z part is 1
        // when the phone lies flat face up and 0 when it stands upright. The
        // two thresholds keep the mode from flickering around 55 degrees.
        val upZ = matrix[8].coerceIn(-1f, 1f)
        upright = if (upright) abs(upZ) < 0.6f else abs(upZ) < 0.45f
        val m = if (upright) {
            SensorManager.remapCoordinateSystem(
                matrix, SensorManager.AXIS_X, SensorManager.AXIS_Z, remapped,
            )
            remapped
        } else {
            matrix
        }
        SensorManager.getOrientation(m, orientation)
        val raw = normalize(Math.toDegrees(orientation[0].toDouble()).toFloat())
        _state.value = Reading(
            azimuth = smoother.next(raw),
            live = true,
            upright = upright,
            // The bubble rises to the high edge: toward whichever side "up" leans.
            levelX = matrix[6],
            levelY = -matrix[7],
            tilt = Math.toDegrees(acos(upZ.toDouble())).toFloat(),
            accuracy = accuracy,
            hasSensor = true,
        )
    }
}
