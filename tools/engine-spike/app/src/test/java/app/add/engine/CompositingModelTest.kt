package app.add.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CompositingModelTest {
    private fun compositeLayer(): VectorLayer = VectorLayer(
        id = "vector-composite",
        name = "COMPOSITE",
        x = 0.1f,
        y = 0.1f,
        w = 0.4f,
        h = 0.32f,
        network = triangleNetwork(),
        fill = 0xFFE86A4A.toInt(),
        stroke = 0xFF11231C.toInt(),
        strokeWidth = 3f,
        paint = PaintSpec.Gradient(
            kind = PaintKind.Linear,
            stops = listOf(
                PaintStop(0f, 0x80FF0000.toInt()),
                PaintStop(0.45f, 0xFF0FBF92.toInt()),
                PaintStop(1f, 0x40FFFFFF),
            ),
        ),
        style = LayerStyle(
            blend = BlendKind.Difference,
            clipToBelow = true,
            effects = LayerEffects(
                exposure = 0.35f,
                contrast = 0.2f,
                saturation = -0.1f,
                blur = 4f,
                grain = 0.25f,
                halftone = 0.1f,
                duotone = true,
                duotoneDark = 0xFF0C1210.toInt(),
                duotoneLight = 0xFFD6E381.toInt(),
            ),
            mask = LayerMask(
                strokes = listOf(
                    MaskStroke(
                        points = listOf(MaskPoint(0.05f, 0.9f), MaskPoint(0.95f, 0.1f)),
                        radius = 0.12f,
                        erase = true,
                    ),
                ),
            ),
        ),
    )

    private fun patternLayer(): TextLayer = TextLayer(
        id = "type-pattern",
        name = "PATTERN TYPE",
        x = 0.2f,
        y = 0.5f,
        w = 0.5f,
        h = 0.1f,
        text = "PATTERN",
        fontSize = 48f,
        lineHeight = 1f,
        color = 0xFF11231C.toInt(),
        paint = PaintSpec.Pattern(
            kind = PatternKind.Dots,
            foreground = 0xFF0FBF92.toInt(),
            background = 0xFFF3EBDD.toInt(),
            scale = 1.5f,
        ),
    )

    @Test
    fun schemaThreeCarriesPaintBlendMaskAndEffects() {
        val layers = listOf(compositeLayer(), patternLayer())
        val encoded = DocumentCodec.encode(layers, "vector-composite", 1f)
        val restored = DocumentCodec.decode(encoded)

        assertNotNull(restored)
        val first = restored!!.layers.first { it.id == "vector-composite" } as VectorLayer
        // A region whose segment ids do not survive the round trip paints an empty path.
        assertEquals(
            listOf("triangle-ab", "triangle-bc", "triangle-ca"),
            first.network.regions.first().segments,
        )
        val gradient = first.paint as PaintSpec.Gradient
        assertEquals(PaintKind.Linear, gradient.kind)
        assertEquals(3, gradient.stops.size)
        assertEquals(0.45f, gradient.stops[1].offset, 0.0005f)
        assertEquals(128f, colorAlpha(gradient.stops.first().color) * 255f, 0.25f)
        assertEquals(BlendKind.Difference, first.style.blend)
        assertTrue(first.style.clipToBelow)
        assertEquals(1, first.style.mask?.strokes?.size)
        assertEquals(2, first.style.mask?.strokes?.first()?.points?.size)
        assertEquals(0.12f, first.style.mask?.strokes?.first()?.radius ?: 0f, 0.0005f)
        assertTrue(first.style.mask?.strokes?.first()?.erase ?: false)
        assertTrue(first.style.effects.hasAny)
        assertEquals(0.35f, first.style.effects.exposure, 0.0005f)
        assertEquals(4f, first.style.effects.blur, 0.0005f)
        assertTrue(first.style.effects.duotone)

        val text = restored.layers.first { it.id == "type-pattern" } as TextLayer
        val pattern = text.paint as PaintSpec.Pattern
        assertEquals(PatternKind.Dots, pattern.kind)
        assertEquals(1.5f, pattern.scale, 0.0005f)
    }

    @Test
    fun schemaTwoStillOpensWithDefaults() {
        val legacy = """
            {"schema":2,"layers":[{"kind":"vector","id":"old","name":"OLD","x":0.1,"y":0.1,
            "w":0.2,"h":0.2,"opacity":1.0,"rotation":0,"fill":-14614638,
            "stroke":-15655460,"strokeWidth":4,"network":{"points":[],"segments":[],"regions":[]}}]}
        """.trimIndent()
        val restored = DocumentCodec.decode(legacy)
        assertNotNull(restored)
        val layer = restored!!.layers.single() as VectorLayer
        assertEquals(null, layer.paint)
        assertEquals(LayerStyle(), layer.style)
    }

    @Test
    fun aFutureSchemaIsRefusedRatherThanGuessed() {
        assertEquals(null, DocumentCodec.decode("""{"schema":4,"layers":[]}"""))
    }

    @Test
    fun theSeedDemoUsesEveryCompositorFeature() {
        val layers = seedLayers()
        val star = layers.first { it.id == "vector-star" } as VectorLayer
        assertTrue(star.paint is PaintSpec.Gradient)
        assertEquals(PaintKind.Linear, (star.paint as PaintSpec.Gradient).kind)
        assertTrue((star.paint as PaintSpec.Gradient).stops.size >= 3)

        val statement = layers.first { it.id == "type-statement" } as TextLayer
        assertEquals(PaintKind.Sweep, (statement.paint as PaintSpec.Gradient).kind)

        val blended = layers.first { it.id == "vector-blend" }
        assertEquals(BlendKind.Difference, blended.style.blend)
        assertTrue(blended.style.mask?.strokes?.isNotEmpty() == true)
        assertTrue(blended.style.effects.hasAny)
        assertTrue(blended.style.effects.grain > 0f)

        assertTrue(layers.first { it.id == "vector-clip" }.style.clipToBelow)
        assertTrue(layers.first { it.id == "type-duotone" }.style.effects.duotone)
        assertFalse(layers.first { it.id == "model-gem" }.visible)
    }

    @Test
    fun imagePaintTargetsASpecificPhotoAndKeepsItsFitMode() {
        val doc = EngineDocument()
        doc.addImage("photo-a.jpg")
        val photo = doc.selected as ImageLayer
        doc.select("vector-star")

        doc.setPaintImage(photo.path, fill = false)
        val paint = (doc.selected as VectorLayer).paint as PaintSpec.Image
        assertEquals("photo-a.jpg", paint.path)
        assertFalse(paint.fill)

        val restored = DocumentCodec.decode(
            DocumentCodec.encode(doc.layers, doc.selectedId, doc.zoom),
        )!!
        val vector = restored.layers.first { it.id == "vector-star" } as VectorLayer
        val imagePaint = vector.paint as PaintSpec.Image
        assertEquals("photo-a.jpg", imagePaint.path)
        assertFalse(imagePaint.fill)

        doc.setPaintImageFill(true)
        assertTrue(((doc.selected as VectorLayer).paint as PaintSpec.Image).fill)
    }

    @Test
    fun shaderStopsKeepEveryStopItsAlphaAndTheirOrder() {
        val stops = listOf(
            PaintStop(1f, 0x40FFFFFF),
            PaintStop(0f, 0xFFFF0000.toInt()),
            PaintStop(0.5f, 0x8000FF00.toInt()),
            PaintStop(0.25f, 0xFF0000FF.toInt()),
        )

        val (colors, positions) = shaderStops(stops)!!

        assertEquals(4, colors.size)
        assertEquals(0xFFFF0000.toInt(), colors.first())
        assertEquals(0x40FFFFFF, colors.last())
        assertEquals(0f, positions.first(), 0.0001f)
        assertEquals(1f, positions.last(), 0.0001f)
        assertTrue(positions.toList().zipWithNext().all { pair -> pair.first <= pair.second })
    }

    @Test
    fun documentControlsEditStopsBlendMasksAndEffects() {
        val doc = EngineDocument()
        doc.select("vector-star")
        doc.setPaintKind(PaintKind.Radial)
        var gradient = (doc.selected as VectorLayer).paint as PaintSpec.Gradient
        assertEquals(PaintKind.Radial, gradient.kind)

        doc.addPaintStop()
        gradient = (doc.selected as VectorLayer).paint as PaintSpec.Gradient
        assertEquals(4, gradient.stops.size)
        doc.removePaintStop(0)
        gradient = (doc.selected as VectorLayer).paint as PaintSpec.Gradient
        assertEquals(3, gradient.stops.size)

        doc.setBlend(BlendKind.Screen)
        assertEquals(BlendKind.Screen, doc.selected?.style?.blend)
        doc.cycleBlend(1)
        assertEquals(BlendKind.Overlay, doc.selected?.style?.blend)

        doc.toggleMaskMode()
        doc.beginMaskStroke(0.2f, 0.2f)
        doc.extendMaskStroke(0.8f, 0.8f)
        doc.endMaskStroke()
        assertEquals(1, doc.selected?.style?.mask?.strokes?.size)
        doc.toggleMaskErase()
        doc.beginMaskStroke(0.5f, 0.5f)
        doc.extendMaskStroke(0.6f, 0.6f)
        doc.endMaskStroke()
        assertEquals(2, doc.selected?.style?.mask?.strokes?.size)
        doc.toggleMaskMode()

        doc.setExposure(0.5f)
        doc.setGrain(0.4f)
        doc.toggleDuotone()
        val effects = doc.selected?.style?.effects!!
        assertTrue(effects.enabled)
        assertEquals(0.5f, effects.exposure, 0.0005f)
        assertEquals(0.4f, effects.grain, 0.0005f)
        assertTrue(effects.duotone)
    }
}
