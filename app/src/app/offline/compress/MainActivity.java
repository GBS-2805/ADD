package app.offline.compress;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Insets;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowInsets;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {

    private static final int REQ_PICK = 1001;
    private static final int REQ_WRITE = 1002;
    private static final int REQ_PREVIEW = 1003;

    /** Hard cap on a batch, matching the queue the strip can hold. */
    private static final int MAX_PHOTOS = 40;

    private static final int[] MAX_DIMENSIONS = {0, 1280, 1920, 2560, 3200, 4096};
    private static final int QUALITY_BASE = 45;

    private final List<Uri> picked = new ArrayList<>();
    private final List<Uri> produced = new ArrayList<>();
    private final ExecutorService pool = Executors.newSingleThreadExecutor();
    private final ExecutorService previews = Executors.newFixedThreadPool(2);
    private final Handler ui = new Handler(Looper.getMainLooper());

    private int format = Compressor.FORMAT_JPEG;

    /** True when the next picker result should join the queue instead of replacing it. */
    private boolean appendMode;

    private View pickCard;
    private TextView pickTitle;
    private TextView pickSub;
    private TextView qualityValue;
    private TextView qualityHint;
    private TextView sizeValue;
    private TextView formatJpeg;
    private TextView formatWebp;
    private TextView formatCaption;
    private TextView compressButton;
    private TextView previewButton;
    private TextView statusText;
    private TextView summaryText;
    private TextView shareAllButton;
    private TextView clearAllButton;
    private TextView themeToggle;
    private LinearLayout statusRow;
    private LinearLayout resultsContainer;
    private HorizontalScrollView pickedStrip;
    private LinearLayout pickedRow;
    private ProgressBar progressBar;
    private SeekBar qualityBar;
    private SeekBar sizeBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ThemePrefs.apply(this);
        setContentView(R.layout.activity_main);

        pickCard = findViewById(R.id.pickCard);
        pickTitle = findViewById(R.id.pickTitle);
        pickSub = findViewById(R.id.pickSub);
        qualityValue = findViewById(R.id.qualityValue);
        qualityHint = findViewById(R.id.qualityHint);
        sizeValue = findViewById(R.id.sizeValue);
        formatJpeg = findViewById(R.id.formatJpeg);
        formatWebp = findViewById(R.id.formatWebp);
        formatCaption = findViewById(R.id.formatCaption);
        compressButton = findViewById(R.id.compressButton);
        previewButton = findViewById(R.id.previewButton);
        statusText = findViewById(R.id.statusText);
        statusRow = findViewById(R.id.statusRow);
        progressBar = findViewById(R.id.progressBar);
        summaryText = findViewById(R.id.summaryText);
        shareAllButton = findViewById(R.id.shareAll);
        clearAllButton = findViewById(R.id.clearAll);
        themeToggle = findViewById(R.id.themeToggle);
        resultsContainer = findViewById(R.id.results);
        pickedStrip = findViewById(R.id.pickedStrip);
        pickedRow = findViewById(R.id.pickedRow);
        qualityBar = findViewById(R.id.qualityBar);
        sizeBar = findViewById(R.id.sizeBar);

        applyInsets();

        pickCard.setOnClickListener(v -> pickImages());
        compressButton.setOnClickListener(v -> startCompression());
        previewButton.setOnClickListener(v -> startPreview());
        shareAllButton.setOnClickListener(v -> shareAll());
        clearAllButton.setOnClickListener(v -> clearSelection());
        themeToggle.setOnClickListener(v -> toggleTheme());
        formatJpeg.setOnClickListener(v -> setFormat(Compressor.FORMAT_JPEG));
        formatWebp.setOnClickListener(v -> setFormat(Compressor.FORMAT_WEBP));

        qualityBar.setOnSeekBarChangeListener(new SimpleSeekListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                updateQualityLabel();
            }
        });
        sizeBar.setOnSeekBarChangeListener(new SimpleSeekListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                updateSizeLabel();
            }
        });

        updateQualityLabel();
        updateSizeLabel();
        setFormat(Compressor.FORMAT_JPEG);
        setBusy(false);
        updateThemeToggle();
    }

    /** Flips the saved palette and rebuilds this screen with it. */
    private void toggleTheme() {
        ThemePrefs.setDark(this, !ThemePrefs.isDark(this));
        recreate();
    }

    private void updateThemeToggle() {
        themeToggle.setText(ThemePrefs.isDark(this) ? "DARK ON" : "DARK OFF");
    }

    @Override
    protected void onDestroy() {
        pool.shutdownNow();
        previews.shutdownNow();
        super.onDestroy();
    }

    private void applyInsets() {
        final View container = findViewById(R.id.container);
        final int startPadLeft = container.getPaddingLeft();
        final int startPadTop = container.getPaddingTop();
        final int startPadRight = container.getPaddingRight();
        final int startPadBottom = container.getPaddingBottom();
        container.setOnApplyWindowInsetsListener((view, insets) -> {
            int top;
            int bottom;
            int left;
            int right;
            if (Build.VERSION.SDK_INT >= 30) {
                Insets bars = insets.getInsets(
                        WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                top = bars.top;
                bottom = bars.bottom;
                left = bars.left;
                right = bars.right;
            } else {
                top = insets.getSystemWindowInsetTop();
                bottom = insets.getSystemWindowInsetBottom();
                left = insets.getSystemWindowInsetLeft();
                right = insets.getSystemWindowInsetRight();
            }
            view.setPadding(startPadLeft + left, startPadTop + top,
                    startPadRight + right, startPadBottom + bottom);
            return insets;
        });
        container.requestApplyInsets();
    }

    private void pickImages() {
        if (picked.size() >= MAX_PHOTOS) {
            Toast.makeText(this, "That is all " + MAX_PHOTOS + " photos at once",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        if (picked.isEmpty()) {
            appendMode = false;
            launchPicker();
            return;
        }
        // Photos are already queued: ask whether the next pick joins them or
        // starts over, so a second tap can never silently discard the first.
        new AlertDialog.Builder(this)
                .setTitle("Add to your selection?")
                .setMessage(picked.size() == 1
                        ? "1 photo is already queued."
                        : picked.size() + " photos are already queued.")
                .setPositiveButton("Add more", (dialog, which) -> {
                    appendMode = true;
                    launchPicker();
                })
                .setNegativeButton("Start from beginning", (dialog, which) -> {
                    appendMode = false;
                    launchPicker();
                })
                .setNeutralButton("Cancel", null)
                .show();
    }

    private void launchPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        try {
            startActivityForResult(intent, REQ_PICK);
        } catch (Exception missingPicker) {
            Toast.makeText(this, "No gallery app available", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_PREVIEW) {
            if (resultCode == RESULT_OK) {
                startCompression();
            }
            return;
        }
        if (requestCode != REQ_PICK || resultCode != RESULT_OK || data == null) {
            return;
        }
        if (!appendMode) {
            picked.clear();
        }
        final int before = picked.size();
        ClipData clip = data.getClipData();
        if (clip != null) {
            for (int i = 0; i < clip.getItemCount() && picked.size() < MAX_PHOTOS; i++) {
                Uri uri = clip.getItemAt(i).getUri();
                if (uri != null && !picked.contains(uri)) {
                    picked.add(uri);
                }
            }
        } else if (data.getData() != null && picked.size() < MAX_PHOTOS) {
            Uri uri = data.getData();
            if (!picked.contains(uri)) {
                picked.add(uri);
            }
        }
        for (int i = before; i < picked.size(); i++) {
            try {
                getContentResolver().takePersistableUriPermission(
                        picked.get(i), Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Exception ignored) {
                // Not every provider offers persistable grants.
            }
        }
        final int added = picked.size() - before;
        if (appendMode && added == 0) {
            Toast.makeText(this, "Nothing new added", Toast.LENGTH_SHORT).show();
        } else if (appendMode) {
            Toast.makeText(this, added + (added == 1 ? " photo added · " : " photos added · ")
                    + picked.size() + " queued", Toast.LENGTH_SHORT).show();
        }
        updatePickedSummary();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_WRITE) {
            boolean granted = grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            if (granted) {
                startCompression();
            } else {
                Toast.makeText(this, "Storage permission is needed to save photos",
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    private void updatePickedSummary() {
        updatePickedSummary(true);
    }

    private void updatePickedSummary(boolean rebuildStrip) {
        int count = picked.size();
        if (rebuildStrip) {
            renderStrip();
        }
        if (count == 0) {
            pickTitle.setText("Select photos");
            pickSub.setText("JPG, PNG or WebP · multiple allowed");
            return;
        }
        pickTitle.setText(count == 1 ? "1 photo selected" : count + " photos selected");
        pickSub.setText("Calculating size…");
        final List<Uri> snapshot = new ArrayList<>(picked);
        pool.execute(() -> {
            long total = 0;
            for (Uri uri : snapshot) {
                total += Compressor.sizeOf(MainActivity.this, uri);
            }
            final long totalBytes = total;
            ui.post(() -> pickSub.setText(formatBytes(totalBytes) + " total · tap to add more"));
        });
    }

    private void renderStrip() {
        pickedRow.removeAllViews();
        if (picked.isEmpty()) {
            pickedStrip.setVisibility(View.GONE);
            clearAllButton.setVisibility(View.GONE);
            return;
        }
        pickedStrip.setVisibility(View.VISIBLE);
        clearAllButton.setVisibility(View.VISIBLE);
        for (Uri uri : picked) {
            View item = LayoutInflater.from(this).inflate(R.layout.item_thumb, pickedRow, false);
            final ImageView image = item.findViewById(R.id.thumbImage);
            final Uri source = uri;
            item.findViewById(R.id.removeButton)
                    .setOnClickListener(v -> removeFromSelection(source));
            pickedRow.addView(item);
            previews.execute(() -> {
                final Bitmap preview = Compressor.thumbnail(MainActivity.this, source, 180);
                if (preview != null) {
                    ui.post(() -> image.setImageBitmap(preview));
                }
            });
        }
    }

    /** Drops one photo from the selection and rebuilds the strip. */
    private void removeFromSelection(Uri uri) {
        if (!picked.remove(uri)) {
            return;
        }
        updatePickedSummary();
        if (picked.isEmpty()) {
            Toast.makeText(this, "Selection cleared", Toast.LENGTH_SHORT).show();
        }
    }

    /** Empties the selection in one tap, at the bottom of the strip. */
    private void clearSelection() {
        if (picked.isEmpty()) {
            return;
        }
        picked.clear();
        updatePickedSummary();
        Toast.makeText(this, "Selection cleared", Toast.LENGTH_SHORT).show();
    }

    private Compressor.Options currentOptions() {
        Compressor.Options options = new Compressor.Options();
        options.quality = QUALITY_BASE + qualityBar.getProgress();
        int index = Math.max(0, Math.min(MAX_DIMENSIONS.length - 1, sizeBar.getProgress()));
        options.maxDimension = MAX_DIMENSIONS[index];
        options.format = format;
        return options;
    }

    /** Encodes the first selected photo to the cache only, then shows the comparison slider. */
    private void startPreview() {
        if (picked.isEmpty()) {
            pickImages();
            return;
        }
        final Compressor.Options options = currentOptions();
        final Uri source = picked.get(0);
        setBusy(true);
        statusText.setText("Building preview…");
        pool.execute(() -> {
            try {
                final Compressor.Trial trial = Compressor.trial(MainActivity.this, source, options);
                ui.post(() -> {
                    setBusy(false);
                    openPreview(source, trial, options);
                });
            } catch (final Exception failure) {
                ui.post(() -> {
                    setBusy(false);
                    Toast.makeText(MainActivity.this, "Preview failed · "
                                    + (failure.getMessage() == null ? "unknown error" : failure.getMessage()),
                            Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void openPreview(Uri source, Compressor.Trial trial, Compressor.Options options) {
        Intent intent = new Intent(this, CompareActivity.class);
        intent.putExtra(CompareActivity.EXTRA_BEFORE, source.toString());
        intent.putExtra(CompareActivity.EXTRA_AFTER, Uri.fromFile(trial.file).toString());
        intent.putExtra(CompareActivity.EXTRA_NAME, "Preview · " + describe(options));
        intent.putExtra(CompareActivity.EXTRA_BYTES_BEFORE, trial.originalBytes);
        intent.putExtra(CompareActivity.EXTRA_BYTES_AFTER, trial.compressedBytes);
        intent.putExtra(CompareActivity.EXTRA_WIDTH, trial.width);
        intent.putExtra(CompareActivity.EXTRA_HEIGHT, trial.height);
        intent.putExtra(CompareActivity.EXTRA_PREVIEW, true);
        intent.putExtra(CompareActivity.EXTRA_TEMP_FILE, trial.file.getAbsolutePath());
        startActivityForResult(intent, REQ_PREVIEW);
    }

    private String describe(Compressor.Options options) {
        String formatName = options.format == Compressor.FORMAT_WEBP ? "WebP" : "JPEG";
        String size = options.maxDimension == 0 ? "original size" : options.maxDimension + " px";
        return formatName + " · q" + options.quality + " · " + size;
    }

    private void startCompression() {
        if (picked.isEmpty()) {
            pickImages();
            return;
        }
        if (Build.VERSION.SDK_INT < 29
                && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQ_WRITE);
            return;
        }

        final Compressor.Options options = currentOptions();

        final List<Uri> batch = new ArrayList<>(picked);
        resultsContainer.removeAllViews();
        produced.clear();
        summaryText.setVisibility(View.GONE);
        shareAllButton.setVisibility(View.GONE);
        setBusy(true);
        statusText.setText("Compressing 1 of " + batch.size() + "…");

        pool.execute(() -> {
            long totalOriginal = 0;
            long totalCompressed = 0;
            for (int i = 0; i < batch.size(); i++) {
                final Uri source = batch.get(i);
                final int position = i + 1;
                ui.post(() -> statusText.setText("Compressing " + position + " of "
                        + batch.size() + "…"));
                try {
                    final Compressor.Result result =
                            Compressor.compress(MainActivity.this, source, options);
                    final Bitmap thumb = Compressor.thumbnail(MainActivity.this, result.uri, 240);
                    totalOriginal += result.originalBytes;
                    totalCompressed += result.compressedBytes;
                    ui.post(() -> {
                        addResultRow(source, result, thumb);
                        produced.add(result.uri);
                    });
                } catch (final Exception failure) {
                    ui.post(() -> addErrorRow(source, failure));
                }
            }
            final long originalTotal = totalOriginal;
            final long compressedTotal = totalCompressed;
            ui.post(() -> finishBatch(originalTotal, compressedTotal));
        });
    }

    private void addResultRow(final Uri source, Compressor.Result result, Bitmap thumb) {
        View row = LayoutInflater.from(this).inflate(R.layout.item_result, resultsContainer, false);
        ImageView image = row.findViewById(R.id.thumb);
        TextView name = row.findViewById(R.id.name);
        TextView sizes = row.findViewById(R.id.sizes);
        TextView badge = row.findViewById(R.id.badge);

        if (thumb != null) {
            image.setImageBitmap(thumb);
        }
        name.setText(result.name);
        sizes.setText(String.format(Locale.US, "%d × %d · %s → %s",
                result.width, result.height,
                formatBytes(result.originalBytes), formatBytes(result.compressedBytes)));

        if (result.shrank()) {
            badge.setText("-" + result.savingsPercent() + "%");
            badge.setBackgroundResource(R.drawable.bg_pill);
            badge.setTextColor(getColor(R.color.badgeInk));
        } else {
            badge.setText("no gain");
            badge.setBackgroundResource(R.drawable.bg_pill_warn);
            badge.setTextColor(getColor(R.color.warn));
        }

        row.setOnClickListener(v -> openCompare(source, result));
        row.findViewById(R.id.shareOne).setOnClickListener(v -> share(result.uri));
        resultsContainer.addView(row);
    }

    private void addErrorRow(Uri source, Exception failure) {
        TextView row = new TextView(this);
        String label = Compressor.displayName(this, source);
        if (label == null) {
            label = "photo";
        }
        row.setText("Could not compress " + label + " · "
                + (failure.getMessage() == null ? "unknown error" : failure.getMessage()));
        row.setTextSize(12.5f);
        row.setTextColor(getColor(R.color.warn));
        int pad = Math.round(8 * getResources().getDisplayMetrics().density);
        row.setPadding(pad, pad, pad, pad);
        resultsContainer.addView(row);
    }

    private void finishBatch(long originalBytes, long compressedBytes) {
        setBusy(false);
        statusRow.setVisibility(View.GONE);
        if (produced.isEmpty()) {
            Toast.makeText(this, "Nothing could be compressed", Toast.LENGTH_SHORT).show();
            updatePickedSummary();
            return;
        }
        long saved = Math.max(0, originalBytes - compressedBytes);
        int percent = originalBytes > 0
                ? (int) Math.round(100.0 * saved / originalBytes) : 0;
        summaryText.setVisibility(View.VISIBLE);
        shareAllButton.setVisibility(View.VISIBLE);
        summaryText.setText(String.format(Locale.US, "Saved %s · %d%% smaller · %d file%s",
                formatBytes(saved), percent, produced.size(),
                produced.size() == 1 ? "" : "s"));
        updatePickedSummary(false);
    }

    private void setBusy(boolean busy) {
        compressButton.setEnabled(!busy);
        compressButton.setAlpha(busy ? 0.5f : 1f);
        previewButton.setEnabled(!busy);
        previewButton.setAlpha(busy ? 0.5f : 1f);
        pickCard.setEnabled(!busy);
        qualityBar.setEnabled(!busy);
        sizeBar.setEnabled(!busy);
        formatJpeg.setEnabled(!busy);
        formatWebp.setEnabled(!busy);
        clearAllButton.setEnabled(!busy);
        clearAllButton.setAlpha(busy ? 0.5f : 1f);
        statusRow.setVisibility(busy ? View.VISIBLE : View.GONE);
        progressBar.setVisibility(busy ? View.VISIBLE : View.GONE);
        if (busy) {
            statusText.setText("Preparing…");
        }
    }

    private void setFormat(int value) {
        format = value;
        boolean jpeg = value == Compressor.FORMAT_JPEG;
        applySegment(formatJpeg, jpeg);
        applySegment(formatWebp, !jpeg);
        formatCaption.setText(jpeg
                ? "JPEG: widely compatible, great for photos"
                : "WebP: smaller files, great for sharing");
    }

    private void applySegment(TextView view, boolean selected) {
        view.setBackgroundResource(selected ? R.drawable.bg_seg_selected : 0);
        view.setTextColor(getColor(selected ? R.color.accentInk : R.color.muted));
        view.setTypeface(null, selected ? Typeface.BOLD : Typeface.NORMAL);
    }

    private void updateQualityLabel() {
        int quality = QUALITY_BASE + qualityBar.getProgress();
        qualityValue.setText(String.valueOf(quality));
        String hint;
        if (quality >= 88) {
            hint = "Maximum detail";
        } else if (quality >= 78) {
            hint = "Excellent quality";
        } else if (quality >= 65) {
            hint = "Balanced";
        } else {
            hint = "Smallest files";
        }
        qualityHint.setText(hint);
    }

    private void updateSizeLabel() {
        int index = Math.max(0, Math.min(MAX_DIMENSIONS.length - 1, sizeBar.getProgress()));
        int value = MAX_DIMENSIONS[index];
        sizeValue.setText(value == 0 ? "Original" : value + " px");
    }

    private void share(Uri uri) {
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("image/*");
        send.putExtra(Intent.EXTRA_STREAM, uri);
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(send, "Share compressed photo"));
    }

    private void shareAll() {
        if (produced.isEmpty()) {
            return;
        }
        if (produced.size() == 1) {
            share(produced.get(0));
            return;
        }
        Intent send = new Intent(Intent.ACTION_SEND_MULTIPLE);
        send.setType("image/*");
        send.putParcelableArrayListExtra(Intent.EXTRA_STREAM, new ArrayList<>(produced));
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(send, "Share compressed photos"));
    }

    private void openCompare(Uri original, Compressor.Result result) {
        Intent intent = new Intent(this, CompareActivity.class);
        intent.putExtra(CompareActivity.EXTRA_BEFORE, original.toString());
        intent.putExtra(CompareActivity.EXTRA_AFTER, result.uri.toString());
        intent.putExtra(CompareActivity.EXTRA_NAME, result.name);
        intent.putExtra(CompareActivity.EXTRA_BYTES_BEFORE, result.originalBytes);
        intent.putExtra(CompareActivity.EXTRA_BYTES_AFTER, result.compressedBytes);
        intent.putExtra(CompareActivity.EXTRA_WIDTH, result.width);
        intent.putExtra(CompareActivity.EXTRA_HEIGHT, result.height);
        startActivity(intent);
    }

    private static String formatBytes(long bytes) {
        if (bytes <= 0) {
            return "0 KB";
        }
        if (bytes < 1024) {
            return bytes + " B";
        }
        double kb = bytes / 1024.0;
        if (kb < 1024) {
            return String.format(Locale.US, "%.0f KB", kb);
        }
        double mb = kb / 1024.0;
        if (mb < 10) {
            return String.format(Locale.US, "%.2f MB", mb);
        }
        return String.format(Locale.US, "%.1f MB", mb);
    }

    private abstract static class SimpleSeekListener implements SeekBar.OnSeekBarChangeListener {
        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
        }
    }
}
