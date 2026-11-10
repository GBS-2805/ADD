package add;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.View;
import android.view.ViewGroup;
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

/**
 * The Studio engine canvas, hosted by the editor rather than an activity of its own.
 *
 * The page is served from assets/www/studio, so it is offline; the only bridge out is
 * a picked photo and a PNG export into Pictures/Studio. The close bridge returns to
 * the editor's Quick tab instead of finishing the editor.
 */
public final class StudioPane extends FrameLayout {

    static final int REQ_PHOTO = 21;

    private static final String ORIGIN = "https://studio.local";
    private static final int MAX_PHOTO = 2048;

    public interface CloseListener {
        void onClose();
    }

    private final Activity host;
    private WebView web;
    private CloseListener closeListener;

    public StudioPane(Activity host) {
        super(host);
        this.host = host;
        setBackgroundColor(Color.parseColor("#131513"));

        web = new WebView(host);
        web.setBackgroundColor(Color.parseColor("#131513"));
        web.setLayerType(View.LAYER_TYPE_HARDWARE, null);
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
        settings.setMediaPlaybackRequiresUserGesture(true);

        web.addJavascriptInterface(new Bridge(), "StudioApp");
        web.setWebViewClient(new LocalClient());
        addView(web, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        web.loadUrl(ORIGIN + "/index.html");
    }

    public void setCloseListener(CloseListener listener) {
        closeListener = listener;
    }

    public void resume() {
        if (web != null) {
            web.onResume();
        }
    }

    public void pause() {
        if (web != null) {
            web.onPause();
        }
    }

    public void destroy() {
        if (web != null) {
            web.destroy();
            web = null;
        }
    }

    /** Consumes the Studio photo picker result; returns false for every other request. */
    public boolean onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode != REQ_PHOTO) {
            return false;
        }
        if (resultCode != Activity.RESULT_OK || data == null) {
            return true;
        }
        Uri uri = data.getData();
        if (uri == null) {
            toast("No photo returned");
            return true;
        }
        Bitmap bitmap = decode(uri);
        if (bitmap == null) {
            toast("Could not read that photo");
            return true;
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 88, out);
        bitmap.recycle();
        String base64 = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP);
        String js = "window.studioSetPhoto('data:image/jpeg;base64," + base64 + "')";
        if (web != null) {
            web.evaluateJavascript(js, null);
        }
        return true;
    }

    private class Bridge {
        @JavascriptInterface
        public void savePng(final String dataUrl) {
            final String result = writePng(dataUrl);
            toast(result);
        }

        @JavascriptInterface
        public void pickPhoto() {
            host.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("image/*");
                    host.startActivityForResult(intent, REQ_PHOTO);
                }
            });
        }

        @JavascriptInterface
        public void close() {
            host.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (closeListener != null) {
                        closeListener.onClose();
                    }
                }
            });
        }
    }

    private Bitmap decode(Uri uri) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            InputStream first = host.getContentResolver().openInputStream(uri);
            if (first == null) {
                return null;
            }
            BitmapFactory.decodeStream(first, null, bounds);
            first.close();
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                return null;
            }
            int sample = 1;
            while (bounds.outWidth / sample > MAX_PHOTO || bounds.outHeight / sample > MAX_PHOTO) {
                sample *= 2;
            }
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = sample;
            InputStream second = host.getContentResolver().openInputStream(uri);
            if (second == null) {
                return null;
            }
            Bitmap bitmap = BitmapFactory.decodeStream(second, null, options);
            second.close();
            return bitmap;
        } catch (IOException | OutOfMemoryError error) {
            return null;
        }
    }

    /** A data URL from the page becomes a PNG in Pictures/Studio, with no permission. */
    private String writePng(String dataUrl) {
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
        String name = "studio-" + System.currentTimeMillis() + ".png";
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
                values.put(MediaStore.MediaColumns.MIME_TYPE, "image/png");
                values.put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/Studio");
                Uri target = host.getContentResolver().insert(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                if (target == null) {
                    return "Could not save the PNG";
                }
                OutputStream out = host.getContentResolver().openOutputStream(target);
                if (out == null) {
                    return "Could not save the PNG";
                }
                out.write(bytes);
                out.close();
                return "Saved to Pictures/Studio/" + name;
            }
            File dir = new File(host.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "Studio");
            if (!dir.exists() && !dir.mkdirs()) {
                return "Could not create Pictures/Studio";
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

    private void toast(final String message) {
        host.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(host, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private class LocalClient extends WebViewClient {
        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            Uri url = request.getUrl();
            if (url == null || !"studio.local".equals(url.getHost())) {
                return null;
            }
            String asset = assetFor(url.getPath());
            try {
                InputStream stream = host.getAssets().open(asset);
                return new WebResourceResponse(mimeOf(asset), encodingOf(asset), stream);
            } catch (IOException missing) {
                return new WebResourceResponse("text/plain", "utf-8", 404, "Not bundled",
                        new HashMap<String, String>(), new ByteArrayInputStream(new byte[0]));
            }
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            Uri url = request.getUrl();
            if (url != null && "studio.local".equals(url.getHost())) {
                return false;
            }
            if (url != null && ("http".equals(url.getScheme()) || "https".equals(url.getScheme()))) {
                host.startActivity(new Intent(Intent.ACTION_VIEW, url));
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
            clean = "/index.html";
        } else if (clean.endsWith("/")) {
            clean = clean + "index.html";
        }
        if (!clean.startsWith("/")) {
            clean = "/" + clean;
        }
        return "www/studio" + clean;
    }

    private static String mimeOf(String asset) {
        if (asset.endsWith(".html")) return "text/html";
        if (asset.endsWith(".css")) return "text/css";
        if (asset.endsWith(".js")) return "text/javascript";
        if (asset.endsWith(".json")) return "application/json";
        if (asset.endsWith(".png")) return "image/png";
        if (asset.endsWith(".jpg") || asset.endsWith(".jpeg")) return "image/jpeg";
        if (asset.endsWith(".svg")) return "image/svg+xml";
        if (asset.endsWith(".woff2")) return "font/woff2";
        if (asset.endsWith(".woff")) return "font/woff";
        if (asset.endsWith(".ttf")) return "font/ttf";
        return "application/octet-stream";
    }

    private static String encodingOf(String asset) {
        if (asset.endsWith(".png") || asset.endsWith(".jpg") || asset.endsWith(".jpeg")
                || asset.endsWith(".woff2") || asset.endsWith(".woff") || asset.endsWith(".ttf")) {
            return null;
        }
        return "utf-8";
    }
}
