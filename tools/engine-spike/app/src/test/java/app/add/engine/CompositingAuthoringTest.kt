package app.add.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CompositingAuthoringTest {
    private fun gradientOf(doc: EngineDocument): PaintSpec.Gradient =
        when (val layer = doc.selected!!) {
            is VectorLayer -> layer.paint as PaintSpec.Gradient
            is TextLayer -> layer.paint as PaintSpec.Gradient
            else -> error("selected layer is not paintable")
        }

    private fun authorStops(
        doc: EngineDocument,
        offsets: List<Float>,
        colors: List<Int>,
        alphas: List<Float>,
    ) {
        offsets.forEachIndexed { index, offset ->
            doc.setPaintStopOffset(index, offset)
            doc.setPaintStopColor(index, colors[index])
            doc.setPaintStopAlpha(index, alphas[index])
        }
    }

    @Test
    fun authoredGradientBlendMaskAndEffectStackSurvivesTheCodecAsRenderInputs() {
        val doc = EngineDocument()

        // Vector: four authored stops, arbitrary order-independent colors and per-stop alpha.
        doc.select("vector-star")
        doc.setPaintKind(PaintKind.Diamond)
        doc.addPaintStop()
        val vectorOffsets = listOf(0f, 0.18f, 0.62f, 1f)
        val vectorColors = listOf(
            0xFFFF4D3D.toInt(),
            0xFF0FBF92.toInt(),
            0xFFD6E381.toInt(),
            0xFF11231C.toInt(),
        )
        val vectorAlphas = listOf(0.2f, 0.55f, 0.8f, 1f)
        authorStops(doc, vectorOffsets, vectorColors, vectorAlphas)

        doc.setBlend(BlendKind.LinearLight)
        doc.toggleClip()
        doc.toggleMaskMode()
        doc.beginMaskStroke(0.08f, 0.12f)
        doc.extendMaskStroke(0.9f, 0.86f)
        doc.endMaskStroke()
        doc.toggleMaskMode()

        doc.setExposure(0.65f)
        doc.setContrast(0.2f)
        doc.setSaturation(-0.15f)
        doc.setBlur(9f)
        doc.setGrain(0.4f)
        doc.setHalftone(0.3f)
        doc.toggleDuotone()

        // Toggling a pass off hides that pass but leaves its authored value intact.
        doc.toggleGrade()
        assertFalse(EffectPass.Grade in doc.selected!!.style.effects.activePasses)
        doc.toggleGrade()
        doc.toggleBlur()
        doc.toggleHalftone()

        val vectorEffects = doc.selected!!.style.effects
        assertEquals(listOf(EffectPass.Grade, EffectPass.Duotone, EffectPass.Grain), vectorEffects.activePasses)
        assertTrue(vectorEffects.hasAny)
        assertEquals(9f, vectorEffects.blur, 0.0005f)
        assertEquals(0.3f, vectorEffects.halftone, 0.0005f)

        // Text: the same gradient authoring path, a different kind and blend mode.
        doc.select("type-statement")
        doc.setPaintKind(PaintKind.Radial)
        doc.addPaintStop()
        val textOffsets = listOf(0f, 0.25f, 0.55f, 1f)
        val textColors = listOf(
            0xFF11231C.toInt(),
            0xFF0FBF92.toInt(),
            0xFFFF4D3D.toInt(),
            0xFFF2EEE3.toInt(),
        )
        val textAlphas = listOf(1f, 0.7f, 0.35f, 0.1f)
        authorStops(doc, textOffsets, textColors, textAlphas)
        doc.setBlend(BlendKind.ColorBurn)
        doc.toggleClip()
        doc.beginMaskStroke(0.15f, 0.2f)
        doc.extendMaskStroke(0.7f, 0.8f)
        doc.endMaskStroke()
        doc.setBlur(5f)
        doc.toggleBlur()
        doc.toggleDuotone()

        val encoded = DocumentCodec.encode(doc.layers, doc.selectedId, doc.zoom)
        val restored = DocumentCodec.decode(encoded)
        assertNotNull(restored)

        val vector = restored!!.layers.first { it.id == "vector-star" } as VectorLayer
        val vectorGradient = vector.paint as PaintSpec.Gradient
        assertEquals(PaintKind.Diamond, vectorGradient.kind)
        assertEquals(4, vectorGradient.stops.size)
        vectorOffsets.forEachIndexed { index, offset ->
            assertEquals(offset, vectorGradient.stops[index].offset, 0.0005f)
            assertEquals(
                vectorColors[index] and 0xFFFFFF,
                vectorGradient.stops[index].color and 0xFFFFFF,
            )
            assertEquals(vectorAlphas[index], colorAlpha(vectorGradient.stops[index].color), 0.005f)
        }

        // PaintRenderer resolves exactly these stops into the shader arrays.
        val (vectorShaderColors, vectorShaderPositions) = shaderStops(vectorGradient.stops)!!
        assertEquals(4, vectorShaderColors.size)
        assertEquals(4, vectorShaderPositions.size)
        assertEquals(0f, vectorShaderPositions.first(), 0.0005f)
        assertEquals(1f, vectorShaderPositions.last(), 0.0005f)
        assertEquals(vectorAlphas[0], colorAlpha(vectorShaderColors[0]), 0.005f)

        // These are the values LayerCompositor reads for the layer.
        assertEquals(BlendKind.LinearLight, vector.style.blend)
        assertTrue(vector.style.clipToBelow)
        assertTrue(vector.style.mask?.enabled == true)
        assertEquals(1, vector.style.mask?.strokes?.size)
        assertEquals(2, vector.style.mask?.strokes?.first()?.points?.size)
        assertEquals(listOf(EffectPass.Grade, EffectPass.Duotone, EffectPass.Grain), vector.style.effects.activePasses)
        assertFalse(vector.style.effects.blurEnabled)
        assertFalse(vector.style.effects.halftoneEnabled)

        val text = restored.layers.first { it.id == "type-statement" } as TextLayer
        val textGradient = text.paint as PaintSpec.Gradient
        assertEquals(PaintKind.Radial, textGradient.kind)
        assertEquals(4, textGradient.stops.size)
        textOffsets.forEachIndexed { index, offset ->
            assertEquals(offset, textGradient.stops[index].offset, 0.0005f)
            assertEquals(textAlphas[index], colorAlpha(textGradient.stops[index].color), 0.005f)
        }
        val (textShaderColors, _) = shaderStops(textGradient.stops)!!
        assertEquals(4, textShaderColors.size)
        assertEquals(BlendKind.ColorBurn, text.style.blend)
        assertTrue(text.style.clipToBelow)
        assertEquals(1, text.style.mask?.strokes?.size)
        assertFalse(text.style.effects.blurEnabled)
        assertTrue(text.style.effects.duotone)
    }

    @Test
    fun everyBlendModeAndEveryGradientKindFlowsThroughTheDocument() {
        val doc = EngineDocument()
        doc.select("vector-star")

        listOf(PaintKind.Linear, PaintKind.Radial, PaintKind.Sweep, PaintKind.Diamond).forEach { kind ->
            doc.setPaintKind(kind)
            assertEquals(kind, gradientOf(doc).kind)
        }

        assertEquals(19, BlendKind.entries.size)
        BlendKind.entries.forEach { kind ->
            doc.setBlend(kind)
            assertEquals(kind, doc.selected!!.style.blend)
        }
    }

    @Test
    fun stopsCanBeReorderedWithoutLosingColorOrAlpha() {
        val doc = EngineDocument()
        doc.select("vector-star")
        doc.setPaintKind(PaintKind.Sweep)
        doc.addPaintStop()

        val colors = listOf(0xFFFF4D3D, 0xFF0FBF92, 0xFFD6E381, 0xFF11231C).map { it.toInt() }
        val alphas = listOf(0.2f, 0.4f, 0.6f, 0.8f)
        authorStops(doc, listOf(0f, 0.2f, 0.6f, 1f), colors, alphas)
        val before = gradientOf(doc).stops.map { it.color }

        doc.movePaintStop(3, -1)
        val after = gradientOf(doc).stops
        assertEquals(before[0], after[0].color)
        assertEquals(before[1], after[1].color)
        assertEquals(before[3], after[2].color)
        assertEquals(before[2], after[3].color)
        assertEquals(alphas[0], colorAlpha(after[0].color), 0.005f)
        assertEquals(alphas[3], colorAlpha(after[2].color), 0.005f)

        val restored = DocumentCodec.decode(
            DocumentCodec.encode(doc.layers, doc.selectedId, doc.zoom),
        )!!
        val gradient = (restored.layers.first { it.id == "vector-star" } as VectorLayer)
            .paint as PaintSpec.Gradient
        assertEquals(4, gradient.stops.size)
        assertEquals(after.map { it.color }, gradient.stops.map { it.color })
    }

    @Test
    fun paintMasksCanBeCreatedToggledAndRemoved() {
        val doc = EngineDocument()
        doc.select("vector-star")
        assertNull(doc.selected!!.style.mask)

        doc.toggleMask()
        assertNotNull(doc.selected!!.style.mask)
        assertTrue(doc.selected!!.style.mask!!.enabled)

        doc.toggleMask()
        assertFalse(doc.selected!!.style.mask!!.enabled)
        doc.toggleMask()
        assertTrue(doc.selected!!.style.mask!!.enabled)

        doc.beginMaskStroke(0.2f, 0.2f)
        doc.extendMaskStroke(0.8f, 0.8f)
        doc.endMaskStroke()
        assertEquals(1, doc.selected!!.style.mask!!.strokes.size)

        doc.removeMask()
        assertNull(doc.selected!!.style.mask)

        assertFalse(doc.selected!!.style.clipToBelow)
        doc.toggleClip()
        assertTrue(doc.selected!!.style.clipToBelow)
        doc.toggleClip()
        assertFalse(doc.selected!!.style.clipToBelow)
    }

    @Test
    fun effectFlagsGatePassesIndependentlyAndKeepAuthoredValues() {
        val effects = LayerEffects(
            exposure = 0.4f,
            blur = 6f,
            grain = 0.2f,
            halftone = 0.3f,
            duotone = true,
        )
        assertEquals(
            listOf(EffectPass.Grade, EffectPass.Duotone, EffectPass.Blur, EffectPass.Halftone, EffectPass.Grain),
            effects.activePasses,
        )

        val noGrade = effects.copy(gradeEnabled = false)
        assertFalse(EffectPass.Grade in noGrade.activePasses)
        assertEquals(0.4f, noGrade.exposure, 0.0005f)

        val noBlur = effects.copy(blurEnabled = false)
        assertFalse(EffectPass.Blur in noBlur.activePasses)
        assertEquals(6f, noBlur.blur, 0.0005f)

        val noHalftone = effects.copy(halftoneEnabled = false)
        assertFalse(EffectPass.Halftone in noHalftone.activePasses)
        assertEquals(0.3f, noHalftone.halftone, 0.0005f)

        val noGrain = effects.copy(grainEnabled = false)
        assertFalse(EffectPass.Grain in noGrain.activePasses)
        assertEquals(0.2f, noGrain.grain, 0.0005f)

        val allOff = effects.copy(
            gradeEnabled = false,
            blurEnabled = false,
            grainEnabled = false,
            halftoneEnabled = false,
            duotone = false,
        )
        assertTrue(allOff.activePasses.isEmpty())
        assertFalse(allOff.hasAny)
    }
}
