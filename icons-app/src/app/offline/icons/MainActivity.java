package app.offline.icons;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.DisplayCutout;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Toast;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * The icon browser, offline. Every page, glyph and font the browser needs ships in
 * assets/www, served to the WebView from a private https origin so fetch(), the JSON
 * catalogs and the clipboard all behave exactly as they do on the site. Nothing is
 * requested from the network: the app has no INTERNET permission to request it with.
 */
public class MainActivity extends Activity {

    private static final String ORIGIN = "https://icons.local";
    private static final String ENTRY = ORIGIN + "/icons/index.html";
    private static final String SITE = "https://site-bfde50ad8d634f69a640767bcad4a006.freebuff.page";
    private static final String FIELD = "#3F4E96";

    private WebView web;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        getWindow().setStatusBarColor(Color.parseColor(FIELD));
        getWindow().setNavigationBarColor(Color.parseColor(FIELD));

        web = new WebView(this);
        web.setBackgroundColor(Color.parseColor(FIELD));
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setTextZoom(100);

        web.addJavascriptInterface(new Bridge(), "IconsApp");
        web.setWebViewClient(new LocalClient());

        // Android 15 draws every app edge to edge, so the band and the first heading
        // end up under the status bar and the camera cutout of a tall phone. The
        // browser sits in a padded frame instead, using the real inset on each edge.
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.parseColor(FIELD));
        root.addView(web, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
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
        setContentView(root);
        root.requestApplyInsets();

        if (state == null) {
            web.loadUrl(ENTRY);
        } else {
            web.restoreState(state);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        web.saveState(out);
    }

    @Override
    public void onBackPressed() {
        if (web.canGoBack()) {
            web.goBack();
        } else {
            super.onBackPressed();
        }
    }

    /** What the page can call: the clipboard, and saving a PNG, WEBP or JPG. */
    private class Bridge {
        @JavascriptInterface
        public void copy(final String text) {
            ClipboardManager manager =
                    (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            manager.setPrimaryClip(ClipData.newPlainText("SVG", text));
            toast("SVG copied");
        }

        @JavascriptInterface
        public void saveImage(final String dataUrl, final String mime, final String name) {
            final String message = writeImage(dataUrl, mime, name);
            toast(message);
        }

        private void toast(final String message) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
                }
            });
        }
    }

    /**
     * A data URL from the page's canvas becomes a picture in Pictures/Icons. On
     * Android 10 and later MediaStore needs no permission at all; on 8 and 9 the file
     * goes to the app's own pictures folder, which also needs none.
     */
    private String writeImage(String dataUrl, String mime, String name) {
        if (dataUrl == null) {
            return "Nothing to save";
        }
        int comma = dataUrl.indexOf(',');
        if (comma < 0) {
            return "Nothing to save";
        }
        byte[] bytes;
        try {
            bytes = Base64.decode(dataUrl.substring(comma + 1), Base64.DEFAULT);
        } catch (IllegalArgumentException bad) {
            return "Could not read that image";
        }
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Images.Media.DISPLAY_NAME, name);
                values.put(MediaStore.Images.Media.MIME_TYPE, mime);
                values.put(MediaStore.Images.Media.RELATIVE_PATH,
                        Environment.DIRECTORY_PICTURES + "/Icons");
                Uri uri = getContentResolver().insert(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                if (uri == null) {
                    return "Could not save " + name;
                }
                OutputStream out = getContentResolver().openOutputStream(uri);
                if (out == null) {
                    return "Could not save " + name;
                }
                out.write(bytes);
                out.close();
                return "Saved to Pictures/Icons/" + name;
            }
            File dir = new File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "Icons");
            if (!dir.exists() && !dir.mkdirs()) {
                return "Could not create the pictures folder";
            }
            File file = new File(dir, name);
            FileOutputStream out = new FileOutputStream(file);
            out.write(bytes);
            out.close();
            return "Saved " + file.getAbsolutePath();
        } catch (IOException error) {
            return "Could not save: " + error.getMessage();
        }
    }

    private class LocalClient extends WebViewClient {
        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            Uri url = request.getUrl();
            if (url == null || url.getHost() == null || !url.getHost().equals("icons.local")) {
                return null;
            }
            String asset = assetFor(url.getPath());
            try {
                InputStream stream = getAssets().open(asset);
                return new WebResourceResponse(mimeOf(asset), encodingOf(asset), stream);
            } catch (IOException missing) {
                return fallbackFor(asset);
            }
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            Uri url = request.getUrl();
            if (url != null && "icons.local".equals(url.getHost())) {
                return false;
            }
            if (url != null && ("http".equals(url.getScheme()) || "https".equals(url.getScheme()))) {
                // A site page the app does not bundle opens in the browser instead.
                startActivity(new Intent(Intent.ACTION_VIEW, url));
                return true;
            }
            return true;
        }
    }

    private static String assetFor(String path) {
        String clean = path == null ? "" : path;
        int query = clean.indexOf('?');
        if (query >= 0) {
            clean = clean.substring(0, query);
        }
        if (clean.isEmpty() || clean.equals("/")) {
            return "www/index.html";
        }
        if (clean.endsWith("/")) {
            clean = clean + "index.html";
        }
        if (!clean.startsWith("/")) {
            clean = "/" + clean;
        }
        return "www" + clean;
    }

    private static String mimeOf(String asset) {
        if (asset.endsWith(".html")) return "text/html";
        if (asset.endsWith(".css")) return "text/css";
        if (asset.endsWith(".js")) return "text/javascript";
        if (asset.endsWith(".json")) return "application/json";
        if (asset.endsWith(".svg")) return "image/svg+xml";
        if (asset.endsWith(".woff2")) return "font/woff2";
        if (asset.endsWith(".ttf")) return "font/ttf";
        if (asset.endsWith(".png")) return "image/png";
        return "application/octet-stream";
    }

    private static String encodingOf(String asset) {
        if (asset.endsWith(".woff2") || asset.endsWith(".ttf") || asset.endsWith(".png")) {
            return null;   // binary: let WebView read the bytes as they are
        }
        return "utf-8";
    }

    /** The site page that corresponds to a bundle path the app does not carry. */
    private static String sitePathFor(String asset) {
        if (asset.contains("/renders/")) return "/renders/";
        if (asset.contains("/download/")) return "/download/";
        if (asset.contains("/portfolio/")) return "/portfolio/";
        if (asset.contains("/tools/")) return "/tools/";
        if (asset.contains("/fonts/")) return "/fonts/";
        return "/";
    }

    /**
     * Only the icon browser is bundled. A site page that is not here (downloads,
     * the portfolio, the render gallery) gets a short explanation and the address of
     * the same page on the site, while a missing asset gets an empty 404 so it cannot
     * be parsed as CSS or JSON.
     */
    private WebResourceResponse fallbackFor(String asset) {
        if (!asset.endsWith(".html")) {
            return new WebResourceResponse("text/plain", "utf-8", 404, "Not bundled",
                    new HashMap<String, String>(), new ByteArrayInputStream(new byte[0]));
        }
        String path = sitePathFor(asset);
        String address = SITE + path;
        String page = "<!doctype html><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
                + "<body style=\"margin:0;padding:56px 24px;background:#3f4e96;color:#d9d9d9;"
                + "font-family:Poppins,system-ui,sans-serif;line-height:1.6\">"
                + "<p style=\"margin:0 0 10px;font-size:13px;letter-spacing:.12em;"
                + "text-transform:uppercase;opacity:.65\">Icons \u00b7 offline</p>"
                + "<h1 style=\"margin:0 0 14px;font-size:28px;font-weight:400\">That page lives on the site</h1>"
                + "<p style=\"margin:0 0 12px;max-width:34ch\">The app bundles the icon browser: both brands, "
                + "their catalogs, the preview and the fonts. Downloads, the portfolio and the render "
                + "gallery stay online.</p>"
                + "<p style=\"margin:0 0 26px\"><code style=\"font-size:13px;word-break:break-all\">"
                + address + "</code></p>"
                + "<a href=\"" + address + "\" style=\"display:inline-block;padding:14px 20px;margin:0 10px 12px 0;"
                + "background:#d9d9d9;color:#000;text-decoration:none\">Open on the web</a>"
                + "<a href=\"/icons/index.html\" style=\"display:inline-block;padding:14px 20px;margin:0 0 12px 0;"
                + "background:transparent;color:#d9d9d9;text-decoration:none;border:1px solid rgba(217,217,217,.55)\">"
                + "Back to the icons</a></body>";
        Map<String, String> headers = new HashMap<String, String>();
        headers.put("Cache-Control", "no-store");
        return new WebResourceResponse("text/html", "utf-8", 404, "Not bundled", headers,
                new ByteArrayInputStream(page.getBytes()));
    }
}
