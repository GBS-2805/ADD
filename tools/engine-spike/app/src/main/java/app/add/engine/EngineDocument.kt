package app.add.engine

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.UUID
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min


class EngineDocument(
    initial: List<Layer>? = null,
    initialTexture: TextureSpec? = null,
    initialTextureWarning: String? = null,
    initialInk: List<TextureInk> = emptyList(),
    initialField: FieldSpec? = null,
) {
    var layers by mutableStateOf(initial ?: seedLayers())
        internal set

    var selectedId by mutableStateOf(initial?.firstOrNull()?.id ?: "vector-star")
    var selectedNodeId by mutableStateOf<String?>(null)

    /** Everything selected at the current editing level; [selectedId] is the primary. */
    var selectedIds by mutableStateOf(setOf(selectedId))
        internal set
    /** The group whose children the stage edits; null means the artboard level. */
    var editingGroupId by mutableStateOf<String?>(null)
        internal set

    /** Parent-local motion paths, keyed by layer. A group carries them by matrix. */
    var motion by mutableStateOf<Map<String, MotionPath>>(emptyMap())
        internal set

    /** The one artboard-level material; null means clean paper. */
    var texture by mutableStateOf(initialTexture)
        private set
    /** A saved texture that could not be regenerated, reported without blocking the page. */
    var textureWarning by mutableStateOf(initialTextureWarning)
        private set

    /** An optional flow-field background; layers always paint above it. */
    var backgroundField by mutableStateOf(initialField)
        private set

    /** Seeded material strokes, kept as recipes and normalized points, never bitmaps. */
    var ink by mutableStateOf(initialInk)
        private set
    var inkMode by mutableStateOf(false)
    var inkFamily by mutableStateOf(initialTexture?.family ?: TextureFamily.Brick)
        private set
    var inkSeed by mutableStateOf(initialTexture?.seed ?: TexturePresets.seedFor(TextureFamily.Brick))
        private set
    var inkWidth by mutableStateOf(0.075f)
        private set
    private var inkGestureStart: EditorSnapshot? = null

    /** A preview only: the painter is displaced while this layer follows its path. */
    var playingLayerId by mutableStateOf<String?>(null)
        internal set
    var motionProgress by mutableStateOf(0f)
        internal set
    var tool by mutableStateOf(Tool.Select)
    var status by mutableStateOf("READY · SKIA ARTBOARD · 4,373 ICONS · FILAMENT 3D")
    var canUndo by mutableStateOf(false)
        private set
    var canRedo by mutableStateOf(false)
        private set

    /** The clock time the last autosave finished, so the shell can say when. */
    var savedAt by mutableStateOf<String?>(null)
        private set
    /** Visible save state: SAVING…, SAVED hh:mm, or SAVE FAILED. */
    var saveState by mutableStateOf("")
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
        internal set

    /** Viewport zoom, 1.0 = fit. View state, but the recorder needs to see it. */
    var zoom by mutableStateOf(1f)

    private val undoStack = ArrayDeque<EditorSnapshot>()
    private val redoStack = ArrayDeque<EditorSnapshot>()
    private var gestureStart: EditorSnapshot? = null
    private var textEditAt = 0L

    val selected: Layer? get() = layers.flattenTree().firstOrNull { it.id == selectedId }
    val selectedVector: VectorLayer? get() = selected as? VectorLayer
    val selectedText: TextLayer? get() = selected as? TextLayer
    val selectedIcon: IconLayer? get() = selected as? IconLayer
    val selectedModel: ModelLayer? get() = selected as? ModelLayer
    val editingModel: ModelLayer? get() = layers.firstOrNull { it.id == editingModelId } as? ModelLayer
    val modelLayers: List<ModelLayer> get() = layers.filterIsInstance<ModelLayer>()
    val modelLayer: ModelLayer? get() = modelLayers.firstOrNull { it.visible }

    fun chooseTool(value: Tool) {
        if (value != Tool.Select) inkMode = false
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
        val wasArmed = tool != Tool.Select || maskMode || inkMode
        editingModelId = null
        maskMode = false
        inkMode = false
        tool = Tool.Select
        selectedNodeId = null
        status = when {
            wasEditing -> "3D EDIT CLOSED · ${selected?.name ?: "NOTHING SELECTED"}"
            wasArmed -> "SETTLED · ${selected?.name ?: "NOTHING SELECTED"}"
            else -> "READY · ${selected?.name ?: "NOTHING SELECTED"}"
        }
    }

    fun selectNode(id: String?) {
        selectedNodeId = id
    }

    /**
     * Topmost visible layer under the pointer at the current editing level. Inside a
     * group the pointer is inverse-mapped into that group's space first, so a turned
     * or skewed group resolves its children rather than their axis-aligned bounds.
     */
    fun layerAt(x: Float, y: Float): Layer? {
        val scope = editingGroupId?.let { layers.findLayer(it) as? GroupLayer }
        if (scope == null) return hitIn(layers, x, y)
        val (lx, ly) = scope.localToWorld.inverse()?.map(x, y) ?: return null
        return hitIn(scope.children, lx, ly)
    }

    private fun hitIn(list: List<Layer>, x: Float, y: Float): Layer? =
        list.asReversed().firstOrNull { layer ->
            layer.visible && Geometry.contains(layer, x, y)
        }

    fun toggleVisibility(id: String) {
        // The layer list toggles rows that are not the selection, so the row travels
        // with the action; naming the selection sent the reader to the wrong layer.
        val target = layers.findLayer(id) ?: return
        act(if (target.visible) "HIDE" else "SHOW", target.name)
        commit { layerList -> layerList.mapLayer(id) { layer -> layer.withVisible(!layer.visible) } }
    }

    fun restoreLocked(ids: Set<String>) {
        lockedIds = ids
    }

    /** Applies a family at its catalogue seed, which is the inspector's one-tap path. */
    fun applyTexture(family: TextureFamily, seed: Int = TexturePresets.seedFor(family)) {
        commitTexture(TextureSpec.create(family, seed))
    }

    /** Stores a full recipe and one undo step; null removes the artboard material. */
    fun commitTexture(next: TextureSpec?) {
        val clean = next?.let {
            it.copy(
                seed = it.seed,
                opacity = if (it.opacity.isFinite()) it.opacity.coerceIn(0.02f, 1f) else it.family.defaultOpacity,
                scale = if (it.scale.isFinite()) it.scale.coerceIn(0.25f, 4f) else 1f,
            )
        }
        if (clean == texture) return
        val before = snapshot()
        texture = clean
        textureWarning = null
        action = if (clean == null) "TEXTURE OFF" else "TEXTURE"
        actionSubject = clean?.family?.label
        actionId++
        record(before)
        syncHistory()
        status = if (clean == null) {
            "ARTBOARD TEXTURE REMOVED"
        } else {
            "TEXTURE · ${clean.family.label} · SEED ${clean.seed}"
        }
    }

    fun setTextureSeed(seed: Int) {
        texture?.let { commitTexture(it.copy(seed = seed)) }
    }

    fun setTextureOpacity(value: Float) {
        texture?.let { commitTexture(it.copy(opacity = value)) }
    }

    fun setTextureScale(value: Float) {
        texture?.let { commitTexture(it.copy(scale = value)) }
    }

    fun removeTexture() = commitTexture(null)

    // ------------------------------------------------------------- material ink

    /**
     * Arms a seeded material brush. The brush writes new strokes into the document;
     * it does not alter or flatten any existing layer.
     */
    fun toggleInkMode() {
        inkMode = !inkMode
        if (inkMode) {
            maskMode = false
            tool = Tool.Select
            inkFamily = texture?.family ?: inkFamily
            inkSeed = texture?.seed ?: TexturePresets.seedFor(inkFamily)
            TextureInkRenderer.prewarm(
                TextureInk(
                    id = "prewarm",
                    recipe = TextureSpec(inkFamily, inkSeed, opacity = 0.72f),
                    width = inkWidth,
                    points = listOf(InkPoint(0.5f, 0.5f)),
                ),
            )
            status = "MATERIAL INK · ${inkFamily.label} · DRAG ON THE STAGE"
        } else {
            status = "MATERIAL INK OFF · ${ink.size} STROKES KEPT"
        }
    }

    fun chooseInkFamily(family: TextureFamily) {
        inkFamily = family
        inkSeed = TexturePresets.seedFor(family)
        TextureInkRenderer.prewarm(
            TextureInk(
                id = "prewarm",
                recipe = TextureSpec(family, inkSeed, opacity = 0.72f),
                width = inkWidth,
                points = listOf(InkPoint(0.5f, 0.5f)),
            ),
        )
        status = "MATERIAL INK · ${family.label} · SEED $inkSeed"
    }

    fun setInkBrushWidth(value: Float) {
        inkWidth = if (value.isFinite()) value.coerceIn(0.012f, 0.25f) else 0.075f
    }

    fun beginInkStroke(x: Float, y: Float) {
        if (!inkMode) return
        inkGestureStart = snapshot()
        ink = ink + TextureInk.create(
            id = newId("ink"),
            family = inkFamily,
            seed = inkSeed,
            width = inkWidth,
            point = InkPoint(x, y),
        )
        action = "MATERIAL INK"
        actionSubject = inkFamily.label
        actionId++
        status = "MATERIAL INK · DRAWING ${inkFamily.label}"
    }

    fun extendInkStroke(x: Float, y: Float) {
        val last = ink.lastOrNull() ?: return
        val previous = last.points.lastOrNull()
        if (previous != null && abs(previous.x - x) + abs(previous.y - y) < 0.003f) return
        if (last.points.size >= TextureInk.MAX_POINTS) return
        ink = ink.dropLast(1) + last.copy(points = last.points + InkPoint(x, y))
    }

    fun endInkStroke() {
        val before = inkGestureStart ?: return
        inkGestureStart = null
        val current = ink.lastOrNull()
        if (current == null || current.points.isEmpty()) {
            ink = before.ink
            return
        }
        if (ink != before.ink) {
            record(before)
            recentActions = (listOf("MATERIAL INK · ${current.recipe.family.label}") + recentActions).take(12)
            syncHistory()
        }
        status = "MATERIAL INK · ${ink.size} STROKE${if (ink.size == 1) "" else "S"} · ${current.recipe.family.label}"
    }

    fun removeLastInk() {
        if (ink.isEmpty()) return
        val before = snapshot()
        ink = ink.dropLast(1)
        action = "INK REMOVE"
        actionId++
        record(before)
        syncHistory()
        status = "MATERIAL INK · ${ink.size} STROKE${if (ink.size == 1) "" else "S"}"
    }

    fun clearInk() {
        if (ink.isEmpty()) return
        val before = snapshot()
        ink = emptyList()
        action = "INK CLEAR"
        actionId++
        record(before)
        syncHistory()
        status = "MATERIAL INK CLEARED"
    }

    /** Opens a stored document in place, with the history starting from it. */
    fun replaceAll(restored: DocumentCodec.Document) {
        layers = restored.layers
        selectedId = restored.selectedId ?: restored.layers.first().id
        selectedIds = setOf(selectedId).filter { it.isNotBlank() }.toSet()
        editingGroupId = restored.editingGroupId?.takeIf { layers.findLayer(it) is GroupLayer }
        zoom = restored.zoom
        lockedIds = restored.locked
        texture = restored.texture
        textureWarning = restored.textureWarning
        backgroundField = restored.backgroundField
        ink = restored.ink
        inkMode = false
        motion = restored.motion.filterKeys { layers.findLayer(it) != null }
        playingLayerId = null
        motionProgress = 0f
        undoStack.clear()
        redoStack.clear()
        gestureStart = null
        syncHistory()
        recentActions = emptyList()
        editingModelId = null
        selectedNodeId = null
        status = restored.textureWarning?.let { "OPENED · $it" } ?: "OPENED · ${layers.size} LAYERS"
    }

    /** A fresh page, seeded like a first launch. */
    fun reset() {
        replaceAll(DocumentCodec.Document(seedLayers(), null, 1f, emptySet()))
        status = "NEW DOCUMENT"
    }

    fun isLocked(id: String): Boolean = id in lockedIds

    fun toggleLock(id: String) {
        val target = layers.findLayer(id) ?: return
        act(if (id in lockedIds) "UNLOCK" else "LOCK", target.name)
        lockedIds = if (id in lockedIds) lockedIds - id else lockedIds + id
        status = if (id in lockedIds) "LAYER LOCKED" else "LAYER UNLOCKED"
    }

    fun rename(id: String, name: String) {
        val clean = name.trim().take(28)
        if (clean.isBlank()) return
        act("NAME", clean)
        commit { layerList -> layerList.mapLayer(id) { it.withName(clean) } }
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

    fun setFontFamily(family: String) {
        val clean = family.trim().ifBlank { "Poppins" }
        act("FONT", clean)
        updateSelected { (it as? TextLayer)?.copy(fontFamily = clean) ?: it }
        status = "TYPE · ${clean.uppercase()}"
    }

    fun setTracking(value: Float) {
        act("TRACKING")
        updateSelected { (it as? TextLayer)?.copy(tracking = value.coerceIn(-0.04f, 0.3f)) ?: it }
    }

    fun setTextCase(value: TextCase) {
        act("CASE", value.name)
        updateSelected { (it as? TextLayer)?.copy(textCase = value) ?: it }
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

    /**
     * Stores a source-space crop. The inspector wraps a drag in beginGesture/endGesture,
     * so one slider movement is one undo step; this setter itself does not push history,
     * matching the other transform setters.
     */
    fun setImageCrop(layerId: String, crop: ImageCrop) {
        val target = layers.findLayer(layerId) as? ImageLayer ?: return
        val clean = crop.normalized()
        if (clean == target.crop) return
        act("CROP", target.name)
        updateLayer(layerId) { (it as? ImageLayer)?.copy(crop = clean) ?: it }
        status = "CROP · ${(clean.w * 100).toInt()} × ${(clean.h * 100).toInt()}%"
    }

    fun resetImageCrop(layerId: String) {
        val target = layers.findLayer(layerId) as? ImageLayer ?: return
        if (target.crop == ImageCrop()) return
        act("CROP RESET", target.name)
        commit { list ->
            list.mapLayer(layerId) { layer ->
                (layer as? ImageLayer)?.copy(crop = ImageCrop()) ?: layer
            }
        }
        status = "CROP RESET · FULL PHOTO"
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
        selectedIds = setOf(id)
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
        val (pdx, pdy) = parentDelta(dx, dy)
        val rawX = current.x + pdx
        val rawY = current.y + pdy
        // Snap against siblings at this editing level, never against artboard layers
        // while the pointer is inside a group.
        val scope = editingGroupId?.let { layers.findLayer(it) as? GroupLayer }?.children ?: layers
        val others = scope.filter { it.id != current.id && it.visible }
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
        stopMotion()
        if (gestureStart == null) gestureStart = snapshot()
    }

    fun endGesture() {
        guides = null
        val before = gestureStart ?: return
        gestureStart = null
        val after = snapshot()
        // A tap that only selects leaves no history entry and keeps redo alive.
        if (before.layers != after.layers || before.motion != after.motion ||
            before.backgroundField != after.backgroundField
        ) {
            record(before)
            syncHistory()
            status = "EDITED · UNDO READY"
        }
    }

    fun moveSelected(dx: Float, dy: Float) {
        val current = selected ?: return
        val (pdx, pdy) = parentDelta(dx, dy)
        val nextX = (current.x + pdx).coerceIn(-0.45f, 1.0f)
        val nextY = (current.y + pdy).coerceIn(-0.45f, 1.0f)
        updateLayer(current.id) { it.withPosition(nextX, nextY) }
    }

    /**
     * A world-space drag becomes a parent-space delta. Inside a rotated or scaled
     * group the pointer moves in artboard space, while the child is stored in the
     * group's space; without this the child runs off at the group's angle.
     */
    private fun parentDelta(dx: Float, dy: Float): Pair<Float, Float> {
        val parent = layers.parentGroup(selectedId) ?: return dx to dy
        val inverse = parent.localToWorld.inverse() ?: return dx to dy
        return inverse.mapVector(dx, dy)
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
    fun markSaving() {
        saveState = "SAVING…"
    }

    fun markSaved(clock: String) {
        savedAt = clock
        saveState = "SAVED $clock"
    }

    fun markSaveFailed() {
        saveState = "SAVE FAILED"
        status = "SAVE FAILED · the previous draft is untouched"
    }

    fun setText(value: String) {
        recordTextEdit("TEXT")
        act("TEXT")
        updateSelected { (it as? TextLayer)?.copy(text = value) ?: it }
    }

    fun setFontSize(value: Float) {
        recordTextEdit("TYPE SIZE")
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
                    is GroupLayer -> layer
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

    fun resetModelFrame() {
        act("RESET FRAME")
        updateSelected { (it as? ModelLayer)?.copy(scaleToUnits = 0.92f) ?: it }
        status = "3D FRAME RESET"
    }

    fun resetModelTurn() {
        act("RESET TURN")
        updateSelected { (it as? ModelLayer)?.copy(rotation = 0f) ?: it }
        status = "3D TURN RESET"
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

    /** The field currently shown for a target, or null when that target has none. */
    fun fieldFor(target: FieldTarget): FieldSpec? {
        return when (target) {
            FieldTarget.Background -> backgroundField
            FieldTarget.Layer -> (paintOf(selected ?: return null) as? PaintSpec.Field)?.field
        }
    }

    fun fieldTargetName(target: FieldTarget): String = when (target) {
        FieldTarget.Background -> "ARTBOARD"
        FieldTarget.Layer -> selected?.name ?: "NO LAYER"
    }

    /**
     * Applies a complete field recipe as one undo step. The inspector's sliders call
     * [updateField] between beginGesture/endGesture so a drag is still one step.
     */
    fun setFieldPreset(target: FieldTarget, preset: FieldSpec) {
        if (target == FieldTarget.Layer && paintable() == null) {
            status = "PICK A TYPE OR VECTOR LAYER FOR A FIELD FILL"
            return
        }
        beginGesture()
        updateField(target) { preset.normalized() }
        endGesture()
        status = "FIELD · ${fieldTargetName(target)} · ${preset.colors.size} COLOURS"
    }

    fun clearField(target: FieldTarget) {
        if (target == FieldTarget.Background) {
            if (backgroundField == null) return
            beginGesture()
            backgroundField = null
            action = "FIELD OFF"
            actionId++
            endGesture()
            status = "ARTBOARD FIELD REMOVED"
        } else {
            if (fieldFor(FieldTarget.Layer) == null) return
            beginGesture()
            updateSelected { it.withPaint(null) }
            action = "FIELD OFF"
            actionId++
            endGesture()
            status = "LAYER FIELD REMOVED"
        }
    }

    /** Mutates the field without pushing history; the caller owns the gesture. */
    fun updateField(target: FieldTarget, transform: (FieldSpec) -> FieldSpec) {
        when (target) {
            FieldTarget.Background -> {
                val current = backgroundField ?: defaultField()
                val next = transform(current).normalized()
                if (next != current || backgroundField == null) {
                    backgroundField = next
                    action = "FIELD"
                    actionSubject = "ARTBOARD"
                    actionId++
                }
            }
            FieldTarget.Layer -> {
                val layer = paintable() ?: return
                val current = (paintOf(layer) as? PaintSpec.Field)?.field ?: defaultField()
                val next = transform(current).normalized()
                if (next != current || paintOf(layer) !is PaintSpec.Field) {
                    updateSelected { it.withPaint(PaintSpec.Field(next)) }
                    action = "FIELD"
                    actionSubject = layer.name
                    actionId++
                }
            }
        }
    }

    fun addFieldColor(target: FieldTarget) {
        val current = fieldFor(target) ?: defaultField()
        if (current.colors.size >= FieldSpec.MAX_FIELD_COLORS) {
            status = "A FIELD KEEPS AT MOST ${FieldSpec.MAX_FIELD_COLORS} COLOURS"
            return
        }
        beginGesture()
        updateField(target) { it.addColor(defaultFieldColors()[current.colors.size % defaultFieldColors().size].color) }
        endGesture()
        status = "FIELD · ${fieldFor(target)?.colors?.size ?: 0} COLOURS"
    }

    fun removeFieldColor(target: FieldTarget, index: Int) {
        val current = fieldFor(target) ?: return
        if (current.colors.size <= 2) {
            status = "A FIELD KEEPS AT LEAST TWO COLOURS"
            return
        }
        beginGesture()
        updateField(target) { it.removeColor(index) }
        endGesture()
        status = "FIELD · ${fieldFor(target)?.colors?.size ?: 0} COLOURS"
    }

    fun setFieldColor(target: FieldTarget, index: Int, color: Int) {
        updateField(target) { it.withSpot(index) { spot -> spot.copy(color = color) } }
    }

    fun setFieldSpot(target: FieldTarget, index: Int, x: Float, y: Float) {
        updateField(target) { it.withSpot(index) { spot -> spot.copy(x = x, y = y) } }
    }

    fun setFieldBand(target: FieldTarget, index: Int, band: Float) {
        updateField(target) { it.withSpot(index) { spot -> spot.copy(band = band) } }
    }

    fun setFieldScale(target: FieldTarget, value: Float) = updateField(target) { it.copy(scale = value) }
    fun setFieldDistortion(target: FieldTarget, value: Float) = updateField(target) { it.copy(distortion = value) }
    fun setFieldSwirl(target: FieldTarget, value: Float) = updateField(target) { it.copy(swirl = value) }
    fun setFieldSpeed(target: FieldTarget, value: Float) = updateField(target) { it.copy(speed = value) }
    fun setFieldSoften(target: FieldTarget, value: Float) = updateField(target) { it.copy(soften = value) }
    fun setFieldNoise(target: FieldTarget, value: Float) = updateField(target) { it.copy(noise = value) }
    fun setFieldAnimated(target: FieldTarget, value: Boolean) = updateField(target) { it.copy(animated = value) }

    fun setPaintKind(kind: PaintKind) {
        val layer = paintable() ?: return
        val base = when (layer) {
            is VectorLayer -> layer.fill
            is TextLayer -> layer.color
            else -> EngineEdition.PlumArgb
        }
        val spec: PaintSpec? = when (kind) {
            PaintKind.Solid -> null
            PaintKind.Linear, PaintKind.Radial, PaintKind.Sweep, PaintKind.Diamond ->
                defaultGradient(base, kind)
            PaintKind.Pattern -> PaintSpec.Pattern(
                kind = PatternKind.Checks,
                foreground = base,
                background = EngineEdition.PaperArgb,
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
        redoStack.addLast(snapshot())
        restore(previous)
        ensureSelection()
        syncHistory()
        status = "UNDO · ${layers.size} LAYERS"
    }

    fun redo() {
        val next = redoStack.removeLastOrNull() ?: return
        act("REDO")
        undoStack.addLast(snapshot())
        while (undoStack.size > HISTORY_LIMIT) undoStack.removeFirst()
        restore(next)
        ensureSelection()
        syncHistory()
        status = "REDO · ${layers.size} LAYERS"
    }

    fun updateLayer(id: String, transform: (Layer) -> Layer) {
        if (id in lockedIds) return
        stopMotion()
        val before = layers.findLayer(id) ?: return
        val next = layers.mapLayer(id, transform)
        val after = next.findLayer(id) ?: return
        val dx = after.centerX - before.centerX
        val dy = after.centerY - before.centerY
        if ((dx != 0f || dy != 0f) && motion.containsKey(id)) {
            // The path lives in the layer's own parent space; a centre change there
            // moves all four points by one delta. A group transform never lands here,
            // because it does not rewrite the child's coordinates.
            motion = motion + (id to clampToParent(id, motion.getValue(id).translated(dx, dy)))
        }
        layers = next
    }

    fun updateSelected(transform: (Layer) -> Layer) {
        updateLayer(selectedId, transform)
        val vector = selectedVector
        if (vector != null && vector.network.point(selectedNodeId ?: "") == null) {
            selectedNodeId = null
        }
    }

    internal fun commit(transform: (List<Layer>) -> List<Layer>) {
        val before = snapshot()
        val next = transform(layers)
        if (next == layers) return
        layers = next
        record(before)
        ensureSelection()
        syncHistory()
    }

    internal fun snapshot(): EditorSnapshot = EditorSnapshot(
        layers = layers,
        motion = motion,
        selectedIds = selectedIds,
        selectedId = selectedId.ifBlank { null },
        editingGroupId = editingGroupId,
        zoom = zoom,
        locked = lockedIds,
        texture = texture,
        ink = ink,
        backgroundField = backgroundField,
    )

    private fun restore(value: EditorSnapshot) {
        layers = value.layers
        motion = value.motion
        selectedIds = value.selectedIds.filter { layers.findLayer(it) != null }.toSet()
        selectedId = value.selectedId?.takeIf { layers.findLayer(it) != null }
            ?: selectedIds.lastOrNull().orEmpty()
        editingGroupId = value.editingGroupId?.takeIf { layers.findLayer(it) is GroupLayer }
        zoom = value.zoom
        lockedIds = value.locked
        texture = value.texture
        ink = value.ink
        backgroundField = value.backgroundField
        playingLayerId = null
        motionProgress = 0f
    }

    internal fun record(before: EditorSnapshot) {
        undoStack.addLast(before)
        while (undoStack.size > HISTORY_LIMIT) undoStack.removeFirst()
        redoStack.clear()
    }

    /** A burst of typing is one undo step, not one entry per keystroke. */
    private fun recordTextEdit(name: String) {
        val now = EngineClock.nowMillis()
        if (action == name && now - textEditAt <= TEXT_DEBOUNCE_MS) {
            textEditAt = now
            return
        }
        record(snapshot())
        syncHistory()
        textEditAt = now
    }

    private fun ensureSelection() {
        val all = layers.flattenTree()
        val ids = all.map { it.id }.toSet()
        selectedIds = selectedIds.filter { it in ids }.toSet()
        // A missing layer clears the selection; it must not silently steal the first
        // layer, which made a delete look like it had selected something else.
        if (selectedId !in ids) selectedId = selectedIds.lastOrNull().orEmpty()
        if (selectedId.isNotBlank() && selectedId !in selectedIds) selectedIds = selectedIds + selectedId
        if (selectedIds.isEmpty()) selectedId = ""
        if (all.none { it.id == editingModelId }) editingModelId = null
        if (editingGroupId != null && layers.findLayer(editingGroupId!!) !is GroupLayer) {
            editingGroupId = null
        }
        pruneMotion()
    }

    internal fun syncHistory() {
        canUndo = undoStack.isNotEmpty()
        canRedo = redoStack.isNotEmpty()
    }

    internal fun act(name: String, subject: String? = null) {
        action = name
        actionSubject = subject
        actionId++
        // Undo is a navigation action, not a new edit. Repeating it should not fill
        // the history sheet with five identical UNDO rows.
        if (name != "UNDO" && name != "REDO") {
            recentActions = (listOfNotNull(subject?.let { "$name · $it" } ?: name) + recentActions).take(12)
        }
    }

    internal fun newId(prefix: String) = "$prefix-${UUID.randomUUID().toString().take(6)}"
}

fun Layer.withStyle(value: LayerStyle): Layer = when (this) {
    is VectorLayer -> copy(style = value)
    is TextLayer -> copy(style = value)
    is IconLayer -> copy(style = value)
    is ImageLayer -> copy(style = value)
    is ModelLayer -> copy(style = value)
    is GroupLayer -> copy(style = value)
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
    is GroupLayer -> copy(name = value)
}

private fun Layer.withVisible(value: Boolean): Layer = when (this) {
    is VectorLayer -> copy(visible = value)
    is ImageLayer -> copy(visible = value)
    is TextLayer -> copy(visible = value)
    is IconLayer -> copy(visible = value)
    is ModelLayer -> copy(visible = value)
    is GroupLayer -> copy(visible = value)
}

internal fun Layer.withPosition(x: Float, y: Float): Layer = when (this) {
    is VectorLayer -> copy(x = x, y = y)
    is ImageLayer -> copy(x = x, y = y)
    is TextLayer -> copy(x = x, y = y)
    is IconLayer -> copy(x = x, y = y)
    is ModelLayer -> copy(x = x, y = y)
    is GroupLayer -> copy(x = x, y = y)
}

private fun Layer.withOpacity(value: Float): Layer = when (this) {
    is VectorLayer -> copy(opacity = value)
    is ImageLayer -> copy(opacity = value)
    is TextLayer -> copy(opacity = value)
    is IconLayer -> copy(opacity = value)
    is ModelLayer -> copy(opacity = value)
    is GroupLayer -> copy(opacity = value)
}

internal fun Layer.duplicate(): Layer {
    val copyId = "${id.substringBeforeLast('-')}-${UUID.randomUUID().toString().take(6)}"
    val nx = min(0.9f, x + 0.04f)
    val ny = min(0.9f, y + 0.04f)
    return when (this) {
        is VectorLayer -> copy(id = copyId, name = "$name COPY", x = nx, y = ny)
        is ImageLayer -> copy(id = copyId, name = "$name COPY", x = nx, y = ny)
        is TextLayer -> copy(id = copyId, name = "$name COPY", x = nx, y = ny)
        is IconLayer -> copy(id = copyId, name = "$name COPY", x = nx, y = ny)
        is GroupLayer -> copy(
            id = copyId,
            name = "$name COPY",
            x = nx,
            y = ny,
            children = children.map { child ->
                child.deepCopy({ old -> "${old.substringBeforeLast('-')}-${UUID.randomUUID().toString().take(6)}" })
            },
        )
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
