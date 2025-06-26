package co.uk.doverguitarteacher.wheresmycar

import android.location.Location
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.google.ar.sceneform.AnchorNode
import com.google.ar.sceneform.Node
import com.google.ar.sceneform.math.Vector3
import com.google.ar.sceneform.rendering.MaterialFactory
import com.google.ar.sceneform.rendering.ModelRenderable
import com.google.ar.sceneform.rendering.ShapeFactory
import com.google.ar.sceneform.ux.ArFragment
import com.google.ar.sceneform.ux.ArSceneView
import com.google.ar.sceneform.ux.TransformableNode
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun ARCarFinderView(
    modifier: Modifier = Modifier,
    phoneAzimuth: Float, // The direction the phone is pointing
    userLocation: Location,
    carLocation: ParkedLocation
) {
    // We'll keep the object at a fixed distance so it's always visible
    val distanceInMeters = 2f

    // Calculate the real-world direction from the user to the car
    val carLocationAsLocation = Location("").apply {
        latitude = carLocation.latitude
        longitude = carLocation.longitude
    }
    val bearingToCar = userLocation.bearingTo(carLocationAsLocation)

    // Calculate the angle to place the object relative to where the phone is pointing
    val angle = bearingToCar - phoneAzimuth
    val angleInRadians = Math.toRadians(angle.toDouble())

    // Calculate the 3D position (x, y, z) in front of the camera
    val x = distanceInMeters * sin(angleInRadians).toFloat()
    val z = -distanceInMeters * cos(angleInRadians).toFloat() // Z is negative because it's "away" from the camera

    // Use AndroidView to embed the classic AR view into our Composable
    AndroidView(
        modifier = modifier,
        factory = { context ->
            val arSceneView = ArSceneView(context)

            // Create a simple red sphere to represent the car
            MaterialFactory.makeOpaqueWithColor(context, com.google.ar.sceneform.rendering.Color(android.graphics.Color.RED))
                .thenAccept { material ->
                    val sphere = ShapeFactory.makeSphere(0.1f, Vector3(0f, 0.1f, 0f), material)
                    // Clear existing car markers
                    arSceneView.scene.children.filter { it.name == "car_anchor" }.forEach {
                        arSceneView.scene.removeChild(it)
                    }

                    // Create a new anchor at the calculated position relative to the camera
                    val frame = arSceneView.arFrame ?: return@thenAccept
                    val cameraPose = frame.camera.pose
                    val newPosition = floatArrayOf(x, 0f, z) // y=0 to keep it level with the phone
                    val newPose = cameraPose.compose(com.google.ar.core.Pose.makeTranslation(newPosition))

                    val anchor = arSceneView.session?.createAnchor(newPose) ?: return@thenAccept

                    // Create a node to hold our anchor and model
                    val anchorNode = AnchorNode(anchor).apply {
                        name = "car_anchor"
                        val modelNode = Node().apply {
                            renderable = sphere
                        }
                        addChild(modelNode)
                    }
                    arSceneView.scene.addChild(anchorNode)
                }
            arSceneView
        },
        update = { arSceneView ->
            // This 'update' block is called whenever the state changes (e.g., phone moves)
            // Re-calculate position and update the anchor
            val frame = arSceneView.arFrame ?: return@AndroidView
            val cameraPose = frame.camera.pose
            val newPosition = floatArrayOf(x, 0f, z)
            val newPose = cameraPose.compose(com.google.ar.core.Pose.makeTranslation(newPosition))

            // Find our existing anchor and update its pose
            arSceneView.scene.children.find { it.name == "car_anchor" }?.let { node ->
                (node as? AnchorNode)?.anchor?.detach()
                node.anchor = arSceneView.session?.createAnchor(newPose)
            }
        }
    )
}
