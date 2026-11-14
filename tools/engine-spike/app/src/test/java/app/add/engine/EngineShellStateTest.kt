package app.add.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EngineShellStateTest {
    private val full = EngineShellState()

    @Test
    fun canvasTouchCollapsesFullToPeekButNotCanvas() {
        assertEquals(ChromeMode.Peek, full.reduce(ShellEvent.CanvasTouched).chrome)
        assertEquals(
            ChromeMode.Canvas,
            EngineShellState(chrome = ChromeMode.Canvas).reduce(ShellEvent.CanvasTouched).chrome,
        )
    }

    @Test
    fun canvasTouchClosesAnOpenSurfaceBeforeChangingChrome() {
        val open = full.reduce(ShellEvent.Open(EngineSurface.Layers))
        val closed = open.reduce(ShellEvent.CanvasTouched)
        assertEquals(EngineSurface.None, closed.surface)
        assertEquals(ChromeMode.Full, closed.chrome)
    }

    @Test
    fun peekRestoresFullChrome() {
        val peek = full.reduce(ShellEvent.ToggleChrome)
        assertEquals(ChromeMode.Peek, peek.chrome)
        assertEquals(ChromeMode.Full, peek.reduce(ShellEvent.PeekTapped).chrome)
    }

    @Test
    fun toggleChromeCyclesAndClosesTheSurface() {
        val peek = full.reduce(ShellEvent.ToggleChrome)
        val canvas = peek.reduce(ShellEvent.ToggleChrome)
        val back = canvas.reduce(ShellEvent.ToggleChrome)
        assertEquals(ChromeMode.Peek, peek.chrome)
        assertEquals(ChromeMode.Canvas, canvas.chrome)
        assertEquals(ChromeMode.Full, back.chrome)
        assertEquals(EngineSurface.None, peek.copy(surface = EngineSurface.Commands)
            .reduce(ShellEvent.ToggleChrome).surface)
    }

    @Test
    fun onlyOneSurfaceIsOpenAtATime() {
        val layers = full.reduce(ShellEvent.Open(EngineSurface.Layers))
        val inspector = layers.reduce(ShellEvent.Open(EngineSurface.Inspector))
        assertEquals(EngineSurface.Inspector, inspector.surface)
        assertEquals(ChromeMode.Full, inspector.chrome)
    }

    @Test
    fun openingAPluginRestoresFullChrome() {
        val canvas = full.reduce(ShellEvent.ToggleChrome).reduce(ShellEvent.ToggleChrome)
        val opened = canvas.reduce(ShellEvent.OpenPlugin("tone"))
        assertEquals(EngineSurface.Plugins, opened.surface)
        assertEquals("tone", opened.pluginId)
        assertEquals(ChromeMode.Full, opened.chrome)
    }

    @Test
    fun hidingAPluginKeepsItsSessionId() {
        val running = full.reduce(ShellEvent.OpenPlugin("tone"))
        val hidden = running.reduce(ShellEvent.HidePlugin)
        assertEquals(EngineSurface.None, hidden.surface)
        assertEquals("tone", hidden.pluginId)
    }

    @Test
    fun closingAPluginClearsItsSession() {
        val running = full.reduce(ShellEvent.OpenPlugin("tone"))
        val closed = running.reduce(ShellEvent.ClosePlugin)
        assertNull(closed.pluginId)
        assertEquals(EngineSurface.Plugins, closed.surface)
    }

    @Test
    fun openingAnotherSurfaceDoesNotDiscardAPluginSession() {
        val running = full.reduce(ShellEvent.OpenPlugin("tone"))
        val layers = running.reduce(ShellEvent.Open(EngineSurface.Layers))
        assertEquals("tone", layers.pluginId)
        assertEquals(EngineSurface.Layers, layers.surface)
    }
}
