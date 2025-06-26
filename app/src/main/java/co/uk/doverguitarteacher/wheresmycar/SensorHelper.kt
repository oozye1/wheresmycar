package co.uk.doverguitarteacher.wheresmycar

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext

// A helper class to manage sensor listeners with smoothing
class SensorManagerHelper(context: Context) : SensorEventListener {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

    private var gravity: FloatArray? = null
    private var geomagnetic: FloatArray? = null

    // This state will hold the latest azimuth (direction in degrees)
    var azimuth by mutableStateOf(0f)

    fun start() {
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI)
        sensorManager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_UI)
    }

    fun stop() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        val alpha = 0.97f // Smoothing factor for the low-pass filter

        if (event?.sensor?.type == Sensor.TYPE_ACCELEROMETER) {
            gravity = gravity?.let { g ->
                floatArrayOf(
                    alpha * g[0] + (1 - alpha) * event.values[0],
                    alpha * g[1] + (1 - alpha) * event.values[1],
                    alpha * g[2] + (1 - alpha) * event.values[2]
                )
            } ?: event.values.clone()
        }
        if (event?.sensor?.type == Sensor.TYPE_MAGNETIC_FIELD) {
            geomagnetic = geomagnetic?.let { g ->
                floatArrayOf(
                    alpha * g[0] + (1 - alpha) * event.values[0],
                    alpha * g[1] + (1 - alpha) * event.values[1],
                    alpha * g[2] + (1 - alpha) * event.values[2]
                )
            } ?: event.values.clone()
        }

        if (gravity != null && geomagnetic != null) {
            val r = FloatArray(9)
            val i = FloatArray(9)
            val success = SensorManager.getRotationMatrix(r, i, gravity, geomagnetic)
            if (success) {
                val orientation = FloatArray(3)
                SensorManager.getOrientation(r, orientation)
                // We get the raw azimuth here from the orientation
                val newAzimuth = Math.toDegrees(orientation[0].toDouble()).toFloat()

                // Apply another filter to the final value for smoother arrow rotation
                azimuth = (azimuth * 0.9f) + (newAzimuth * 0.1f)
                azimuth = (azimuth + 360) % 360 // Normalize to 0-360 degrees
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not needed for this use case
    }
}

// A Composable 'remember' function to safely use our helper
@Composable
fun rememberSensorManagerHelper(): SensorManagerHelper {
    val context = LocalContext.current
    val sensorManagerHelper = remember { SensorManagerHelper(context) }

    // Safely start and stop the sensor listeners with the Composable's lifecycle
    DisposableEffect(Unit) {
        sensorManagerHelper.start()
        onDispose {
            sensorManagerHelper.stop()
        }
    }
    return sensorManagerHelper
}
