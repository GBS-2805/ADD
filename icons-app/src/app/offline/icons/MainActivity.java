package app.offline.icons;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * The icon browser, offline. Every page, glyph and font the browser needs ships in
 * assets/www, served to the WebView from a private https origin so fetch(), the
 * service worker-free JSON catalogs and the clipboard all behave exactly as they do
 * on the site. Nothing is requested from the network: the app has no INTERNET
 * permission to request it with.
 */
public class MainActivity extends Activity {

    private static final String ORIGIN = "https://icons.local";
    private static final String ENTRY = ORIGIN + "/icons/index.html";
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
        setContentView(web, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

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

    /** The site's copy button lands here when the page runs inside the APK. */
    private class Bridge {
        @JavascriptInterface
        public void copy(final String text) {
            ClipboardManager manager =
                    (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            manager.setPrimaryClip(ClipData.newPlainText("SVG", text));
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(MainActivity.this, "SVG copied", Toast.LENGTH_SHORT).show();
                }
            });
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

    /**
     * Only the icon browser is bundled. A site page that is not here (downloads,
     * the portfolio, the render gallery) gets a short explanation instead of a
     * blank screen, while a missing asset gets an empty 404 so it cannot be parsed
     * as CSS or JSON.
     */
    private WebResourceResponse fallbackFor(String asset) {
        if (!asset.endsWith(".html")) {
            return new WebResourceResponse("text/plain", "utf-8", 404, "Not bundled",
                    new HashMap<String, String>(), new ByteArrayInputStream(new byte[0]));
        }
        String page = "<!doctype html><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
                + "<body style=\"margin:0;padding:56px 24px;background:#3f4e96;color:#d9d9d9;"
                + "font-family:Poppins,system-ui,sans-serif;line-height:1.6\">"
                + "<p style=\"margin:0 0 10px;font-size:13px;letter-spacing:.12em;"
                + "text-transform:uppercase;opacity:.65\">Icons \u00b7 offline</p>"
                + "<h1 style=\"margin:0 0 14px;font-size:28px;font-weight:400\">That page lives on the site</h1>"
                + "<p style=\"margin:0 0 26px;max-width:34ch\">The app bundles the icon browser: both brands, "
                + "their catalogs, the preview and the fonts. Downloads, the portfolio and the render "
                + "gallery stay online.</p>"
                + "<a href=\"/icons/index.html\" style=\"display:inline-block;padding:14px 20px;"
                + "background:#d9d9d9;color:#000;text-decoration:none\">Back to the icons</a></body>";
        Map<String, String> headers = new HashMap<String, String>();
        headers.put("Cache-Control", "no-store");
        return new WebResourceResponse("text/html", "utf-8", 404, "Not bundled", headers,
                new ByteArrayInputStream(page.getBytes()));
    }
}
