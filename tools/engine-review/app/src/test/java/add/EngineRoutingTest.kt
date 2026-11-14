package add

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class EngineRoutingTest {
    private val root: File = generateSequence(File(System.getProperty("user.dir"))) { it.parentFile }
        .first { File(it, "app/src/add/EditorActivity.java").isFile }

    private fun text(path: String): String = File(root, path).readText()

    @Test
    fun studioRouteStartsTheEngineActivity() {
        val editor = text("app/src/add/EditorActivity.java")
        assertTrue(editor.contains("app.add.engine.MainActivity"))
        assertTrue(editor.contains("showStudio"))
    }

    @Test
    fun engineActivityRunsInItsOwnProcess() {
        val manifest = text("tools/engine-review/app/src/main/AndroidManifest.xml")
        assertTrue(manifest.contains("android:name=\"app.add.engine.MainActivity\""))
        assertTrue(manifest.contains("android:process=\":engine\""))
    }

    @Test
    fun releaseBuildDoesNotExposeTheEngineLabDoor() {
        val editor = text("app/src/add/EditorActivity.java")
        assertFalse(editor.contains("DevFlags.engineLab"))
    }

    @Test
    fun studioPaneClassIsGone() {
        assertFalse(File(root, "app/src/add/StudioPane.java").exists())
    }

    @Test
    fun studioAssetsAreAbsentFromTheReleaseTree() {
        assertFalse(File(root, "app/studio-assets").exists())
        assertFalse(File(root, "app/assets/www/studio").exists())
    }

    @Test
    fun savedLooksAreReachableThroughTheEngineLibrary() {
        val dialogs = text("tools/engine-spike/app/src/main/java/app/add/engine/EngineDialogs.kt")
        assertTrue(dialogs.contains("LEGACY LOOKS"))
        assertTrue(dialogs.contains("LegacyLookMigration.migrate"))
    }
}
