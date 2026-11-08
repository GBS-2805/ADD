package app.add.engine

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.UUID
import kotlin.math.max
import kotlin.math.min

enum class Tool(val label: String, val glyph: String) {
    Select("Select", "V"),
    Vector("Vector", "A"),
    Type("Type", "T"),
    Icon("Icon", "I"),
    Sticker("Sticker", "S"),
    Model("3D", "3"),
}

enum class TextAlign { Left, Center, Right }

sealed interface Layer {
    val id: String
    val name: String
    val visible: Boolean
    val x: Float
    val y: Float
    val w: Float
    val h: Float
    val opacity: Float
    val rotation: Float
}

data class VectorLayer(
    override val id: String,
    override val name: String,
    override val visible: Boolean = true,
    override val x: Float,
    override val y: Float,
    override val w: Float,
    override val h: Float,
    override val opacity: Float = 1f,
    override val rotation: Float = 0f,
    val network: VectorNetwork,
    val fill: Int,
    val stroke: Int,
    val strokeWidth: Float,
) : Layer

data class TextLayer(
    override val id: String,
    override val name: String,
    override val visible: Boolean = true,
    override val x: Float,
    override val y: Float,
    override val w: Float,
    override val h: Float,
    override val opacity: Float = 1f,
    override val rotation: Float = 0f,
    val text: String,
    val fontSize: Float,
    val lineHeight: Float,
    val color: Int,
    val align: TextAlign = TextAlign.Left,
    val weight: Int = 600,
) : Layer

data class IconLayer(
    override val id: String,
    override val name: String,
    override val visible: Boolean = true,
    override val x: Float,
    override val y: Float,
    override val w: Float,
    override val h: Float,
    override val opacity: Float = 1f,
    override val rotation: Float = 0f,
    val glyph: IconGlyph,
    val tint: Int,
) : Layer

data class ModelLayer(
    override val id: String,
    override val name: String,
    override val visible: Boolean = true,
    override val x: Float,
    override val y: Float,
    override val w: Float,
    override val h: Float,
    override val opacity: Float = 1f,
    override val rotation: Float = 0f,
    val scaleToUnits: Float = 1.0f,
    val asset: ModelAsset = ModelAsset.Gem,
) : Layer

val Layer.kindLabel: String
    get() = when (this) {
        is VectorLayer -> "VECTOR NETWORK"
        is TextLayer -> "TYPE / HARFBUZZ"
        is IconLayer -> "ICON / PATH PARSER"
        is ModelLayer -> "3D / ${asset.label.uppercase()}"
    }

val Layer.isVector: Boolean get() = this is VectorLayer

fun seedLayers(): List<Layer> = listOf(
    TextLayer(
        id = "type-kicker",
        name = "TYPE / KICKER",
        x = 0.07f,
        y = 0.055f,
        w = 0.84f,
        h = 0.035f,
        text = "ADD / NATIVE ENGINE / 001",
        fontSize = 22f,
        lineHeight = 1f,
        color = 0xFF11231C.toInt(),
        weight = 500,
    ),
    VectorLayer(
        id = "vector-star",
        name = "VECTOR / STAR",
        x = 0.055f,
        y = 0.11f,
        w = 0.44f,
        h = squareFrameHeight(0.44f),
        network = starNetwork(),
        fill = 0xFF0FBF92.toInt(),
        stroke = 0xFF11231C.toInt(),
        strokeWidth = 5f,
    ),
    VectorLayer(
        id = "vector-orbit",
        name = "VECTOR / ORBIT",
        x = 0.55f,
        y = 0.09f,
        w = 0.38f,
        h = squareFrameHeight(0.38f),
        network = orbitNetwork(),
        fill = 0x00000000,
        stroke = 0xFF11231C.toInt(),
        strokeWidth = 4f,
    ),
    TextLayer(
        id = "type-statement",
        name = "TYPE / STATEMENT",
        x = 0.07f,
        y = 0.555f,
        w = 0.47f,
        h = 0.33f,
        text = "FORM\nFOLLOWS\nPLAY.",
        fontSize = 104f,
        lineHeight = 0.88f,
        color = 0xFF11231C.toInt(),
        weight = 600,
    ),
    ModelLayer(
        id = "model-gem",
        name = "3D / GEM",
        x = 0.57f,
        y = 0.49f,
        w = 0.36f,
        h = 0.34f,
        scaleToUnits = 0.92f,
        asset = ModelAsset.Gem,
    ),
)

class EngineDocument {
    var layers by mutableStateOf(seedLayers())
        private set

    var selectedId by mutableStateOf("vector-star")
    var selectedNodeId by mutableStateOf<String?>(null)
    var tool by mutableStateOf(Tool.Select)
    var status by mutableStateOf("READY · SKIA ARTBOARD · 4,373 ICONS · FILAMENT 3D")
    var canUndo by mutableStateOf(false)
        private set
    var canRedo by mutableStateOf(false)
        private set
    var exporting by mutableStateOf(false)
    var editingModelId by mutableStateOf<String?>(null)
        private set

    /** Viewport zoom, 1.0 = fit. View state, but the recorder needs to see it. */
    var zoom by mutableStateOf(1f)

    private val undoStack = ArrayDeque<List<Layer>>()
    private val redoStack = ArrayDeque<List<Layer>>()
    private var gestureStart: List<Layer>? = null

    val selected: Layer? get() = layers.firstOrNull { it.id == selectedId }
    val selectedVector: VectorLayer? get() = selected as? VectorLayer
    val selectedText: TextLayer? get() = selected as? TextLayer
    val selectedIcon: IconLayer? get() = selected as? IconLayer
    val selectedModel: ModelLayer? get() = selected as? ModelLayer
    val editingModel: ModelLayer? get() = layers.firstOrNull { it.id == editingModelId } as? ModelLayer
    val modelLayers: List<ModelLayer> get() = layers.filterIsInstance<ModelLayer>()
    val modelLayer: ModelLayer? get() = modelLayers.firstOrNull { it.visible }

    fun chooseTool(value: Tool) {
        tool = value
        when (value) {
            Tool.Icon -> {
                status = "ICON CATALOG · LINEICONS / COOLICONS / PHOSPHOR / STREAMLINE"
                return
            }
            Tool.Sticker -> {
                status = "STICKER / ${StickerLibrary.presets.size} LOCAL SHAPES"
                return
            }
            Tool.Vector -> if (selected !is VectorLayer) {
                layers.firstOrNull { it is VectorLayer && it.visible }?.let { select(it.id) }
            }
            Tool.Type -> if (selected !is TextLayer) {
                layers.firstOrNull { it is TextLayer && it.visible }?.let { select(it.id) }
            }
            Tool.Model -> if (selected !is ModelLayer) {
                modelLayer?.let { select(it.id) }
            }
            Tool.Select -> Unit
        }
    }

    fun select(id: String) {
        if (layers.any { it.id == id }) {
            if (editingModelId != null && editingModelId != id) editingModelId = null
            selectedId = id
            selectedNodeId = null
            if (tool == Tool.Sticker) tool = Tool.Select
        }
    }

    fun selectNode(id: String?) {
        selectedNodeId = id
    }

    fun layerAt(x: Float, y: Float): Layer? =
        layers.asReversed().firstOrNull { layer ->
            layer.visible && Geometry.contains(layer, x, y)
        }

    fun toggleVisibility(id: String) = commit {
        it.map { layer -> if (layer.id == id) layer.withVisible(!layer.visible) else layer }
    }

    fun beginGesture() {
        if (gestureStart == null) gestureStart = layers
    }

    fun endGesture() {
        val before = gestureStart ?: return
        gestureStart = null
        if (before != layers) {
            undoStack.addLast(before)
            redoStack.clear()
            syncHistory()
            status = "EDITED · UNDO READY"
        }
    }

    fun moveSelected(dx: Float, dy: Float) {
        val current = selected ?: return
        val nextX = (current.x + dx).coerceIn(-0.45f, 1.0f)
        val nextY = (current.y + dy).coerceIn(-0.45f, 1.0f)
        updateLayer(current.id) { it.withPosition(nextX, nextY) }
    }

    fun movePoint(pointId: String, dx: Float, dy: Float) {
        val vector = selectedVector ?: return
        val point = vector.network.point(pointId) ?: return
        updateLayer(vector.id) {
            (it as? VectorLayer)?.copy(
                network = it.network.movePoint(pointId, point.x + dx, point.y + dy)
            ) ?: it
        }
    }

    /** Node drag in the layer's rotated space, so a turned path still tracks the finger. */
    fun movePointLocal(pointId: String, dx: Float, dy: Float) {
        val vector = selectedVector ?: return
        val point = vector.network.point(pointId) ?: return
        val (lateral, vertical) = Geometry.rotateVector(dx, dy, -vector.rotation)
        updateLayer(vector.id) {
            (it as? VectorLayer)?.copy(
                network = it.network.movePoint(
                    pointId,
                    (point.x + lateral / vector.w).coerceIn(-2f, 3f),
                    (point.y + vertical / vector.h).coerceIn(-2f, 3f),
                )
            ) ?: it
        }
    }

    fun addNode() = commit { layers ->
        layers.map { layer ->
            if (layer.id == selectedId && layer is VectorLayer) {
                layer.copy(network = layer.network.addNode())
            } else layer
        }
    }

    fun deleteNode() {
        val nodeId = selectedNodeId ?: return
        commit { layers ->
            layers.map { layer ->
                if (layer.id == selectedId && layer is VectorLayer) {
                    layer.copy(network = layer.network.deleteNode(nodeId))
                } else layer
            }
        }
        if (selectedVector?.network?.point(nodeId) == null) selectedNodeId = null
    }

    fun addText() {
        val id = newId("type")
        val layer = TextLayer(
            id = id,
            name = "TYPE / NEW",
            x = 0.12f,
            y = 0.12f,
            w = 0.58f,
            h = 0.16f,
            text = "NEW LINE",
            fontSize = 58f,
            lineHeight = 0.95f,
            color = 0xFF11231C.toInt(),
        )
        commit { it + layer }
        selectedId = id
        tool = Tool.Type
        status = "TYPE LAYER ADDED"
    }

    fun addVector() {
        val id = newId("vector")
        val layer = VectorLayer(
            id = id,
            name = "VECTOR / NEW",
            x = 0.18f,
            y = 0.18f,
            w = 0.32f,
            h = squareFrameHeight(0.32f),
            network = triangleNetwork(),
            fill = 0xFFE86A4A.toInt(),
            stroke = 0xFF11231C.toInt(),
            strokeWidth = 4f,
        )
        commit { it + layer }
        selectedId = id
        tool = Tool.Vector
        status = "VECTOR NETWORK ADDED · 3 NODES"
    }

    fun addSticker(preset: StickerPreset) {
        val id = newId("sticker")
        val offset = (layers.count { it.name.startsWith(preset.kind) } * 0.03f).coerceAtMost(0.18f)
        val layer = VectorLayer(
            id = id,
            name = "${preset.kind} / ${preset.label}",
            x = 0.2f + offset,
            y = 0.2f + offset,
            w = 0.26f,
            h = squareFrameHeight(0.26f),
            network = preset.network,
            fill = preset.fill,
            stroke = preset.stroke,
            strokeWidth = preset.strokeWidth,
        )
        commit { it + layer }
        selectedId = id
        selectedNodeId = null
        tool = Tool.Select
        status = "${preset.kind} ADDED · ${preset.label}"
    }

    fun addIcon(glyph: IconGlyph) {
        val id = newId("icon")
        val offset = (layers.count { it is IconLayer } * 0.025f).coerceAtMost(0.16f)
        val layer = IconLayer(
            id = id,
            name = "ICON / ${glyph.name.uppercase()}",
            x = (0.16f + offset).coerceAtMost(0.7f),
            y = (0.22f + offset).coerceAtMost(0.7f),
            w = 0.22f,
            h = squareFrameHeight(0.22f),
            glyph = glyph,
            tint = 0xFF11231C.toInt(),
        )
        commit { it + layer }
        selectedId = id
        selectedNodeId = null
        tool = Tool.Select
        status = "ICON PLACED · ${glyph.brandLabel.ifBlank { glyph.brandSlug }} / ${glyph.name} · ${glyph.paths.size} PATHS"
    }

    fun addModel(asset: ModelAsset) {
        val id = newId("model")
        val offset = (modelLayers.size * 0.05f).coerceAtMost(0.14f)
        val layer = ModelLayer(
            id = id,
            name = "3D / ${asset.label.uppercase()}",
            x = (0.08f + offset).coerceAtMost(0.5f),
            y = (0.08f + offset).coerceAtMost(0.5f),
            w = 0.34f,
            h = 0.32f,
            scaleToUnits = 0.92f,
            asset = asset,
        )
        commit { it + layer }
        selectedId = id
        tool = Tool.Model
        status = "3D LAYER ADDED · ${asset.label.uppercase()} · ${asset.path}"
    }

    /** Steps a type layer through the three bundled Poppins faces. */
    fun setWeight(weight: Int) {
        val id = selectedId ?: return
        val face = weight.coerceIn(400, 600)
        commit { layers -> layers.map { if (it.id == id && it is TextLayer) it.copy(weight = face) else it } }
        status = "TYPE FACE $face"
    }

    fun duplicateSelected() {
        val layer = selected ?: return
        val copy = layer.duplicate()
        commit { it + copy }
        selectedId = copy.id
        selectedNodeId = null
        editingModelId = null
        status = when (copy) {
            is ModelLayer -> "3D LAYER DUPLICATED · ${modelLayers.size} VIEWPORTS"
            is IconLayer -> "ICON LAYER DUPLICATED"
            else -> "LAYER DUPLICATED"
        }
    }

    fun deleteSelected() {
        val id = selectedId
        if (layers.size <= 1) return
        commit { layerList -> layerList.filterNot { it.id == id } }
        if (editingModelId == id) editingModelId = null
        selectedId = layers.firstOrNull()?.id.orEmpty()
        selectedNodeId = null
        status = "LAYER DELETED"
    }

    fun reorderSelected(delta: Int) {
        val index = layers.indexOfFirst { it.id == selectedId }
        if (index < 0) return
        val target = (index + delta).coerceIn(0, layers.lastIndex)
        if (target == index) return
        commit { layerList ->
            layerList.toMutableList().also {
                val moving = it.removeAt(index)
                it.add(target, moving)
            }
        }
        status = if (delta > 0) "LAYER MOVED FORWARD" else "LAYER MOVED BACK"
    }

    fun setText(value: String) = updateSelected {
        (it as? TextLayer)?.copy(text = value) ?: it
    }

    fun setFontSize(value: Float) = updateSelected {
        (it as? TextLayer)?.copy(fontSize = value.coerceIn(18f, 180f)) ?: it
    }

    fun setFill(color: Int) = commit { layerList ->
        layerList.map { layer ->
            if (layer.id != selectedId) layer
            else when (layer) {
                is VectorLayer -> layer.copy(fill = color)
                is TextLayer -> layer.copy(color = color)
                is IconLayer -> layer.copy(tint = color)
                is ModelLayer -> layer
            }
        }
    }

    fun setStrokeWidth(value: Float) = commit { layerList ->
        layerList.map { layer ->
            if (layer.id == selectedId && layer is VectorLayer) {
                layer.copy(strokeWidth = value.coerceIn(0f, 18f))
            } else layer
        }
    }

    fun setModelScale(value: Float) = updateSelected {
        (it as? ModelLayer)?.copy(scaleToUnits = value.coerceIn(0.25f, 2f)) ?: it
    }

    /** The inspector's uniform scale dial. Width is in artboard fractions. */
    fun setScaleToWidth(value: Float) {
        val layer = selected ?: return
        if (layer.w <= 0f) return
        val target = value.coerceIn(0.04f, 1.2f)
        updateSelected { Geometry.scaleCentered(it, target / it.w) }
    }

    fun setModelAsset(asset: ModelAsset) = updateSelected { layer ->
        (layer as? ModelLayer)?.let { model ->
            val auto = model.name == "3D / ${model.asset.label.uppercase()}" ||
                model.name == "3D / ${model.asset.label.uppercase()} COPY"
            model.copy(
                asset = asset,
                name = if (auto) model.name.replace(model.asset.label.uppercase(), asset.label.uppercase()) else model.name,
            )
        } ?: layer
    }

    fun setRotation(value: Float) = updateSelected { it.withRotation(value) }

    fun centerSelected() = updateSelected {
        it.withBounds((1f - it.w) / 2f, (1f - it.h) / 2f, it.w, it.h).withRotation(0f)
    }

    fun toggleModelEdit(id: String) {
        val model = layers.firstOrNull { it.id == id } as? ModelLayer ?: return
        editingModelId = if (editingModelId == id) {
            status = "3D EDIT DONE · ${model.asset.label.uppercase()}"
            null
        } else {
            selectedId = id
            selectedNodeId = null
            tool = Tool.Model
            status = "3D EDIT · DRAG INSIDE TO TURN · CORNERS RESIZE FRAME"
            id
        }
    }

    fun setOpacity(value: Float) = updateSelected {
        it.withOpacity(value.coerceIn(0.1f, 1f))
    }

    fun undo() {
        val previous = undoStack.removeLastOrNull() ?: return
        redoStack.addLast(layers)
        layers = previous
        ensureSelection()
        syncHistory()
        status = "UNDO · ${layers.size} LAYERS"
    }

    fun redo() {
        val next = redoStack.removeLastOrNull() ?: return
        undoStack.addLast(layers)
        layers = next
        ensureSelection()
        syncHistory()
        status = "REDO · ${layers.size} LAYERS"
    }

    fun updateLayer(id: String, transform: (Layer) -> Layer) {
        layers = layers.map { if (it.id == id) transform(it) else it }
    }

    fun updateSelected(transform: (Layer) -> Layer) {
        updateLayer(selectedId, transform)
        val vector = selectedVector
        if (vector != null && vector.network.point(selectedNodeId ?: "") == null) {
            selectedNodeId = null
        }
    }

    private fun commit(transform: (List<Layer>) -> List<Layer>) {
        undoStack.addLast(layers)
        redoStack.clear()
        layers = transform(layers)
        ensureSelection()
        syncHistory()
    }

    private fun ensureSelection() {
        if (layers.none { it.id == selectedId }) {
            selectedId = layers.firstOrNull()?.id.orEmpty()
            selectedNodeId = null
        }
        if (layers.none { it.id == editingModelId }) editingModelId = null
    }

    private fun syncHistory() {
        canUndo = undoStack.isNotEmpty()
        canRedo = redoStack.isNotEmpty()
    }

    private fun newId(prefix: String) = "$prefix-${UUID.randomUUID().toString().take(6)}"
}

private fun Layer.withVisible(value: Boolean): Layer = when (this) {
    is VectorLayer -> copy(visible = value)
    is TextLayer -> copy(visible = value)
    is IconLayer -> copy(visible = value)
    is ModelLayer -> copy(visible = value)
}

private fun Layer.withPosition(x: Float, y: Float): Layer = when (this) {
    is VectorLayer -> copy(x = x, y = y)
    is TextLayer -> copy(x = x, y = y)
    is IconLayer -> copy(x = x, y = y)
    is ModelLayer -> copy(x = x, y = y)
}

private fun Layer.withOpacity(value: Float): Layer = when (this) {
    is VectorLayer -> copy(opacity = value)
    is TextLayer -> copy(opacity = value)
    is IconLayer -> copy(opacity = value)
    is ModelLayer -> copy(opacity = value)
}

private fun Layer.duplicate(): Layer {
    val id = "${id.substringBeforeLast('-')}-${UUID.randomUUID().toString().take(6)}"
    return when (this) {
        is VectorLayer -> copy(id = id, name = "$name COPY", x = min(0.9f, x + 0.04f), y = min(0.9f, y + 0.04f))
        is TextLayer -> copy(id = id, name = "$name COPY", x = min(0.9f, x + 0.04f), y = min(0.9f, y + 0.04f))
        is IconLayer -> copy(id = id, name = "$name COPY", x = min(0.9f, x + 0.04f), y = min(0.9f, y + 0.04f))
        is ModelLayer -> {
            val copyW = min(w, 0.30f)
            val copyH = min(h, 0.30f)
            val copyX = if (x > 0.5f) {
                (x - w - 0.04f).coerceIn(0.02f, 0.6f)
            } else {
                (x + w + 0.04f).coerceIn(0.02f, 0.6f)
            }
            copy(
                id = id,
                name = "$name COPY",
                x = copyX,
                y = min(0.9f, y + 0.02f),
                w = copyW,
                h = copyH,
            )
        }
    }
}
