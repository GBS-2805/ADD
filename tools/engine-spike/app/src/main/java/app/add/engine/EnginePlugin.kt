package app.add.engine

import androidx.compose.runtime.Composable

data class EnginePluginCommand(
    val id: String,
    val label: String,
    val keywords: String = "",
)

/** The only two targets an effect plugin may address. */
enum class EngineEffectTarget(val label: String) {
    Artboard("ARTBOARD"),
    Layer("LAYER"),
}

/**
 * The narrow seam between a plugin and the document. A plugin never writes paint or
 * history directly: this facade owns target validation and routes every change
 * through EngineDocument, so undo, serialization and the no-op rule stay in one place.
 */
interface EnginePluginEffects {
    fun canTarget(target: EngineEffectTarget): Boolean
    fun fieldFor(target: EngineEffectTarget): FieldSpec?
    fun applyFieldPreset(target: EngineEffectTarget, preset: FieldSpec): Boolean
    fun clearField(target: EngineEffectTarget): Boolean
}

class EngineDocumentEffects(private val doc: EngineDocument) : EnginePluginEffects {
    override fun canTarget(target: EngineEffectTarget): Boolean = when (target) {
        EngineEffectTarget.Artboard -> true
        EngineEffectTarget.Layer -> doc.selected is TextLayer || doc.selected is VectorLayer
    }

    override fun fieldFor(target: EngineEffectTarget): FieldSpec? =
        doc.fieldFor(target.toFieldTarget())

    override fun applyFieldPreset(target: EngineEffectTarget, preset: FieldSpec): Boolean {
        if (!canTarget(target)) return false
        val clean = preset.normalized()
        // Re-applying the current recipe must be a true no-op, not a history entry
        // that makes the undo sheet claim something changed.
        if (fieldFor(target)?.normalized() == clean) return true
        doc.setFieldPreset(target.toFieldTarget(), clean)
        return true
    }

    override fun clearField(target: EngineEffectTarget): Boolean {
        if (!canTarget(target)) return false
        if (fieldFor(target) == null) return false
        doc.clearField(target.toFieldTarget())
        return true
    }
}

private fun EngineEffectTarget.toFieldTarget(): FieldTarget = when (this) {
    EngineEffectTarget.Artboard -> FieldTarget.Background
    EngineEffectTarget.Layer -> FieldTarget.Layer
}

interface EnginePlugin {
    val id: String
    val name: String
    val glyph: String
    val summary: String
    val commands: List<EnginePluginCommand>

    @Composable
    fun Panel(scope: EnginePluginScope)
}

data class EnginePluginScope(
    val doc: EngineDocument,
    val effects: EnginePluginEffects,
    val close: () -> Unit,
    val hide: () -> Unit,
    val toast: (String) -> Unit,
)

/** One source for the command-sheet rows contributed by the compiled-in plugins. */
fun enginePluginCommands(
    plugins: List<EnginePlugin>,
    onOpen: (String) -> Unit,
): List<EngineCommand> = plugins.flatMap { plugin ->
    plugin.commands.map { command ->
        EngineCommand(
            id = command.id,
            label = command.label,
            group = "Plugins",
            glyph = plugin.glyph,
            keywords = command.keywords,
        ) { onOpen(plugin.id) }
    }
}

object EnginePlugins {
    val builtIn: List<EnginePlugin> = listOf(TonePlugin, PrismPlugin)
}
