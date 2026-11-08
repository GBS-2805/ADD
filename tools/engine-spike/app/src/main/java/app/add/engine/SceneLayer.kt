package app.add.engine

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sceneview.Scene
import io.github.sceneview.SceneView
import io.github.sceneview.node.ModelNode
import io.github.sceneview.rememberCameraNode
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberEnvironmentLoader
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.rememberNode
import io.github.sceneview.rememberNodes
import dev.romainguy.kotlin.math.Float3

enum class ModelAsset(val label: String, val path: String) {
    Gem("Gem", "models/engine_gem.glb"),
    Ring("Ring", "models/engine_ring.glb"),
}

class SceneHandle {
    var view by mutableStateOf<SceneView?>(null)
    var ready by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var width by mutableStateOf(0)
    var height by mutableStateOf(0)
    var modelLoaded by mutableStateOf(false)

    /** Applies a turn straight to the Filament node while a gesture is in flight. */
    var applyRotation: ((Float) -> Unit)? = null
}

@Composable
fun FilamentLayer(
    layer: ModelLayer,
    handle: SceneHandle,
    rotation: Float,
    modifier: Modifier = Modifier,
) {
    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val environmentLoader = rememberEnvironmentLoader(engine)
    val loaded = remember(modelLoader, layer.asset) {
        runCatching { modelLoader.createModelInstance(layer.asset.path) }
    }
    val error = loaded.exceptionOrNull()?.message
    val instance = loaded.getOrNull()
    val currentHandle by rememberUpdatedState(handle)

    LaunchedEffect(error, instance) {
        handle.error = error
        handle.modelLoaded = instance != null
        handle.ready = error == null
    }

    if (instance == null) {
        Box(
            modifier = modifier.background(Ink).padding(10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "3D OFFLINE\n${layer.asset.path}\n" + (error ?: "not loaded"),
                color = Clay,
                fontSize = 8.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        return
    }

    val node = rememberNode {
        ModelNode(
            modelInstance = instance,
            scaleToUnits = layer.scaleToUnits,
            autoAnimate = false,
        )
    }
    val nodes = rememberNodes { add(node) }

    // Pinned camera: the model is framed by its own scale, never by the size of the
    // viewport. Resizing the layer's frame therefore resizes the whole layer, and the
    // unit scale resizes the model inside a frame that stays where it is. Without
    // this the view re-framed itself and every resize read as a zoom.
    // Distance chosen so a model at unit scale 1.0 fills its frame instead of sitting
    // at a quarter of it: at 4.0 the gem occupied about 25% of the viewport, which read
    // as "the model is tiny in a big black box". 1.25 puts it near the frame edges.
    val cameraNode = rememberCameraNode(engine) { position = Float3(0f, 0f, 1.25f) }

    LaunchedEffect(layer.scaleToUnits) {
        node.scaleToUnitCube(layer.scaleToUnits)
    }

    // Turn the mesh, not the SurfaceView. Rotating the Android view as a 2D quad
    // clips the surface into a diamond and shears the model at oblique angles.
    LaunchedEffect(node) {
        currentHandle.applyRotation = { degrees ->
            node.rotation = Float3(0f, degrees, 0f)
        }
    }
    LaunchedEffect(node, rotation) {
        node.rotation = Float3(0f, rotation, 0f)
    }

    Scene(
        modifier = modifier,
        engine = engine,
        modelLoader = modelLoader,
        environmentLoader = environmentLoader,
        cameraNode = cameraNode,
        cameraManipulator = null as io.github.sceneview.gesture.CameraGestureDetector.CameraManipulator?,
        childNodes = nodes,
        isOpaque = true,
        onViewCreated = {
            currentHandle.view = this
            currentHandle.width = width
            currentHandle.height = height
            currentHandle.ready = true
            currentHandle.error = null
        },
        onViewUpdated = {
            if (currentHandle.view !== this) currentHandle.view = this
            if (currentHandle.width != width) currentHandle.width = width
            if (currentHandle.height != height) currentHandle.height = height
        },
    )

    DisposableEffect(instance) {
        onDispose {
            currentHandle.applyRotation = null
            if (currentHandle.view != null) currentHandle.view = null
            currentHandle.width = 0
            currentHandle.height = 0
            currentHandle.modelLoaded = false
            currentHandle.ready = false
        }
    }
}
