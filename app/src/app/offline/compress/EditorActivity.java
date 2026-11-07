package app.offline.compress;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.DisplayCutout;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * The editor: a photo, type set in the bundled faces or an imported pack, seven photo
 * presets and the reader's own saved looks, five live adjustments, reorderable
 * layers, and an export to Pictures/Editor. QUICK and STUDIO are tabs in this one
 * activity, so the native tools and the Fabric canvas never open a second editor.
 *
 * Nothing here needs a permission. The photo and the pack arrive through the system
 * picker, which hands over one document at a time, and the export goes through
 * MediaStore. This activity does not touch the network; the only network code in ADD
 * is the Font packs screen.
 */
public class EditorActivity extends Activity {

    private static final int REQ_PHOTO = 11;
    private static final int REQ_PACKS = 12;

    private static final int MIN_PT = 10;
    private static final int MAX_PT = 120;
    /** The SeekBar's 10..120 becomes 2.5%..30% of the stage's shorter edge. */
    private static final float PT_SCALE = 400f;

    private EditorView stage;
    private TextView fontButton;
    private TextView sizeValue;
    private TextView colourButton;
    private TextView photoButton;
    private TextView exportButton;
    private TextView effectButton;
    private TextView adjustButton;
    private TextView layersButton;
    private TextView typeButton;
    private SeekBar sizeBar;
    private TextView undoLookButton;
    private TextView quickTab;
    private TextView studioTab;
    private TextView editorSubtitle;
    private View dock;
    private FrameLayout studioHost;
    private StudioPane studioPane;
    private boolean studioOpen;
    /** The look application still reversible from the stage, if any. */
    private LookUndo pendingUndo;

    private List<FontStore.Face> faces;
    private FontStore.Face currentFace;
    private int currentColour = Color.BLACK;
    private boolean colourPicked;
    private boolean syncing;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        ThemePrefs.applyTheme(this);
        setContentView(R.layout.activity_editor);
        applyInsets();

        stage = findViewById(R.id.stage);
        photoButton = findViewById(R.id.photoButton);
        exportButton = findViewById(R.id.exportButton);
        fontButton = findViewById(R.id.fontButton);
        sizeValue = findViewById(R.id.sizeValue);
        colourButton = findViewById(R.id.colourButton);
        effectButton = findViewById(R.id.effectButton);
        adjustButton = findViewById(R.id.adjustButton);
        layersButton = findViewById(R.id.layersButton);
        typeButton = findViewById(R.id.typeButton);
        sizeBar = findViewById(R.id.sizeBar);
        undoLookButton = findViewById(R.id.undoLookButton);
        undoLookButton.setOnClickListener(v -> undoSavedLook());
        quickTab = findViewById(R.id.quickTab);
        studioTab = findViewById(R.id.studioTab);
        editorSubtitle = findViewById(R.id.editorSubtitle);
        dock = findViewById(R.id.dock);
        studioHost = findViewById(R.id.studioHost);
        quickTab.setOnClickListener(v -> showQuick());
        studioTab.setOnClickListener(v -> showStudio());

        faces = FontStore.faces(this);
        currentFace = FontStore.defaultFace(this);
        stage.setSelectionColour(colour(R.attr.accent, Color.WHITE));
        showFace();

        findViewById(R.id.backButton).setOnClickListener(v -> {
            if (studioOpen) {
                showQuick();
            } else {
                finish();
            }
        });
        photoButton.setOnClickListener(v -> photoDialog());
        findViewById(R.id.addTextButton).setOnClickListener(v -> textDialog(null));
        exportButton.setOnClickListener(v -> exportDialog());
        fontButton.setOnClickListener(v -> fontDialog());
        effectButton.setOnClickListener(v -> presetDialog());
        adjustButton.setOnClickListener(v -> adjustDialog());
        layersButton.setOnClickListener(v -> layersDialog());
        typeButton.setOnClickListener(v -> typeDialog());
        colourButton.setOnClickListener(v -> colourDialog(false));
        findViewById(R.id.deleteButton).setOnClickListener(v -> {
            stage.removeSelected();
            syncUi();
        });

        sizeBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                sizeValue.setText(String.valueOf(progress));
                if (!syncing && stage.selected() != null) {
                    stage.setSize(progress / PT_SCALE);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar bar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar bar) {
            }
        });

        stage.setListener(new EditorView.Listener() {
            @Override
            public void onSelect(EditorView.Layer layer) {
                syncUi();
            }

            @Override
            public void onEdit(EditorView.Layer layer) {
                textDialog(layer);
            }

            @Override
            public void onChange() {
                clearUndo();
            }
        });

        if (state == null) {
            // A new document: one line of type so the stage is never blank on arrival.
            stage.addText("ART DESIGN", currentFace.typeface, currentFace.label, sizeFrac(), currentColour);
        }
        syncUi();
        restoreTab(state);
    }

    /** Studio is the starting tab; Quick is one tap away. */
    private void restoreTab(Bundle state) {
        boolean studio = state == null || state.getBoolean("editor_studio", true);
        if (studio) {
            showStudio();
        } else {
            showQuick();
        }
    }

    private void paintTabs(boolean quick) {
        quickTab.setBackgroundResource(quick ? R.drawable.bg_chip : R.drawable.bg_ghost);
        quickTab.setTextColor(colour(quick ? R.attr.accentInk : R.attr.on, Color.WHITE));
        studioTab.setBackgroundResource(quick ? R.drawable.bg_ghost : R.drawable.bg_chip);
        studioTab.setTextColor(colour(quick ? R.attr.on : R.attr.accentInk, Color.WHITE));
    }

    private void showQuick() {
        studioOpen = false;
        if (studioPane != null) {
            studioPane.pause();
        }
        stage.setVisibility(View.VISIBLE);
        dock.setVisibility(View.VISIBLE);
        studioHost.setVisibility(View.GONE);
        photoButton.setVisibility(View.VISIBLE);
        exportButton.setVisibility(View.VISIBLE);
        undoLookButton.setVisibility(pendingUndo == null ? View.GONE : View.VISIBLE);
        editorSubtitle.setText("QUICK TOOLS \u00b7 OFFLINE");
        paintTabs(true);
    }

    private void showStudio() {
        studioOpen = true;
        stage.setVisibility(View.GONE);
        dock.setVisibility(View.GONE);
        photoButton.setVisibility(View.GONE);
        exportButton.setVisibility(View.GONE);
        undoLookButton.setVisibility(View.GONE);
        studioHost.setVisibility(View.VISIBLE);
        if (studioPane == null) {
            studioPane = new StudioPane(this);
            studioPane.setCloseListener(this::showQuick);
            studioHost.addView(studioPane, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
        studioPane.resume();
        editorSubtitle.setText("STUDIO \u00b7 FABRIC+");
        paintTabs(false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (studioPane != null) {
            studioPane.resume();
        }
        List<FontStore.Face> refreshed = FontStore.faces(this);
        faces = refreshed;
        if (currentFace != null && currentFace.imported()
                && FontStore.find(refreshed, currentFace.label) == null) {
            currentFace = FontStore.defaultFace(this);
            showFace();
        }
    }

    @Override
    protected void onPause() {
        if (studioPane != null) {
            studioPane.pause();
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (studioPane != null) {
            studioPane.destroy();
            studioPane = null;
        }
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (studioOpen) {
            showQuick();
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putBoolean("editor_studio", studioOpen);
    }

    private void applyInsets() {
        final View root = findViewById(R.id.editorRoot);
        root.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override
            public WindowInsets onApplyWindowInsets(View view, WindowInsets insets) {
                int top = insets.getSystemWindowInsetTop();
                if (android.os.Build.VERSION.SDK_INT >= 28) {
                    DisplayCutout cutout = insets.getDisplayCutout();
                    if (cutout != null) {
                        top = Math.max(top, cutout.getSafeInsetTop());
                    }
                }
                view.setPadding(insets.getSystemWindowInsetLeft(), top,
                        insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
                return insets;
            }
        });
        root.requestApplyInsets();
    }

    private float sizeFrac() {
        return Math.max(MIN_PT, Math.min(MAX_PT, sizeBar.getProgress())) / PT_SCALE;
    }

    private void showFace() {
        fontButton.setText(currentFace.label);
        fontButton.setTextColor(colour(R.attr.accentInk, Color.WHITE));
        colourButton.setTextColor(currentColour);
    }

    /** The look chip reports the active preset, or CUSTOM after a hand adjustment. */
    private void showLook() {
        String name = stage.presetName();
        effectButton.setText(name.toUpperCase(Locale.US));
        boolean hasPhoto = stage.hasImage();
        effectButton.setEnabled(hasPhoto);
        effectButton.setAlpha(hasPhoto ? 1f : 0.45f);
        effectButton.setContentDescription(hasPhoto
                ? "Look: " + name + ". Tap to choose a preset."
                : "Load a photo to choose a preset");
        adjustButton.setEnabled(hasPhoto);
        adjustButton.setAlpha(hasPhoto ? 1f : 0.45f);
        adjustButton.setContentDescription(hasPhoto
                ? "Adjust the photo" : "Load a photo to adjust it");
        boolean hasLayers = stage.layerCount() > 0;
        layersButton.setEnabled(hasLayers);
        layersButton.setAlpha(hasLayers ? 1f : 0.45f);
        layersButton.setContentDescription(hasLayers
                ? "Reorder text layers" : "No text layers yet");
        typeButton.setEnabled(hasLayers);
        typeButton.setAlpha(hasLayers ? 1f : 0.45f);
        typeButton.setContentDescription(hasLayers
                ? "Text style, spacing and opacity" : "No text layers yet");
    }

    /** The bars and labels follow the selection, so a dragged line reports itself. */
    private void syncUi() {
        showFace();
        showLook();
        EditorView.Layer layer = stage.selected();
        if (layer == null) {
            sizeValue.setText(String.valueOf(sizeBar.getProgress()));
            return;
        }
        syncing = true;
        int progress = Math.max(MIN_PT, Math.min(MAX_PT, Math.round(layer.sizeFrac * PT_SCALE)));
        sizeBar.setProgress(progress);
        sizeValue.setText(String.valueOf(progress));
        syncing = false;
        fontButton.setText(layer.faceLabel);
        colourButton.setTextColor(layer.colour);
    }

    // ---- photo ----------------------------------------------------------------

    private void pickPhoto() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivityForResult(intent, REQ_PHOTO);
    }

    private void loadPhoto(final Uri uri) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                final Bitmap bitmap;
                try {
                    bitmap = decode(uri);
                } catch (IOException | OutOfMemoryError bad) {
                    runOnUiThread(() -> Toast.makeText(EditorActivity.this,
                            "That photo could not be opened", Toast.LENGTH_LONG).show());
                    return;
                }
                runOnUiThread(() -> {
                    if (bitmap == null || isFinishing()) {
                        return;
                    }
                    stage.setImage(bitmap);
                    if (!colourPicked) {
                        currentColour = Color.WHITE;
                        EditorView.Layer selected = stage.selected();
                        if (selected != null) {
                            selected.colour = currentColour;
                        }
                        stage.invalidate();
                    }
                    syncUi();
                });
            }
        }).start();
    }

    /** Bounds first, then a sample no larger than 2048; EXIF rotation is applied. */
    private Bitmap decode(Uri uri) throws IOException {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            BitmapFactory.decodeStream(in, null, bounds);
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return null;
        }
        int sample = 1;
        while (Math.max(bounds.outWidth, bounds.outHeight) / sample > 2048) {
            sample *= 2;
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample;
        Bitmap bitmap;
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            bitmap = BitmapFactory.decodeStream(in, null, options);
        }
        if (bitmap == null) {
            return null;
        }
        int rotation = 0;
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            ExifInterface exif = new ExifInterface(in);
            int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL);
            if (orientation == ExifInterface.ORIENTATION_ROTATE_90) {
                rotation = 90;
            } else if (orientation == ExifInterface.ORIENTATION_ROTATE_180) {
                rotation = 180;
            } else if (orientation == ExifInterface.ORIENTATION_ROTATE_270) {
                rotation = 270;
            }
        } catch (IOException ignored) {
            // No EXIF is not a failure: the pixels are already upright often enough.
        }
        if (rotation == 0) {
            return bitmap;
        }
        Matrix matrix = new Matrix();
        matrix.postRotate(rotation);
        Bitmap turned = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(),
                matrix, true);
        if (turned != bitmap) {
            bitmap.recycle();
        }
        return turned;
    }

    // ---- text ----------------------------------------------------------------

    private void textDialog(final EditorView.Layer layer) {
        EditText input = new EditText(this);
        input.setText(layer == null ? "" : layer.text);
        input.setHint("Type a line");
        input.setHintTextColor(colour(R.attr.onDim, Color.GRAY));
        input.setTextColor(colour(R.attr.on, Color.WHITE));
        input.setPadding(dp(20), dp(10), dp(20), dp(10));
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(layer == null ? "Add text" : "Edit text")
                .setView(input)
                .setPositiveButton("Set", (which, unused) -> {
                    String text = input.getText().toString().trim();
                    if (text.isEmpty()) {
                        text = "Text";
                    }
                    if (layer == null) {
                        stage.addText(text, currentFace.typeface, currentFace.label,
                                sizeFrac(), currentColour);
                    } else {
                        stage.editText(layer, text);
                    }
                    syncUi();
                })
                .setNegativeButton("Cancel", null)
                .create();
        showDialog(dialog);
    }

    // ---- fonts ---------------------------------------------------------------

    private void fontDialog() {
        faces = FontStore.faces(this);
        String[] labels = new String[faces.size()];
        int checked = -1;
        EditorView.Layer selected = stage.selected();
        String active = selected == null ? currentFace.label : selected.faceLabel;
        for (int i = 0; i < faces.size(); i++) {
            labels[i] = faces.get(i).label;
            if (labels[i].equals(active)) {
                checked = i;
            }
        }
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Font")
                .setSingleChoiceItems(labels, checked, (picker, which) -> {
                    currentFace = faces.get(which);
                    if (stage.selected() != null) {
                        stage.setFace(currentFace.typeface, currentFace.label);
                    }
                    showFace();
                    picker.dismiss();
                })
                .setNeutralButton("Font packs\u2026", (picker, which) -> {
                    picker.dismiss();
                    startActivityForResult(
                            new Intent(EditorActivity.this, PacksActivity.class), REQ_PACKS);
                })
                .setPositiveButton("Close", null)
                .create();
        showDialog(dialog);
    }

    // ---- look ----------------------------------------------------------------

    /**
     * A colour picker that does not stop at six swatches: a broad palette, a live hex
     * field and RGB sliders. The same sheet paints text or the canvas behind a photo.
     */
    private void colourDialog(final boolean canvas) {
        final int initial = canvas ? stage.canvasColour()
                : (stage.selected() == null ? currentColour : stage.selected().colour);
        final int[] palette = {
                0xFFFFFFFF, 0xFFF2EEE3, 0xFFD9D3C7, 0xFFAAA79E, 0xFF6B6A64, 0xFF131513,
                0xFFFFE9C7, 0xFFFFC98A, 0xFFFFA15C, 0xFFE2553F, 0xFFB63A2B, 0xFF7A241B,
                0xFFFFF1A8, 0xFFF2C14E, 0xFFBFD96A, 0xFF6FBF73, 0xFF0FBF92, 0xFF0B7A63,
                0xFFD7F0F2, 0xFF9AD7E8, 0xFF72B6B4, 0xFF7FB2F0, 0xFF4D7BD6, 0xFF1C1D2F,
                0xFFFFDCE6, 0xFFF7B6C8, 0xFFE87A9A, 0xFFC45C9C, 0xFF8E5BB5, 0xFF3A234D,
                0xFFF4E3FF, 0xFFD9B8F2, 0xFFB38BD9, 0xFF7F5BA6, 0xFF4C356B, 0xFF2A1D3D
        };
        final int[] draft = {initial};
        final boolean[] guard = {false};

        LinearLayout content = sheetBody(canvas ? "Canvas colour" : "Text colour");
        content.addView(note(canvas
                ? "Paints the card behind the type. A loaded photo fills it, so this shows "
                        + "on a document without one."
                : (stage.selected() == null
                        ? "Applies to the next line you add. Pick a line on the canvas to "
                                + "recolour it, or mix any colour here."
                        : "Mix on the wheel, tap a swatch, or type a hex value.")));

        // The sheet is only as tall as its controls: the palette, the hex field and
        // all three RGB rows sit above the action row, with no pane to scroll and no
        // control hidden behind the buttons. A short window lets the dialog scroll.
        LinearLayout stack = new LinearLayout(this);
        stack.setOrientation(LinearLayout.VERTICAL);
        content.addView(stack, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // The mixer: a hue/saturation wheel beside the hex field and RGB bars, so the
        // sheet gains a real wheel without growing taller than the window.
        final ColourWheel wheel = new ColourWheel(this);
        wheel.setColour(initial);
        LinearLayout mixer = new LinearLayout(this);
        mixer.setOrientation(LinearLayout.HORIZONTAL);
        mixer.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams wheelParams =
                new LinearLayout.LayoutParams(dp(140), dp(140));
        wheelParams.setMargins(0, dp(4), dp(12), dp(4));
        mixer.addView(wheel, wheelParams);
        final LinearLayout side = new LinearLayout(this);
        side.setOrientation(LinearLayout.VERTICAL);
        mixer.addView(side, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        stack.addView(mixer, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        final SeekBar bright = new SeekBar(this);
        bright.setMax(100);
        bright.setPadding(dp(4), 0, dp(4), 0);
        final TextView brightValue = valueLabel();
        LinearLayout brightRow = new LinearLayout(this);
        brightRow.setOrientation(LinearLayout.HORIZONTAL);
        brightRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView brightName = new TextView(this);
        brightName.setText("BRIGHT");
        brightName.setTextColor(colour(R.attr.onDim, Color.GRAY));
        brightName.setTextSize(10);
        brightName.setLetterSpacing(0.08f);
        brightRow.addView(brightName, new LinearLayout.LayoutParams(dp(56), dp(36)));
        brightRow.addView(bright, new LinearLayout.LayoutParams(0, dp(36), 1f));
        brightRow.addView(brightValue, new LinearLayout.LayoutParams(dp(38), dp(36)));
        LinearLayout.LayoutParams brightParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        brightParams.setMargins(0, dp(2), 0, 0);
        stack.addView(brightRow, brightParams);

        final Runnable[] repaint = new Runnable[1];
        final GridLayout grid = new GridLayout(this);
        grid.setColumnCount(9);
        final View[] dots = new View[palette.length];
        for (int i = 0; i < palette.length; i++) {
            final int swatchColour = palette[i];
            final View dot = new View(this);
            dot.setBackground(swatch(swatchColour, swatchColour == draft[0]));
            dot.setContentDescription("Colour " + colourHex(swatchColour));
            dot.setClickable(true);
            dot.setFocusable(true);
            dot.setOnClickListener(v -> {
                draft[0] = swatchColour;
                wheel.setColour(swatchColour);
                if (repaint[0] != null) {
                    repaint[0].run();
                }
            });
            dots[i] = dot;
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = 0;
            params.height = dp(32);
            params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            params.setMargins(dp(2), dp(2), dp(2), dp(2));
            grid.addView(dot, params);
        }
        stack.addView(grid, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        final EditText hex = new EditText(this);
        hex.setSingleLine(true);
        hex.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        hex.setFilters(new InputFilter[]{new InputFilter.LengthFilter(7)});
        hex.setText(colourHex(initial));
        hex.setTextColor(colour(R.attr.on, Color.WHITE));
        hex.setTextSize(14);
        hex.setPadding(dp(10), 0, dp(10), 0);
        GradientDrawable field = new GradientDrawable();
        field.setColor(colour(R.attr.panel2, Color.DKGRAY));
        field.setCornerRadius(dp(8));
        field.setStroke(dp(1), colour(R.attr.rule, Color.GRAY));
        hex.setBackground(field);
        LinearLayout hexRow = new LinearLayout(this);
        hexRow.setOrientation(LinearLayout.HORIZONTAL);
        hexRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView hexLabel = new TextView(this);
        hexLabel.setText("HEX");
        hexLabel.setTextColor(colour(R.attr.onDim, Color.GRAY));
        hexLabel.setTextSize(10);
        hexLabel.setLetterSpacing(0.08f);
        hexRow.addView(hexLabel, new LinearLayout.LayoutParams(dp(42), dp(40)));
        hexRow.addView(hex, new LinearLayout.LayoutParams(0, dp(40), 1f));
        LinearLayout.LayoutParams hexParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hexParams.setMargins(0, dp(4), 0, 0);
        side.addView(hexRow, hexParams);

        final SeekBar red = new SeekBar(this);
        final SeekBar green = new SeekBar(this);
        final SeekBar blue = new SeekBar(this);
        for (SeekBar bar : new SeekBar[]{red, green, blue}) {
            bar.setMax(255);
            bar.setPadding(dp(4), 0, dp(4), 0);
        }
        final TextView redValue = valueLabel();
        final TextView greenValue = valueLabel();
        final TextView blueValue = valueLabel();
        side.addView(rgbRow("R", red, redValue));
        side.addView(rgbRow("G", green, greenValue));
        side.addView(rgbRow("B", blue, blueValue));

        SeekBar.OnSeekBarChangeListener rgbListener = new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (guard[0]) {
                    return;
                }
                int value = Color.rgb(red.getProgress(), green.getProgress(), blue.getProgress());
                draft[0] = value;
                wheel.setColour(value);
                paintColourDraft(value, false, guard, hex, red, green, blue,
                        dots, palette, bright, brightValue);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        };
        red.setOnSeekBarChangeListener(rgbListener);
        green.setOnSeekBarChangeListener(rgbListener);
        blue.setOnSeekBarChangeListener(rgbListener);

        hex.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (guard[0]) {
                    return;
                }
                String raw = s.toString().trim();
                if (!raw.startsWith("#")) {
                    raw = "#" + raw;
                }
                if (raw.length() != 7) {
                    return;
                }
                try {
                    int value = Color.parseColor(raw);
                    draft[0] = value;
                    wheel.setColour(value);
                    paintColourDraft(value, true, guard, hex, red, green, blue,
                            dots, palette, bright, brightValue);
                } catch (IllegalArgumentException ignored) {
                    // Keep the last valid colour while the reader is still typing.
                }
            }
        });

        bright.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (guard[0]) {
                    return;
                }
                wheel.setBrightness(progress / 100f);
                int value = wheel.colour();
                draft[0] = value;
                paintColourDraft(value, false, guard, hex, red, green, blue,
                        dots, palette, bright, brightValue);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        wheel.setListener(colour -> {
            // A wheel touch at zero brightness would pick a black; lift the value first
            // so the thumb always produces a colour the reader can see.
            if (wheel.brightness() < 0.05f) {
                wheel.setBrightness(1f);
            }
            draft[0] = wheel.colour();
            paintColourDraft(draft[0], false, guard, hex, red, green, blue,
                    dots, palette, bright, brightValue);
        });

        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(content)
                .create();
        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setPadding(0, dp(6), 0, dp(2));
        TextView cancel = chip("CANCEL");
        TextView use = chip("USE COLOUR");
        use.setBackgroundResource(R.drawable.bg_chip);
        use.setTextColor(colour(R.attr.accentInk, Color.BLACK));
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(0, dp(42), 1f);
        actionParams.setMargins(dp(3), 0, dp(3), 0);
        actions.addView(cancel, actionParams);
        actions.addView(use, actionParams);
        content.addView(actions, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        cancel.setOnClickListener(v -> dialog.dismiss());
        use.setOnClickListener(v -> {
            if (canvas) {
                stage.setCanvasColour(draft[0]);
            } else {
                currentColour = draft[0];
                colourPicked = true;
                stage.setColour(draft[0]);
                colourButton.setTextColor(draft[0]);
            }
            syncUi();
            dialog.dismiss();
        });

        repaint[0] = () -> paintColourDraft(draft[0], false, guard, hex,
                red, green, blue, dots, palette, bright, brightValue);
        paintColourDraft(initial, false, guard, hex, red, green, blue,
                dots, palette, bright, brightValue);
        showDialog(dialog);
    }

    /** Repaint the preview, swatches, hex field and RGB bars without feedback loops. */
    private void paintColourDraft(int colour, boolean fromHex, boolean[] guard,
                                  EditText hex, SeekBar red, SeekBar green, SeekBar blue,
                                  View[] dots, int[] palette,
                                  SeekBar bright, TextView brightValue) {
        guard[0] = true;
        if (hex != null && !fromHex) {
            hex.setText(colourHex(colour));
        }
        if (red != null) {
            red.setProgress(Color.red(colour));
            green.setProgress(Color.green(colour));
            blue.setProgress(Color.blue(colour));
        }
        for (int i = 0; i < dots.length; i++) {
            dots[i].setBackground(swatch(palette[i], palette[i] == colour));
        }
        if (bright != null || brightValue != null) {
            float[] hsv = new float[3];
            Color.colorToHSV(colour, hsv);
            int percent = Math.round(hsv[2] * 100);
            if (bright != null) {
                bright.setProgress(percent);
            }
            if (brightValue != null) {
                brightValue.setText(String.valueOf(percent));
            }
        }
        guard[0] = false;
    }

    private GradientDrawable swatch(int colour, boolean selected) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(colour);
        shape.setCornerRadius(dp(8));
        shape.setStroke(dp(selected ? 3 : 1),
                colour(selected ? R.attr.accent : R.attr.rule, Color.GRAY));
        return shape;
    }

    private String colourHex(int colour) {
        return String.format(Locale.US, "#%06X", 0xFFFFFF & colour);
    }

    private TextView valueLabel() {
        TextView value = new TextView(this);
        value.setTextColor(colour(R.attr.on, Color.WHITE));
        value.setTextSize(11);
        value.setGravity(Gravity.END);
        return value;
    }

    private LinearLayout rgbRow(String label, SeekBar bar, TextView value) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView name = new TextView(this);
        name.setText(label);
        name.setTextColor(colour(R.attr.onDim, Color.GRAY));
        name.setTextSize(10);
        name.setLetterSpacing(0.08f);
        row.addView(name, new LinearLayout.LayoutParams(dp(20), dp(36)));
        row.addView(bar, new LinearLayout.LayoutParams(0, dp(36), 1f));
        row.addView(value, new LinearLayout.LayoutParams(dp(34), dp(36)));
        return row;
    }

    /**
     * Type controls that are more than a colour: alignment, tracking, leading,
     * opacity and shadow, all live on the selected line.
     */
    private void typeDialog() {
        final EditorView.Layer layer = stage.selected();
        if (layer == null) {
            textDialog(null);
            return;
        }

        LinearLayout content = sheetBody("Type");
        content.addView(note("Live controls for the selected line. Changes apply to this line only."));

        TextView face = chip(layer.faceLabel.toUpperCase(Locale.US));
        face.setOnClickListener(v -> fontDialog());
        addFull(content, face, 42, 6);

        LinearLayout alignRow = new LinearLayout(this);
        alignRow.setOrientation(LinearLayout.HORIZONTAL);
        String[] names = {"LEFT", "CENTER", "RIGHT"};
        final TextView[] alignButtons = new TextView[names.length];
        for (int i = 0; i < names.length; i++) {
            final int index = i;
            final TextView button = chip(names[i]);
            button.setBackgroundResource(i == layer.align ? R.drawable.bg_chip : R.drawable.bg_ghost);
            button.setTextColor(colour(i == layer.align ? R.attr.accentInk : R.attr.on, Color.WHITE));
            button.setOnClickListener(v -> {
                stage.setAlign(index);
                for (int j = 0; j < alignButtons.length; j++) {
                    boolean active = j == index;
                    alignButtons[j].setBackgroundResource(active
                            ? R.drawable.bg_chip : R.drawable.bg_ghost);
                    alignButtons[j].setTextColor(colour(active
                            ? R.attr.accentInk : R.attr.on, Color.WHITE));
                }
            });
            alignButtons[i] = button;
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(40), 1f);
            params.setMargins(dp(2), dp(6), dp(2), 0);
            alignRow.addView(button, params);
        }
        content.addView(alignRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        addSlider(content, "LETTER SPACING", -2, 30,
                Math.round(layer.letterSpacing * 100),
                value -> String.format(Locale.US, "%+.2f", value / 100f),
                value -> stage.setLetterSpacing(value / 100f));
        addSlider(content, "LINE HEIGHT", 80, 180,
                Math.round(layer.lineSpacing * 100),
                value -> String.format(Locale.US, "%.2f\u00d7", value / 100f),
                value -> stage.setLineSpacing(value / 100f));
        addSlider(content, "OPACITY", 0, 255, layer.opacity,
                value -> Math.round(value * 100f / 255f) + "%",
                value -> stage.setOpacity(value));

        LinearLayout lower = new LinearLayout(this);
        lower.setOrientation(LinearLayout.HORIZONTAL);
        final TextView shadow = chip(layer.shadow ? "SHADOW ON" : "SHADOW OFF");
        shadow.setBackgroundResource(layer.shadow ? R.drawable.bg_chip : R.drawable.bg_ghost);
        shadow.setTextColor(colour(layer.shadow ? R.attr.accentInk : R.attr.on, Color.WHITE));
        shadow.setOnClickListener(v -> {
            boolean enabled = !layer.shadow;
            stage.setShadow(enabled);
            shadow.setText(enabled ? "SHADOW ON" : "SHADOW OFF");
            shadow.setBackgroundResource(enabled ? R.drawable.bg_chip : R.drawable.bg_ghost);
            shadow.setTextColor(colour(enabled ? R.attr.accentInk : R.attr.on, Color.WHITE));
        });
        TextView textColour = chip("TEXT COLOUR");
        textColour.setTextColor(layer.colour);
        textColour.setOnClickListener(v -> colourDialog(false));
        LinearLayout.LayoutParams half = new LinearLayout.LayoutParams(0, dp(40), 1f);
        half.setMargins(dp(2), dp(8), dp(2), 0);
        lower.addView(shadow, half);
        lower.addView(textColour, half);
        content.addView(lower, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(content)
                .setPositiveButton("Done", null)
                .create();
        showDialog(dialog);
    }

    private void addFull(LinearLayout content, View view, int height, int top) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(height));
        params.setMargins(0, dp(top), 0, 0);
        content.addView(view, params);
    }

    private void addSlider(LinearLayout content, String label, int min, int max, int value,
                           SliderFormat format, SliderListener listener) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(4), 0, 0);

        TextView name = new TextView(this);
        name.setText(label);
        name.setTextColor(colour(R.attr.on, Color.WHITE));
        name.setTextSize(10);
        name.setLetterSpacing(0.03f);
        row.addView(name, new LinearLayout.LayoutParams(dp(104), dp(40)));

        final SeekBar bar = new SeekBar(this);
        bar.setMax(max - min);
        bar.setProgress(Math.max(0, Math.min(max - min, value - min)));
        bar.setPadding(dp(4), 0, dp(4), 0);
        row.addView(bar, new LinearLayout.LayoutParams(0, dp(40), 1f));

        final TextView shown = valueLabel();
        shown.setText(format.format(value));
        row.addView(shown, new LinearLayout.LayoutParams(dp(48), dp(40)));
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int current = min + progress;
                shown.setText(format.format(current));
                listener.onChanged(current);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        content.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private void photoDialog() {
        LinearLayout content = sheetBody("Photo");
        content.addView(note(stage.hasImage()
                ? "Choose a photo, turn it, or reset the frame. The photo is the canvas,\n"
                        + "so its own colours are the background."
                : "Choose a photo first; turning applies once it is in. With no photo the\n"
                        + "canvas is a 4:5 card you can colour."));

        final AlertDialog[] dialog = new AlertDialog[1];
        addAction(content, "CHOOSE PHOTO", true, () -> {
            dialog[0].dismiss();
            pickPhoto();
        });
        addAction(content, "ROTATE 90\u00b0", false, () -> stage.rotate90());
        addAction(content, "FLIP HORIZONTAL", false, () -> stage.flipHorizontal());
        addAction(content, "FLIP VERTICAL", false, () -> stage.flipVertical());
        addAction(content, "RESET TRANSFORM", false, () -> stage.resetTransform());
        if (!stage.hasImage()) {
            // A loaded photo covers the card exactly, so this would look dead there.
            addAction(content, "CANVAS COLOUR", false, () -> {
                dialog[0].dismiss();
                colourDialog(true);
            });
        }

        dialog[0] = new AlertDialog.Builder(this)
                .setView(content)
                .create();
        showDialog(dialog[0]);
    }

    private void addAction(LinearLayout content, String label, boolean primary, Runnable action) {
        TextView button = chip(label);
        if (primary) {
            button.setBackgroundResource(R.drawable.bg_chip);
            button.setTextColor(colour(R.attr.accentInk, Color.BLACK));
        }
        button.setGravity(Gravity.CENTER);
        button.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(42));
        params.setMargins(0, dp(5), 0, 0);
        content.addView(button, params);
    }

    /**
     * The reader's own saved looks, then the built-in presets, both rendered from the
     * current canvas so a choice is judged on the photograph rather than a swatch.
     * SAVE CURRENT LOOK sits under the scrolling grid, so it stays within a thumb as
     * the list of saved looks grows.
     */
    private void presetDialog() {
        LinearLayout content = sheetBody("Presets");
        content.addView(note("Looks render from this canvas. A saved look keeps the photo grade "
                + "and every line's type. Hold one to remove it."));

        final LinearLayout stack = new LinearLayout(this);
        stack.setOrientation(LinearLayout.VERTICAL);
        stack.addView(sectionLabel("MY STYLES"), sectionParams());

        final TextView noLooks = note("Nothing saved yet. Set a look, then SAVE CURRENT LOOK.");
        noLooks.setPadding(dp(4), 0, dp(4), dp(8));
        stack.addView(noLooks);

        final GridLayout savedGrid = new GridLayout(this);
        savedGrid.setColumnCount(4);
        stack.addView(savedGrid, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        stack.addView(sectionLabel("BUILT-IN"), sectionParams());

        final GridLayout grid = new GridLayout(this);
        grid.setColumnCount(4);
        final List<ImageView> frames = new ArrayList<>();
        final List<TextView> labels = new ArrayList<>();
        final int[] chosen = {stage.preset()};
        final int previewWidth = dp(78);
        final int previewHeight = dp(86);
        final Runnable[] reload = new Runnable[1];

        for (int i = 0; i < EditorView.PRESETS.length; i++) {
            final int index = i;
            LinearLayout cell = new LinearLayout(this);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER_HORIZONTAL);
            cell.setFocusable(true);
            cell.setClickable(true);
            cell.setContentDescription(EditorView.PRESETS[index] + " preset");

            ImageView frame = new ImageView(this);
            frame.setImageBitmap(stage.thumbnail(index, previewWidth, previewHeight));
            frame.setScaleType(ImageView.ScaleType.CENTER_CROP);
            frame.setPadding(dp(2), dp(2), dp(2), dp(2));
            frame.setContentDescription(EditorView.PRESETS[index] + " preview");
            frames.add(frame);
            cell.addView(frame, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, previewHeight));

            TextView label = new TextView(this);
            label.setText(EditorView.PRESETS[index].toUpperCase(Locale.US));
            label.setGravity(Gravity.CENTER);
            label.setLetterSpacing(0.06f);
            label.setTextSize(9);
            label.setMaxLines(1);
            label.setPadding(0, dp(3), 0, 0);
            labels.add(label);
            cell.addView(label, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            cell.setOnClickListener(v -> {
                stage.applyPreset(index);
                chosen[0] = stage.preset();
                syncUi();
                refreshPresetFrames(frames, labels, chosen[0]);
                reload[0].run();
            });

            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = 0;
            params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            params.setMargins(dp(2), dp(2), dp(2), dp(6));
            grid.addView(cell, params);
        }

        stack.addView(grid, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        ScrollView scroller = new ScrollView(this);
        scroller.setFillViewport(false);
        scroller.setId(R.id.sheet_scroller);
        scroller.setTag("presets");
        scroller.addView(stack);
        content.addView(scroller, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(300)));

        TextView save = chip("SAVE CURRENT LOOK");
        save.setBackgroundResource(R.drawable.bg_chip);
        save.setTextColor(colour(R.attr.accentInk, Color.BLACK));
        save.setOnClickListener(v -> saveLookDialog(reload[0]));
        LinearLayout.LayoutParams saveParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(42));
        saveParams.setMargins(0, dp(10), 0, 0);
        content.addView(save, saveParams);

        reload[0] = () -> {
            savedGrid.removeAllViews();
            List<StyleStore.Look> looks = StyleStore.load(this);
            int[] current = stage.adjustments();
            List<EditorView.TypeStyle> currentTypes = stage.typeStyles();
            noLooks.setVisibility(looks.isEmpty() ? View.VISIBLE : View.GONE);
            for (StyleStore.Look look : looks) {
                boolean active = samePoster(look, current, currentTypes);
                LinearLayout cell = new LinearLayout(this);
                cell.setOrientation(LinearLayout.VERTICAL);
                cell.setGravity(Gravity.CENTER_HORIZONTAL);
                selectable(cell);
                cell.setContentDescription(look.name
                        + " saved look. Tap to apply, hold to remove.");

                ImageView frame = new ImageView(this);
                frame.setImageBitmap(stage.thumbnail(look.adjust, previewWidth, previewHeight));
                frame.setScaleType(ImageView.ScaleType.CENTER_CROP);
                frame.setPadding(dp(2), dp(2), dp(2), dp(2));
                frame.setBackground(previewFrame(active));
                cell.addView(frame, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, previewHeight));

                TextView label = new TextView(this);
                label.setText(look.name);
                label.setGravity(Gravity.CENTER);
                label.setSingleLine(true);
                label.setEllipsize(android.text.TextUtils.TruncateAt.END);
                label.setLetterSpacing(0.05f);
                label.setTextSize(9);
                label.setPadding(0, dp(3), 0, 0);
                label.setTextColor(colour(active ? R.attr.accent : R.attr.onDim,
                        active ? Color.WHITE : Color.GRAY));
                cell.addView(label, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));

                cell.setOnClickListener(v -> {
                    LookUndo before = new LookUndo(stage.adjustments(), stage.preset(),
                            stage.lookName(), stage.typeStyles());
                    int lines = stage.layerCount();

                    stage.applyLook(look.adjust, look.name);
                    stage.applyTypeStyles(look.types);
                    syncUi();
                    chosen[0] = stage.preset();
                    refreshPresetFrames(frames, labels, chosen[0]);
                    reload[0].run();

                    rememberUndo(before);
                    if (look.types.isEmpty()) {
                        Toast.makeText(this, "Grade applied.", Toast.LENGTH_SHORT).show();
                    } else if (lines == 0) {
                        Toast.makeText(this, "Grade applied. This poster has no lines to "
                                + "restyle.", Toast.LENGTH_LONG).show();
                    } else {
                        int styled = Math.min(lines, look.types.size());
                        String message = "Look applied to " + styled
                                + (styled == 1 ? " line" : " lines");
                        if (styled < lines) {
                            message += "; " + (lines - styled) + " kept their type";
                        }
                        Toast.makeText(this, message + ".", Toast.LENGTH_SHORT).show();
                    }
                });
                cell.setOnLongClickListener(v -> {
                    removeLookDialog(look, reload[0]);
                    return true;
                });

                GridLayout.LayoutParams params = new GridLayout.LayoutParams();
                params.width = 0;
                params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
                params.setMargins(dp(2), dp(2), dp(2), dp(6));
                savedGrid.addView(cell, params);
            }
        };
        reload[0].run();
        refreshPresetFrames(frames, labels, chosen[0]);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(content)
                .setPositiveButton("Done", null)
                .create();
        showDialog(dialog);
    }

    /** A small caps heading between the sections of a sheet. */
    private TextView sectionLabel(String label) {
        TextView view = new TextView(this);
        view.setText(label);
        view.setTextSize(9);
        view.setLetterSpacing(0.14f);
        view.setTextColor(colour(R.attr.onDim, Color.GRAY));
        return view;
    }

    private LinearLayout.LayoutParams sectionParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(dp(4), dp(6), dp(4), dp(2));
        return params;
    }

    /**
     * A saved look is active on its grade; a look that carries type is only active when
     * every line matches too, so the ring never claims more than the canvas shows.
     */
    private boolean samePoster(StyleStore.Look look, int[] grade,
                               List<EditorView.TypeStyle> current) {
        if (!Arrays.equals(look.adjust, grade)) {
            return false;
        }
        if (look.types.isEmpty()) {
            return true;
        }
        if (look.types.size() != current.size()) {
            return false;
        }
        for (int i = 0; i < look.types.size(); i++) {
            if (!sameType(look.types.get(i), current.get(i))) {
                return false;
            }
        }
        return true;
    }

    /** Two type treatments match within a slider step, or both are absent. */
    private boolean sameType(EditorView.TypeStyle a, EditorView.TypeStyle b) {
        if (a == null || b == null) {
            return a == b;
        }
        return a.colour == b.colour && a.align == b.align && a.opacity == b.opacity
                && a.shadow == b.shadow
                && Math.abs(a.letterSpacing - b.letterSpacing) < 0.005f
                && Math.abs(a.lineSpacing - b.lineSpacing) < 0.005f;
    }

    /** Names the grade and every line's type as they stand into a saved look. */
    private void saveLookDialog(Runnable done) {
        LinearLayout content = sheetBody("Save look");
        int lines = stage.layerCount();
        content.addView(note(lines == 0
                ? "Keeps the photo grade. This poster has no type lines, so no lettering is "
                        + "captured."
                : "Keeps the photo grade and the colour, spacing, opacity and shadow of all "
                        + lines + (lines == 1 ? " line" : " lines")
                        + ", so the whole poster returns in one tap. Reusing a name replaces "
                        + "it."));

        final EditText name = new EditText(this);
        name.setSingleLine(true);
        name.setHint("Look name");
        name.setHintTextColor(colour(R.attr.onDim, Color.GRAY));
        name.setTextColor(colour(R.attr.on, Color.WHITE));
        name.setText(StyleStore.suggest(this));
        name.setSelectAllOnFocus(true);
        name.setPadding(dp(10), 0, dp(10), 0);
        content.addView(name, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(46)));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(content)
                .setNegativeButton("CANCEL", null)
                .setPositiveButton("SAVE", (which, unused) -> {
                    String saved = StyleStore.save(this, name.getText().toString(),
                            stage.adjustments(), stage.typeStyles());
                    if (saved == null) {
                        Toast.makeText(this, "Your saved looks are full. Remove one to make room.",
                                Toast.LENGTH_LONG).show();
                        return;
                    }
                    stage.applyLook(stage.adjustments(), saved);
                    syncUi();
                    done.run();
                    Toast.makeText(this, "Saved " + saved, Toast.LENGTH_SHORT).show();
                })
                .create();
        showDialog(dialog);
        name.requestFocus();
    }

    /** Keeps the last look application reversible until the canvas changes again. */
    private void rememberUndo(LookUndo state) {
        pendingUndo = state;
        undoLookButton.setVisibility(View.VISIBLE);
    }

    private void clearUndo() {
        pendingUndo = null;
        if (undoLookButton != null) {
            undoLookButton.setVisibility(View.GONE);
        }
    }

    /** Puts the grade and every line's type back where they were before the look. */
    private void undoSavedLook() {
        LookUndo state = pendingUndo;
        if (state == null) {
            return;
        }
        clearUndo();
        if (state.preset == EditorView.CUSTOM) {
            stage.applyLook(state.grade, state.name);
        } else {
            stage.applyPreset(state.preset);
        }
        stage.applyTypeStyles(state.types);
        syncUi();
        Toast.makeText(this, "Look undone.", Toast.LENGTH_SHORT).show();
    }

    /** A saved look is removed on a hold, after one confirmation. */
    private void removeLookDialog(StyleStore.Look look, Runnable done) {
        LinearLayout content = sheetBody("Remove look");
        content.addView(note("Removes \u201c" + look.name + "\u201d from your saved looks. "
                + "The canvas keeps the adjustments it has now."));
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(content)
                .setNegativeButton("CANCEL", null)
                .setPositiveButton("REMOVE", (which, unused) -> {
                    StyleStore.remove(this, look.name);
                    done.run();
                })
                .create();
        showDialog(dialog);
    }

    private void refreshPresetFrames(List<ImageView> frames, List<TextView> labels, int chosen) {
        for (int i = 0; i < frames.size(); i++) {
            boolean active = i == chosen;
            frames.get(i).setBackground(previewFrame(active));
            labels.get(i).setTextColor(colour(active ? R.attr.accent : R.attr.onDim,
                    active ? Color.WHITE : Color.GRAY));
        }
    }

    /** Five live sliders over the same colour matrix the presets use. */
    private void adjustDialog() {
        LinearLayout content = sheetBody("Adjust");
        content.addView(note("Tweak a preset, or start from Original. A hand-tuned look becomes Custom."));

        final List<SeekBar> bars = new ArrayList<>();
        for (int i = 0; i < EditorView.ADJUSTMENTS.length; i++) {
            final int which = i;
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp(4), 0, 0);

            TextView label = new TextView(this);
            label.setText(EditorView.ADJUSTMENTS[which]);
            label.setTextColor(colour(R.attr.on, Color.WHITE));
            label.setTextSize(12);
            row.addView(label, new LinearLayout.LayoutParams(dp(78), dp(40)));

            final SeekBar bar = new SeekBar(this);
            bar.setMax(200);
            bar.setProgress(stage.adjustment(which) + 100);
            bar.setPadding(dp(4), 0, dp(4), 0);
            row.addView(bar, new LinearLayout.LayoutParams(0, dp(40), 1f));
            bars.add(bar);

            final TextView value = new TextView(this);
            value.setText(String.valueOf(stage.adjustment(which)));
            value.setTextColor(colour(R.attr.on, Color.WHITE));
            value.setTextSize(12);
            value.setGravity(Gravity.END);
            row.addView(value, new LinearLayout.LayoutParams(dp(38), dp(40)));

            bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    int amount = progress - 100;
                    value.setText(String.valueOf(amount));
                    if (fromUser) {
                        stage.setAdjustment(which, amount);
                        syncUi();
                    }
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                }
            });
            content.addView(row, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        TextView reset = chip("RESET TO ORIGINAL");
        reset.setOnClickListener(v -> {
            stage.applyPreset(0);
            syncUi();
            for (int i = 0; i < bars.size(); i++) {
                bars.get(i).setProgress(stage.adjustment(i) + 100);
            }
        });
        LinearLayout.LayoutParams resetParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(40));
        resetParams.setMargins(0, dp(8), 0, 0);
        content.addView(reset, resetParams);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(content)
                .setPositiveButton("Done", null)
                .create();
        showDialog(dialog);
    }

    /**
     * The layer panel is ordered front to back. Dragging the handle translates the row,
     * then snaps it to the nearest row on release; the arrows do the same one step at a
     * time for keyboard and screen-reader use.
     */
    private void layersDialog() {
        LinearLayout content = sheetBody("Layers");
        content.addView(note("Top row draws in front. Drag \u2261 to reorder, or use the arrows."));

        final ScrollView scroll = new ScrollView(this);
        scroll.setId(R.id.sheet_scroller);
        scroll.setTag("layers");
        final LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list);
        content.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(240)));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setPadding(0, dp(6), 0, 0);
        final TextView duplicate = chip("DUPLICATE");
        final TextView center = chip("CENTER");
        final TextView remove = chip("DELETE");
        remove.setTextColor(colour(R.attr.warn, 0xFFFFC98A));
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(0, dp(40), 1f);
        actionParams.setMargins(dp(2), 0, dp(2), 0);
        actions.addView(duplicate, actionParams);
        actions.addView(center, actionParams);
        actions.addView(remove, actionParams);
        content.addView(actions, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        final Runnable[] refresh = new Runnable[1];
        refresh[0] = () -> {
            list.removeAllViews();
            List<EditorView.Layer> layers = stage.layerList();
            int count = layers.size();
            if (count == 0) {
                TextView empty = note("No lines yet. Add text to start a layer.");
                empty.setPadding(dp(4), dp(16), dp(4), dp(16));
                list.addView(empty);
            }
            for (int display = 0; display < count; display++) {
                final EditorView.Layer layer = layers.get(count - 1 - display);
                final int shown = display;
                final LinearLayout row = new LinearLayout(EditorActivity.this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(2), 0, dp(2), 0);
                selectable(row);
                row.setOnClickListener(v -> {
                    stage.selectLayer(layer);
                    syncUi();
                    list.post(refresh[0]);
                });

                TextView handle = new TextView(EditorActivity.this);
                handle.setText("\u2261");
                handle.setTextSize(20);
                handle.setTextColor(colour(R.attr.onDim, Color.GRAY));
                handle.setGravity(Gravity.CENTER);
                handle.setContentDescription("Drag to reorder");
                handle.setClickable(true);
                handle.setFocusable(true);
                row.addView(handle, new LinearLayout.LayoutParams(dp(32), dp(46)));

                final TextView eye = new TextView(EditorActivity.this);
                eye.setText(layer.visible ? "\u25c9" : "\u25cb");
                eye.setTextSize(16);
                eye.setTextColor(colour(layer.visible ? R.attr.on : R.attr.onDim, Color.WHITE));
                eye.setGravity(Gravity.CENTER);
                eye.setContentDescription(layer.visible ? "Hide layer" : "Show layer");
                eye.setClickable(true);
                eye.setFocusable(true);
                eye.setOnClickListener(v -> {
                    stage.toggleVisible(layer);
                    syncUi();
                    refresh[0].run();
                });
                row.addView(eye, new LinearLayout.LayoutParams(dp(30), dp(46)));

                TextView name = new TextView(EditorActivity.this);
                name.setText(layer.text);
                name.setSingleLine(true);
                name.setEllipsize(android.text.TextUtils.TruncateAt.END);
                name.setTextSize(13);
                name.setAlpha(layer.visible ? 1f : 0.45f);
                name.setTextColor(stage.selected() == layer
                        ? colour(R.attr.accent, Color.WHITE) : colour(R.attr.on, Color.WHITE));
                row.addView(name, new LinearLayout.LayoutParams(0, dp(46), 1f));

                final TextView up = new TextView(EditorActivity.this);
                up.setText("\u2191");
                up.setTextSize(16);
                up.setGravity(Gravity.CENTER);
                up.setContentDescription("Move forward");
                up.setEnabled(display > 0);
                up.setAlpha(display > 0 ? 1f : 0.35f);
                up.setOnClickListener(v -> {
                    stage.moveLayerToDisplay(layer, Math.max(0, shown - 1));
                    syncUi();
                    list.post(refresh[0]);
                });
                row.addView(up, new LinearLayout.LayoutParams(dp(32), dp(46)));

                final TextView down = new TextView(EditorActivity.this);
                down.setText("\u2193");
                down.setTextSize(16);
                down.setGravity(Gravity.CENTER);
                down.setContentDescription("Move backward");
                down.setEnabled(display < count - 1);
                down.setAlpha(display < count - 1 ? 1f : 0.35f);
                down.setOnClickListener(v -> {
                    stage.moveLayerToDisplay(layer, Math.min(count - 1, shown + 1));
                    syncUi();
                    list.post(refresh[0]);
                });
                row.addView(down, new LinearLayout.LayoutParams(dp(32), dp(46)));

                final float[] downRaw = new float[1];
                handle.setOnTouchListener((view, event) -> {
                    switch (event.getActionMasked()) {
                        case android.view.MotionEvent.ACTION_DOWN:
                            downRaw[0] = event.getRawY();
                            row.setAlpha(0.55f);
                            stage.selectLayer(layer);
                            syncUi();
                            if (row.getParent() != null) {
                                row.getParent().requestDisallowInterceptTouchEvent(true);
                            }
                            return true;
                        case android.view.MotionEvent.ACTION_MOVE:
                            row.setTranslationY(event.getRawY() - downRaw[0]);
                            return true;
                        case android.view.MotionEvent.ACTION_UP:
                            float dy = event.getRawY() - downRaw[0];
                            int steps = Math.round(dy / dp(46));
                            int target = Math.max(0, Math.min(count - 1, shown + steps));
                            row.setAlpha(1f);
                            row.setTranslationY(0);
                            if (row.getParent() != null) {
                                row.getParent().requestDisallowInterceptTouchEvent(false);
                            }
                            stage.moveLayerToDisplay(layer, target);
                            syncUi();
                            list.post(refresh[0]);
                            return true;
                        case android.view.MotionEvent.ACTION_CANCEL:
                            row.setAlpha(1f);
                            row.setTranslationY(0);
                            if (row.getParent() != null) {
                                row.getParent().requestDisallowInterceptTouchEvent(false);
                            }
                            return true;
                        default:
                            return false;
                    }
                });

                list.addView(row, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(46)));
            }

            boolean hasLayer = stage.selected() != null;
            duplicate.setEnabled(hasLayer);
            center.setEnabled(hasLayer);
            remove.setEnabled(hasLayer);
            duplicate.setAlpha(hasLayer ? 1f : 0.4f);
            center.setAlpha(hasLayer ? 1f : 0.4f);
            remove.setAlpha(hasLayer ? 1f : 0.4f);
        };

        duplicate.setOnClickListener(v -> {
            stage.duplicateSelected();
            syncUi();
            refresh[0].run();
        });
        center.setOnClickListener(v -> {
            stage.centerSelected();
            syncUi();
            refresh[0].run();
        });
        remove.setOnClickListener(v -> {
            stage.removeSelected();
            syncUi();
            refresh[0].run();
        });

        refresh[0].run();
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(content)
                .setPositiveButton("Done", null)
                .create();
        showDialog(dialog);
    }

    private void exportDialog() {
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Export")
                .setItems(new String[]{"PNG", "JPEG"}, (picker, which) -> {
                    try {
                        Bitmap out = stage.render();
                        String message = Saver.save(this, out, which == 1);
                        out.recycle();
                        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                    } catch (OutOfMemoryError tooBig) {
                        Toast.makeText(this, "That photo is too large to export",
                                Toast.LENGTH_LONG).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .create();
        showDialog(dialog);
    }

    // ---- plumbing ------------------------------------------------------------

    /** Renders one slider value as the label beside its bar. */
    /** What was on the stage before a saved look was applied, so one tap can go back. */
    private static final class LookUndo {
        final int[] grade;
        final int preset;
        final String name;
        final List<EditorView.TypeStyle> types;

        LookUndo(int[] grade, int preset, String name, List<EditorView.TypeStyle> types) {
            this.grade = grade;
            this.preset = preset;
            this.name = name;
            this.types = types;
        }
    }

    private interface SliderFormat {
        String format(int value);
    }

    /** Applies one slider value to the selected line as it moves. */
    private interface SliderListener {
        void onChanged(int value);
    }

    /**
     * Every sheet opens from the bottom of the screen and slides up over the stage, so
     * the controls land under a thumb instead of in the middle of the glass. The window
     * is edge to edge and transparent; the sheet itself is the card surface, with the
     * system's navigation inset as its floor.
     */
    private void showDialog(AlertDialog dialog) {
        Window window = dialog.getWindow();
        if (window != null) {
            window.setGravity(Gravity.BOTTOM);
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setDimAmount(0.6f);
            window.setWindowAnimations(R.style.SheetAnimation);
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.WRAP_CONTENT);
        }
        dialog.setCanceledOnTouchOutside(true);
        dialog.show();
        View panel = dialog.findViewById(android.R.id.content);
        if (panel == null) {
            return;
        }
        panel.setBackgroundResource(R.drawable.bg_sheet);
        panel.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(insets.getSystemWindowInsetLeft(), 0,
                    insets.getSystemWindowInsetRight(),
                    Math.max(insets.getSystemWindowInsetBottom(), dp(6)));
            return insets;
        });
        panel.requestApplyInsets();

        // Every custom sheet is dragged by its header: down closes it, up grows the
        // one list a sheet carries (presets, layers) until the card meets the top.
        View header = dialog.findViewById(R.id.sheet_header);
        if (header != null) {
            View list = dialog.findViewById(R.id.sheet_scroller);
            int base = list == null ? 0 : list.getLayoutParams().height;
            SheetDrag drag = new SheetDrag(dialog, header, panel, list, base);
            header.setOnTouchListener(drag);
            panel.post(drag::restore);
        }
    }

    /** The start of a custom sheet: a grab handle, its heading, then the controls. */
    private LinearLayout sheetBody(String title) {
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(18), 0, dp(18), dp(12));

        // The handle and heading make one grab zone: a drag here moves the sheet, so
        // the controls below keep their own gestures. showDialog() finds it by id.
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setId(R.id.sheet_header);

        View handle = new View(this);
        handle.setBackgroundResource(R.drawable.bg_handle);
        LinearLayout.LayoutParams handleParams = new LinearLayout.LayoutParams(dp(36), dp(4));
        handleParams.setMargins(0, dp(10), 0, 0);
        handleParams.gravity = Gravity.CENTER_HORIZONTAL;
        header.addView(handle, handleParams);

        TextView heading = new TextView(this);
        heading.setText(title);
        heading.setTextColor(colour(R.attr.on, Color.WHITE));
        heading.setTextSize(19);
        heading.setLetterSpacing(-0.01f);
        LinearLayout.LayoutParams headingParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        headingParams.setMargins(0, dp(6), 0, dp(2));
        header.addView(heading, headingParams);

        body.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return body;
    }

    private GradientDrawable previewFrame(boolean selected) {
        GradientDrawable frame = new GradientDrawable();
        frame.setColor(Color.TRANSPARENT);
        frame.setCornerRadius(dp(6));
        frame.setStroke(dp(selected ? 2 : 1),
                colour(selected ? R.attr.accent : R.attr.rule, Color.GRAY));
        return frame;
    }

    private TextView note(String message) {
        TextView view = new TextView(this);
        view.setText(message);
        view.setTextColor(colour(R.attr.onDim, Color.GRAY));
        view.setTextSize(11);
        view.setLineSpacing(dp(2), 1f);
        return view;
    }

    private TextView chip(String label) {
        TextView view = new TextView(this);
        view.setText(label);
        view.setTextSize(10);
        view.setLetterSpacing(0.04f);
        view.setGravity(Gravity.CENTER);
        view.setMinHeight(dp(40));
        view.setPadding(dp(8), 0, dp(8), 0);
        view.setBackgroundResource(R.drawable.bg_ghost);
        view.setTextColor(colour(R.attr.on, Color.WHITE));
        view.setClickable(true);
        view.setFocusable(true);
        return view;
    }

    private void selectable(View view) {
        TypedValue value = new TypedValue();
        if (getTheme().resolveAttribute(android.R.attr.selectableItemBackground, value, true)
                && value.resourceId != 0) {
            view.setBackgroundResource(value.resourceId);
        }
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (studioPane != null && studioPane.onActivityResult(request, result, data)) {
            return;
        }
        if (request == REQ_PACKS) {
            // Reopen the list after the shelf closes, even on Cancel: a pack may have
            // been installed while it was open, and the old dialog would still show
            // the faces from before that download.
            fontDialog();
            return;
        }
        if (result != RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        Uri uri = data.getData();
        if (request == REQ_PHOTO) {
            loadPhoto(uri);
        }
    }

    /** A theme attribute as an ARGB int, with a fallback for a missing attribute. */
    private int colour(int attr, int fallback) {
        TypedValue value = new TypedValue();
        if (!getTheme().resolveAttribute(attr, value, true)) {
            return fallback;
        }
        if (value.resourceId != 0) {
            return getResources().getColor(value.resourceId, getTheme());
        }
        return value.data;
    }

    private int dp(int value) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                getResources().getDisplayMetrics()));
    }
}
