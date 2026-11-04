package app.offline.compress;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Insets;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.util.TypedValue;
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

    /** Public download page, opened in the browser from the footer link. */
    private static final String SITE_URL = "https://artbase-olive.vercel.app/";

    /** Hard cap on a batch, matching the queue the strip can hold. */
    private static final int MAX_PHOTOS = 40;

    // Keys for the state that has to survive a theme flip (which recreates us).
    private static final String STATE_PICKED = "picked";
    private static final String STATE_DONE_SOURCE = "doneSource";
    private static final String STATE_DONE_FILE = "doneFile";
    private static final String STATE_DONE_NAME = "doneName";
    private static final String STATE_DONE_WIDTH = "doneWidth";
    private static final String STATE_DONE_HEIGHT = "doneHeight";
    private static final String STATE_DONE_ORIGINAL = "doneOriginal";
    private static final String STATE_DONE_COMPRESSED = "doneCompressed";

    /** Set on the intent before a recreate, so the panel survives a look change. */
    private static final String EXTRA_DRAWER = "drawerOpen";

    /** Below this width the band's two descriptor lines run under its mark. */
    private static final int NARROW_BAND = 400;

    private static final int[] MAX_DIMENSIONS = {0, 1280, 1920, 2560, 3200, 4096};
    private static final int QUALITY_BASE = 45;

    private final List<Uri> picked = new ArrayList<>();
    private final List<Done> done = new ArrayList<>();

    /** Photos dropped by the last Clear all, kept so it can be undone. */
    private final List<Uri> undoClear = new ArrayList<>();
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
    private LinearLayout siteLink;
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
        ThemePrefs.applyTheme(this);
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
        siteLink = findViewById(R.id.siteLink);
        resultsContainer = findViewById(R.id.results);
        pickedStrip = findViewById(R.id.pickedStrip);
        pickedRow = findViewById(R.id.pickedRow);
        qualityBar = findViewById(R.id.qualityBar);
        sizeBar = findViewById(R.id.sizeBar);

        applyInsets();
        setupChrome();

        pickCard.setOnClickListener(v -> pickImages());
        compressButton.setOnClickListener(v -> startCompression());
        previewButton.setOnClickListener(v -> startPreview());
        shareAllButton.setOnClickListener(v -> shareAll());
        clearAllButton.setOnClickListener(v -> {
            if (undoClear.isEmpty()) {
                clearSelection();
            } else {
                restoreSelection();
            }
        });
        siteLink.setOnClickListener(v -> openSite());
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

        if (savedInstanceState != null) {
            restoreState(savedInstanceState);
        } else {
            restoreQueue();
        }

        acceptShared(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        acceptShared(intent);
    }

    /**
     * Photos handed over by another app's Share, appended to the queue.
     *
     * The action is cleared once it has been read, because a theme flip recreates
     * this activity and would otherwise queue the same photos a second time.
     */
    private void acceptShared(Intent intent) {
        if (intent == null) {
            return;
        }
        String action = intent.getAction();
        if (!Intent.ACTION_SEND.equals(action) && !Intent.ACTION_SEND_MULTIPLE.equals(action)) {
            return;
        }
        intent.setAction(null);

        ArrayList<Uri> incoming = new ArrayList<>();
        if (Intent.ACTION_SEND.equals(action)) {
            Uri single = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            if (single != null) {
                incoming.add(single);
            }
        } else {
            ArrayList<Uri> many = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
            if (many != null) {
                incoming.addAll(many);
            }
        }

        final int before = picked.size();
        for (Uri uri : incoming) {
            if (uri == null || picked.contains(uri) || picked.size() >= MAX_PHOTOS) {
                continue;
            }
            picked.add(uri);
            try {
                getContentResolver().takePersistableUriPermission(
                        uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Exception ignored) {
                // A share grant lasts as long as this activity; not every provider
                // offers one that outlives it.
            }
        }

        final int added = picked.size() - before;
        if (added > 0) {
            undoClear.clear();
            persistQueue();
            updatePickedSummary();
            Toast.makeText(this, added == 1
                    ? "1 photo shared in · " + picked.size() + " queued"
                    : added + " photos shared in · " + picked.size() + " queued",
                    Toast.LENGTH_SHORT).show();
        } else if (!incoming.isEmpty()) {
            Toast.makeText(this, "That is all " + MAX_PHOTOS + " photos at once",
                    Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        // A theme flip recreates this activity, so the queue and the finished
        // files are carried across rather than silently dropped.
        ArrayList<String> sources = new ArrayList<>();
        for (Uri uri : picked) {
            sources.add(uri.toString());
        }
        outState.putStringArrayList(STATE_PICKED, sources);

        ArrayList<String> resultSources = new ArrayList<>();
        ArrayList<String> resultFiles = new ArrayList<>();
        ArrayList<String> names = new ArrayList<>();
        ArrayList<Integer> widths = new ArrayList<>();
        ArrayList<Integer> heights = new ArrayList<>();
        ArrayList<Long> originals = new ArrayList<>();
        ArrayList<Long> compressed = new ArrayList<>();
        for (Done entry : done) {
            resultSources.add(entry.source.toString());
            resultFiles.add(entry.compressed.toString());
            names.add(entry.name);
            widths.add(entry.width);
            heights.add(entry.height);
            originals.add(entry.originalBytes);
            compressed.add(entry.compressedBytes);
        }
        outState.putStringArrayList(STATE_DONE_SOURCE, resultSources);
        outState.putStringArrayList(STATE_DONE_FILE, resultFiles);
        outState.putStringArrayList(STATE_DONE_NAME, names);
        outState.putIntegerArrayList(STATE_DONE_WIDTH, widths);
        outState.putIntegerArrayList(STATE_DONE_HEIGHT, heights);
        outState.putSerializable(STATE_DONE_ORIGINAL, originals);
        outState.putSerializable(STATE_DONE_COMPRESSED, compressed);
    }

    private void restoreState(Bundle state) {
        ArrayList<String> sources = state.getStringArrayList(STATE_PICKED);
        if (sources != null) {
            for (String value : sources) {
                Uri uri = value == null ? null : Uri.parse(value);
                if (uri != null && picked.size() < MAX_PHOTOS && !picked.contains(uri)) {
                    picked.add(uri);
                }
            }
        }

        ArrayList<String> resultSources = state.getStringArrayList(STATE_DONE_SOURCE);
        ArrayList<String> resultFiles = state.getStringArrayList(STATE_DONE_FILE);
        ArrayList<String> names = state.getStringArrayList(STATE_DONE_NAME);
        ArrayList<Integer> widths = state.getIntegerArrayList(STATE_DONE_WIDTH);
        ArrayList<Integer> heights = state.getIntegerArrayList(STATE_DONE_HEIGHT);
        ArrayList<Long> originals = serializableList(state, STATE_DONE_ORIGINAL);
        ArrayList<Long> compressed = serializableList(state, STATE_DONE_COMPRESSED);
        if (resultSources != null && resultFiles != null && names != null
                && widths != null && heights != null && originals != null && compressed != null) {
            int count = Math.min(names.size(), Math.min(resultSources.size(), resultFiles.size()));
            for (int i = 0; i < count && i < originals.size() && i < compressed.size(); i++) {
                done.add(new Done(Uri.parse(resultSources.get(i)), Uri.parse(resultFiles.get(i)),
                        names.get(i), widths.get(i), heights.get(i),
                        originals.get(i), compressed.get(i)));
            }
        }

        if (!done.isEmpty()) {
            renderDone();
            showSummary();
        }
        if (!picked.isEmpty()) {
            updatePickedSummary();
        }
    }

    /**
     * Brings back the queue that was still there when the app was closed.
     *
     * A read grant can lapse while the app is shut, and a photo we can no longer
     * open would only ever draw as an empty tile, so those are dropped from the
     * queue (and from prefs) instead of being restored.
     */
    private void restoreQueue() {
        int dropped = 0;
        for (String value : SelectionStore.load(this)) {
            Uri uri = value == null ? null : Uri.parse(value);
            if (uri == null) {
                dropped++;
                continue;
            }
            if (picked.contains(uri) || picked.size() >= MAX_PHOTOS) {
                continue;
            }
            if (!hasReadAccess(uri)) {
                dropped++;
                continue;
            }
            picked.add(uri);
        }
        if (dropped > 0) {
            persistQueue();
            Toast.makeText(this, dropped == 1
                    ? "1 photo dropped · no longer readable"
                    : dropped + " photos dropped · no longer readable",
                    Toast.LENGTH_LONG).show();
        }
        if (!picked.isEmpty()) {
            updatePickedSummary();
        }
    }

    /** True while this app still holds a read grant for the photo behind the URI. */
    private boolean hasReadAccess(Uri uri) {
        return checkUriPermission(uri, Process.myPid(), Process.myUid(),
                Intent.FLAG_GRANT_READ_URI_PERMISSION) != PackageManager.PERMISSION_DENIED;
    }

    private void persistQueue() {
        SelectionStore.save(this, picked);
    }

    @SuppressWarnings("unchecked")
    private static ArrayList<Long> serializableList(Bundle state, String key) {
        Object value = state.getSerializable(key);
        return value instanceof ArrayList ? (ArrayList<Long>) value : null;
    }

    /** Rebuilds the results list from the kept entries, used after a recreate. */
    private void renderDone() {
        resultsContainer.removeAllViews();
        for (final Done entry : done) {
            final ImageView image = addResultRow(entry, null);
            previews.execute(() -> {
                final Bitmap thumb = Compressor.thumbnail(MainActivity.this, entry.compressed, 240);
                if (thumb != null) {
                    ui.post(() -> image.setImageBitmap(thumb));
                }
            });
        }
    }

    /** Totals the kept files back into the summary line. */
    private void showSummary() {
        long original = 0;
        long compressed = 0;
        for (Done entry : done) {
            original += entry.originalBytes;
            compressed += entry.compressedBytes;
        }
        finishBatch(original, compressed);
    }

    /**
     * Hands a page of the shelf to a browser. The app itself still has no network
     * permission: the intent is resolved by whatever browser is installed.
     */
    private void openSite() {
        openInBrowser(SITE_URL);
    }

    private void openSite(String path) {
        openInBrowser(SITE_URL + path);
    }

    /**
     * Opens one of the shelf's web tools, which ship inside the APK: no network, no
     * second app, and the same pages the site serves.
     */
    private void openShelf(String tool) {
        Intent intent = new Intent(this, ShelfActivity.class);
        intent.putExtra(ShelfActivity.EXTRA_PAGE, tool);
        startActivity(intent);
        closeDrawer();
    }

    private void openInBrowser(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "No browser installed", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * The band, the side panel and the three palette chips.
     *
     * Every destination is a page of the shelf, opened in the browser: the app
     * keeps no network permission of its own.
     */
    private void setupChrome() {
        final View drawer = findViewById(R.id.drawer);
        final View scrim = findViewById(R.id.scrim);

        // The site's rule, kept here: the page name opens the panel it names, and the
        // mark goes to the shelf. Neither is a link to another app screen.
        findViewById(R.id.burger).setOnClickListener(v -> openDrawer());
        findViewById(R.id.bandName).setOnClickListener(v -> openDrawer());
        findViewById(R.id.bandMark).setOnClickListener(v -> openSite());
        findViewById(R.id.drawerClose).setOnClickListener(v -> closeDrawer());
        scrim.setOnClickListener(v -> closeDrawer());

        // The pull-down is the tool list: Icons and Fonts are the shelf's web tools and
        // open offline in their own screen; Compress is this screen, so its row closes
        // the panel.
        findViewById(R.id.linkIcons).setOnClickListener(v -> openShelf("icons"));
        findViewById(R.id.linkFonts).setOnClickListener(v -> openShelf("fonts"));
        findViewById(R.id.linkCompress).setOnClickListener(v -> closeDrawer());

        link(R.id.linkDownload, "download/");
        link(R.id.linkPortfolio, "portfolio/");
        link(R.id.linkRenders, "renders/");
        link(R.id.linkReleases, "releases/");

        // The site drops the band's descriptor on a phone, where the two lines and
        // the centred mark cannot both fit; the app does the same rather than let
        // them overlap.
        if (getResources().getConfiguration().screenWidthDp < NARROW_BAND) {
            findViewById(R.id.bandDescriptors).setVisibility(View.GONE);
        }

        buildPaletteRow();

        // The panel is off-screen by its own width until it is opened.
        drawer.setTranslationX(dp(320));

        final int padLeft = drawer.getPaddingLeft();
        final int padTop = drawer.getPaddingTop();
        final int padRight = drawer.getPaddingRight();
        final int padBottom = drawer.getPaddingBottom();
        drawer.setOnApplyWindowInsetsListener((view, insets) -> {
            int top;
            int bottom;
            if (Build.VERSION.SDK_INT >= 30) {
                Insets bars = insets.getInsets(
                        WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                top = bars.top;
                bottom = bars.bottom;
            } else {
                top = insets.getSystemWindowInsetTop();
                bottom = insets.getSystemWindowInsetBottom();
            }
            view.setPadding(padLeft, padTop + top, padRight, padBottom + bottom);
            return insets;
        });
        drawer.requestApplyInsets();

        // A palette or theme change recreates the activity, and this runs last so the
        // panel is not pushed off screen after being opened: picking a colour must not
        // shut the panel it was picked in.
        if (getIntent().getBooleanExtra(EXTRA_DRAWER, false)) {
            getIntent().removeExtra(EXTRA_DRAWER);
            openDrawerNow();
        }
    }

    private void link(int id, final String path) {
        findViewById(id).setOnClickListener(v -> {
            closeDrawer();
            openSite(path);
        });
    }

    private void openDrawer() {
        final View drawer = findViewById(R.id.drawer);
        final View scrim = findViewById(R.id.scrim);
        drawer.setVisibility(View.VISIBLE);
        drawer.animate().translationX(0f).setDuration(180).start();
        scrim.setVisibility(View.VISIBLE);
        scrim.animate().alpha(1f).setDuration(180).start();
    }

    private void closeDrawer() {
        final View drawer = findViewById(R.id.drawer);
        final View scrim = findViewById(R.id.scrim);
        drawer.animate()
                .translationX(drawer.getWidth() == 0 ? dp(320) : drawer.getWidth())
                .setDuration(160)
                .withEndAction(() -> drawer.setVisibility(View.GONE))
                .start();
        scrim.animate().alpha(0f).setDuration(160)
                .withEndAction(() -> scrim.setVisibility(View.GONE))
                .start();
    }

    /**
     * The chips and the switch, drawn by PaletteRow: picking either one stores it and
     * repaints, so the panel shows the choice it just made rather than closing on it.
     */
    private void buildPaletteRow() {
        PaletteRow.fill(findViewById(R.id.palRow),
                ThemePrefs.palette(this), ThemePrefs.isDark(this),
                id -> {
                    ThemePrefs.setPalette(this, id);
                    repaintKeepingDrawer();
                },
                () -> {
                    ThemePrefs.toggleDark(this);
                    repaintKeepingDrawer();
                });
    }

    /** Recreate for the new colours, with the panel left open across the rebuild. */
    private void repaintKeepingDrawer() {
        getIntent().putExtra(EXTRA_DRAWER, true);
        recreate();
    }

    /** Show the panel fully open, without the slide, for the frame after a recreate. */
    private void openDrawerNow() {
        final View drawer = findViewById(R.id.drawer);
        final View scrim = findViewById(R.id.scrim);
        drawer.setVisibility(View.VISIBLE);
        drawer.setTranslationX(0f);
        scrim.setVisibility(View.VISIBLE);
        scrim.setAlpha(1f);
    }

    /** A theme attribute as a packed colour. */
    private int color(int attribute) {
        TypedValue value = new TypedValue();
        if (!getTheme().resolveAttribute(attribute, value, true)) {
            return 0xffffffff;
        }
        return value.data;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        pool.shutdownNow();
        previews.shutdownNow();
        super.onDestroy();
    }

    private void applyInsets() {
        // The band now runs to the top of the window, so the insets go on the
        // whole content column rather than on the padded container inside it.
        final View container = findViewById(R.id.content);
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
        undoClear.clear();
        persistQueue();
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
            updateClearButton();
            return;
        }
        pickedStrip.setVisibility(View.VISIBLE);
        updateClearButton();
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
        undoClear.clear();
        persistQueue();
        updatePickedSummary();
        if (picked.isEmpty()) {
            Toast.makeText(this, "Selection cleared", Toast.LENGTH_SHORT).show();
        }
    }

    /** Empties the selection in one tap, keeping a copy the chip can restore. */
    private void clearSelection() {
        if (picked.isEmpty()) {
            return;
        }
        undoClear.clear();
        undoClear.addAll(picked);
        picked.clear();
        persistQueue();
        updatePickedSummary();
        Toast.makeText(this, "Selection cleared · tap RESTORE to undo",
                Toast.LENGTH_SHORT).show();
    }

    /** Puts back the photos dropped by the last Clear all. */
    private void restoreSelection() {
        if (undoClear.isEmpty()) {
            return;
        }
        for (Uri uri : undoClear) {
            if (picked.size() < MAX_PHOTOS && !picked.contains(uri)) {
                picked.add(uri);
            }
        }
        undoClear.clear();
        persistQueue();
        updatePickedSummary();
        Toast.makeText(this, "Selection restored", Toast.LENGTH_SHORT).show();
    }

    /** The chip reads CLEAR ALL, or RESTORE while a clear can still be undone. */
    private void updateClearButton() {
        boolean canRestore = !undoClear.isEmpty();
        clearAllButton.setText(canRestore ? "RESTORE" : "CLEAR ALL");
        clearAllButton.setVisibility(picked.isEmpty() && !canRestore
                ? View.GONE : View.VISIBLE);
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
        done.clear();
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
                    final Done entry = new Done(source, result.uri, result.name,
                            result.width, result.height,
                            result.originalBytes, result.compressedBytes);
                    ui.post(() -> {
                        done.add(entry);
                        addResultRow(entry, thumb);
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

    private ImageView addResultRow(final Done entry, Bitmap thumb) {
        View row = LayoutInflater.from(this).inflate(R.layout.item_result, resultsContainer, false);
        ImageView image = row.findViewById(R.id.thumb);
        TextView name = row.findViewById(R.id.name);
        TextView sizes = row.findViewById(R.id.sizes);
        TextView badge = row.findViewById(R.id.badge);

        if (thumb != null) {
            image.setImageBitmap(thumb);
        }
        name.setText(entry.name);
        sizes.setText(String.format(Locale.US, "%d × %d · %s → %s",
                entry.width, entry.height,
                formatBytes(entry.originalBytes), formatBytes(entry.compressedBytes)));

        if (entry.shrank()) {
            badge.setText("-" + entry.savingsPercent() + "%");
            badge.setBackgroundResource(R.drawable.bg_pill);
            badge.setTextColor(color(R.attr.accentInk));
        } else {
            badge.setText("no gain");
            badge.setBackgroundResource(R.drawable.bg_pill_warn);
            badge.setTextColor(color(R.attr.warn));
        }

        row.setOnClickListener(v -> openCompare(entry.source, entry));
        row.findViewById(R.id.shareOne).setOnClickListener(v -> share(entry.compressed));
        resultsContainer.addView(row);
        return image;
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
        row.setTextColor(color(R.attr.warn));
        int pad = Math.round(8 * getResources().getDisplayMetrics().density);
        row.setPadding(pad, pad, pad, pad);
        resultsContainer.addView(row);
    }

    private void finishBatch(long originalBytes, long compressedBytes) {
        setBusy(false);
        statusRow.setVisibility(View.GONE);
        if (done.isEmpty()) {
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
                formatBytes(saved), percent, done.size(),
                done.size() == 1 ? "" : "s"));
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
        view.setTextColor(color(selected ? R.attr.accentInk : R.attr.onDim));
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
        if (done.isEmpty()) {
            return;
        }
        if (done.size() == 1) {
            share(done.get(0).compressed);
            return;
        }
        ArrayList<Uri> files = new ArrayList<>();
        for (Done entry : done) {
            files.add(entry.compressed);
        }
        Intent send = new Intent(Intent.ACTION_SEND_MULTIPLE);
        send.setType("image/*");
        send.putParcelableArrayListExtra(Intent.EXTRA_STREAM, files);
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(send, "Share compressed photos"));
    }

    private void openCompare(Uri original, Done entry) {
        Intent intent = new Intent(this, CompareActivity.class);
        intent.putExtra(CompareActivity.EXTRA_BEFORE, original.toString());
        intent.putExtra(CompareActivity.EXTRA_AFTER, entry.compressed.toString());
        intent.putExtra(CompareActivity.EXTRA_NAME, entry.name);
        intent.putExtra(CompareActivity.EXTRA_BYTES_BEFORE, entry.originalBytes);
        intent.putExtra(CompareActivity.EXTRA_BYTES_AFTER, entry.compressedBytes);
        intent.putExtra(CompareActivity.EXTRA_WIDTH, entry.width);
        intent.putExtra(CompareActivity.EXTRA_HEIGHT, entry.height);
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

    /** One finished file, kept so a recreate can redraw the results list. */
    private static final class Done {
        final Uri source;
        final Uri compressed;
        final String name;
        final int width;
        final int height;
        final long originalBytes;
        final long compressedBytes;

        Done(Uri source, Uri compressed, String name, int width, int height,
             long originalBytes, long compressedBytes) {
            this.source = source;
            this.compressed = compressed;
            this.name = name;
            this.width = width;
            this.height = height;
            this.originalBytes = originalBytes;
            this.compressedBytes = compressedBytes;
        }

        boolean shrank() {
            return compressedBytes < originalBytes;
        }

        int savingsPercent() {
            if (originalBytes <= 0) {
                return 0;
            }
            return (int) Math.round(100.0 * (originalBytes - compressedBytes) / originalBytes);
        }
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
