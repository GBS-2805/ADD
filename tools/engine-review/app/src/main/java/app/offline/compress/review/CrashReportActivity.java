package app.offline.compress.review;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * The crash, in full, on the phone. Built in plain views on purpose: a report about a
 * Compose screen must not be a Compose screen.
 */
public class CrashReportActivity extends Activity {

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        int pad = dp(20);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#12171A"));
        root.setPadding(pad, dp(28), pad, pad);

        TextView title = new TextView(this);
        title.setText("ADD stopped");
        title.setTextColor(Color.parseColor("#F2EEE3"));
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26);
        title.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        root.addView(title);

        TextView note = new TextView(this);
        note.setText("The trace below was saved before the process died. COPY it and paste it "
                + "back; the engine stays held until the next build, so the editor opens "
                + "without it in the meantime.");
        note.setTextColor(Color.parseColor("#A9BBB4"));
        note.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        note.setPadding(0, dp(10), 0, dp(16));
        root.addView(note);

        final String trace = read();

        ScrollView scroller = new ScrollView(this);
        TextView body = new TextView(this);
        body.setText(trace);
        body.setTextColor(Color.parseColor("#D8E2DC"));
        body.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        body.setTypeface(Typeface.MONOSPACE);
        body.setTextIsSelectable(true);
        scroller.addView(body);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        root.addView(scroller, scrollParams);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.END);
        row.setPadding(0, dp(16), 0, 0);

        Button close = button("CLOSE");
        close.setOnClickListener(v -> finish());
        row.addView(close);

        Button copy = button("COPY");
        copy.setOnClickListener(v -> {
            ClipboardManager manager =
                    (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            manager.setPrimaryClip(ClipData.newPlainText("ADD crash", trace));
            Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show();
        });
        row.addView(copy);
        root.addView(row);

        setContentView(root);
    }

    private Button button(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextColor(Color.parseColor("#F2EEE3"));
        button.setBackgroundColor(Color.parseColor("#1F2A2E"));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.leftMargin = dp(10);
        button.setLayoutParams(params);
        return button;
    }

    private String read() {
        File file = CrashWatch.crashFile(this);
        try {
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            }
            return "Unreadable on this Android version: " + file.getAbsolutePath();
        } catch (IOException | RuntimeException error) {
            return "The trace file could not be read (" + error + ").";
        }
    }

    private int dp(int value) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                getResources().getDisplayMetrics()));
    }
}
