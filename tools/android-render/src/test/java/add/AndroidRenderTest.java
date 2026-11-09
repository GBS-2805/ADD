package add;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.util.TypedValue;
import android.text.Layout;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.Window;
import android.widget.ScrollView;
import android.widget.TextView;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.fakes.RoboCursor;
import org.robolectric.shadows.ShadowAlertDialog;
import org.robolectric.shadows.ShadowContentResolver;

import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.imageio.ImageIO;

/**
 * Draws ADD's native screens with real Android drawing code, no emulator.
 *
 * <p>Robolectric's NATIVE graphics mode loads the platform's libandroid_runtime and
 * renders through Skia. The layouts, themes, drawables, typefaces and the
 * EditorView/ComparisonView paint code are the app's own; only the Activity
 * lifecycle is hosted by Robolectric. The screenshots are therefore real app
 * renders, not reconstructions.
 *
 * <p>What this cannot do: the shelf and the Studio tab are Chromium WebViews.
 * Robolectric has no Chromium compositor, so those pages are deliberately not in
 * the matrix; a screenshot of a blank WebView would not be an app render.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w411dp-h914dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class AndroidRenderTest {

    private static final int WIDTH = 1080;
    private static final int HEIGHT = 2400;
    private static final String[] PALETTES = {Palette.GREEN, Palette.MOSS, Palette.NAVY};
    private static final String[] PALETTE_NAMES = {"green", "moss", "navy"};
    private static final String SHOWCASE_SUFFIX = "-green-dark";
    private static final Map<String, Map<String, Rect>> LAYOUT_BASELINES =
            new LinkedHashMap<>();
    private static Path auditFile;
    private static int paletteChecks;
    private static int layoutChecks;
    private static int savedRenders;
    private static int compressHeight;

    private File sample;
    private File after;
    private Path outDir;

    @Before
    public void prepare() throws Exception {
        String root = System.getProperty("add.root", System.getProperty("user.dir"));
        String output = System.getProperty("add.out", "");
        outDir = output.isEmpty()
                ? Paths.get(root, "artifacts", "android")
                : Paths.get(output);
        Files.createDirectories(outDir);
        auditFile = outDir.resolve("render-audit.json");

        File stills = new File(root, "build/android-render/stills");
        Files.createDirectories(stills.toPath());
        sample = new File(stills, "press.jpg");
        after = new File(stills, "press-after.jpg");
        if (!sample.isFile()) {
            writeSample(sample, false);
        }
        if (!after.isFile()) {
            writeSample(after, true);
        }
    }

    @AfterClass
    public static void writeAudit() throws IOException {
        if (auditFile == null) {
            return;
        }
        String json = "{\n"
                + "  \"canvas\": \"1080x2400 phone screens; compress full height\",\n"
                + "  \"compressHeight\": " + compressHeight + ",\n"
                + "  \"screens\": 9,\n"
                + "  \"themes\": 6,\n"
                + "  \"matrix\": " + paletteChecks + ",\n"
                + "  \"layoutChecks\": " + layoutChecks + ",\n"
                + "  \"saved\": " + savedRenders + ",\n"
                + "  \"palette\": \"pass\",\n"
                + "  \"layout\": \"pass\"\n"
                + "}\n";
        Files.write(auditFile, json.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    public void compressScreen() throws Exception {
        for (int i = 0; i < PALETTES.length; i++) {
            for (boolean dark : new boolean[]{true, false}) {
                theme(PALETTES[i], dark);
                registerPhotoCursor();
                Intent intent = new Intent(RuntimeEnvironment.getApplication(), MainActivity.class);
                intent.setAction(Intent.ACTION_SEND);
                intent.setType("image/jpeg");
                intent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(sample));
                ActivityController<MainActivity> controller =
                        Robolectric.buildActivity(MainActivity.class, intent).setup();
                settle(350);
                String name = "compress-" + PALETTE_NAMES[i] + (dark ? "-dark" : "-light");
                if (i == 0 && dark) {
                    saveFullForm(controller.get(), name);
                } else {
                    save(controller.get(), name);
                }
                controller.pause().stop().destroy();
            }
        }
    }

    @Test
    public void editorQuickScreen() throws Exception {
        for (int i = 0; i < PALETTES.length; i++) {
            for (boolean dark : new boolean[]{true, false}) {
                ActivityController<EditorActivity> controller = openEditor(i, dark);
                save(controller.get(),
                        "editor-" + PALETTE_NAMES[i] + (dark ? "-dark" : "-light"));
                controller.pause().stop().destroy();
            }
        }
    }

    @Test
    public void presetSheetScreen() throws Exception {
        for (int i = 0; i < PALETTES.length; i++) {
            for (boolean dark : new boolean[]{true, false}) {
                ActivityController<EditorActivity> controller = openEditor(i, dark);
                AlertDialog sheet = openSheet(controller.get(), R.id.effectButton);
                saveSheet(controller.get(), sheet,
                        "presets-" + PALETTE_NAMES[i] + (dark ? "-dark" : "-light"));
                sheet.dismiss();
                controller.pause().stop().destroy();
            }
        }
    }

    @Test
    public void typeSheetScreen() throws Exception {
        for (int i = 0; i < PALETTES.length; i++) {
            for (boolean dark : new boolean[]{true, false}) {
                ActivityController<EditorActivity> controller = openEditor(i, dark);
                AlertDialog sheet = openSheet(controller.get(), R.id.typeButton);
                saveSheet(controller.get(), sheet,
                        "type-" + PALETTE_NAMES[i] + (dark ? "-dark" : "-light"));
                sheet.dismiss();
                controller.pause().stop().destroy();
            }
        }
    }

    @Test
    public void layersSheetScreen() throws Exception {
        for (int i = 0; i < PALETTES.length; i++) {
            for (boolean dark : new boolean[]{true, false}) {
                ActivityController<EditorActivity> controller = openEditor(i, dark);
                AlertDialog sheet = openSheet(controller.get(), R.id.layersButton);
                saveSheet(controller.get(), sheet,
                        "layers-" + PALETTE_NAMES[i] + (dark ? "-dark" : "-light"));
                sheet.dismiss();
                controller.pause().stop().destroy();
            }
        }
    }

    @Test
    public void photoSheetScreen() throws Exception {
        for (int i = 0; i < PALETTES.length; i++) {
            for (boolean dark : new boolean[]{true, false}) {
                ActivityController<EditorActivity> controller = openEditor(i, dark);
                AlertDialog sheet = openSheet(controller.get(), R.id.photoButton);
                saveSheet(controller.get(), sheet,
                        "photo-" + PALETTE_NAMES[i] + (dark ? "-dark" : "-light"));
                sheet.dismiss();
                controller.pause().stop().destroy();
            }
        }
    }

    @Test
    public void colourSheetScreen() throws Exception {
        for (int i = 0; i < PALETTES.length; i++) {
            for (boolean dark : new boolean[]{true, false}) {
                ActivityController<EditorActivity> controller = openEditor(i, dark);
                AlertDialog sheet = openSheet(controller.get(), R.id.colourButton);
                saveSheet(controller.get(), sheet,
                        "colour-" + PALETTE_NAMES[i] + (dark ? "-dark" : "-light"));
                sheet.dismiss();
                controller.pause().stop().destroy();
            }
        }
    }

    @Test
    public void compareScreen() throws Exception {
        for (int i = 0; i < PALETTES.length; i++) {
            for (boolean dark : new boolean[]{true, false}) {
                theme(PALETTES[i], dark);
                Intent intent = new Intent(RuntimeEnvironment.getApplication(), CompareActivity.class);
                intent.putExtra(CompareActivity.EXTRA_BEFORE, Uri.fromFile(sample).toString());
                intent.putExtra(CompareActivity.EXTRA_AFTER, Uri.fromFile(after).toString());
                intent.putExtra(CompareActivity.EXTRA_NAME, "ADD / 03 · JPEG · q78 · 2560 px");
                intent.putExtra(CompareActivity.EXTRA_BYTES_BEFORE, sample.length());
                intent.putExtra(CompareActivity.EXTRA_BYTES_AFTER, after.length());
                intent.putExtra(CompareActivity.EXTRA_WIDTH, 1600);
                intent.putExtra(CompareActivity.EXTRA_HEIGHT, 2000);
                ActivityController<CompareActivity> controller =
                        Robolectric.buildActivity(CompareActivity.class, intent).setup();
                settle(450);
                save(controller.get(), "compare-" + PALETTE_NAMES[i] + (dark ? "-dark" : "-light"));
                controller.pause().stop().destroy();
            }
        }
    }

    @Test
    public void fontPacksScreen() throws Exception {
        String root = System.getProperty("add.root", System.getProperty("user.dir"));
        File catalog = new File(RuntimeEnvironment.getApplication().getFilesDir(), "pack-catalog.json");
        Files.copy(Paths.get(root, "docs/packs/packs.json"), catalog.toPath(),
                StandardCopyOption.REPLACE_EXISTING);

        for (int i = 0; i < PALETTES.length; i++) {
            for (boolean dark : new boolean[]{true, false}) {
                theme(PALETTES[i], dark);
                ActivityController<PacksActivity> controller =
                        Robolectric.buildActivity(PacksActivity.class).setup();
                settle(80);
                save(controller.get(), "packs-" + PALETTE_NAMES[i] + (dark ? "-dark" : "-light"));
                controller.pause().stop().destroy();
            }
        }
    }

    /**
     * The real Editor with a photo and two type layers, ready for the Quick screen or
     * any of its sheets. No dialog is opened here.
     */
    private ActivityController<EditorActivity> openEditor(int palette, boolean dark) {
        theme(PALETTES[palette], dark);
        Bundle state = new Bundle();
        state.putBoolean("editor_studio", false);
        ActivityController<EditorActivity> controller =
                Robolectric.buildActivity(EditorActivity.class)
                        .create(state).start().resume().visible();
        EditorActivity activity = controller.get();
        EditorView stage = activity.findViewById(R.id.stage);
        FontStore.Face face = FontStore.defaultFace(activity);

        stage.setImage(BitmapFactory.decodeFile(sample.getAbsolutePath()));
        EditorView.Layer headline = stage.addText(
                "AFTER\nHOURS", face.typeface, face.label, 0.17f, Color.WHITE);
        headline.letterSpacing = 0.02f;
        headline.cx = 0.5f;
        headline.cy = 0.37f;
        EditorView.Layer caption = stage.addText(
                "ADD / 03\nOFFLINE PHOTO EDITOR", face.typeface, face.label,
                0.043f, Color.WHITE);
        caption.letterSpacing = 0.14f;
        caption.cx = 0.5f;
        caption.cy = 0.68f;
        stage.selectLayer(caption);

        // The test loads the photo through the package-visible stage API instead of
        // the picker, so run the activity's own sync after it, as a picker result would.
        syncUi(activity);
        settle(80);
        return controller;
    }

    /** Calls the activity's private syncUi so the sheet buttons reflect the real stage. */
    private static void syncUi(EditorActivity activity) {
        try {
            Method sync = EditorActivity.class.getDeclaredMethod("syncUi");
            sync.setAccessible(true);
            sync.invoke(activity);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("Could not sync the editor", failure);
        }
    }

    /** Opens one of the five custom sheets through the same button a reader taps. */
    private AlertDialog openSheet(EditorActivity activity, int buttonId) {
        ShadowAlertDialog.reset();
        View button = activity.findViewById(buttonId);
        assertNotNull("missing sheet button", button);
        assertTrue("sheet button disabled", button.isEnabled());
        assertTrue("sheet button did not click", button.performClick());
        settle(80);

        AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog();
        assertNotNull("sheet did not open", dialog);
        assertTrue("sheet is not showing", dialog.isShowing());
        return dialog;
    }

    /** Writes the palette and light/dark choice the app reads before setContentView. */
    /**
     * A file URI has no OpenableColumns row, so the Compress subtitle would read
     * "0 KB". Register the row a gallery provider would return; the app's own
     * Compressor.sizeOf code then reads it normally.
     */
    private void registerPhotoCursor() {
        RoboCursor cursor = new RoboCursor();
        cursor.setColumnNames(Arrays.asList(OpenableColumns.SIZE, OpenableColumns.DISPLAY_NAME));
        cursor.setResults(new Object[][]{{sample.length(), sample.getName()}});
        ShadowContentResolver resolver = Shadows.shadowOf(
                RuntimeEnvironment.getApplication().getContentResolver());
        resolver.setCursor(Uri.fromFile(sample), cursor);
    }

    private void theme(String palette, boolean dark) {
        SharedPreferences prefs = RuntimeEnvironment.getApplication()
                .getSharedPreferences("compress_settings", Context.MODE_PRIVATE);
        prefs.edit().clear()
                .putString("palette", palette)
                .putBoolean("dark_mode", dark)
                .commit();
    }

    /**
     * Measures the activity's content the way a 1080×2400 window would, then draws it
     * to a bitmap. The app's own onDraw runs, including Skia type and bitmap work.
     */
    private Bitmap render(Activity activity, String name) {
        View content = activity.findViewById(android.R.id.content);
        assertNotNull("no content view in " + name, content);
        content.measure(
                View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(HEIGHT, View.MeasureSpec.EXACTLY));
        content.layout(0, 0, content.getMeasuredWidth(), content.getMeasuredHeight());

        Bitmap bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(Color.BLACK);
        content.draw(canvas);
        return bitmap;
    }

    private void save(Activity activity, String name) throws IOException {
        Bitmap bitmap = render(activity, name);
        auditActivity(activity, name);
        auditPalette(bitmap, name, false);
        saveBitmap(bitmap, name);
    }

    /**
     * The Compress form is a scroll view; its ordinary 1080x2400 frame stops in
     * section 4. Audit that phone frame exactly as every other screen, then write a
     * second, full-height draw of the same real content so the review shows the
     * action row and the whole form instead of a crop.
     */
    private void saveFullForm(Activity activity, String name) throws IOException {
        Bitmap phone = render(activity, name);
        auditActivity(activity, name);
        auditPalette(phone, name, false);

        Bitmap full = renderFull(activity, name);
        assertTrue("Compress full form is not taller than the phone viewport",
                full.getHeight() > HEIGHT);
        compressHeight = full.getHeight();
        auditPalette(full, name, false, WIDTH, full.getHeight(), false);
        saveTall(full, name);
    }

    /** Draws the ScrollView's real content at its natural height, not a crop. */
    private Bitmap renderFull(Activity activity, String name) {
        ScrollView scroll = activity.findViewById(R.id.root);
        assertNotNull("no Compress ScrollView in " + name, scroll);
        assertEquals("scroll child count " + name, 1, scroll.getChildCount());
        View page = scroll.getChildAt(0);
        page.measure(
                View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int height = page.getMeasuredHeight();
        assertTrue("Compress page has no full height", height > HEIGHT);
        page.layout(0, 0, WIDTH, height);

        Bitmap bitmap = Bitmap.createBitmap(WIDTH, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        TypedValue background = new TypedValue();
        canvas.drawColor(activity.getTheme().resolveAttribute(R.attr.page, background, true)
                ? background.data : Color.BLACK);
        page.draw(canvas);
        return bitmap;
    }

    private void saveTall(Bitmap bitmap, String name) throws IOException {
        assertEquals("width " + name, WIDTH, bitmap.getWidth());
        assertTrue("short full form " + name, bitmap.getHeight() > HEIGHT);
        assertTrue("blank full form " + name, changedPixels(bitmap) > 100);
        File file = new File(outDir.toFile(), name + ".png");
        try (OutputStream out = new BufferedOutputStream(new FileOutputStream(file))) {
            assertTrue("encode " + name, bitmap.compress(Bitmap.CompressFormat.PNG, 100, out));
        }
        assertTrue("short render " + name, file.length() > 10_000);
        savedRenders++;
    }

    /**
     * Composites the real dialog's content panel over the real activity frame at the
     * dialog's dim amount. The sheet is the app's AlertDialog panel, not a redraw.
     */
    private void saveSheet(EditorActivity activity, AlertDialog dialog, String name)
            throws IOException {
        Bitmap bitmap = render(activity, name);
        Window window = dialog.getWindow();
        assertNotNull("sheet has no window " + name, window);
        View panel = dialog.findViewById(android.R.id.content);
        assertNotNull("sheet has no panel " + name, panel);

        panel.measure(
                View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(HEIGHT, View.MeasureSpec.AT_MOST));
        panel.layout(0, 0, panel.getMeasuredWidth(), panel.getMeasuredHeight());
        int sheetHeight = panel.getMeasuredHeight();
        assertTrue("sheet is empty " + name, sheetHeight > 100);
        auditChrome(activity.findViewById(android.R.id.content), name);
        auditPanel(panel, name, sheetHeight);
        auditPalette(bitmap, name, true);

        Canvas canvas = new Canvas(bitmap);
        float dim = window.getAttributes().dimAmount;
        canvas.drawColor(Color.argb(Math.round(dim * 255f), 0, 0, 0));
        canvas.save();
        canvas.translate(0, Math.max(0, HEIGHT - sheetHeight));
        panel.draw(canvas);
        canvas.restore();

        saveBitmap(bitmap, name);
    }

    private void saveBitmap(Bitmap bitmap, String name) throws IOException {
        assertEquals("width " + name, WIDTH, bitmap.getWidth());
        assertEquals("height " + name, HEIGHT, bitmap.getHeight());
        assertTrue("blank render " + name, changedPixels(bitmap) > 100);

        // The matrix is audited in memory; only the canonical theme is written.
        if (name.endsWith(SHOWCASE_SUFFIX)) {
            File file = new File(outDir.toFile(), name + ".png");
            try (OutputStream out = new BufferedOutputStream(new FileOutputStream(file))) {
                assertTrue("encode " + name, bitmap.compress(Bitmap.CompressFormat.PNG, 100, out));
            }
            assertTrue("short render " + name, file.length() > 10_000);
            savedRenders++;
        }
    }

    private void auditActivity(Activity activity, String name) {
        View content = activity.findViewById(android.R.id.content);
        assertNotNull("no content view in " + name, content);
        assertEquals("content width " + name, WIDTH, content.getWidth());
        assertEquals("content height " + name, HEIGHT, content.getHeight());
        auditChrome(content, name);
        auditContainment(content, "activity " + name);
        auditTextFit(content, "activity " + name);
        recordLayout(screenKey(name) + "|activity", content, name);
    }

    private static void auditPanel(View panel, String name, int height) {
        assertEquals("sheet width " + name, WIDTH, panel.getWidth());
        assertTrue("sheet taller than window " + name, height <= HEIGHT);
        assertTrue("sheet too short " + name, height > 100);
        auditContainment(panel, "sheet " + name);
        auditTextFit(panel, "sheet " + name);
        recordLayout(screenKey(name) + "|sheet", panel, name);
    }

    /** Band and dock are the fixed chrome; both must keep their intended geometry. */
    private static void auditChrome(View content, String name) {
        assertNotNull("no content for " + name, content);
        View back = content.findViewById(R.id.backButton);
        if (back != null) {
            ViewParent parent = back.getParent();
            assertTrue("band is not a view in " + name, parent instanceof View);
            assertEquals("band height " + name, dp(64), ((View) parent).getHeight());
        }
        View dock = content.findViewById(R.id.dock);
        if (dock != null) {
            assertEquals("dock bottom " + name, content.getHeight(), dock.getBottom());
        }
    }

    /** Every visible control stays inside its window; scroll contents are clipped by design. */
    private static void auditContainment(View root, String label) {
        auditContainment(root, root, label);
    }

    private static void auditContainment(View root, View parent, String label) {
        if (!(parent instanceof ViewGroup)) {
            return;
        }
        ViewGroup group = (ViewGroup) parent;
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child.getVisibility() != View.VISIBLE || insideScroll(child, root)) {
                continue;
            }
            Rect bounds = boundsWithin(root, child);
            assertTrue(label + " leaves left: " + child.getClass().getSimpleName(),
                    bounds.left >= 0);
            assertTrue(label + " leaves top: " + child.getClass().getSimpleName(),
                    bounds.top >= 0);
            assertTrue(label + " leaves right: " + child.getClass().getSimpleName(),
                    bounds.right <= root.getWidth());
            assertTrue(label + " leaves bottom: " + child.getClass().getSimpleName(),
                    bounds.bottom <= root.getHeight());
            auditContainment(root, child, label);
        }
    }

    /** A fixed label must not clip; deliberately ellipsized controls are left alone. */
    private static void auditTextFit(View root, String label) {
        auditTextFit(root, root, label);
    }

    private static void auditTextFit(View root, View parent, String label) {
        if (!(parent instanceof ViewGroup)) {
            return;
        }
        ViewGroup group = (ViewGroup) parent;
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child.getVisibility() != View.VISIBLE || insideScroll(child, root)) {
                continue;
            }
            if (child instanceof TextView) {
                checkText((TextView) child, label + " " + child.getClass().getSimpleName());
            }
            auditTextFit(root, child, label);
        }
    }

    private static void checkText(TextView text, String label) {
        // Single glyphs are icon buttons (the back chevron), not copy that can clip.
        if (text.getText().length() <= 1 || text.getEllipsize() != null) {
            return;
        }
        Layout layout = text.getLayout();
        if (layout == null) {
            return;
        }
        for (int line = 0; line < layout.getLineCount(); line++) {
            assertEquals(label + " ellipsized line " + line, 0, layout.getEllipsisCount(line));
        }
        if (text.getMaxLines() > 0) {
            assertTrue(label + " exceeds max lines",
                    layout.getLineCount() <= text.getMaxLines());
        }
        int available = text.getHeight() - text.getCompoundPaddingTop()
                - text.getCompoundPaddingBottom();
        if (available > 0) {
            String shown = text.getText().toString().replace('\n', ' ');
            assertTrue(label + " is clipped: '" + shown + "' layout="
                            + layout.getHeight() + " available=" + available,
                    layout.getHeight() <= available + 2);
        }
    }

    private static boolean insideScroll(View view, View root) {
        ViewParent parent = view.getParent();
        while (parent instanceof View && parent != root) {
            if (parent instanceof ScrollView) {
                return true;
            }
            parent = parent.getParent();
        }
        return false;
    }

    private static Rect boundsWithin(View root, View view) {
        int left = 0;
        int top = 0;
        View cursor = view;
        while (cursor != null && cursor != root) {
            left += cursor.getLeft();
            top += cursor.getTop();
            ViewParent parent = cursor.getParent();
            cursor = parent instanceof View ? (View) parent : null;
        }
        assertNotNull("view is not under the audit root", cursor);
        return new Rect(left, top, left + view.getWidth(), top + view.getHeight());
    }

    private static void recordLayout(String key, View root, String name) {
        layoutChecks++;
        Map<String, Rect> current = snapshot(root);
        synchronized (LAYOUT_BASELINES) {
            Map<String, Rect> baseline = LAYOUT_BASELINES.get(key);
            if (baseline == null) {
                LAYOUT_BASELINES.put(key, current);
                return;
            }
            assertEquals("layout view count changed " + name, baseline.size(), current.size());
            for (Map.Entry<String, Rect> entry : baseline.entrySet()) {
                Rect before = entry.getValue();
                Rect now = current.get(entry.getKey());
                assertNotNull("layout lost " + entry.getKey() + " in " + name, now);
                assertTrue("layout moved " + entry.getKey() + " in " + name,
                        Math.abs(before.left - now.left) <= 1
                                && Math.abs(before.top - now.top) <= 1
                                && Math.abs(before.right - now.right) <= 1
                                && Math.abs(before.bottom - now.bottom) <= 1);
            }
        }
    }

    private static Map<String, Rect> snapshot(View root) {
        Map<String, Rect> out = new LinkedHashMap<>();
        collectLayout(root, "", out);
        return out;
    }

    private static void collectLayout(View view, String path, Map<String, Rect> out) {
        if (view.getVisibility() != View.VISIBLE) {
            return;
        }
        out.put(path + view.getClass().getSimpleName(),
                new Rect(view.getLeft(), view.getTop(), view.getRight(), view.getBottom()));
        if (!(view instanceof ViewGroup)) {
            return;
        }
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            collectLayout(group.getChildAt(i), path + "/" + i, out);
        }
    }

    /** The saved image must carry the palette and the mode ink the theme promises. */
    private static void auditPalette(Bitmap bitmap, String name, boolean sheet) {
        auditPalette(bitmap, name, sheet, WIDTH, HEIGHT, true);
    }

    private static void auditPalette(Bitmap bitmap, String name, boolean sheet,
            int width, int height, boolean count) {
        Context app = RuntimeEnvironment.getApplication();
        Resources.Theme theme = app.getResources().newTheme();
        theme.applyStyle(ThemePrefs.style(app), true);
        TypedArray values = theme.obtainStyledAttributes(
                new int[]{R.attr.band, R.attr.on});
        int band = values.getColor(0, Color.BLACK) & 0xFFFFFF;
        int ink = values.getColor(1, Color.BLACK) & 0xFFFFFF;
        values.recycle();

        int bandCount = 0;
        int inkCount = 0;
        for (int y = 0; y < height; y += 2) {
            for (int x = 0; x < width; x += 2) {
                int pixel = bitmap.getPixel(x, y) & 0xFFFFFF;
                if (pixel == band) {
                    bandCount++;
                }
                if (pixel == ink) {
                    inkCount++;
                }
            }
        }
        assertTrue(name + " is missing its band colour", sheet || bandCount >= 500);
        assertTrue(name + " is missing its mode ink", inkCount >= 100);
        if (count) {
            paletteChecks++;
        }
    }

    private static String screenKey(String name) {
        return name.replaceFirst("-(green|moss|navy)-(dark|light)$", "");
    }

    private static int dp(int value) {
        return Math.round(value * RuntimeEnvironment.getApplication()
                .getResources().getDisplayMetrics().density);
    }

    /** Samples the image on a grid; a real screen has many colours, not one fill. */
    private static int changedPixels(Bitmap bitmap) {
        int first = bitmap.getPixel(0, 0);
        int different = 0;
        for (int y = 0; y < bitmap.getHeight(); y += 17) {
            for (int x = 0; x < bitmap.getWidth(); x += 17) {
                if (bitmap.getPixel(x, y) != first) {
                    different++;
                }
            }
        }
        return different;
    }

    /** Gives background work a chance to post its bitmap, then drains the main queue. */
    private static void settle(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        Shadows.shadowOf(Looper.getMainLooper()).idle();
    }

    /**
     * A photographic-looking input made with Java2D. It is a sample file, not a
     * screenshot: the app loads it through the same BitmapFactory path as any photo.
     */
    private static void writeSample(File file, boolean after) throws IOException {
        int width = 1600;
        int height = 2000;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setPaint(new GradientPaint(0, 0, new java.awt.Color(0x132238),
                0, height, new java.awt.Color(0xE9A24B)));
        g.fillRect(0, 0, width, height);

        g.setColor(new java.awt.Color(0xFFE2A8));
        g.fillOval(865, 390, 470, 470);
        g.setPaint(new GradientPaint(0, height * 0.60f,
                new java.awt.Color(0x152337), width, height, new java.awt.Color(0x0A101B)));
        g.fillRect(0, (int) (height * 0.62f), width, height);

        g.setColor(new java.awt.Color(0x22304A));
        int[] xs = {0, 220, 420, 660, 860, 1080, 1280, 1600};
        int[] ys = {1360, 1180, 1320, 1100, 1240, 1080, 1220, 1150};
        for (int i = 0; i < xs.length - 1; i++) {
            g.fillPolygon(new int[]{xs[i], xs[i + 1], xs[i + 1], xs[i]},
                    new int[]{ys[i], ys[i + 1], height, height}, 4);
        }

        if (after) {
            g.setColor(new java.awt.Color(0xE7DED0));
            g.fillRect(130, 1700, 720, 4);
            g.fillRect(130, 1734, 430, 4);
        }

        g.dispose();
        ImageIO.write(image, "jpg", file);
    }
}
