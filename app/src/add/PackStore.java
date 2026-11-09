package add;

import android.content.Context;
import android.net.Uri;

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
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Installs the packs the site publishes, without opening a socket itself.
 *
 * A pack is a zip: one manifest.json, its DER signature and the font files it lists.
 * The download screen streams one file and hands it here; the picker path copies a
 * document the reader chose. Either way the app reads exactly that one archive.
 *
 * Everything is checked before a byte is installed: the outer SHA-256 from the
 * listing, the manifest's ECDSA P-256 signature against the key in PackKey, the
 * archive shape, the size caps and a SHA-256 per file. The pack is written to a
 * scratch folder first and moved into place only when all of it verified, so a bad
 * pack leaves the previous install untouched.
 */
final class PackStore {

    static final String ROOT = "font-packs";

    private static final int MAX_ENTRIES = 64;
    private static final long MAX_TOTAL = 12L * 1024 * 1024;
    private static final long MAX_FILE = 6L * 1024 * 1024;
    private static final long MAX_MANIFEST = 64 * 1024;

    /** One installed pack, read from its own manifest rather than a second registry. */
    static final class Installed {
        final String slug;
        final String family;
        final String version;
        final JSONArray styles;
        final long bytes;
        final File dir;

        Installed(String slug, String family, String version, JSONArray styles, long bytes, File dir) {
            this.slug = slug;
            this.family = family;
            this.version = version;
            this.styles = styles;
            this.bytes = bytes;
            this.dir = dir;
        }

        int faceCount() {
            return styles == null ? 0 : styles.length();
        }
    }

    /** What the importer answers with: one line, already fit for a toast. */
    static final class Result {
        final boolean ok;
        final String message;

        Result(boolean ok, String message) {
            this.ok = ok;
            this.message = message;
        }
    }

    private PackStore() {
    }

    /**
     * Every installed pack, alphabetical. The folder tree is the registry: a pack
     * exists when its manifest does, which cannot drift the way a second index can.
     */
    static List<Installed> installed(Context context) {
        List<Installed> packs = new ArrayList<>();
        File root = new File(context.getFilesDir(), ROOT);
        File[] slugs = root.listFiles();
        if (slugs == null) {
            return packs;
        }
        for (File slugDir : slugs) {
            // A dot folder is an install in progress, never a pack of its own.
            if (!slugDir.isDirectory() || slugDir.getName().startsWith(".")) {
                continue;
            }
            File[] versions = slugDir.listFiles();
            if (versions == null) {
                continue;
            }
            boolean versioned = false;
            for (File versionDir : versions) {
                if (!versionDir.isDirectory()) {
                    continue;
                }
                Installed pack = read(versionDir, true);
                if (pack != null) {
                    packs.add(pack);
                    versioned = true;
                }
            }
            // Builds before the 3.6 install fix wrote manifest and TTFs directly
            // under <slug>/. Keep reading that layout so an existing install is
            // visible after the update; the next download replaces it with the
            // versioned layout above.
            if (!versioned) {
                Installed legacy = read(slugDir, false);
                if (legacy != null) {
                    packs.add(legacy);
                }
            }
        }
        Collections.sort(packs, new Comparator<Installed>() {
            @Override
            public int compare(Installed a, Installed b) {
                return a.family.compareToIgnoreCase(b.family);
            }
        });
        return packs;
    }

    static Installed find(Context context, String slug) {
        for (Installed pack : installed(context)) {
            if (pack.slug.equals(slug)) {
                return pack;
            }
        }
        return null;
    }

    static boolean remove(Context context, String slug) {
        File dir = new File(new File(context.getFilesDir(), ROOT), slug);
        return deleteTree(dir);
    }

    /** Reads an installed manifest, or null when the folder is not a valid pack. */
    private static Installed read(File dir, boolean versionedLayout) {
        File manifest = new File(dir, "manifest.json");
        if (!manifest.isFile()) {
            return null;
        }
        try {
            JSONObject json = new JSONObject(readText(manifest));
            if (json.optInt("schema", 0) != 1 || !"font".equals(json.optString("kind"))) {
                return null;
            }
            String slug = json.optString("slug");
            String version = json.optString("version");
            if (!validSlug(slug) || !validVersion(version)
                    || (versionedLayout && (!dir.getName().equals(version)
                    || !dir.getParentFile().getName().equals(slug)))) {
                return null;
            }
            long bytes = 0;
            JSONArray files = json.optJSONArray("files");
            if (files == null) {
                return null;
            }
            for (int i = 0; i < files.length(); i++) {
                bytes += files.getJSONObject(i).optLong("bytes", 0);
            }
            JSONArray styles = json.optJSONArray("styles");
            if (styles == null || styles.length() == 0) {
                return null;
            }
            String family = json.optString("family", slug);
            return new Installed(slug, family, version, styles, bytes, dir);
        } catch (JSONException | IOException bad) {
            return null;
        }
    }

    /**
     * Verifies a pack and installs it. The caller passes the URI the picker or an
     * "Open with ADD" handed over; the grant covers that one file.
     */
    static Result importPack(Context context, Uri uri) {
        if (uri == null) {
            return new Result(false, "Nothing to import");
        }
        File scratch = new File(context.getCacheDir(), "pack-uri");
        File archive = new File(scratch, "incoming.addpack");
        try {
            deleteTree(scratch);
            if (!scratch.mkdirs()) {
                return new Result(false, "Could not open a scratch folder");
            }
            copy(context, uri, archive);
            return importArchive(context, archive, null);
        } catch (IOException bad) {
            return new Result(false, friendly(bad));
        } finally {
            deleteTree(scratch);
        }
    }

    /** The download path: the listing's outer hash, then the manifest signature. */
    static Result importPack(Context context, File archive, String expectedSha256) {
        if (archive == null || !archive.isFile()) {
            return new Result(false, "That file could not be opened");
        }
        if (archive.length() > MAX_TOTAL) {
            return new Result(false, "That pack is too big");
        }
        return importArchive(context, archive, expectedSha256);
    }

    private static Result importArchive(Context context, File archive, String expectedSha256) {
        File work = new File(context.getCacheDir(), "pack-work");
        File unpacked = new File(work, "unpacked");
        try {
            deleteTree(work);
            if (!work.mkdirs()) {
                return new Result(false, "Could not open a scratch folder");
            }
            if (expectedSha256 != null && !expectedSha256.equalsIgnoreCase(sha256(archive))) {
                return new Result(false, "The pack is damaged \u2014 download it again");
            }
            JSONObject manifest = unpack(archive, unpacked);
            String slug = manifest.getString("slug");
            String version = manifest.getString("version");
            String family = manifest.optString("family", slug);
            int faces = manifest.getJSONArray("styles").length();

            File root = new File(context.getFilesDir(), ROOT);
            if (!root.isDirectory() && !root.mkdirs()) {
                return new Result(false, "Could not install into the app's storage");
            }
            // Stage beside the real folder, then swap: the previous version is only
            // removed once the new one is complete, so a failed move leaves it usable.
            File staging = new File(root, ".new-" + slug);
            File backup = new File(root, ".old-" + slug);
            deleteTree(staging);
            deleteTree(backup);
            if (!staging.mkdirs()) {
                return new Result(false, "Could not install into the app's storage");
            }
            // installed() reads <slug>/<version>/manifest.json; keep that layout here so
            // a successful install is visible to the editor on the very next read.
            File versionDir = new File(staging, version);
            if (!versionDir.mkdirs()) {
                deleteTree(staging);
                return new Result(false, "Could not install into the app's storage");
            }
            if (!moveContents(unpacked, versionDir)) {
                deleteTree(staging);
                return new Result(false, "Could not install " + family);
            }
            File slugDir = new File(root, slug);
            boolean replaced = slugDir.exists();
            if (replaced && !slugDir.renameTo(backup)) {
                deleteTree(staging);
                return new Result(false, "Could not replace the old " + family + " pack");
            }
            if (!staging.renameTo(slugDir)) {
                if (replaced) {
                    backup.renameTo(slugDir);
                }
                deleteTree(staging);
                return new Result(false, "Could not install " + family);
            }
            deleteTree(backup);
            return new Result(true, family + " installed \u00b7 " + faces + " faces");
        } catch (JSONException | IOException bad) {
            return new Result(false, friendly(bad));
        } finally {
            deleteTree(work);
        }
    }

    /**
     * Extracts and verifies the archive into {@code into}. Only a manifest.json, its
     * signature and the files the manifest lists are allowed, flat, inside the caps,
     * each with its own hash.
     */
    private static JSONObject unpack(File archive, File into) throws IOException, JSONException {
        if (!into.mkdirs()) {
            throw new IOException("scratch folder");
        }
        try (ZipFile zip = new ZipFile(archive)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            Map<String, ZipEntry> byName = new HashMap<>();
            long total = 0;
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                if (entry.isDirectory() || name == null || name.isEmpty()
                        || name.contains("/") || name.contains("\\") || name.contains("..")) {
                    throw new IOException("unsafe entry");
                }
                if (byName.put(name, entry) != null) {
                    throw new IOException("duplicate entry");
                }
                if (byName.size() > MAX_ENTRIES) {
                    throw new IOException("too many entries");
                }
                total += entry.getSize();
                if (entry.getSize() < 0 || entry.getSize() > MAX_FILE || total > MAX_TOTAL) {
                    throw new IOException("size cap");
                }
            }
            ZipEntry manifestEntry = byName.remove("manifest.json");
            if (manifestEntry == null || manifestEntry.getSize() > MAX_MANIFEST) {
                throw new IOException("manifest");
            }
            byte[] manifestBytes = readEntry(zip, manifestEntry);
            String manifestText = new String(manifestBytes, StandardCharsets.UTF_8);
            JSONObject manifest = new JSONObject(manifestText);

            ZipEntry signatureEntry = byName.remove("manifest.sig");
            if (signatureEntry == null || signatureEntry.getSize() > 1024) {
                throw new IOException("unsigned");
            }
            if (!PackKey.ID.equals(manifest.optString("key"))) {
                throw new IOException("key");
            }
            if (!"ES256".equals(manifest.optString("alg"))) {
                throw new IOException("alg");
            }
            if (!PackKey.verify(manifestBytes, readEntry(zip, signatureEntry))) {
                throw new IOException("signature");
            }
            validate(manifest);

            JSONArray files = manifest.getJSONArray("files");
            for (int i = 0; i < files.length(); i++) {
                JSONObject file = files.getJSONObject(i);
                String path = file.getString("path");
                ZipEntry entry = byName.remove(path);
                if (entry == null) {
                    throw new IOException("missing " + path);
                }
                byte[] bytes = readEntry(zip, entry);
                if (bytes.length != file.getLong("bytes")
                        || !sha256(bytes).equals(file.getString("sha256"))) {
                    throw new IOException("hash " + path);
                }
                write(new File(into, path), bytes);
            }
            if (!byName.isEmpty()) {
                throw new IOException("extra entry");
            }

            File out = new File(into, "manifest.json");
            write(out, manifestText.getBytes("UTF-8"));
            return manifest;
        }
    }

    private static void validate(JSONObject manifest) throws JSONException, IOException {
        if (manifest.optInt("schema", 0) != 1) {
            throw new IOException("schema");
        }
        if (!"font".equals(manifest.optString("kind"))) {
            throw new IOException("kind");
        }
        if (!validSlug(manifest.optString("slug")) || !validVersion(manifest.optString("version"))) {
            throw new IOException("name");
        }
        JSONArray files = manifest.optJSONArray("files");
        JSONArray styles = manifest.optJSONArray("styles");
        if (files == null || files.length() == 0 || styles == null || styles.length() == 0) {
            throw new IOException("manifest lists");
        }
        Map<String, Boolean> listed = new HashMap<>();
        for (int i = 0; i < files.length(); i++) {
            JSONObject file = files.getJSONObject(i);
            String path = file.optString("path");
            String hash = file.optString("sha256");
            if (path.isEmpty() || path.contains("/") || !hash.matches("[0-9a-f]{64}")) {
                throw new IOException("file entry");
            }
            if (listed.put(path, Boolean.TRUE) != null) {
                throw new IOException("duplicate file");
            }
        }
        for (int i = 0; i < styles.length(); i++) {
            JSONObject style = styles.getJSONObject(i);
            if (style.optString("name").isEmpty() || !listed.containsKey(style.optString("file"))) {
                throw new IOException("style entry");
            }
        }
    }

    private static void copy(Context context, Uri uri, File to) throws IOException {
        InputStream in = context.getContentResolver().openInputStream(uri);
        if (in == null) {
            throw new IOException("unreadable");
        }
        try (InputStream source = in; OutputStream out = new FileOutputStream(to)) {
            byte[] chunk = new byte[8192];
            int read;
            while ((read = source.read(chunk)) > 0) {
                out.write(chunk, 0, read);
            }
        }
    }

    private static String readText(File file) throws IOException {
        try (InputStream in = new java.io.FileInputStream(file)) {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int read;
            while ((read = in.read(chunk)) > 0) {
                buffer.write(chunk, 0, read);
            }
            return buffer.toString("UTF-8");
        }
    }

    private static byte[] readEntry(ZipFile zip, ZipEntry entry) throws IOException {
        try (InputStream in = zip.getInputStream(entry)) {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int read;
            while ((read = in.read(chunk)) > 0) {
                buffer.write(chunk, 0, read);
            }
            return buffer.toByteArray();
        }
    }

    private static void write(File file, byte[] bytes) throws IOException {
        try (OutputStream out = new FileOutputStream(file)) {
            out.write(bytes);
        }
    }

    private static boolean moveContents(File from, File to) {
        File[] children = from.listFiles();
        if (children == null) {
            return false;
        }
        for (File child : children) {
            File target = new File(to, child.getName());
            if (!child.renameTo(target)) {
                if (!copyFile(child, target)) {
                    return false;
                }
                if (!child.delete()) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean copyFile(File from, File to) {
        try (InputStream in = new java.io.FileInputStream(from);
             OutputStream out = new FileOutputStream(to)) {
            byte[] chunk = new byte[8192];
            int read;
            while ((read = in.read(chunk)) > 0) {
                out.write(chunk, 0, read);
            }
            return true;
        } catch (IOException bad) {
            return false;
        }
    }

    private static boolean deleteTree(File file) {
        if (file == null || !file.exists()) {
            return true;
        }
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteTree(child);
            }
        }
        return file.delete();
    }

    static String sha256(byte[] bytes) {
        try {
            return hex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException missing) {
            return "";
        }
    }

    private static String sha256(File file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream in = new FileInputStream(file)) {
                byte[] chunk = new byte[8192];
                int read;
                while ((read = in.read(chunk)) > 0) {
                    digest.update(chunk, 0, read);
                }
            }
            return hex(digest.digest());
        } catch (IOException | NoSuchAlgorithmException bad) {
            return "";
        }
    }

    private static String hex(byte[] hash) {
        StringBuilder text = new StringBuilder(hash.length * 2);
        for (byte value : hash) {
            text.append(String.format("%02x", value & 0xff));
        }
        return text.toString();
    }

    private static boolean validSlug(String slug) {
        return slug != null && slug.matches("[a-z0-9][a-z0-9-]{0,39}");
    }

    private static boolean validVersion(String version) {
        return version != null && version.matches("[0-9a-f]{6,16}");
    }

    /** A failure the reader can act on, rather than a stack trace class name. */
    private static String friendly(Exception error) {
        String message = error.getMessage();
        if ("unreadable".equals(message)) {
            return "That file could not be opened";
        }
        if ("unsafe entry".equals(message) || "extra entry".equals(message)) {
            return "That pack has files it should not";
        }
        if ("schema".equals(message) || "kind".equals(message) || "name".equals(message)) {
            return "That is not an ADD font pack";
        }
        if ("unsigned".equals(message)) {
            return "That pack is not signed";
        }
        if ("signature".equals(message) || "key".equals(message) || "alg".equals(message)) {
            return "That pack's signature is not one this app trusts";
        }
        if (message != null && message.startsWith("hash ")) {
            return "The pack is damaged \u2014 download it again";
        }
        if (message != null && message.startsWith("missing ")) {
            return "The pack is missing a file";
        }
        if ("size cap".equals(message) || "too many entries".equals(message)) {
            return "That pack is too big";
        }
        return "That pack could not be read";
    }

    static String humanBytes(long bytes) {
        if (bytes >= 1024L * 1024L) {
            return Math.round(bytes / (1024.0 * 1024.0)) + " MB";
        }
        if (bytes >= 1024L) {
            return Math.round(bytes / 1024.0) + " KB";
        }
        return bytes + " bytes";
    }
}
