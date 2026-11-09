package app.add.engine

/**
 * The 19 modes the compositor implements. Names are stable because the document
 * codec writes them verbatim; labels are for the inspector.
 */
enum class BlendKind(val label: String) {
    Normal("NORMAL"),
    Multiply("MULTIPLY"),
    Screen("SCREEN"),
    Overlay("OVERLAY"),
    Darken("DARKEN"),
    Lighten("LIGHTEN"),
    ColorDodge("COLOR DODGE"),
    ColorBurn("COLOR BURN"),
    HardLight("HARD LIGHT"),
    SoftLight("SOFT LIGHT"),
    Difference("DIFFERENCE"),
    Exclusion("EXCLUSION"),
    Hue("HUE"),
    Saturation("SATURATION"),
    Color("COLOR"),
    Luminosity("LUMINOSITY"),
    LinearBurn("LINEAR BURN"),
    LinearDodge("LINEAR DODGE"),
    LinearLight("LINEAR LIGHT"),
    ;

    companion object {
        fun fromName(value: String?): BlendKind =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: Normal
    }
}

/** One point in a layer-local 0..1 mask stroke. */
data class MaskPoint(val x: Float, val y: Float)

/**
 * A paint-mask stroke. The mask starts fully visible; an erase stroke hides its
 * path and a restore stroke brings it back. Storing the stroke keeps masks
 * editable and small in JSON instead of forcing a bitmap into the document.
 */
data class MaskStroke(
    val points: List<MaskPoint>,
    val radius: Float,
    val erase: Boolean = true,
)

data class LayerMask(
    val strokes: List<MaskStroke> = emptyList(),
    val enabled: Boolean = true,
)

/**
 * Non-destructive grading. Everything here is applied to the rendered layer in
 * the compositor, so the source pixels and the document never change.
 */
data class LayerEffects(
    val enabled: Boolean = true,
    val exposure: Float = 0f,
    val contrast: Float = 0f,
    val saturation: Float = 0f,
    val shadows: Float = 0f,
    val midtones: Float = 0f,
    val highlights: Float = 0f,
    val blur: Float = 0f,
    val grain: Float = 0f,
    val halftone: Float = 0f,
    val duotone: Boolean = false,
    val duotoneDark: Int = 0xFF11231C.toInt(),
    val duotoneLight: Int = 0xFFD6E381.toInt(),
) {
    val hasAny: Boolean
        get() = exposure != 0f || contrast != 0f || saturation != 0f ||
            shadows != 0f || midtones != 0f || highlights != 0f ||
            blur > 0f || grain > 0f || halftone > 0f || duotone
}

data class LayerStyle(
    val blend: BlendKind = BlendKind.Normal,
    val effects: LayerEffects = LayerEffects(),
    val mask: LayerMask? = null,
    val clipToBelow: Boolean = false,
)
