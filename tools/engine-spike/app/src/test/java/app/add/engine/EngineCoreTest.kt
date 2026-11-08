package app.add.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineCoreTest {
    @Test
    fun addNodeSplitsASegmentAndKeepsTheRegionClosed() {
        val network = starNetwork()
        assertEquals(10, network.points.size)
        assertEquals(10, network.segments.size)
        assertEquals(10, network.regions.single().segments.size)

        val added = network.addNode()
        assertEquals(11, added.points.size)
        assertEquals(11, added.segments.size)
        assertEquals(11, added.regions.single().segments.size)
        assertTrue(added.points.any { it.id !in network.points.map { old -> old.id } })
    }

    @Test
    fun deleteNodeBridgesItsNeighbours() {
        val network = starNetwork().addNode()
        val added = network.points.first { it.id !in starNetwork().points.map { old -> old.id }.toSet() }

        val removed = network.deleteNode(added.id)
        assertEquals(10, removed.points.size)
        assertEquals(10, removed.segments.size)
        assertEquals(10, removed.regions.single().segments.size)
        assertTrue(removed.points.none { it.id == added.id })
    }

    @Test
    fun deletingSelectedNodeClearsNodeSelection() {
        val document = EngineDocument()
        document.select("vector-star")
        val point = (document.selected as VectorLayer).network.points.first()
        document.selectNode(point.id)
        document.deleteNode()
        assertNull(document.selectedNodeId)
    }

    @Test
    fun dragGestureCreatesOneUndoStep() {
        val document = EngineDocument()
        document.select("vector-star")
        val startX = (document.selected as VectorLayer).x
        document.beginGesture()
        document.moveSelected(0.08f, 0.02f)
        document.moveSelected(0.02f, 0.01f)
        document.endGesture()

        val movedX = (document.selected as VectorLayer).x
        assertTrue(movedX > startX)
        assertTrue(document.canUndo)

        document.undo()
        assertEquals(startX, (document.selected as VectorLayer).x, 0.0001f)
        assertTrue(document.canRedo)
    }

    @Test
    fun duplicate3dLayerAddsASecondDistinctModelLayer() {
        val document = EngineDocument()
        val original = document.layers.size
        document.select("model-gem")

        document.duplicateSelected()

        assertEquals(original + 1, document.layers.size)
        val models = document.layers.filterIsInstance<ModelLayer>()
        assertEquals(2, models.size)
        assertNotEquals(models[0].id, models[1].id)
        assertEquals("3D / GEM COPY", models[1].name)
        assertTrue(document.selected is ModelLayer)
    }

    @Test
    fun stickerLibraryAddsAnEditableVectorLayer() {
        val document = EngineDocument()
        val original = document.layers.size
        val preset = StickerLibrary.presets.first()

        document.chooseTool(Tool.Sticker)
        document.addSticker(preset)

        assertEquals(original + 1, document.layers.size)
        val layer = document.selected as VectorLayer
        assertEquals("STICKER / STAR", layer.name)
        assertEquals(preset.fill, layer.fill)
        assertEquals(preset.network.points.size, layer.network.points.size)
        assertEquals(preset.network.segments.size, layer.network.segments.size)
        assertEquals(squareFrameHeight(layer.w), layer.h, 0.0005f)
        assertEquals(Tool.Select, document.tool)
    }

    @Test
    fun everyStickerAndIconHasAClosedVectorRegion() {
        StickerLibrary.presets.forEach { preset ->
            assertEquals(preset.network.points.size, preset.network.segments.size)
            assertEquals(1, preset.network.regions.size)
            assertEquals(preset.network.segments.size, preset.network.regions.single().segments.size)
        }
    }

    @Test
    fun aRotatedLayerUsesItsTurnedBoundsForHitTesting() {
        val layer = VectorLayer(
            id = "rotated",
            name = "VECTOR / ROTATED",
            x = 0.2f,
            y = 0.4f,
            w = 0.4f,
            h = 0.1f,
            rotation = 90f,
            network = starNetwork(),
            fill = 0xFF0FBF92.toInt(),
            stroke = 0xFF11231C.toInt(),
            strokeWidth = 2f,
        )
        val turned = Geometry.rotatePoint(layer.centerX, layer.y + layer.h * 0.1f, layer.centerX, layer.centerY, 90f)
        assertTrue(Geometry.contains(layer, turned.first, turned.second))

        val onlyInsideAxisAlignedBox = layer.x + layer.w * 0.95f to layer.centerY
        assertTrue(!Geometry.contains(layer, onlyInsideAxisAlignedBox.first, onlyInsideAxisAlignedBox.second))
    }

    @Test
    fun cornerScaleKeepsTheOppositeCornerFixedAndScalesType() {
        val text = TextLayer(
            id = "type",
            name = "TYPE",
            x = 0.2f,
            y = 0.2f,
            w = 0.4f,
            h = 0.2f,
            text = "SCALE",
            fontSize = 50f,
            lineHeight = 1f,
            color = 0xFF11231C.toInt(),
        )
        val scaled = Geometry.scaleLayer(text, LayerHandle.TopLeft, 2f) as TextLayer
        assertEquals(0.6f, scaled.x + scaled.w, 0.0005f)
        assertEquals(0.4f, scaled.y + scaled.h, 0.0005f)
        assertEquals(0.8f, scaled.w, 0.0005f)
        assertEquals(100f, scaled.fontSize, 0.0005f)

        val clamped = Geometry.scaleLayer(text, LayerHandle.BottomRight, 10f) as TextLayer
        assertEquals(180f, clamped.fontSize, 0.0005f)
    }

    @Test
    fun rotationIsSnappedAndSurvivesDuplication() {
        assertEquals(90f, Geometry.snapAngle(88f), 0.0005f)
        assertEquals(90f, Geometry.snapAngle(92f), 0.0005f)
        assertEquals(85f, Geometry.snapAngle(85f), 0.0005f)
        assertEquals(350f, Geometry.snapAngle(-10f), 0.0005f)

        val document = EngineDocument()
        document.select("vector-star")
        document.setRotation(45f)
        document.duplicateSelected()
        assertEquals(45f, (document.selected as VectorLayer).rotation, 0.0005f)
    }

    @Test
    fun anIconLayerKeepsItsGlyphWhenDuplicated() {
        val glyph = IconGlyph(
            id = "test/box",
            name = "box",
            brandSlug = "test",
            brandLabel = "Test",
            style = "Line",
            category = "Symbols",
            viewBox = listOf(0f, 0f, 24f, 24f),
            paths = listOf(
                IconPathSpec("M0 0h24v24H0z", "currentColor", "none", 1f, 1f, "nonzero", null)
            ),
        )
        val document = EngineDocument()
        document.addIcon(glyph)
        val placed = document.selected as IconLayer
        assertEquals("test/box", placed.glyph.id)
        assertEquals(0.22f, placed.w, 0.0005f)

        document.duplicateSelected()
        val copy = document.selected as IconLayer
        assertEquals("test/box", copy.glyph.id)
        assertNotEquals(placed.id, copy.id)
    }

    @Test
    fun theModelLayerCanSwapToTheRingAndKeepsItWhenDuplicated() {
        val document = EngineDocument()
        document.select("model-gem")
        document.setModelAsset(ModelAsset.Ring)
        val swapped = document.selected as ModelLayer
        assertEquals(ModelAsset.Ring, swapped.asset)
        assertTrue(swapped.name.contains("RING"))

        document.duplicateSelected()
        assertEquals(ModelAsset.Ring, (document.selected as ModelLayer).asset)

        document.addModel(ModelAsset.Ring)
        assertEquals(3, document.modelLayers.size)
        assertEquals(ModelAsset.Ring, (document.selected as ModelLayer).asset)
    }

    @Test
    fun placedStickersKeepTheirPhysicalAspectRatioWhenCornerScaled() {
        StickerLibrary.presets.forEach { preset ->
            val layer = VectorLayer(
                id = preset.id,
                name = "${preset.kind} / ${preset.label}",
                x = 0.2f,
                y = 0.2f,
                w = 0.26f,
                h = squareFrameHeight(0.26f),
                network = preset.network,
                fill = preset.fill,
                stroke = preset.stroke,
                strokeWidth = preset.strokeWidth,
            )
            val scaled = Geometry.scaleLayer(layer, LayerHandle.BottomRight, 1.7f)
            assertEquals(squareFrameHeight(scaled.w), scaled.h, 0.0005f)
        }
    }

    @Test
    fun everyVectorLayerUsesAPhysicallySquareFrame() {
        seedLayers().filterIsInstance<VectorLayer>().forEach { layer ->
            assertEquals(squareFrameHeight(layer.w), layer.h, 0.0005f)
        }
    }

    @Test
    fun scaleCenteredKeepsTheCentreAndPhysicalAspect() {
        val layer = VectorLayer(
            id = "scaled",
            name = "STICKER / SCALED",
            x = 0.2f,
            y = 0.25f,
            w = 0.3f,
            h = squareFrameHeight(0.3f),
            network = starNetwork(),
            fill = 0xFF0FBF92.toInt(),
            stroke = 0xFF11231C.toInt(),
            strokeWidth = 4f,
        )
        val scaled = Geometry.scaleCentered(layer, 1.5f)
        assertEquals(layer.centerX, scaled.centerX, 0.0005f)
        assertEquals(layer.centerY, scaled.centerY, 0.0005f)
        assertEquals(squareFrameHeight(scaled.w), scaled.h, 0.0005f)
    }

    @Test
    fun scaleDialUsesWidthAndKeepsTheCentre() {
        val document = EngineDocument()
        document.select("vector-star")
        val before = document.selected as VectorLayer

        document.setScaleToWidth(0.66f)

        val after = document.selected as VectorLayer
        assertEquals(0.66f, after.w, 0.0005f)
        assertEquals(squareFrameHeight(0.66f), after.h, 0.0005f)
        assertEquals(before.centerX, after.centerX, 0.0005f)
        assertEquals(before.centerY, after.centerY, 0.0005f)
    }

    @Test
    fun modelRotationTurnsTheMeshButNotItsFrame() {
        val model = ModelLayer(
            id = "model",
            name = "3D / RING",
            x = 0.2f,
            y = 0.3f,
            w = 0.3f,
            h = 0.2f,
            rotation = 90f,
            asset = ModelAsset.Ring,
        )
        assertEquals(0f, model.frameRotation, 0.0005f)
        assertTrue(Geometry.contains(model, model.centerX, model.y + 0.01f))
    }

    @Test
    fun addTextUndoAndRedoWork() {
        val document = EngineDocument()
        val original = document.layers.size
        document.addText()
        assertEquals(original + 1, document.layers.size)
        assertNotNull(document.selectedText)

        document.undo()
        assertEquals(original, document.layers.size)
        document.redo()
        assertEquals(original + 1, document.layers.size)
    }

    @Test
    fun theScaleDialResizesA3dFrameWithoutTouchingTheModel() {
        val document = EngineDocument()
        document.select("model-gem")
        document.setModelScale(1.4f)

        document.setScaleToWidth(0.6f)

        val model = document.selected as ModelLayer
        assertEquals(0.6f, model.w, 0.0005f)
        assertEquals(1.4f, model.scaleToUnits, 0.0005f)
    }

    @Test
    fun typeAtItsCapStillGrowsItsBox() {
        val text = TextLayer(
            id = "type-cap",
            name = "TYPE / CAP",
            x = 0.2f,
            y = 0.2f,
            w = 0.4f,
            h = 0.2f,
            text = "LARGE",
            fontSize = 180f,
            lineHeight = 1f,
            color = 0xFF11231C.toInt(),
        )
        val scaled = Geometry.scaleCentered(text, 1.5f) as TextLayer
        assertEquals(0.6f, scaled.w, 0.0005f)
        assertEquals(180f, scaled.fontSize, 0.0005f)
    }

    @Test
    fun theViewportZoomStaysInsideItsRange() {
        assertEquals(ZOOM_MIN, clampZoom(0.1f), 0.0005f)
        assertEquals(ZOOM_MAX, clampZoom(12f), 0.0005f)
        assertEquals(2f, clampZoom(2f), 0.0005f)
    }
}
