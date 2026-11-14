package app.add.engine

/**
 * The BMO shell has one chrome level and at most one open surface. Keeping this
 * reducer pure makes the "never trap the reader" rules testable without Compose.
 */
enum class ChromeMode { Full, Peek, Canvas }

enum class EngineSurface { None, Layers, Inspector, Commands, Plugins }

data class EngineShellState(
    val chrome: ChromeMode = ChromeMode.Full,
    val surface: EngineSurface = EngineSurface.None,
    val pluginId: String? = null,
)

sealed interface ShellEvent {
    data object CanvasTouched : ShellEvent
    data object PeekTapped : ShellEvent
    data object ToggleChrome : ShellEvent
    data class Open(val surface: EngineSurface) : ShellEvent
    data object Dismiss : ShellEvent
    data class OpenPlugin(val id: String) : ShellEvent
    data object HidePlugin : ShellEvent
    data object ClosePlugin : ShellEvent
}

fun EngineShellState.reduce(event: ShellEvent): EngineShellState = when (event) {
    ShellEvent.CanvasTouched -> when {
        surface != EngineSurface.None -> copy(surface = EngineSurface.None)
        chrome == ChromeMode.Full -> copy(chrome = ChromeMode.Peek)
        else -> this
    }
    ShellEvent.PeekTapped -> copy(chrome = ChromeMode.Full)
    ShellEvent.ToggleChrome -> copy(
        chrome = when (chrome) {
            ChromeMode.Full -> ChromeMode.Peek
            ChromeMode.Peek -> ChromeMode.Canvas
            ChromeMode.Canvas -> ChromeMode.Full
        },
        surface = EngineSurface.None,
    )
    is ShellEvent.Open -> copy(surface = event.surface, chrome = ChromeMode.Full)
    ShellEvent.Dismiss -> copy(surface = EngineSurface.None)
    is ShellEvent.OpenPlugin -> copy(
        pluginId = event.id,
        surface = EngineSurface.Plugins,
        chrome = ChromeMode.Full,
    )
    ShellEvent.HidePlugin -> copy(surface = EngineSurface.None)
    ShellEvent.ClosePlugin -> copy(
        pluginId = null,
        // Closing the running plugin leaves the shelf visible; closing it from
        // another surface does not pull that surface away from the reader.
        surface = if (surface == EngineSurface.Plugins) EngineSurface.Plugins else surface,
    )
}
