package app.add.engine

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.UUID
import kotlin.math.abs
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

/** Where a moved layer caught: the lines the stage draws while the finger is down. */
data class SnapGuides(val vertical: List<Float> = emptyList(), val horizontal: List<Float> = emptyList())

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
    /** Blend, mask and non-destructive effects; defaults leave older layers unchanged. */
    val style: LayerStyle
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
    val paint: PaintSpec? = null,
    override val style: LayerStyle = LayerStyle(),
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
    val paint: PaintSpec? = null,
    override val style: LayerStyle = LayerStyle(),
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
    override val style: LayerStyle = LayerStyle(),
) : Layer

data class ImageLayer(
    override val id: String,
    override val name: String,
    override val visible: Boolean = true,
    override val x: Float,
    override val y: Float,
    override val w: Float,
    override val h: Float,
    override val opacity: Float = 1f,
    override val rotation: Float = 0f,
    /** The file name inside filesDir/media. Copied in, never referenced in place. */
    val path: String,
    /** True fills the frame and crops; false fits the whole photograph inside it. */
    val fill: Boolean = true,
    override val style: LayerStyle = LayerStyle(),
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
    override val style: LayerStyle = LayerStyle(),
) : Layer

val Layer.kindLabel: String
    get() = when (this) {
        is VectorLayer -> "VECTOR NETWORK"
        is TextLayer -> "TYPE / HARFBUZZ"
        is IconLayer -> "ICON / PATH PARSER"
        is ModelLayer -> "3D / ${asset.label.uppercase()}"
        is ImageLayer -> if (fill) "PHOTO / FILL" else "PHOTO / FIT"
    }

val Layer.isVector: Boolean get() = this is VectorLayer

fun diamondNetwork(): VectorNetwork = VectorNetwork(
    points = listOf(
        VecPoint("diamond-a", 0.5f, 0.04f),
        VecPoint("diamond-b", 0.96f, 0.5f),
        VecPoint("diamond-c", 0.5f, 0.96f),
        VecPoint("diamond-d", 0.04f, 0.5f),
    ),
    segments = listOf(
        VecSegment("diamond-ab", "diamond-a", "diamond-b"),
        VecSegment("diamond-bc", "diamond-b", "diamond-c"),
        VecSegment("diamond-cd", "diamond-c", "diamond-d"),
        VecSegment("diamond-da", "diamond-d", "diamond-a"),
    ),
    regions = listOf(
        VecRegion(
            "diamond-region",
            listOf("diamond-ab", "diamond-bc", "diamond-cd", "diamond-da"),
            0xFFE86A4A.toInt(),
        )
    ),
)

/**
 * The first page is also the engine's own test plate: a gradient star, a
 * Difference layer carrying a paint mask and a grade, a diamond clipped to it,
 * gradient type and a duotone caption. The 3D layer is off so a device without
 * a working Filament context still paints the whole composition.
 */
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
        name = "VECTOR / GRADIENT STAR",
        x = 0.055f,
        y = 0.11f,
        w = 0.44f,
        h = squareFrameHeight(0.44f),
        network = starNetwork(),
        fill = 0xFF0FBF92.toInt(),
        stroke = 0xFF11231C.toInt(),
        strokeWidth = 5f,
        paint = PaintSpec.Gradient(
            kind = PaintKind.Linear,
            stops = listOf(
                PaintStop(0f, 0xFF11231C.toInt()),
                PaintStop(0.42f, 0xFF0FBF92.toInt()),
                PaintStop(0.78f, colorWithAlpha(0xFFD6E381.toInt(), 0.86f)),
                PaintStop(1f, colorWithAlpha(0xFFF2EEE3.toInt(), 0.55f)),
            ),
        ),
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
    VectorLayer(
        id = "vector-blend",
        name = "VECTOR / DIFFERENCE + MASK",
        x = 0.46f,
        y = 0.14f,
        w = 0.42f,
        h = squareFrameHeight(0.42f),
        network = diamondNetwork(),
        fill = 0xFFE86A4A.toInt(),
        stroke = 0xFF11231C.toInt(),
        strokeWidth = 4f,
        style = LayerStyle(
            blend = BlendKind.Difference,
            effects = LayerEffects(
                enabled = true,
                exposure = 0.25f,
                contrast = 0.4f,
                saturation = 0.2f,
                grain = 0.22f,
            ),
            mask = LayerMask(
                strokes = listOf(
                    MaskStroke(
                        points = listOf(
                            MaskPoint(0.06f, 0.82f),
                            MaskPoint(0.5f, 0.5f),
                            MaskPoint(0.94f, 0.18f),
                        ),
                        radius = 0.13f,
                    ),
                ),
            ),
        ),
    ),
    VectorLayer(
        id = "vector-clip",
        name = "VECTOR / CLIP TO BELOW",
        x = 0.60f,
        y = 0.215f,
        w = 0.17f,
        h = squareFrameHeight(0.17f),
        network = diamondNetwork(),
        fill = 0xFFD6E381.toInt(),
        stroke = 0xFF11231C.toInt(),
        strokeWidth = 2f,
        style = LayerStyle(clipToBelow = true),
    ),
    TextLayer(
        id = "type-statement",
        name = "TYPE / SWEEP GRADIENT",
        x = 0.07f,
        y = 0.555f,
        w = 0.47f,
        h = 0.33f,
        text = "FORM\nFOLLOWS\nPLAY.",
        fontSize = 104f,
        lineHeight = 0.88f,
        color = 0xFF11231C.toInt(),
        weight = 600,
        paint = PaintSpec.Gradient(
            kind = PaintKind.Sweep,
            stops = listOf(
                PaintStop(0f, 0xFF0FBF92.toInt()),
                PaintStop(0.35f, 0xFFE86A4A.toInt()),
                PaintStop(0.7f, 0xFFD6E381.toInt()),
                PaintStop(1f, 0xFF0FBF92.toInt()),
            ),
        ),
        style = LayerStyle(effects = LayerEffects(contrast = 0.12f, grain = 0.18f)),
    ),
    TextLayer(
        id = "type-duotone",
        name = "TYPE / DUOTONE",
        x = 0.57f,
        y = 0.80f,
        w = 0.36f,
        h = 0.06f,
        text = "DUOTONE / GRAIN",
        fontSize = 28f,
        lineHeight = 1f,
        color = 0xFF11231C.toInt(),
        weight = 600,
        style = LayerStyle(
            effects = LayerEffects(
                duotone = true,
                duotoneDark = 0xFF0C1210.toInt(),
                duotoneLight = 0xFFD6E381.toInt(),
                grain = 0.18f,
            ),
        ),
    ),
    ModelLayer(
        id = "model-gem",
        name = "3D / GEM",
        visible = false,
        x = 0.57f,
        y = 0.49f,
        w = 0.36f,
        h = 0.34f,
        scaleToUnits = 0.92f,
        asset = ModelAsset.Gem,
    ),
)

class EngineDocument(initial: List<Layer>? = null) {
    var layers by mutableStateOf(initial ?: seedLayers())
        private set

    var selectedId by mutableStateOf(initial?.firstOrNull()?.id ?: "vector-star")
    var selectedNodeId by mutableStateOf<String?>(null)
    var tool by mutableStateOf(Tool.Select)
    var status by mutableStateOf("READY · SKIA ARTBOARD · 4,373 ICONS · FILAMENT 3D")
    var canUndo by mutableStateOf(false)
        private set
    var canRedo by mutableStateOf(false)
        private set

    /** The clock time the last autosave finished, so the shell can say when. */
    var savedAt by mutableStateOf<String?>(null)
        private set

    /** Locked layers refuse every transform; the id lives here so layers stay data. */
    var lockedIds by mutableStateOf<Set<String>>(emptySet())
        private set

    /** Names of the last actions, newest first, for the undo sheet. */
    var recentActions by mutableStateOf<List<String>>(emptyList())
        private set

    /** The lines the current drag is caught on, if any. */
    var guides by mutableStateOf<SnapGuides?>(null)
        private set

    /** A composition guide, not a document property: it is off on every launch. */
    var safeArea by mutableStateOf(false)

    /** Paint-mask drawing: when true a drag on the stage writes mask strokes. */
    var maskMode by mutableStateOf(false)
    /** True erases from the mask, false paints it back. */
    var maskErase by mutableStateOf(true)
    /** Brush size as a fraction of the layer's short side. */
    var maskBrushSize by mutableStateOf(0.12f)

    /** The control that made the last change, so the recorder can name it. */
    var action by mutableStateOf("OPEN")
        private set
    /** The thing the action changed, when it was not the selected layer. */
    var actionSubject by mutableStateOf<String?>(null)
        private set
    var actionId by mutableStateOf(0)
        private set

    val undoDepth: Int get() = undoStack.size
    val redoDepth: Int get() = redoStack.size

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

    /**
     * Leaves placement and 3D edit mode and returns to Select. This is the one visible
     * way out of every mode; it is wired to the DONE chips and the system back gesture,
     * so a half-finished add can never trap the reader in the engine.
     */
    fun settle() {
        val wasEditing = editingModelId != null
        val wasArmed = tool != Tool.Select || maskMode
        editingModelId = null
        maskMode = false
        tool = Tool.Select
        selectedNodeId = null
        status = when {
            wasEditing -> "3D EDIT CLOSED · ${selected?.name ?: "NOTHING SELECTED"}"
            wasArmed -> "SETTLED · ${selected?.name ?: "NOTHING SELECTED"}"
            else -> "READY · ${selected?.name ?: "NOTHING SELECTED"}"
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

    /** Tapping bare stage returns to Create, so the deck is never a one-time screen. */
    fun clearSelection() {
        selectedId = ""
        selectedNodeId = null
        editingModelId = null
        status = "NOTHING SELECTED · TAP CREATE OR A LAYER"
    }

    fun selectNode(id: String?) {
        selectedNodeId = id
    }

    fun layerAt(x: Float, y: Float): Layer? =
        layers.asReversed().firstOrNull { layer ->
            layer.visible && Geometry.contains(layer, x, y)
        }

    fun toggleVisibility(id: String) {
        // The layer list toggles rows that are not the selection, so the row travels
        // with the action; naming the selection sent the reader to the wrong layer.
        val target = layers.firstOrNull { it.id == id }
        act(if (target?.visible == true) "HIDE" else "SHOW", target?.name)
        commit { layers ->
            layers.map { layer -> if (layer.id == id) layer.withVisible(!layer.visible) else layer }
        }
    }

    fun restoreLocked(ids: Set<String>) {
        lockedIds = ids
    }

    /** Opens a stored document in place, with the history starting from it. */
    fun replaceAll(restored: DocumentCodec.Document) {
        layers = restored.layers
        selectedId = restored.selectedId ?: restored.layers.first().id
        zoom = restored.zoom
        lockedIds = restored.locked
        undoStack.clear()
        redoStack.clear()
        syncHistory()
        recentActions = emptyList()
        editingModelId = null
        selectedNodeId = null
        status = "OPENED · ${layers.size} LAYERS"
    }

    /** A fresh page, seeded like a first launch. */
    fun reset() {
        replaceAll(DocumentCodec.Document(seedLayers(), null, 1f, emptySet()))
        status = "NEW DOCUMENT"
    }

    fun isLocked(id: String): Boolean = id in lockedIds

    fun toggleLock(id: String) {
        val target = layers.firstOrNull { it.id == id } ?: return
        act(if (id in lockedIds) "UNLOCK" else "LOCK", target.name)
        lockedIds = if (id in lockedIds) lockedIds - id else lockedIds + id
        status = if (id in lockedIds) "LAYER LOCKED" else "LAYER UNLOCKED"
    }

    fun rename(id: String, name: String) {
        val clean = name.trim().take(28)
        if (clean.isBlank()) return
        act("NAME", clean)
        commit { layerList -> layerList.map { if (it.id == id) it.withName(clean) else it } }
        status = "RENAMED · $clean"
    }

    fun setAlign(align: TextAlign) {
        act("ALIGN")
        updateSelected { (it as? TextLayer)?.copy(align = align) ?: it }
    }

    fun setLineHeight(value: Float) {
        act("LEADING")
        updateSelected { (it as? TextLayer)?.copy(lineHeight = value.coerceIn(0.7f, 1.6f)) ?: it }
    }

    fun setFillHex(hex: String) {
        val clean = hex.removePrefix("#").trim()
        if (clean.length != 6) return
        val value = clean.toIntOrNull(16) ?: return
        setFill((0xFF shl 24) or value)
    }

    fun setImageFill(fill: Boolean) {
        act("PHOTO FIT")
        updateSelected { (it as? ImageLayer)?.copy(fill = fill) ?: it }
        status = if (fill) "PHOTO FILLS ITS FRAME" else "PHOTO FITS INSIDE ITS FRAME"
    }

    fun addImage(path: String) {
        act("PHOTO+")
        val id = newId("photo")
        val offset = (layers.count { it is ImageLayer } * 0.02f).coerceAtMost(0.08f)
        val layer = ImageLayer(
            id = id,
            name = "PHOTO / ${layers.count { it is ImageLayer } + 1}",
            x = 0.1f + offset,
            y = 0.14f + offset,
            w = 0.8f,
            h = squareFrameHeight(0.8f),
            path = path,
        )
        commit { it + layer }
        selectedId = id
        selectedNodeId = null
        tool = Tool.Select
        status = "PHOTO PLACED · DRAG, PINCH OR TYPE A PLACE"
    }

    /**
     * A move that catches on the page and on other layers. The threshold is in
     * artboard fractions, so it reads the same at every zoom.
     */
    fun moveSelectedSnapped(dx: Float, dy: Float, threshold: Float = 0.012f) {
        val current = selected ?: return
        if (isLocked(current.id)) return
        val rawX = current.x + dx
        val rawY = current.y + dy
        val others = layers.filter { it.id != current.id && it.visible }
        val verticals = listOf(0f, 0.5f, 1f) +
            others.flatMap { listOf(it.x, it.x + it.w / 2f, it.x + it.w) }
        val horizontals = listOf(0f, 0.5f, 1f) +
            others.flatMap { listOf(it.y, it.y + it.h / 2f, it.y + it.h) }

        var nextX = rawX
        var guideX: Float? = null
        listOf(rawX, rawX + current.w / 2f, rawX + current.w).forEach { candidate ->
            val target = verticals.minByOrNull { kotlin.math.abs(it - candidate) }
            if (target != null && kotlin.math.abs(target - candidate) <= threshold && guideX == null) {
                nextX = rawX + (target - candidate)
                guideX = target
            }
        }
        var nextY = rawY
        var guideY: Float? = null
        listOf(rawY, rawY + current.h / 2f, rawY + current.h).forEach { candidate ->
            val target = horizontals.minByOrNull { kotlin.math.abs(it - candidate) }
            if (target != null && kotlin.math.abs(target - candidate) <= threshold && guideY == null) {
                nextY = rawY + (target - candidate)
                guideY = target
            }
        }
        guides = SnapGuides(
            vertical = listOfNotNull(guideX),
            horizontal = listOfNotNull(guideY),
        )
        updateLayer(current.id) { it.withPosition(nextX, nextY) }
    }

    fun clearGuides() {
        guides = null
    }

    fun undoSteps(count: Int) {
        repeat(count.coerceIn(1, undoDepth)) { undo() }
    }

    fun beginGesture() {
        if (gestureStart == null) gestureStart = layers
    }

    fun endGesture() {
        guides = null
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

    fun addNode() {
        act("NODE+")
        commit { layers ->
            layers.map { layer ->
                if (layer.id == selectedId && layer is VectorLayer) {
                    layer.copy(network = layer.network.addNode())
                } else layer
            }
        }
    }

    fun deleteNode() {
        val nodeId = selectedNodeId ?: return
        act("NODE−")
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
        act("TYPE+")
        val id = newId("type")
        val layer = TextLayer(
            id = id,
            name = "TYPE / NEW",
            x = 0.18f,
            y = 0.16f,
            w = 0.54f,
            h = 0.16f,
            text = "NEW LINE",
            fontSize = 58f,
            lineHeight = 0.95f,
            color = 0xFF11231C.toInt(),
        )
        commit { it + layer }
        selectedId = id
        // An add is a finished action, not an armed tool: the new line is selected and
        // ready to move. Leaving Type armed made every later tap feel like another add.
        tool = Tool.Select
        status = "TYPE ADDED · DRAG TO PLACE"
    }

    fun addVector() {
        act("VECTOR+")
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
        tool = Tool.Select
        status = "VECTOR ADDED · 3 NODES · DRAG TO PLACE"
    }

    fun addSticker(preset: StickerPreset) {
        act("STICKER+")
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
        act("ICON+")
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
        act("3D+")
        val id = newId("model")
        val offset = (modelLayers.size * 0.05f).coerceAtMost(0.14f)
        val layer = ModelLayer(
            id = id,
            name = "3D / ${asset.label.uppercase()}",
            x = (0.18f + offset).coerceAtMost(0.58f),
            y = (0.18f + offset).coerceAtMost(0.58f),
            w = 0.34f,
            h = 0.32f,
            scaleToUnits = 0.92f,
            asset = asset,
        )
        commit { it + layer }
        selectedId = id
        tool = Tool.Select
        status = "3D ADDED · ${asset.label.uppercase()} · DRAG TO PLACE"
    }

    /** Steps a type layer through the three bundled Poppins faces. */
    fun setWeight(weight: Int) {
        val id = selectedId ?: return
        act("WEIGHT")
        val face = weight.coerceIn(400, 600)
        commit { layers -> layers.map { if (it.id == id && it is TextLayer) it.copy(weight = face) else it } }
        status = "TYPE FACE $face"
    }

    fun duplicateSelected() {
        val layer = selected ?: return
        act("DUP")
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
        act("DEL", selected?.name)
        commit { layerList -> layerList.filterNot { it.id == id } }
        if (editingModelId == id) editingModelId = null
        selectedId = layers.firstOrNull()?.id.orEmpty()
        selectedNodeId = null
        status = "LAYER DELETED"
    }

    fun reorderSelected(delta: Int) = moveLayer(selectedId, delta)

    /** Moves any row, because the layer list reorders in place and not only the selection. */
    fun moveLayer(id: String, delta: Int) {
        val index = layers.indexOfFirst { it.id == id }
        if (index < 0) return
        val target = (index + delta).coerceIn(0, layers.lastIndex)
        if (target == index) return
        act(if (delta > 0) "FRONT" else "BACK", layers[index].name)
        commit { layerList ->
            layerList.toMutableList().also {
                val moving = it.removeAt(index)
                it.add(target, moving)
            }
        }
        status = if (delta > 0) "LAYER MOVED FORWARD" else "LAYER MOVED BACK"
    }

    /** X, Y, W and H typed in artboard pixels, so a placement can be exact. */
    fun setBounds(x: Float, y: Float, w: Float, h: Float) {
        if (selected == null) return
        act("PLACE")
        val nextW = w.coerceIn(0.04f, 1.6f)
        val nextH = h.coerceIn(0.02f, 1.6f)
        updateSelected {
            it.withBounds(
                x.coerceIn(-0.5f, 1.2f),
                y.coerceIn(-0.5f, 1.2f),
                nextW,
                nextH,
            )
        }
    }

    /** One-pixel nudges from the placement row; a finger drag stays coarser. */
    fun nudge(dxPx: Float, dyPx: Float) {
        if (selected == null) return
        act("NUDGE")
        updateSelected {
            it.withPosition(it.x + dxPx / ArtboardPainter.W, it.y + dyPx / ArtboardPainter.H)
        }
    }

    /** Called by the shell once the debounced write lands. */
    fun markSaved(clock: String) {
        savedAt = clock
    }

    fun setText(value: String) {
        act("TEXT")
        updateSelected { (it as? TextLayer)?.copy(text = value) ?: it }
    }

    fun setFontSize(value: Float) {
        act("TYPE SIZE")
        updateSelected { (it as? TextLayer)?.copy(fontSize = value.coerceIn(18f, 180f)) ?: it }
    }

    fun setFill(color: Int) {
        act("FILL")
        commit { layerList ->
            layerList.map { layer ->
                if (layer.id != selectedId) layer
                else when (layer) {
                    is VectorLayer -> layer.copy(fill = color)
                    is TextLayer -> layer.copy(color = color)
                    is IconLayer -> layer.copy(tint = color)
                    is ModelLayer -> layer
                    is ImageLayer -> layer
                }
            }
        }
    }

    fun setStrokeWidth(value: Float) {
        act("STROKE")
        commit { layerList ->
            layerList.map { layer ->
                if (layer.id == selectedId && layer is VectorLayer) {
                    layer.copy(strokeWidth = value.coerceIn(0f, 18f))
                } else layer
            }
        }
    }

    fun setModelScale(value: Float) {
        act("3D SCALE")
        updateSelected { (it as? ModelLayer)?.copy(scaleToUnits = value.coerceIn(0.25f, 2f)) ?: it }
    }

    /** The inspector's uniform scale dial. Width is in artboard fractions. */
    fun setScaleToWidth(value: Float) {
        val layer = selected ?: return
        if (layer.w <= 0f) return
        act("SCALE")
        val target = value.coerceIn(0.04f, 1.2f)
        updateSelected { Geometry.scaleCentered(it, target / it.w) }
    }

    fun setModelAsset(asset: ModelAsset) {
        act("3D ASSET")
        updateSelected { layer ->
            (layer as? ModelLayer)?.let { model ->
                val auto = model.name == "3D / ${model.asset.label.uppercase()}" ||
                    model.name == "3D / ${model.asset.label.uppercase()} COPY"
                model.copy(
                    asset = asset,
                    name = if (auto) model.name.replace(model.asset.label.uppercase(), asset.label.uppercase()) else model.name,
                )
            } ?: layer
        }
    }

    fun setRotation(value: Float) {
        act("TURN")
        updateSelected { it.withRotation(value) }
    }

    fun centerSelected() {
        act("CENTER")
        updateSelected {
            it.withBounds((1f - it.w) / 2f, (1f - it.h) / 2f, it.w, it.h).withRotation(0f)
        }
    }

    fun toggleModelEdit(id: String) {
        val model = layers.firstOrNull { it.id == id } as? ModelLayer ?: return
        act("3D EDIT")
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

    fun setOpacity(value: Float) {
        act("OPACITY")
        updateSelected { it.withOpacity(value.coerceIn(0.1f, 1f)) }
    }

    // ------------------------------------------------------------------ paint

    /** The two layer kinds that carry a paint; icons keep their own tint rules. */
    private fun paintable(): Layer? = selected?.takeIf { it is VectorLayer || it is TextLayer }

    private fun paintOf(layer: Layer): PaintSpec? = when (layer) {
        is VectorLayer -> layer.paint
        is TextLayer -> layer.paint
        else -> null
    }

    fun setPaintKind(kind: PaintKind) {
        val layer = paintable() ?: return
        val base = when (layer) {
            is VectorLayer -> layer.fill
            is TextLayer -> layer.color
            else -> 0xFF0FBF92.toInt()
        }
        val spec: PaintSpec? = when (kind) {
            PaintKind.Solid -> null
            PaintKind.Linear, PaintKind.Radial, PaintKind.Sweep, PaintKind.Diamond ->
                defaultGradient(base, kind)
            PaintKind.Pattern -> PaintSpec.Pattern(
                kind = PatternKind.Checks,
                foreground = base,
                background = 0xFFF3EBDD.toInt(),
            )
            PaintKind.Image -> layers.filterIsInstance<ImageLayer>().firstOrNull()
                ?.let { PaintSpec.Image(it.path) }
        }
        if (kind == PaintKind.Image && spec == null) {
            status = "NO PHOTO IN THE DOCUMENT TO FILL FROM"
            return
        }
        act("PAINT ${kind.label}", layer.name)
        commit { list -> list.map { if (it.id == layer.id) it.withPaint(spec) else it } }
        status = "PAINT · ${spec?.label ?: "SOLID"}"
    }

    /** Chooses which photograph an image paint samples, and how it maps into the frame. */
    fun setPaintImage(path: String, fill: Boolean = true) {
        val layer = paintable() ?: return
        val photo = layers.filterIsInstance<ImageLayer>().firstOrNull { it.path == path } ?: return
        act("PAINT IMAGE · ${photo.name}", layer.name)
        updateSelected { it.withPaint(PaintSpec.Image(photo.path, fill)) }
        status = "PAINT · IMAGE / ${photo.name} / ${if (fill) "FILL" else "FIT"}"
    }

    fun setPaintImageFill(fill: Boolean) {
        val layer = paintable() ?: return
        val image = paintOf(layer) as? PaintSpec.Image ?: return
        act(if (fill) "PAINT IMAGE FILL" else "PAINT IMAGE FIT", layer.name)
        updateSelected { it.withPaint(image.copy(fill = fill)) }
        status = if (fill) "IMAGE PAINT · FILL THE FRAME" else "IMAGE PAINT · FIT INSIDE"
    }

    fun setPatternKind(kind: PatternKind) {
        val layer = paintable() ?: return
        val pattern = paintOf(layer) as? PaintSpec.Pattern ?: return
        act("PATTERN ${kind.label}", layer.name)
        updateSelected { it.withPaint(pattern.copy(kind = kind)) }
        status = "PATTERN · ${kind.label}"
    }

    fun setPatternScale(value: Float) {
        val layer = paintable() ?: return
        val pattern = paintOf(layer) as? PaintSpec.Pattern ?: return
        act("PATTERN SCALE")
        updateSelected { it.withPaint(pattern.copy(scale = value.coerceIn(0.25f, 4f))) }
    }

    fun setPaintStopOffset(index: Int, offset: Float) {
        act("STOP POSITION")
        updateSelected { layer ->
            layer.withGradient { gradient ->
                gradient.copy(stops = gradient.stops.mapIndexed { i, stop ->
                    if (i == index) stop.copy(offset = offset.coerceIn(0f, 1f)) else stop
                })
            }
        }
    }

    fun setPaintStopColor(index: Int, color: Int) {
        act("STOP COLOR")
        updateSelected { layer ->
            layer.withGradient { gradient ->
                gradient.copy(stops = gradient.stops.mapIndexed { i, stop ->
                    if (i == index) stop.copy(color = colorWithAlpha(color, colorAlpha(stop.color))) else stop
                })
            }
        }
    }

    fun setPaintStopAlpha(index: Int, alpha: Float) {
        act("STOP ALPHA")
        updateSelected { layer ->
            layer.withGradient { gradient ->
                gradient.copy(stops = gradient.stops.mapIndexed { i, stop ->
                    if (i == index) stop.copy(color = colorWithAlpha(stop.color, alpha)) else stop
                })
            }
        }
    }

    fun addPaintStop() {
        val layer = paintable() ?: return
        val gradient = paintOf(layer)?.gradient ?: return
        val ordered = gradient.stops.sortedBy { it.offset }
        var gapIndex = 0
        var gap = -1f
        for (index in 0 until ordered.size - 1) {
            val span = ordered[index + 1].offset - ordered[index].offset
            if (span > gap) {
                gap = span
                gapIndex = index
            }
        }
        val offset = (ordered[gapIndex].offset + ordered[gapIndex + 1].offset) / 2f
        val color = mixColor(ordered[gapIndex].color, ordered[gapIndex + 1].color, 0.5f)
        act("STOP+", layer.name)
        commit { list ->
            list.map { item ->
                if (item.id == layer.id) {
                    item.withPaint(gradient.copy(stops = gradient.stops + PaintStop(offset, color)))
                } else item
            }
        }
        status = "GRADIENT · ${gradient.stops.size + 1} STOPS"
    }

    fun removePaintStop(index: Int) {
        val layer = paintable() ?: return
        val gradient = paintOf(layer)?.gradient ?: return
        if (gradient.stops.size <= 2) {
            status = "A GRADIENT KEEPS AT LEAST TWO STOPS"
            return
        }
        act("STOP−", layer.name)
        commit { list ->
            list.map { item ->
                if (item.id == layer.id) {
                    item.withPaint(gradient.copy(
                        stops = gradient.stops.filterIndexed { i, _ -> i != index },
                    ))
                } else item
            }
        }
        status = "GRADIENT · ${gradient.stops.size - 1} STOPS"
    }

    fun movePaintStop(index: Int, delta: Int) {
        val layer = paintable() ?: return
        val gradient = paintOf(layer)?.gradient ?: return
        val target = (index + delta).coerceIn(0, gradient.stops.lastIndex)
        if (target == index) return
        act("STOP ORDER", layer.name)
        commit { list ->
            list.map { item ->
                if (item.id != layer.id) item
                else {
                    val reordered = gradient.stops.toMutableList()
                    reordered.add(target, reordered.removeAt(index))
                    item.withPaint(gradient.copy(stops = reordered))
                }
            }
        }
        status = "GRADIENT · STOP ${index + 1} → ${target + 1}"
    }

    // ------------------------------------------------------------------ blend

    fun setBlend(kind: BlendKind) {
        if (selected == null) return
        act("BLEND ${kind.label}", selected?.name)
        updateSelected { it.withStyle(it.style.copy(blend = kind)) }
        status = "BLEND · ${kind.label}"
    }

    fun cycleBlend(delta: Int) {
        val current = selected?.style?.blend ?: BlendKind.Normal
        val values = BlendKind.entries
        val next = Math.floorMod(values.indexOf(current) + delta, values.size)
        setBlend(values[next])
    }

    fun toggleClip() {
        val layer = selected ?: return
        act("CLIP", layer.name)
        updateSelected { it.withStyle(it.style.copy(clipToBelow = !it.style.clipToBelow)) }
        status = if (selected?.style?.clipToBelow == true) "CLIPPED TO THE LAYER BELOW" else "CLIP OFF"
    }

    // ------------------------------------------------------------------- mask

    fun toggleMaskMode() {
        maskMode = !maskMode
        if (maskMode) {
            tool = Tool.Select
            status = "MASK PAINT · DRAG ON THE STAGE · ${if (maskErase) "ERASE" else "RESTORE"}"
        } else {
            status = "MASK PAINT DONE"
        }
    }

    fun toggleMaskErase() {
        maskErase = !maskErase
        status = if (maskErase) "MASK BRUSH · ERASE" else "MASK BRUSH · RESTORE"
    }

    fun setMaskRadius(value: Float) {
        maskBrushSize = value.coerceIn(0.02f, 0.4f)
        act("MASK SIZE")
    }

    fun beginMaskStroke(x: Float, y: Float) {
        val layer = selected ?: return
        act("MASK PAINT", layer.name)
        val mask = layer.style.mask ?: LayerMask()
        val stroke = MaskStroke(listOf(MaskPoint(x, y)), radius = maskBrushSize, erase = maskErase)
        layers = layers.map { item ->
            if (item.id != layer.id) item
            else item.withStyle(item.style.copy(mask = mask.copy(enabled = true, strokes = mask.strokes + stroke)))
        }
    }

    fun extendMaskStroke(x: Float, y: Float) {
        val layer = selected ?: return
        val mask = layer.style.mask ?: return
        val last = mask.strokes.lastOrNull() ?: return
        val previous = last.points.lastOrNull()
        if (previous != null && abs(previous.x - x) + abs(previous.y - y) < 0.002f) return
        val strokes = mask.strokes.dropLast(1) + last.copy(points = last.points + MaskPoint(x, y))
        layers = layers.map { item ->
            if (item.id != layer.id) item
            else item.withStyle(item.style.copy(mask = mask.copy(strokes = strokes)))
        }
    }

    fun endMaskStroke() {
        status = "MASK · ${selected?.style?.mask?.strokes?.size ?: 0} STROKES"
    }

    fun clearMask() {
        val layer = selected ?: return
        val mask = layer.style.mask ?: return
        act("MASK CLEAR", layer.name)
        updateSelected { it.withStyle(it.style.copy(mask = mask.copy(strokes = emptyList()))) }
        status = "MASK CLEARED"
    }

    fun toggleMask() {
        val layer = selected ?: return
        val mask = layer.style.mask
        val next = mask?.copy(enabled = !mask.enabled) ?: LayerMask()
        act(if (mask == null) "MASK+" else "MASK", layer.name)
        updateSelected { it.withStyle(it.style.copy(mask = next)) }
        status = when {
            mask == null -> "PAINT MASK CREATED · PAINT TO CUT"
            next.enabled -> "PAINT MASK ON"
            else -> "PAINT MASK OFF"
        }
    }

    fun removeMask() {
        val layer = selected ?: return
        if (layer.style.mask == null) {
            status = "NO PAINT MASK TO REMOVE"
            return
        }
        act("MASK−", layer.name)
        updateSelected { it.withStyle(it.style.copy(mask = null)) }
        status = "PAINT MASK REMOVED"
    }

    // ---------------------------------------------------------------- effects

    private fun effect(name: String, transform: (LayerEffects) -> LayerEffects) {
        if (selected == null) return
        act(name)
        updateSelected { layer ->
            layer.withStyle(layer.style.copy(effects = transform(layer.style.effects.copy(enabled = true))))
        }
    }

    fun setExposure(value: Float) = effect("EXPOSURE") { it.copy(exposure = value.coerceIn(-2f, 2f)) }
    fun setContrast(value: Float) = effect("CONTRAST") { it.copy(contrast = value.coerceIn(-1f, 1f)) }
    fun setSaturation(value: Float) = effect("SATURATION") { it.copy(saturation = value.coerceIn(-1f, 1f)) }
    fun setShadows(value: Float) = effect("SHADOWS") { it.copy(shadows = value.coerceIn(-1f, 1f)) }
    fun setMidtones(value: Float) = effect("MIDTONES") { it.copy(midtones = value.coerceIn(-1f, 1f)) }
    fun setHighlights(value: Float) = effect("HIGHLIGHTS") { it.copy(highlights = value.coerceIn(-1f, 1f)) }
    fun setBlur(value: Float) = effect("BLUR") { it.copy(blur = value.coerceIn(0f, 24f)) }
    fun setGrain(value: Float) = effect("GRAIN") { it.copy(grain = value.coerceIn(0f, 1f)) }
    fun setHalftone(value: Float) = effect("HALFTONE") { it.copy(halftone = value.coerceIn(0f, 1f)) }

    private fun toggleEffectFlag(name: String, transform: (LayerEffects) -> LayerEffects) {
        val layer = selected ?: return
        act(name, layer.name)
        updateSelected {
            val effects = transform(it.style.effects).copy(enabled = true)
            it.withStyle(it.style.copy(effects = effects))
        }
    }

    fun toggleGrade() = toggleEffectFlag("GRADE") { it.copy(gradeEnabled = !it.gradeEnabled) }
    fun toggleBlur() = toggleEffectFlag("BLUR") { it.copy(blurEnabled = !it.blurEnabled) }
    fun toggleGrain() = toggleEffectFlag("GRAIN") { it.copy(grainEnabled = !it.grainEnabled) }
    fun toggleHalftone() = toggleEffectFlag("HALFTONE") { it.copy(halftoneEnabled = !it.halftoneEnabled) }

    fun toggleDuotone() {
        val layer = selected ?: return
        act("DUOTONE", layer.name)
        updateSelected {
            it.withStyle(it.style.copy(
                effects = it.style.effects.copy(enabled = true, duotone = !it.style.effects.duotone),
            ))
        }
        status = if (selected?.style?.effects?.duotone == true) "DUOTONE LUT ON" else "DUOTONE LUT OFF"
    }

    fun toggleEffects() {
        val layer = selected ?: return
        act("EFFECTS", layer.name)
        updateSelected {
            it.withStyle(it.style.copy(effects = it.style.effects.copy(enabled = !it.style.effects.enabled)))
        }
        status = if (selected?.style?.effects?.enabled == true) "EFFECTS ON" else "EFFECTS BYPASSED"
    }

    /** A recipe writes one coherent effect stack instead of six separate dials. */
    fun applyEffectsPreset(name: String, effects: LayerEffects) {
        val layer = selected ?: return
        act("FX PRESET $name", layer.name)
        updateSelected { it.withStyle(it.style.copy(effects = effects)) }
        status = "FX PRESET · $name"
    }

    fun undo() {
        val previous = undoStack.removeLastOrNull() ?: return
        act("UNDO")
        redoStack.addLast(layers)
        layers = previous
        ensureSelection()
        syncHistory()
        status = "UNDO · ${layers.size} LAYERS"
    }

    fun redo() {
        val next = redoStack.removeLastOrNull() ?: return
        act("REDO")
        undoStack.addLast(layers)
        layers = next
        ensureSelection()
        syncHistory()
        status = "REDO · ${layers.size} LAYERS"
    }

    fun updateLayer(id: String, transform: (Layer) -> Layer) {
        if (id in lockedIds) return
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

    private fun act(name: String, subject: String? = null) {
        action = name
        actionSubject = subject
        actionId++
        recentActions = (listOfNotNull(subject?.let { "$name · $it" } ?: name) + recentActions).take(12)
    }

    private fun newId(prefix: String) = "$prefix-${UUID.randomUUID().toString().take(6)}"
}

fun Layer.withStyle(value: LayerStyle): Layer = when (this) {
    is VectorLayer -> copy(style = value)
    is TextLayer -> copy(style = value)
    is IconLayer -> copy(style = value)
    is ImageLayer -> copy(style = value)
    is ModelLayer -> copy(style = value)
}

fun Layer.withPaint(value: PaintSpec?): Layer = when (this) {
    is VectorLayer -> copy(paint = value)
    is TextLayer -> copy(paint = value)
    else -> this
}

/** Applies a gradient change, leaving a solid/image/pattern paint untouched. */
fun Layer.withGradient(transform: (PaintSpec.Gradient) -> PaintSpec.Gradient): Layer {
    val gradient = ((this as? VectorLayer)?.paint ?: (this as? TextLayer)?.paint)?.gradient ?: return this
    return withPaint(transform(gradient))
}

private fun Layer.withName(value: String): Layer = when (this) {
    is VectorLayer -> copy(name = value)
    is TextLayer -> copy(name = value)
    is IconLayer -> copy(name = value)
    is ModelLayer -> copy(name = value)
    is ImageLayer -> copy(name = value)
}

private fun Layer.withVisible(value: Boolean): Layer = when (this) {
    is VectorLayer -> copy(visible = value)
    is ImageLayer -> copy(visible = value)
    is TextLayer -> copy(visible = value)
    is IconLayer -> copy(visible = value)
    is ModelLayer -> copy(visible = value)
}

private fun Layer.withPosition(x: Float, y: Float): Layer = when (this) {
    is VectorLayer -> copy(x = x, y = y)
    is ImageLayer -> copy(x = x, y = y)
    is TextLayer -> copy(x = x, y = y)
    is IconLayer -> copy(x = x, y = y)
    is ModelLayer -> copy(x = x, y = y)
}

private fun Layer.withOpacity(value: Float): Layer = when (this) {
    is VectorLayer -> copy(opacity = value)
    is ImageLayer -> copy(opacity = value)
    is TextLayer -> copy(opacity = value)
    is IconLayer -> copy(opacity = value)
    is ModelLayer -> copy(opacity = value)
}

private fun Layer.duplicate(): Layer {
    val copyId = "${id.substringBeforeLast('-')}-${UUID.randomUUID().toString().take(6)}"
    val nx = min(0.9f, x + 0.04f)
    val ny = min(0.9f, y + 0.04f)
    return when (this) {
        is VectorLayer -> copy(id = copyId, name = "$name COPY", x = nx, y = ny)
        is ImageLayer -> copy(id = copyId, name = "$name COPY", x = nx, y = ny)
        is TextLayer -> copy(id = copyId, name = "$name COPY", x = nx, y = ny)
        is IconLayer -> copy(id = copyId, name = "$name COPY", x = nx, y = ny)
        is ModelLayer -> {
            val copyW = min(w, 0.30f)
            val copyH = min(h, 0.30f)
            val copyX = if (x > 0.5f) {
                (x - w - 0.04f).coerceIn(0.02f, 0.6f)
            } else {
                (x + w + 0.04f).coerceIn(0.02f, 0.6f)
            }
            copy(
                id = copyId,
                name = "$name COPY",
                x = copyX,
                y = min(0.9f, y + 0.02f),
                w = copyW,
                h = copyH,
            )
        }
    }
}
