package add;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * The pack listing the site publishes at /packs/packs.json, plus the download of one
 * pack. The last listing is cached in the app's own storage, so the screen can name
 * every family with no connection; the packs themselves are never cached here --
 * an installed pack lives in PackStore's folder and is already the offline copy.
 *
 * Only HTTPS, only the shipped origin, only when the reader asks. Nothing in this
 * class runs from a background service or on app start.
 */
final class PackCatalog {

    private static final String PATH = "packs/packs.json";
    private static final String CACHE = "pack-catalog.json";
    private static final int MAX_JSON = 256 * 1024;
    private static final long MAX_PACK = 12L * 1024 * 1024;
    private static final int CONNECT_TIMEOUT = 10000;
    private static final int READ_TIMEOUT = 20000;

    /** One family as the site lists it. The signature itself travels in the zip. */
    static final class Pack {
        final String slug;
        final String family;
        final String version;
        final int faces;
        final long bytes;
        final String file;
        final String sha256;

        Pack(String slug, String family, String version, int faces, long bytes,
             String file, String sha256) {
            this.slug = slug;
            this.family = family;
            this.version = version;
            this.faces = faces;
            this.bytes = bytes;
            this.file = file;
            this.sha256 = sha256;
        }
    }

    private PackCatalog() {
    }

    /** The listing saved by the last successful check, or an empty list. */
    static List<Pack> cached(Context context) {
        File file = new File(context.getFilesDir(), CACHE);
        if (!file.isFile() || file.length() > MAX_JSON) {
            return new ArrayList<>();
        }
        try {
            return parse(readText(file));
        } catch (IOException | JSONException bad) {
            return new ArrayList<>();
        }
    }

    /** The live listing. Throws so the screen can say whether it was the network. */
    static String fetch() throws IOException {
        HttpURLConnection connection = open(MainActivity.SITE_URL + PATH);
        try {
            int code = connection.getResponseCode();
            if (code != 200) {
                throw new IOException("HTTP " + code);
            }
            try (InputStream in = connection.getInputStream()) {
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                byte[] chunk = new byte[8192];
                int read;
                while ((read = in.read(chunk)) > 0) {
                    if (buffer.size() + read > MAX_JSON) {
                        throw new IOException("listing too big");
                    }
                    buffer.write(chunk, 0, read);
                }
                return buffer.toString("UTF-8");
            }
        } finally {
            connection.disconnect();
        }
    }

    /** Best effort: a listing that cannot be saved still works for this visit. */
    static void save(Context context, String json) {
        File target = new File(context.getFilesDir(), CACHE);
        File temp = new File(target.getPath() + ".tmp");
        try (OutputStream out = new FileOutputStream(temp)) {
            out.write(json.getBytes(StandardCharsets.UTF_8));
        } catch (IOException bad) {
            temp.delete();
            return;
        }
        if (!temp.renameTo(target)) {
            temp.delete();
        }
    }

    /** Streams one pack to {@code to}, with the same caps PackStore applies. */
    static void download(Pack pack, File to) throws IOException {
        if (pack.bytes > MAX_PACK) {
            throw new IOException("too big");
        }
        HttpURLConnection connection = open(MainActivity.SITE_URL + "packs/" + pack.file);
        try {
            int code = connection.getResponseCode();
            if (code != 200) {
                throw new IOException("HTTP " + code);
            }
            long announced = connection.getContentLengthLong();
            if (announced > MAX_PACK) {
                throw new IOException("too big");
            }
            long total = 0;
            try (InputStream in = connection.getInputStream();
                 OutputStream out = new FileOutputStream(to)) {
                byte[] chunk = new byte[8192];
                int read;
                while ((read = in.read(chunk)) > 0) {
                    total += read;
                    if (total > MAX_PACK) {
                        throw new IOException("too big");
                    }
                    out.write(chunk, 0, read);
                }
            }
            if (total != pack.bytes || (announced > 0 && announced != total)) {
                throw new IOException("incomplete");
            }
        } finally {
            connection.disconnect();
        }
    }

    private static HttpURLConnection open(String address) throws IOException {
        URL url = new URL(address);
        if (!"https".equals(url.getProtocol())) {
            throw new IOException("not https");
        }
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(CONNECT_TIMEOUT);
        connection.setReadTimeout(READ_TIMEOUT);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("User-Agent", "ADD/3.6 (Android)");
        return connection;
    }

    static List<Pack> parse(String json) throws JSONException {
        JSONObject root = new JSONObject(json);
        JSONArray rows = root.optJSONArray("packs");
        if (rows == null || rows.length() == 0) {
            throw new JSONException("no packs");
        }
        List<Pack> packs = new ArrayList<>();
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.optJSONObject(i);
            if (row == null) {
                continue;
            }
            String slug = row.optString("slug");
            String family = row.optString("family", slug);
            String version = row.optString("version");
            String file = row.optString("file");
            String sha = row.optString("sha256");
            long bytes = row.optLong("bytes", 0);
            int faces = row.optInt("faces", 0);
            if (!slug.matches("[a-z0-9][a-z0-9-]{0,39}")) {
                continue;
            }
            if (family.isEmpty() || version.isEmpty() || sha.length() != 64) {
                continue;
            }
            if (file.isEmpty() || file.contains("/") || file.contains("\\")
                    || file.contains("..") || !file.endsWith(".addpack")) {
                continue;
            }
            if (bytes <= 0 || bytes > MAX_PACK || faces <= 0) {
                continue;
            }
            packs.add(new Pack(slug, family, version, faces, bytes, file, sha));
        }
        if (packs.isEmpty()) {
            throw new JSONException("no valid packs");
        }
        return packs;
    }

    private static String readText(File file) throws IOException {
        try (InputStream in = new FileInputStream(file)) {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int read;
            while ((read = in.read(chunk)) > 0) {
                buffer.write(chunk, 0, read);
            }
            return buffer.toString("UTF-8");
        }
    }
}
