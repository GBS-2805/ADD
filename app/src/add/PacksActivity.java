package add;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.DisplayCutout;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONException;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The family shelf: what the site offers, what is already on this phone, and the one
 * place the app is allowed to use the network.
 *
 * Opening the screen reads the cached listing; an empty cache asks the site for it.
 * Refresh asks again; Download asks for one pack and PackStore verifies its signature
 * and every hash before installing. An installed pack is the offline copy: it lives
 * in filesDir and the editor keeps using it with no connection.
 */
public class PacksActivity extends Activity {

    private static final int REQ_PACK = 21;

    private LinearLayout list;
    private TextView status;
    private TextView refresh;

    private List<PackCatalog.Pack> packs = new ArrayList<>();
    private final Set<String> downloading = new HashSet<>();
    private boolean loading;
    private boolean offline;
    private String problem;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        ThemePrefs.applyTheme(this);
        setContentView(R.layout.activity_packs);
        applyInsets();

        list = findViewById(R.id.packsList);
        status = findViewById(R.id.packsStatus);
        refresh = findViewById(R.id.packsRefresh);

        findViewById(R.id.packsBack).setOnClickListener(v -> finish());
        refresh.setOnClickListener(v -> loadCatalog());
        findViewById(R.id.packsImport).setOnClickListener(v -> pickPack());

        packs = PackCatalog.cached(this);
        render();
        if (packs.isEmpty()) {
            loadCatalog();
        }
    }

    private void loadCatalog() {
        if (loading) {
            return;
        }
        loading = true;
        refresh.setEnabled(false);
        status.setText("Checking the site\u2026");
        new Thread(new Runnable() {
            @Override
            public void run() {
                List<PackCatalog.Pack> fetched = null;
                String failure = null;
                try {
                    String json = PackCatalog.fetch();
                    fetched = PackCatalog.parse(json);
                    PackCatalog.save(PacksActivity.this, json);
                } catch (IOException bad) {
                    failure = networkMessage(bad);
                } catch (JSONException bad) {
                    failure = "The pack list could not be read.";
                }
                final List<PackCatalog.Pack> result = fetched;
                final String reason = failure;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (isFinishing()) {
                            return;
                        }
                        loading = false;
                        refresh.setEnabled(true);
                        if (result != null) {
                            packs = result;
                            offline = false;
                            problem = null;
                        } else {
                            offline = true;
                            problem = reason;
                        }
                        render();
                    }
                });
            }
        }).start();
    }

    private void render() {
        list.removeAllViews();
        List<PackStore.Installed> installed = PackStore.installed(this);
        Map<String, PackStore.Installed> bySlug = new HashMap<>();
        for (PackStore.Installed pack : installed) {
            bySlug.put(pack.slug, pack);
        }

        Set<String> shown = new HashSet<>();
        for (PackCatalog.Pack pack : packs) {
            shown.add(pack.slug);
            addPackRow(pack, bySlug.get(pack.slug));
        }

        List<PackStore.Installed> extra = new ArrayList<>();
        for (PackStore.Installed pack : installed) {
            if (!shown.contains(pack.slug)) {
                extra.add(pack);
            }
        }
        if (!extra.isEmpty()) {
            list.addView(section("ALREADY ON THIS PHONE"));
            for (PackStore.Installed pack : extra) {
                list.addView(installedRow(pack));
                list.addView(divider());
            }
        }

        if (packs.isEmpty() && installed.isEmpty() && !loading) {
            TextView empty = text("No packs yet. Reconnect and tap Refresh.", 13, R.attr.onDim);
            empty.setPadding(0, dp(20), 0, dp(20));
            list.addView(empty);
        }
        status.setText(statusLine());
    }

    private String statusLine() {
        if (loading) {
            return "Checking the site\u2026";
        }
        if (offline) {
            if (!packs.isEmpty()) {
                return "Offline \u2014 showing the list saved on this phone.";
            }
            return problem != null ? problem : "No connection. Installed packs still work offline.";
        }
        long bytes = 0;
        for (PackCatalog.Pack pack : packs) {
            bytes += pack.bytes;
        }
        return packs.size() + (packs.size() == 1 ? " pack" : " packs")
                + " \u00b7 " + PackStore.humanBytes(bytes)
                + " \u00b7 downloads only when you tap";
    }

    private void addPackRow(PackCatalog.Pack pack, PackStore.Installed installed) {
        LinearLayout row = shell();
        LinearLayout words = new LinearLayout(this);
        words.setOrientation(LinearLayout.VERTICAL);
        words.addView(text(pack.family, 15, R.attr.on));

        String meta = pack.faces + (pack.faces == 1 ? " face" : " faces")
                + " \u00b7 " + PackStore.humanBytes(pack.bytes);
        if (installed != null) {
            meta += " \u00b7 installed";
            if (!pack.version.equals(installed.version)) {
                meta += " \u00b7 update available";
            }
        }
        words.addView(text(meta, 12, R.attr.onDim));
        row.addView(words, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        if (downloading.contains(pack.slug)) {
            row.addView(state("DOWNLOADING\u2026"));
        } else if (installed == null) {
            row.addView(action("DOWNLOAD", v -> download(pack)));
        } else {
            if (!pack.version.equals(installed.version)) {
                row.addView(action("UPDATE", v -> download(pack)));
            } else {
                row.addView(state("INSTALLED"));
            }
            row.addView(removeButton(pack.family, pack.slug));
        }
        list.addView(row);
        list.addView(divider());
    }

    private View installedRow(final PackStore.Installed pack) {
        LinearLayout row = shell();
        LinearLayout words = new LinearLayout(this);
        words.setOrientation(LinearLayout.VERTICAL);
        words.addView(text(pack.family, 15, R.attr.on));
        words.addView(text(pack.faceCount() + " faces \u00b7 "
                + PackStore.humanBytes(pack.bytes) + " \u00b7 installed", 12, R.attr.onDim));
        row.addView(words, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(state("INSTALLED"));
        row.addView(removeButton(pack.family, pack.slug));
        return row;
    }

    private void download(final PackCatalog.Pack pack) {
        if (!downloading.add(pack.slug)) {
            return;
        }
        render();
        new Thread(new Runnable() {
            @Override
            public void run() {
                File file = new File(getCacheDir(), "download-" + pack.slug + ".addpack");
                String message;
                try {
                    PackCatalog.download(pack, file);
                    PackStore.Result result = PackStore.importPack(
                            PacksActivity.this, file, pack.sha256);
                    message = result.message;
                } catch (IOException bad) {
                    message = networkMessage(bad);
                } finally {
                    file.delete();
                }
                final String answer = message;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (isFinishing()) {
                            return;
                        }
                        downloading.remove(pack.slug);
                        Toast.makeText(PacksActivity.this, answer, Toast.LENGTH_LONG).show();
                        render();
                    }
                });
            }
        }).start();
    }

    private String networkMessage(IOException error) {
        String message = error.getMessage();
        if (message == null) {
            return "Could not download that pack.";
        }
        if (message.startsWith("HTTP 404")) {
            return "The site has no packs page yet.";
        }
        if (message.startsWith("HTTP ")) {
            return "The site answered " + message.substring(5) + ".";
        }
        if ("too big".equals(message)) {
            return "That pack is too big.";
        }
        if ("incomplete".equals(message)) {
            return "The download was incomplete \u2014 try again.";
        }
        return "No connection \u2014 try again.";
    }

    private void confirmRemove(final String family, final String slug) {
        new AlertDialog.Builder(this)
                .setTitle(family)
                .setMessage("Remove this pack from the phone? Its faces leave the font list.")
                .setPositiveButton("Remove", (dialog, which) -> {
                    PackStore.remove(PacksActivity.this, slug);
                    render();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void pickPack() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivityForResult(intent, REQ_PACK);
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != REQ_PACK || result != RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        final Uri uri = data.getData();
        Toast.makeText(this, "Checking the pack\u2026", Toast.LENGTH_SHORT).show();
        new Thread(new Runnable() {
            @Override
            public void run() {
                final PackStore.Result answer = PackStore.importPack(PacksActivity.this, uri);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (isFinishing()) {
                            return;
                        }
                        Toast.makeText(PacksActivity.this, answer.message, Toast.LENGTH_LONG).show();
                        render();
                    }
                });
            }
        }).start();
    }

    // ---- pieces ---------------------------------------------------------------

    private LinearLayout shell() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(13), 0, dp(13));
        return row;
    }

    private TextView text(String value, int sp, int attr) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        view.setTextColor(colour(attr, Color.WHITE));
        view.setLineSpacing(0, 1.15f);
        return view;
    }

    private TextView state(String value) {
        TextView view = text(value, 10, R.attr.onDim);
        view.setLetterSpacing(0.06f);
        view.setPadding(dp(8), 0, dp(8), 0);
        return view;
    }

    private TextView action(String label, View.OnClickListener tap) {
        TextView view = new TextView(this);
        view.setText(label);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        view.setTypeface(getResources().getFont(R.font.poppins_semibold));
        view.setLetterSpacing(0.06f);
        view.setGravity(Gravity.CENTER);
        view.setBackgroundResource(R.drawable.bg_primary);
        view.setTextColor(colour(R.attr.accentInk, Color.WHITE));
        view.setClickable(true);
        view.setFocusable(true);
        view.setPadding(dp(12), 0, dp(12), 0);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dp(36));
        params.setMarginStart(dp(8));
        view.setLayoutParams(params);
        view.setOnClickListener(tap);
        return view;
    }

    private TextView removeButton(final String family, final String slug) {
        TextView view = new TextView(this);
        view.setText("\u2715");
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        view.setTextColor(colour(R.attr.onDim, Color.GRAY));
        view.setGravity(Gravity.CENTER);
        view.setContentDescription("Remove " + family);
        view.setClickable(true);
        view.setFocusable(true);
        view.setLayoutParams(new LinearLayout.LayoutParams(dp(38), dp(38)));
        view.setOnClickListener(v -> confirmRemove(family, slug));
        return view;
    }

    private TextView section(String value) {
        TextView view = text(value, 11, R.attr.onDim);
        view.setLetterSpacing(0.12f);
        view.setPadding(0, dp(18), 0, dp(4));
        return view;
    }

    private View divider() {
        View line = new View(this);
        line.setBackgroundColor(colour(R.attr.rule, 0xFF3A3D38));
        line.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Math.max(1, dp(1))));
        return line;
    }

    private void applyInsets() {
        final View root = findViewById(R.id.packsRoot);
        root.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override
            public WindowInsets onApplyWindowInsets(View view, WindowInsets insets) {
                int top = insets.getSystemWindowInsetTop();
                if (Build.VERSION.SDK_INT >= 28) {
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
