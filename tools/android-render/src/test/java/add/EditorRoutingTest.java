package add;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import android.app.Activity;
import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

/**
 * The drawer has one Editor row, and the row and the shelf's Editor Tools card
 * reach the editor each build carries. Both branches take an explicit flag, so the
 * routes are asserted without depending on how this harness resolves assets.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class EditorRoutingTest {

    @Test
    public void drawerHasOneEditorRow() {
        ActivityController<MainActivity> controller =
                Robolectric.buildActivity(MainActivity.class).setup();
        MainActivity activity = controller.get();

        View drawer = activity.findViewById(R.id.drawer);
        assertNotNull("missing side panel", drawer);
        assertEquals("the side panel has one Editor row", 1, countEditorRows(drawer));
        assertEquals("a second editor row came back", 0, activity.getResources()
                .getIdentifier("linkEditorWeb", "id", activity.getPackageName()));
        controller.pause().stop().destroy();
    }

    @Test
    public void editorRowOpensTheBundledPage() {
        ActivityController<MainActivity> controller =
                Robolectric.buildActivity(MainActivity.class).setup();
        controller.get().openEditor(true);
        assertBundledPage(controller.get());
        controller.pause().stop().destroy();
    }

    @Test
    public void editorRowOpensQuickWithoutTheBundle() {
        ActivityController<MainActivity> controller =
                Robolectric.buildActivity(MainActivity.class).setup();
        controller.get().openEditor(false);
        assertQuickEditor(controller.get());
        controller.pause().stop().destroy();
    }

    @Test
    public void toolsCardOpensTheBundledPage() {
        ActivityController<ShelfActivity> controller =
                Robolectric.buildActivity(ShelfActivity.class).setup();
        controller.get().routeTool("editor", true);
        assertBundledPage(controller.get());
        controller.pause().stop().destroy();
    }

    @Test
    public void toolsCardOpensQuickWithoutTheBundle() {
        ActivityController<ShelfActivity> controller =
                Robolectric.buildActivity(ShelfActivity.class).setup();
        controller.get().routeTool("editor", false);
        assertQuickEditor(controller.get());
        controller.pause().stop().destroy();
    }

    private static void assertBundledPage(Activity activity) {
        Intent next = Shadows.shadowOf(activity).getNextStartedActivity();
        assertNotNull("the editor entry started nothing", next);
        assertEquals(ShelfActivity.class.getName(), next.getComponent().getClassName());
        assertEquals("editor-web", next.getStringExtra(ShelfActivity.EXTRA_PAGE));
    }

    private static void assertQuickEditor(Activity activity) {
        Intent next = Shadows.shadowOf(activity).getNextStartedActivity();
        assertNotNull("the editor entry started nothing", next);
        assertEquals(EditorActivity.class.getName(), next.getComponent().getClassName());
    }

    private static int countEditorRows(View view) {
        int count = 0;
        if (view instanceof TextView
                && ((TextView) view).getText().toString().trim().equals("Editor")) {
            count++;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                count += countEditorRows(group.getChildAt(i));
            }
        }
        return count;
    }
}
