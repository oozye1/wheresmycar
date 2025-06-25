// In file: MainActivity.kt
package co.uk.doverguitarteacher.wheresmycar

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.os.Looper
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import co.uk.doverguitarteacher.wheresmycar.ui.theme.WheresmycarTheme
import com.google.android.gms.location.*
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val db by lazy { AppDatabase.getDatabase(this) }
    private val dao by lazy { db.parkedLocationDao() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WheresmycarTheme {
                ParkMyCarScreen(dao)
            }
        }
    }
}

@Composable
fun ParkMyCarScreen(dao: ParkedLocationDao) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val savedLocationState by dao.getParkedLocation().collectAsState(initial = null)

    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        )
    }

    // --- NEW: Get sensor and live location data FROM THE HELPER FUNCTIONS ---
    val sensorHelper = rememberSensorManagerHelper()
    val currentUserLocation by rememberUpdatedLocationState()
    // ----------------------------------------------------------------------

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            hasLocationPermission = isGranted
            if (!isGranted) {
                Toast.makeText(context, "Permission denied. App cannot function without location.", Toast.LENGTH_LONG).show()
            }
        }
    )

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(51.1279, 1.3136), 10f) // Default to Dover
    }

    var isFindingLocation by remember { mutableStateOf(false) }

    LaunchedEffect(savedLocationState) {
        savedLocationState?.let { location ->
            val latLng = LatLng(location.latitude, location.longitude)
            cameraPositionState.animate(
                update = com.google.android.gms.maps.CameraUpdateFactory.newLatLngZoom(latLng, 17f),
                durationMs = 1000
            )
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        if (hasLocationPermission) {
            Box(modifier = Modifier.fillMaxSize()) {
                GoogleMap(
                    modifier = Modifier.fillMaxSize(),
                    cameraPositionState = cameraPositionState,
                    uiSettings = MapUiSettings(zoomControlsEnabled = false)
                ) {
                    savedLocationState?.let { location ->
                        Marker(
                            state = MarkerState(position = LatLng(location.latitude, location.longitude)),
                            title = "My Car",
                            snippet = "Lat: ${location.latitude}, Lng: ${location.longitude}"
                        )
                    }
                }

                // --- NEW: Add the CompassView to the UI ---
                // We only show the compass if a car has been parked.
                if (savedLocationState != null) {
                    CompassView(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 32.dp),
                        phoneAzimuth = sensorHelper.azimuth,
                        userLocation = currentUserLocation,
                        carLocation = savedLocationState
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.Bottom,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (isFindingLocation) {
                        CircularProgressIndicator()
                    } else {
                        Button(onClick = {
                            isFindingLocation = true
                            getCurrentLocation(context) { location ->
                                isFindingLocation = false
                                val parkedLocation = ParkedLocation(
                                    latitude = location.latitude,
                                    longitude = location.longitude
                                )
                                coroutineScope.launch {
                                    dao.upsertParkedLocation(parkedLocation)
                                }
                                Toast.makeText(context, "Location Saved!", Toast.LENGTH_SHORT).show()
                            }
                        }) {
                            Text("Park My Car!")
                        }
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("We need location permission to find your car.")
                Button(
                    onClick = { permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION) },
                    modifier = Modifier.padding(top = 20.dp)
                ) {
                    Text("Grant Permission")
                }
            }
        }
    }
}

@SuppressLint("MissingPermission")
fun getCurrentLocation(context: Context, onLocationFetched: (Location) -> Unit) {
    val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
    fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
        .addOnSuccessListener { location: Location? ->
            location?.let {
                onLocationFetched(it)
            } ?: Toast.makeText(context, "Could not get location. Try again.", Toast.LENGTH_SHORT).show()
        }
        .addOnFailureListener { e ->
            Toast.makeText(context, "Failed to get location: ${e.message}", Toast.LENGTH_SHORT).show()
        }
}

// Helper Composable for getting LIVE location updates
@SuppressLint("MissingPermission")
@Composable
fun rememberUpdatedLocationState(): State<Location?> {
    val context = LocalContext.current
    val locationState = remember { mutableStateOf<Location?>(null) }
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    DisposableEffect(key1 = fusedLocationClient) {
        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                locationState.value = result.lastLocation
            }
        }

        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            1000 // Update every 1 second
        ).build()

        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())

        onDispose {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
    }
    return locationState
}
