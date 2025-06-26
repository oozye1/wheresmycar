package co.uk.doverguitarteacher.wheresmycar

import android.Manifest
import android.annotation.SuppressLint
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material3.*
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

// Defines the screens we can navigate between
enum class Screen { MAP, AR }

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
    var currentScreen by remember { mutableStateOf(Screen.MAP) }

    val savedLocationState by dao.getParkedLocation().collectAsState(initial = null)
    val currentUserLocation by rememberUpdatedLocationState()

    var hasLocationPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
    }

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
            Scaffold(
                bottomBar = {
                    NavigationBar {
                        NavigationBarItem(
                            selected = currentScreen == Screen.MAP,
                            onClick = { currentScreen = Screen.MAP },
                            label = { Text("Map") },
                            icon = { Icon(Icons.Filled.LocationOn, "Map") }
                        )
                        NavigationBarItem(
                            selected = currentScreen == Screen.AR,
                            onClick = { currentScreen = Screen.AR },
                            label = { Text("AR") },
                            icon = { Icon(Icons.Filled.ViewInAr, "AR View") }
                        )
                    }
                }
            ) { paddingValues ->
                Box(modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)) {

                    when (currentScreen) {
                        Screen.MAP -> {
                            GoogleMap(
                                modifier = Modifier.fillMaxSize(),
                                properties = MapProperties(isMyLocationEnabled = true),
                                cameraPositionState = cameraPositionState,
                                uiSettings = MapUiSettings(zoomControlsEnabled = false)
                            ) {
                                savedLocationState?.let { location ->
                                    Marker(
                                        state = MarkerState(position = LatLng(location.latitude, location.longitude)),
                                        title = "My Car",
                                    )
                                }
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
                                        currentUserLocation?.let { location ->
                                            val parkedLocation = ParkedLocation(latitude = location.latitude, longitude = location.longitude)
                                            coroutineScope.launch { dao.upsertParkedLocation(parkedLocation) }
                                            Toast.makeText(context, "Location Saved!", Toast.LENGTH_SHORT).show()
                                            isFindingLocation = false
                                        } ?: run {
                                            Toast.makeText(context, "Could not get location. Move to a clearer area.", Toast.LENGTH_SHORT).show()
                                            isFindingLocation = false
                                        }
                                    }) {
                                        Text("Park My Car!")
                                    }
                                }
                            }
                        }
                        Screen.AR -> {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("AR View Coming Soon...")
                            }
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
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = { permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION) }) {
                    Text("Grant Permission")
                }
            }
        }
    }
}


@SuppressLint("MissingPermission")
@Composable
fun rememberUpdatedLocationState(): State<Location?> {
    val context = LocalContext.current
    val locationState = remember { mutableStateOf<Location?>(null) }
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    DisposableEffect(key1 = fusedLocationClient) {
        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { location ->
                    if (location.accuracy < 30.0f) {
                        locationState.value = location
                    }
                }
            }
        }
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000).build()
        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
        onDispose {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
    }
    return locationState
}
