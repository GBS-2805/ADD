package app.add.engine

data class EngineCommand(
    val id: String,
    val label: String,
    val group: String,
    val glyph: String,
    val keywords: String = "",
    val enabled: Boolean = true,
    val run: () -> Unit,
)

fun filterCommands(commands: List<EngineCommand>, query: String): List<EngineCommand> {
    val needle = query.trim().lowercase()
    if (needle.isEmpty()) return commands
    return commands.filter { command ->
        command.label.lowercase().contains(needle) ||
            command.keywords.lowercase().contains(needle) ||
            command.group.lowercase().contains(needle)
    }
}
