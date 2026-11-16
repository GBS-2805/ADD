package app.add.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PrismPluginTest {

    private val preset = FieldPresets.all.first().field

    @Test
    fun applyingPresetChangesTheArtboardField() {
        val doc = EngineDocument()
        val effects = EngineDocumentEffects(doc)

        assertTrue(effects.applyFieldPreset(EngineEffectTarget.Artboard, preset))

        assertEquals(preset.normalized(), effects.fieldFor(EngineEffectTarget.Artboard))
    }

    @Test
    fun applyingPresetChangesTheSelectedLayerField() {
        val doc = EngineDocument()
        doc.addText()
        val effects = EngineDocumentEffects(doc)

        assertTrue(effects.applyFieldPreset(EngineEffectTarget.Layer, preset))

        assertEquals(preset.normalized(), effects.fieldFor(EngineEffectTarget.Layer))
    }

    @Test
    fun applyingPresetIsUndoable() {
        val doc = EngineDocument()
        doc.addText()
        val effects = EngineDocumentEffects(doc)
        val depth = doc.undoDepth

        assertTrue(effects.applyFieldPreset(EngineEffectTarget.Layer, preset))
        assertEquals(depth + 1, doc.undoDepth)

        doc.undo()

        assertNull(effects.fieldFor(EngineEffectTarget.Layer))
    }

    @Test
    fun clearingPresetIsUndoable() {
        val doc = EngineDocument()
        val effects = EngineDocumentEffects(doc)
        assertTrue(effects.applyFieldPreset(EngineEffectTarget.Artboard, preset))
        val applied = effects.fieldFor(EngineEffectTarget.Artboard)

        assertTrue(effects.clearField(EngineEffectTarget.Artboard))
        assertNull(effects.fieldFor(EngineEffectTarget.Artboard))

        doc.undo()

        assertEquals(applied, effects.fieldFor(EngineEffectTarget.Artboard))
    }

    @Test
    fun layerTargetIsEnabledForAVectorSelection() {
        val doc = EngineDocument()
        doc.addVector()
        val effects = EngineDocumentEffects(doc)

        assertTrue(effects.canTarget(EngineEffectTarget.Layer))
        assertTrue(effects.applyFieldPreset(EngineEffectTarget.Layer, preset))
        assertEquals(preset.normalized(), effects.fieldFor(EngineEffectTarget.Layer))
    }

    @Test
    fun layerTargetIsDisabledWithoutATextOrVectorSelection() {
        val doc = EngineDocument()
        doc.addImage("/tmp/prism-disabled.png")
        val effects = EngineDocumentEffects(doc)

        assertFalse(effects.canTarget(EngineEffectTarget.Layer))
        assertFalse(effects.applyFieldPreset(EngineEffectTarget.Layer, preset))
        assertTrue(effects.canTarget(EngineEffectTarget.Artboard))
    }

    @Test
    fun reapplyingTheSamePresetDoesNotAddHistory() {
        val doc = EngineDocument()
        val effects = EngineDocumentEffects(doc)
        assertTrue(effects.applyFieldPreset(EngineEffectTarget.Artboard, preset))
        val depth = doc.undoDepth

        assertTrue(effects.applyFieldPreset(EngineEffectTarget.Artboard, preset))

        assertEquals(depth, doc.undoDepth)
    }

    @Test
    fun prismCommandOpensThePlugin() {
        var opened: String? = null
        val commands = enginePluginCommands(EnginePlugins.builtIn) { opened = it }
        val command = commands.single { it.id == "prism.open" }

        command.run()

        assertEquals("prism", opened)
        assertTrue(EnginePlugins.builtIn.any { it.id == PrismPlugin.id })
    }
}
