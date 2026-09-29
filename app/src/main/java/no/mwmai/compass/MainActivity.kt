package no.mwmai.compass

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.hardware.GeomagneticField
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.os.CancellationSignal
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.location.LocationManagerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import no.mwmai.compass.ui.CompassScreen
import no.mwmai.compass.ui.MwmCompassTheme

class MainActivity : ComponentActivity() {
    private lateinit var sensor: CompassSensor
    private val prefs by lazy { getSharedPreferences("compass", MODE_PRIVATE) }

    // Magnetic north unless the user switches to true north. The mark is kept
    // as a magnetic bearing so it stays put when the reference is switched.
    private var trueNorth by mutableStateOf(false)
    private var declination by mutableStateOf<Float?>(null)
    private var mark by mutableStateOf<Float?>(null)
    private var locating by mutableStateOf(false)

    private val askLocation = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) locate() else if (declination == null) switchNorth(false)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val bar = SystemBarStyle.dark(0xFF14171C.toInt())
        enableEdgeToEdge(statusBarStyle = bar, navigationBarStyle = bar)
        // A compass is read at a glance on a trail; the screen should not
        // time out between glances while the app is open.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        sensor = CompassSensor(this)
        trueNorth = prefs.getBoolean("true_north", false)
        mark = prefs.getFloat("mark", Float.NaN).takeUnless { it.isNaN() }
        if (prefs.contains("lat")) {
            declination = declinationAt(prefs.getFloat("lat", 0f), prefs.getFloat("lon", 0f), prefs.getFloat("alt", 0f))
        }
        if (trueNorth && hasLocationPermission()) locate()

        setContent {
            MwmCompassTheme {
                val reading by sensor.state.collectAsStateWithLifecycle()
                CompassScreen(
                    reading = reading,
                    trueNorth = trueNorth,
                    declination = declination,
                    locating = locating,
                    mark = mark,
                    onToggleNorth = { switchNorth(!trueNorth) },
                    onMark = { saveMark(if (mark == null) reading.azimuth else null) },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        sensor.start()
    }

    override fun onPause() {
        sensor.stop()
        super.onPause()
    }

    private fun saveMark(value: Float?) {
        mark = value
        prefs.edit { if (value == null) remove("mark") else putFloat("mark", value) }
    }

    private fun switchNorth(on: Boolean) {
        trueNorth = on
        prefs.edit { putBoolean("true_north", on) }
        if (!on) return
        if (hasLocationPermission()) locate()
        else askLocation.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
    }

    private fun hasLocationPermission(): Boolean = ContextCompat.checkSelfPermission(
        this, Manifest.permission.ACCESS_COARSE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED

    /**
     * Declination only changes noticeably over tens of kilometres, so any fix
     * will do: the last one Android already has, then one fresh reading. The
     * position never leaves the phone; it goes into Android's built-in World
     * Magnetic Model and is stored only to reuse the declination offline.
     */
    @SuppressLint("MissingPermission")
    private fun locate() {
        if (!hasLocationPermission()) return
        val lm = getSystemService(LocationManager::class.java) ?: return
        val providers = lm.getProviders(true)
        providers.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
            ?.let(::useFix)
        val provider = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
            .firstOrNull { it in providers } ?: return
        locating = declination == null
        LocationManagerCompat.getCurrentLocation(
            lm, provider, CancellationSignal(), ContextCompat.getMainExecutor(this),
        ) { fix ->
            locating = false
            if (fix != null) useFix(fix)
        }
    }

    private fun useFix(fix: Location) {
        val lat = fix.latitude.toFloat()
        val lon = fix.longitude.toFloat()
        val alt = if (fix.hasAltitude()) fix.altitude.toFloat() else 0f
        declination = declinationAt(lat, lon, alt)
        prefs.edit { putFloat("lat", lat); putFloat("lon", lon); putFloat("alt", alt) }
    }

    private fun declinationAt(lat: Float, lon: Float, alt: Float): Float =
        GeomagneticField(lat, lon, alt, System.currentTimeMillis()).declination
}
