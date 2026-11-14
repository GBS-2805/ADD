package app.add.engine

/**
 * The layer tree and its value types. Keeping the model separate from the
 * document's history and commands lets both sides be tested without Compose.
 */

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

internal const val HISTORY_LIMIT = 100
internal const val TEXT_DEBOUNCE_MS = 600L

/**
 * One undo step. The payload is the tree plus its motion paths; selection, the
 * entered group, zoom and locks travel with it so an undo puts the stage back
 * where the reader left it. Selection alone never creates one of these.
 */
data class EditorSnapshot(
    val layers: List<Layer>,
    val motion: Map<String, MotionPath> = emptyMap(),
    val selectedIds: Set<String> = emptySet(),
    val selectedId: String? = selectedIds.firstOrNull(),
    val editingGroupId: String? = null,
    val zoom: Float = 1f,
    val locked: Set<String> = emptySet(),
    val texture: TextureSpec? = null,
    val ink: List<TextureInk> = emptyList(),
    val backgroundField: FieldSpec? = null,
)

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
    /** Horizontal shear produced when a non-uniform group scale is flattened. */
    val skewX: Float
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
    override val skewX: Float = 0f,
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
    override val skewX: Float = 0f,
    val text: String,
    val fontSize: Float,
    val lineHeight: Float,
    val color: Int,
    val align: TextAlign = TextAlign.Left,
    val weight: Int = 600,
    val fontFamily: String = "Poppins",
    /** Extra tracking in em, applied at paint time only. */
    val tracking: Float = 0f,
    val textCase: TextCase = TextCase.None,
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
    override val skewX: Float = 0f,
    val glyph: IconGlyph,
    val tint: Int,
    override val style: LayerStyle = LayerStyle(),
) : Layer

/**
 * A normalised rectangle in the decoded photograph's own pixels. It is a stored
 * instruction, never a flattened bitmap, so changing it never rewrites the source
 * and undo only has to carry four floats.
 */
data class ImageCrop(
    val x: Float = 0f,
    val y: Float = 0f,
    val w: Float = 1f,
    val h: Float = 1f,
) {
    fun normalized(): ImageCrop {
        val nw = w.coerceIn(0.05f, 1f)
        val nh = h.coerceIn(0.05f, 1f)
        return ImageCrop(
            x = x.coerceIn(0f, 1f - nw),
            y = y.coerceIn(0f, 1f - nh),
            w = nw,
            h = nh,
        )
    }
}

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
    override val skewX: Float = 0f,
    /** The file name inside filesDir/media. Copied in, never referenced in place. */
    val path: String,
    /** True fills the frame and crops; false fits the whole photograph inside it. */
    val fill: Boolean = true,
    /** Source-space crop; the full photograph is the default for older documents. */
    val crop: ImageCrop = ImageCrop(),
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
    override val skewX: Float = 0f,
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
        is ImageLayer -> when {
            crop != ImageCrop() -> "PHOTO / CROP"
            fill -> "PHOTO / FILL"
            else -> "PHOTO / FIT"
        }
        is GroupLayer -> "GROUP / ${children.size} LAYERS"
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
            EngineEdition.InkArgb,
        )
    ),
)

/**
 * The first page is also the engine's own test plate: a gradient star, a
 * Difference layer carrying a paint mask and grain, a diamond clipped to it,
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
        color = EngineEdition.InkArgb,
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
        fill = EngineEdition.PlumArgb,
        stroke = EngineEdition.InkArgb,
        strokeWidth = 5f,
        paint = PaintSpec.Gradient(
            kind = PaintKind.Linear,
            stops = listOf(
                PaintStop(0f, EngineEdition.InkArgb),
                PaintStop(0.42f, EngineEdition.PlumArgb),
                PaintStop(0.78f, colorWithAlpha(EngineEdition.PlumHoverArgb, 0.86f)),
                PaintStop(1f, colorWithAlpha(EngineEdition.PaperArgb, 0.55f)),
            ),
        ),
    ),
    VectorLayer(
        id = "vector-orbit",
        name = "VECTOR / BLEND DISC",
        x = 0.54f,
        y = 0.075f,
        w = 0.40f,
        h = squareFrameHeight(0.40f),
        network = orbitNetwork(),
        // True black lets Difference return the exact paper token; the ink
        // stroke keeps the disc edge in the Review palette.
        fill = 0xFF000000.toInt(),
        stroke = EngineEdition.InkArgb,
        strokeWidth = 4f,
    ),
    VectorLayer(
        id = "vector-blend",
        name = "VECTOR / DIFFERENCE + MASK",
        x = 0.55f,
        y = 0.09f,
        w = 0.38f,
        h = squareFrameHeight(0.38f),
        network = diamondNetwork(),
        fill = EngineEdition.PaperArgb,
        stroke = EngineEdition.PlumHoverArgb,
        strokeWidth = 4f,
        style = LayerStyle(
            blend = BlendKind.Difference,
            effects = LayerEffects(
                enabled = true,
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
        fill = EngineEdition.PlumArgb,
        stroke = EngineEdition.PaperArgb,
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
        color = EngineEdition.InkArgb,
        weight = 600,
        paint = PaintSpec.Gradient(
            kind = PaintKind.Sweep,
            stops = listOf(
                PaintStop(0f, EngineEdition.PlumArgb),
                PaintStop(0.35f, EngineEdition.PlumHoverArgb),
                PaintStop(0.7f, EngineEdition.InkArgb),
                PaintStop(1f, EngineEdition.PlumArgb),
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
        color = EngineEdition.InkArgb,
        weight = 600,
        style = LayerStyle(
            effects = LayerEffects(
                duotone = true,
                duotoneDark = EngineEdition.InkArgb,
                duotoneLight = EngineEdition.PaperArgb,
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

internal val ADD_ACTIONS = setOf("TYPE+", "VECTOR+", "PHOTO+", "STICKER+", "ICON+", "3D+")
