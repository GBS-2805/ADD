package add;

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
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * The shelf's web tools, offline: the icon browser and the fonts page, served to the
 * WebView from a private origin out of assets/www. It is one half of the ADD app; the
 * compressor next door is native, because encoding photos is the platform's job.
 *
 * The pages themselves fetch nothing: they are served from `assets/www`, and the
 * only network code in ADD is the Font packs screen.
 */
public class ShelfActivity extends Activity {

    /** Which tool to open: "icons" or "fonts". */
    public static final String EXTRA_PAGE = "page";

    private static final String ORIGIN = "https://icons.local";

    /** The page's own colour before any page reports it: the shelf opens on paper. */
    private static final String PAGE = "#e9e0cb";

    /**
     * Asked of the page once loaded and again whenever the look changes, so the system
     * bars continue the page edge to edge instead of framing it in a colour of their own.
     */
    private static final String CHROME_JS =
            "(function(){"
            + "function bg(el){if(!el)return '';var c=getComputedStyle(el).backgroundColor;"
            + "return c==='rgba(0, 0, 0, 0)'?'':c;}"
            + "function report(){var band=bg(document.querySelector('.band'))||bg(document.querySelector('header'));"
            + "var page=bg(document.body)||bg(document.documentElement);"
            + "if(window.IconsApp&&IconsApp.chrome)IconsApp.chrome(band,page);}"
            + "report();"
            + "new MutationObserver(report).observe(document.documentElement,"
            + "{attributes:true,attributeFilter:['data-palette','data-theme']});"
            + "})()";

    private WebView web;
    private FrameLayout root;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        getWindow().setStatusBarColor(Color.parseColor(PAGE));
        getWindow().setNavigationBarColor(Color.parseColor(PAGE));

        web = new WebView(this);
        web.setBackgroundColor(Color.parseColor(PAGE));
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setTextZoom(100);

        // The site's pages call window.IconsApp; the name stays, so one bundle serves
        // every tool instead of each page needing to know which app it is inside.
        web.addJavascriptInterface(new Bridge(), "IconsApp");
        web.setWebViewClient(new LocalClient());

        root = new FrameLayout(this);
        root.setBackgroundColor(Color.parseColor(PAGE));
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
            web.loadUrl(entry());
        } else {
            web.restoreState(state);
        }
    }

    private String entry() {
        String tool = getIntent() == null ? null : getIntent().getStringExtra(EXTRA_PAGE);
        if ("fonts".equals(tool)) {
            return ORIGIN + "/fonts/index.html";
        }
        return ORIGIN + "/index.html";
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

    /** What the pages can call: the clipboard, saving a raster, and the bars' colours. */
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
            toast(writeFile(dataUrl, mime, name, true));
        }

        /** A bulk zip goes to Downloads, where a file of many pictures belongs. */
        @JavascriptInterface
        public void saveFile(final String dataUrl, final String mime, final String name) {
            toast(writeFile(dataUrl, mime, name, false));
        }

        /**
         * A font the page offers as a download. A WebView has nobody to hand a download
         * to, so the page passes the href and the bundle's own bytes are written out.
         */
        @JavascriptInterface
        public void saveFont(final String href) {
            toast(writeFont(href));
        }

        /**
         * A Tools card that names a native tool. The page keeps its href for the site;
         * inside the app the bridge takes the tap and opens the native screen instead.
         */
        @JavascriptInterface
        public void openTool(final String slug) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if ("editor".equals(slug)) {
                        EditorActivity.open(ShelfActivity.this);
                    } else if ("compress".equals(slug)) {
                        startActivity(new Intent(ShelfActivity.this, MainActivity.class));
                    }
                }
            });
        }

        @JavascriptInterface
        public void chrome(final String band, final String page) {
            final int bandColour = css(band);
            final int pageColour = css(page);
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (bandColour != 0) {
                        getWindow().setStatusBarColor(bandColour);
                    }
                    if (pageColour != 0) {
                        getWindow().setNavigationBarColor(pageColour);
                        root.setBackgroundColor(pageColour);
                    }
                }
            });
        }

        private void toast(final String message) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(ShelfActivity.this, message, Toast.LENGTH_LONG).show();
                }
            });
        }
    }

    /**
     * A data URL from the page's canvas becomes a picture in Pictures/Shelf, or a zip
     * in Downloads/Shelf. On Android 10 and later MediaStore needs no permission; on
     * 8 and 9 the file goes to the app's own folder, which also needs none.
     */
    private String writeFile(String dataUrl, String mime, String name, boolean picture) {
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
        return saveBytes(bytes, mime, name,
                (picture ? Environment.DIRECTORY_PICTURES : Environment.DIRECTORY_DOWNLOADS) + "/Shelf",
                picture, "Downloads");
    }

    /**
     * A font the page offers as a download, read out of the bundle rather than
     * fetched: the app carries the woff2 it sets and the woff beside it, and both land
     * in Downloads/Fonts, where a font belongs. Nothing is saved that is not there.
     */
    private String writeFont(String href) {
        String path = href == null ? "" : href.trim();
        int cut = path.indexOf('?');
        if (cut >= 0) {
            path = path.substring(0, cut);
        }
        cut = path.indexOf('#');
        if (cut >= 0) {
            path = path.substring(0, cut);
        }
        if (path.startsWith(ORIGIN)) {
            path = path.substring(ORIGIN.length());
        }
        if (path.isEmpty()) {
            return "Nothing to save";
        }
        String asset = assetFor(path);
        String name = asset.substring(asset.lastIndexOf('/') + 1);
        String format = name.substring(name.lastIndexOf('.') + 1);
        try {
            InputStream in = getAssets().open(asset);
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int read;
            while ((read = in.read(chunk)) > 0) {
                buffer.write(chunk, 0, read);
            }
            in.close();
            return saveBytes(buffer.toByteArray(), mimeOf(asset), name,
                    Environment.DIRECTORY_DOWNLOADS + "/Fonts", false, "Downloads");
        } catch (IOException missing) {
            return "That " + format + " stays on the site";
        }
    }

    /** The one write path, so every file lands by the same rule. */
    private String saveBytes(byte[] bytes, String mime, String name, String relative,
                             boolean picture, String label) {
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
                values.put(MediaStore.MediaColumns.MIME_TYPE, mime);
                values.put(MediaStore.MediaColumns.RELATIVE_PATH, relative);
                Uri collection = picture
                        ? MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                        : MediaStore.Downloads.EXTERNAL_CONTENT_URI;
                Uri uri = getContentResolver().insert(collection, values);
                if (uri == null) {
                    return "Could not save " + name;
                }
                OutputStream out = getContentResolver().openOutputStream(uri);
                if (out == null) {
                    return "Could not save " + name;
                }
                out.write(bytes);
                out.close();
                return "Saved to " + relative + "/" + name;
            }
            File dir = new File(getExternalFilesDir(
                    picture ? Environment.DIRECTORY_PICTURES : Environment.DIRECTORY_DOWNLOADS),
                    relative.substring(relative.indexOf('/') + 1));
            if (!dir.exists() && !dir.mkdirs()) {
                return "Could not create the " + label + " folder";
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

    /** A computed CSS colour as an ARGB int, or 0 when it cannot be read. */
    private static int css(String value) {
        if (value == null) {
            return 0;
        }
        String text = value.trim();
        if (text.isEmpty()) {
            return 0;
        }
        if (text.startsWith("#")) {
            try {
                return Color.parseColor(text);
            } catch (IllegalArgumentException bad) {
                return 0;
            }
        }
        if (!text.startsWith("rgb")) {
            return 0;
        }
        int open = text.indexOf('(');
        int close = text.indexOf(')');
        if (open < 0 || close < open) {
            return 0;
        }
        String[] parts = text.substring(open + 1, close).split(",");
        if (parts.length < 3) {
            return 0;
        }
        try {
            int red = Math.round(Float.parseFloat(parts[0].trim()));
            int green = Math.round(Float.parseFloat(parts[1].trim()));
            int blue = Math.round(Float.parseFloat(parts[2].trim()));
            float alpha = parts.length > 3 ? Float.parseFloat(parts[3].trim()) : 1f;
            return Color.argb(Math.round(alpha * 255), red, green, blue);
        } catch (NumberFormatException bad) {
            return 0;
        }
    }

    private class LocalClient extends WebViewClient {
        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
            view.evaluateJavascript(CHROME_JS, null);
        }

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
        if (asset.endsWith(".woff")) return "font/woff";
        if (asset.endsWith(".ttf")) return "font/ttf";
        if (asset.endsWith(".png")) return "image/png";
        return "application/octet-stream";
    }

    private static String encodingOf(String asset) {
        if (asset.endsWith(".woff2") || asset.endsWith(".woff") || asset.endsWith(".ttf")
                || asset.endsWith(".png")) {
            return null;
        }
        return "utf-8";
    }

    /** The site page that corresponds to a bundle path the app does not carry. */
    private static String sitePathFor(String asset) {
        if (asset.contains("/renders/")) return "/renders/";
        if (asset.contains("/figma/")) return "/figma/";
        if (asset.contains("/packs/")) return "/packs/";
        if (asset.contains("/download/")) return "/download/";
        if (asset.contains("/portfolio/")) return "/portfolio/";
        if (asset.contains("/tools/")) return "/tools/";
        if (asset.contains("/fonts/")) return "/fonts/";
        return "/";
    }

    /**
     * Only the shelf's web tools are bundled. A page that is not here gets a short
     * explanation and its address on the site; a missing asset gets an empty 404 so it
     * cannot be parsed as CSS or JSON.
     */
    private WebResourceResponse fallbackFor(String asset) {
        if (!asset.endsWith(".html")) {
            return new WebResourceResponse("text/plain", "utf-8", 404, "Not bundled",
                    new HashMap<String, String>(), new ByteArrayInputStream(new byte[0]));
        }
        String address = "https://artbase-olive.vercel.app" + sitePathFor(asset);
        String page = "<!doctype html><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
                + "<body style=\"margin:0;padding:56px 24px;background:#e9e0cb;color:#15120e;"
                + "font-family:'Libre Franklin',system-ui,sans-serif;line-height:1.6\">"
                + "<p style=\"margin:0 0 10px;font-size:13px;letter-spacing:.12em;"
                + "text-transform:uppercase;opacity:.65\">ADD \u00b7 offline</p>"
                + "<h1 style=\"margin:0 0 14px;font-family:'Instrument Serif',Georgia,serif;font-size:28px;font-weight:400\">That page lives on the site</h1>"
                + "<p style=\"margin:0 0 12px;max-width:34ch;font-family:'Spectral',Georgia,serif\">The app carries the tools: every "
                + "brand, their catalogs, the pages and the fonts. The two galleries are pictures, "
                + "so they stay on the site, where they already live.</p>"
                + "<p style=\"margin:0 0 26px\"><code style=\"font-size:13px;word-break:break-all\">"
                + address + "</code></p>"
                + "<a href=\"" + address + "\" style=\"display:inline-block;padding:14px 20px;margin:0 10px 12px 0;"
                + "background:#dd4124;color:#f2eee3;text-decoration:none\">Open on the web</a>"
                + "<a href=\"/icons/index.html\" style=\"display:inline-block;padding:14px 20px;margin:0 0 12px 0;"
                + "background:transparent;color:#15120e;text-decoration:none;border:1px solid rgba(21,18,14,.35)\">"
                + "Back to the icons</a></body>";
        Map<String, String> headers = new HashMap<String, String>();
        headers.put("Cache-Control", "no-store");
        return new WebResourceResponse("text/html", "utf-8", 404, "Not bundled", headers,
                new ByteArrayInputStream(page.getBytes()));
    }
}
