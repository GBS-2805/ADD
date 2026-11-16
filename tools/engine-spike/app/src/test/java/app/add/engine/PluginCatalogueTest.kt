package app.add.engine

import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private fun cataloguePlugin(
    id: String,
    name: String,
    summary: String,
    commandLabel: String = "",
    commandKeywords: String = "",
) = object : EnginePlugin {
    override val id = id
    override val name = name
    override val glyph = "◆"
    override val summary = summary
    override val commands = if (commandLabel.isBlank()) {
        emptyList()
    } else {
        listOf(EnginePluginCommand("$id.open", commandLabel, commandKeywords))
    }

    @Composable
    override fun Panel(scope: EnginePluginScope) {
        Text(name)
    }
}

class PluginCatalogueTest {
    private val alpha = cataloguePlugin("alpha", "Alpha", "A fill plugin", "Open Alpha", "swatch colour")
    private val beta = cataloguePlugin("beta", "Beta", "A vector plugin", "Open Beta", "shape path")
    private val gamma = cataloguePlugin("gamma", "Gamma", "A field plugin")

    @Test
    fun blankQueryKeepsRecentOrderAndRemovesDuplicates() {
        val ordered = filterPluginCatalogue(
            plugins = listOf(alpha, beta, gamma),
            recentIds = listOf("gamma", "alpha", "gamma"),
            query = "  ",
        )

        assertEquals(listOf("gamma", "alpha", "beta"), ordered.map { it.id })
    }

    @Test
    fun matchesNameSummaryAndCommandTextCaseInsensitively() {
        val plugins = listOf(alpha, beta, gamma)

        assertEquals(listOf("alpha"), filterPluginCatalogue(plugins, emptyList(), "ALPHA").map { it.id })
        assertEquals(listOf("beta"), filterPluginCatalogue(plugins, emptyList(), "vector").map { it.id })
        assertEquals(listOf("alpha"), filterPluginCatalogue(plugins, emptyList(), "swatch colour").map { it.id })
        assertEquals(listOf("beta"), filterPluginCatalogue(plugins, emptyList(), "open beta").map { it.id })
    }

    @Test
    fun requiresEveryTermAndReturnsNoMatchesForUnknownText() {
        val plugins = listOf(alpha, beta, gamma)

        assertEquals(listOf("alpha"), filterPluginCatalogue(plugins, emptyList(), "alpha fill").map { it.id })
        assertTrue(filterPluginCatalogue(plugins, emptyList(), "alpha vector").isEmpty())
        assertTrue(filterPluginCatalogue(plugins, emptyList(), "no-such-plugin").isEmpty())
    }
}
