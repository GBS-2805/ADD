package app.offline.compress;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.drawable.GradientDrawable;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.DisplayCutout;
import android.view.View;
import android.view.WindowInsets;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;

/**
 * The editor: a photo, type set in the bundled faces or an imported pack, a handful of
 * effects, and an export to Pictures/Editor.
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
    private TextView effectButton;
    private SeekBar sizeBar;

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
        fontButton = findViewById(R.id.fontButton);
        sizeValue = findViewById(R.id.sizeValue);
        colourButton = findViewById(R.id.colourButton);
        effectButton = findViewById(R.id.effectButton);
        sizeBar = findViewById(R.id.sizeBar);

        faces = FontStore.faces(this);
        currentFace = FontStore.defaultFace(this);
        stage.setSelectionColour(colour(R.attr.accent, Color.WHITE));
        showFace();

        findViewById(R.id.backButton).setOnClickListener(v -> finish());
        findViewById(R.id.photoButton).setOnClickListener(v -> pickPhoto());
        findViewById(R.id.addTextButton).setOnClickListener(v -> textDialog(null));
        findViewById(R.id.exportButton).setOnClickListener(v -> exportDialog());
        fontButton.setOnClickListener(v -> fontDialog());
        effectButton.setOnClickListener(v -> effectDialog());
        colourButton.setOnClickListener(v -> colourDialog());
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
        });

        if (state == null) {
            // A new document: one line of type so the stage is never blank on arrival.
            stage.addText("ART DESIGN", currentFace.typeface, currentFace.label, sizeFrac(), currentColour);
            syncUi();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        List<FontStore.Face> refreshed = FontStore.faces(this);
        faces = refreshed;
        if (currentFace != null && currentFace.imported()
                && FontStore.find(refreshed, currentFace.label) == null) {
            currentFace = FontStore.defaultFace(this);
            showFace();
        }
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

    /** The bars and labels follow the selection, so a dragged line reports itself. */
    private void syncUi() {
        showFace();
        EditorView.Layer layer = stage.selected();
        if (layer == null) {
            sizeValue.setText(String.valueOf(sizeBar.getProgress()));
            effectButton.setText(EditorView.EFFECTS[stage.effect()].toUpperCase(Locale.US));
            return;
        }
        syncing = true;
        int progress = Math.max(MIN_PT, Math.min(MAX_PT, Math.round(layer.sizeFrac * PT_SCALE)));
        sizeBar.setProgress(progress);
        sizeValue.setText(String.valueOf(progress));
        syncing = false;
        fontButton.setText(layer.faceLabel);
        colourButton.setTextColor(layer.colour);
        effectButton.setText(EditorView.EFFECTS[stage.effect()].toUpperCase(Locale.US));
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
                        showFace();
                        stage.invalidate();
                    }
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
        new AlertDialog.Builder(this)
                .setTitle(layer == null ? "Add text" : "Edit text")
                .setView(input)
                .setPositiveButton("Set", (dialog, which) -> {
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
                .show();
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
        new AlertDialog.Builder(this)
                .setTitle("Font")
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    currentFace = faces.get(which);
                    if (stage.selected() != null) {
                        stage.setFace(currentFace.typeface, currentFace.label);
                    }
                    showFace();
                    dialog.dismiss();
                })
                .setNeutralButton("Font packs\u2026", (dialog, which) -> {
                    dialog.dismiss();
                    startActivityForResult(
                            new Intent(EditorActivity.this, PacksActivity.class), REQ_PACKS);
                })
                .setPositiveButton("Close", null)
                .show();
    }

    // ---- look ----------------------------------------------------------------

    private void colourDialog() {
        int[] swatches = {Color.WHITE, Color.BLACK, colour(R.attr.accent, Color.WHITE),
                colour(R.attr.warn, 0xFFE2553F), 0xFFF2C14E, 0xFF7FB2F0};
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(dp(20), dp(12), dp(20), dp(12));
        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Text colour")
                .setView(row)
                .setNegativeButton("Cancel", null)
                .create();
        for (int swatch : swatches) {
            View dot = new View(this);
            GradientDrawable shape = new GradientDrawable();
            shape.setColor(swatch);
            shape.setCornerRadius(dp(6));
            shape.setStroke(dp(1), colour(R.attr.onDim, Color.GRAY));
            dot.setBackground(shape);
            dot.setContentDescription(swatch == Color.WHITE ? "White" : "Colour");
            LinearLayout.LayoutParams params =
                    new LinearLayout.LayoutParams(dp(40), dp(40));
            params.setMargins(dp(4), 0, dp(4), 0);
            row.addView(dot, params);
            dot.setOnClickListener(v -> {
                currentColour = swatch;
                colourPicked = true;
                stage.setColour(swatch);
                colourButton.setTextColor(swatch);
                dialog.dismiss();
            });
        }
        dialog.show();
    }

    private void effectDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Effect")
                .setSingleChoiceItems(EditorView.EFFECTS, stage.effect(), (dialog, which) -> {
                    stage.setEffect(which);
                    effectButton.setText(EditorView.EFFECTS[which].toUpperCase(Locale.US));
                    dialog.dismiss();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void exportDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Export")
                .setItems(new String[]{"PNG", "JPEG"}, (dialog, which) -> {
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
                .show();
    }

    // ---- plumbing ------------------------------------------------------------

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
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
