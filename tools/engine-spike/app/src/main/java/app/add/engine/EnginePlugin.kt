package app.add.engine

import androidx.compose.runtime.Composable

data class EnginePluginCommand(
    val id: String,
    val label: String,
    val keywords: String = "",
)

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
    val close: () -> Unit,
    val hide: () -> Unit,
    val toast: (String) -> Unit,
)

object EnginePlugins {
    val builtIn: List<EnginePlugin> = listOf(TonePlugin)
}
