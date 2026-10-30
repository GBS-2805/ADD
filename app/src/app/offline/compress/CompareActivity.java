package app.offline.compress;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Insets;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.WindowInsets;
import android.widget.TextView;

import java.io.File;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Before/after slider. Opened two ways: preview mode (cache-only trial encode of
 * the current settings) or result mode (checking a file that was saved).
 */
public class CompareActivity extends Activity {

    public static final String EXTRA_BEFORE = "before";
    public static final String EXTRA_AFTER = "after";
    public static final String EXTRA_NAME = "name";
    public static final String EXTRA_BYTES_BEFORE = "bytes_before";
    public static final String EXTRA_BYTES_AFTER = "bytes_after";
    public static final String EXTRA_WIDTH = "width";
    public static final String EXTRA_HEIGHT = "height";
    public static final String EXTRA_PREVIEW = "preview";
    public static final String EXTRA_TEMP_FILE = "temp_file";

    private final ExecutorService pool = Executors.newSingleThreadExecutor();
    private final Handler ui = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ThemePrefs.apply(this);
        setContentView(R.layout.activity_compare);

        final ComparisonView comparison = findViewById(R.id.comparison);
        final TextView title = findViewById(R.id.title);
        final TextView stats = findViewById(R.id.stats);
        final TextView action = findViewById(R.id.shareButton);
        final TextView hint = findViewById(R.id.hint);

        applyInsets();

        final boolean preview = getIntent().getBooleanExtra(EXTRA_PREVIEW, false);
        final String tempPath = getIntent().getStringExtra(EXTRA_TEMP_FILE);
        final Uri before = Uri.parse(extra(EXTRA_BEFORE, ""));
        final Uri after = Uri.parse(extra(EXTRA_AFTER, ""));

        long bytesBefore = getIntent().getLongExtra(EXTRA_BYTES_BEFORE, 0);
        long bytesAfter = getIntent().getLongExtra(EXTRA_BYTES_AFTER, 0);
        int width = getIntent().getIntExtra(EXTRA_WIDTH, 0);
        int height = getIntent().getIntExtra(EXTRA_HEIGHT, 0);

        title.setText(extra(EXTRA_NAME, "Compressed photo"));

        String dimensions = width > 0 && height > 0 ? width + " × " + height + " · " : "";
        long saved = Math.max(0, bytesBefore - bytesAfter);
        int percent = bytesBefore > 0 ? (int) Math.round(100.0 * saved / bytesBefore) : 0;
        stats.setText(String.format(Locale.US, "%s%s%s → %s · %d%% smaller",
                preview ? "est. " : "", dimensions,
                formatBytes(bytesBefore), formatBytes(bytesAfter), percent));

        findViewById(R.id.back).setOnClickListener(v -> finish());

        if (preview) {
            hint.setText("This is what your settings would produce — nothing saved yet");
            action.setText("Compress all photos");
            action.setOnClickListener(v -> {
                setResult(RESULT_OK);
                finish();
            });
        } else {
            hint.setText("Drag the handle to compare before and after");
            action.setText("Share compressed photo");
            action.setOnClickListener(v -> {
                Intent send = new Intent(Intent.ACTION_SEND);
                send.setType("image/*");
                send.putExtra(Intent.EXTRA_STREAM, after);
                send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(Intent.createChooser(send, "Share compressed photo"));
            });
        }

        pool.execute(() -> {
            final Bitmap beforeBitmap = Compressor.loadOriented(this, before, 2400);
            final Bitmap afterBitmap = Compressor.loadOriented(this, after, 2400);
            if (tempPath != null) {
                //noinspection ResultOfMethodCallIgnored
                new File(tempPath).delete();
            }
            ui.post(() -> {
                if (isFinishing()) {
                    return;
                }
                comparison.setImages(beforeBitmap, afterBitmap);
                if (beforeBitmap == null) {
                    stats.setText("Could not load the original image");
                }
            });
        });
    }

    @Override
    protected void onDestroy() {
        pool.shutdownNow();
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

    private String extra(String key, String fallback) {
        String value = getIntent().getStringExtra(key);
        return value == null || value.isEmpty() ? fallback : value;
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
}
