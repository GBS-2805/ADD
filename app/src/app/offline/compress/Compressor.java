package app.offline.compress;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * On-device image compression. Uses only platform APIs: decodes with
 * sub-sampling, resizes, applies EXIF rotation and re-encodes with a
 * descending quality ladder so the result is always the smallest of the
 * attempts. Nothing leaves the phone.
 */
public final class Compressor {

    public static final int FORMAT_JPEG = 0;
    public static final int FORMAT_WEBP = 1;

    public static final class Options {
        public int quality = 78;
        public int maxDimension = 2560;
        public int format = FORMAT_JPEG;
    }

    public static final class Result {
        public Uri uri;
        public String name;
        public long originalBytes;
        public long compressedBytes;
        public int width;
        public int height;

        public boolean shrank() {
            return compressedBytes < originalBytes;
        }

        public long savingsPercent() {
            if (originalBytes <= 0) {
                return 0;
            }
            long saved = originalBytes - compressedBytes;
            return Math.round(100.0 * saved / originalBytes);
        }
    }

    private Compressor() {
    }

    /** A trial encode kept in the cache, so settings can be previewed before saving. */
    public static final class Trial {
        public File file;
        public long originalBytes;
        public long compressedBytes;
        public int width;
        public int height;

        public boolean shrank() {
            return compressedBytes < originalBytes;
        }

        public long savingsPercent() {
            if (originalBytes <= 0) {
                return 0;
            }
            return Math.round(100.0 * (originalBytes - compressedBytes) / originalBytes);
        }
    }

    public static Result compress(Context context, Uri source, Options options) throws Exception {
        Trial trial = trial(context, source, options);
        String extension = options.format == FORMAT_WEBP ? ".webp" : ".jpg";
        String mime = options.format == FORMAT_WEBP ? "image/webp" : "image/jpeg";
        try {
            String requestedName = outputName(context, source, extension);
            Uri output = publish(context, trial.file, requestedName, mime);

            Result result = new Result();
            result.uri = output;
            result.name = displayName(context, output);
            if (result.name == null || result.name.isEmpty()) {
                result.name = requestedName;
            }
            result.originalBytes = trial.originalBytes;
            result.compressedBytes = trial.compressedBytes;
            result.width = trial.width;
            result.height = trial.height;
            return result;
        } finally {
            //noinspection ResultOfMethodCallIgnored
            trial.file.delete();
        }
    }

    /**
     * Runs the whole pipeline but leaves the result in the cache instead of
     * publishing it, so the UI can show what the current settings would do.
     * The caller owns {@link Trial#file} and must delete it.
     */
    public static Trial trial(Context context, Uri source, Options options) throws Exception {
        long originalBytes = sizeOf(context, source);
        if (originalBytes <= 0) {
            throw new Exception("The selected file is empty");
        }

        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream in = open(context, source)) {
            BitmapFactory.decodeStream(in, null, bounds);
        }
        int sourceWidth = bounds.outWidth;
        int sourceHeight = bounds.outHeight;
        if (sourceWidth <= 0 || sourceHeight <= 0) {
            throw new Exception("This format cannot be decoded on-device");
        }

        int longest = Math.max(sourceWidth, sourceHeight);
        int target = options.maxDimension > 0 ? Math.min(options.maxDimension, longest) : longest;

        int sample = 1;
        while (longest / (sample * 2) >= target) {
            sample *= 2;
        }

        BitmapFactory.Options decode = new BitmapFactory.Options();
        decode.inSampleSize = sample;
        decode.inPreferredConfig = Bitmap.Config.ARGB_8888;

        Bitmap bitmap;
        try (InputStream in = open(context, source)) {
            bitmap = BitmapFactory.decodeStream(in, null, decode);
        }
        if (bitmap == null) {
            throw new Exception("Could not decode this photo");
        }

        float scale = (float) target / Math.max(bitmap.getWidth(), bitmap.getHeight());
        if (scale < 0.999f) {
            int width = Math.max(1, Math.round(bitmap.getWidth() * scale));
            int height = Math.max(1, Math.round(bitmap.getHeight() * scale));
            Bitmap scaled = Bitmap.createScaledBitmap(bitmap, width, height, true);
            if (scaled != bitmap) {
                bitmap.recycle();
            }
            bitmap = scaled;
        }

        int rotation = exifRotation(context, source);
        if (rotation != 0) {
            Matrix matrix = new Matrix();
            matrix.postRotate(rotation);
            Bitmap rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
            if (rotated != bitmap) {
                bitmap.recycle();
            }
            bitmap = rotated;
        }

        boolean webp = options.format == FORMAT_WEBP;
        String extension = webp ? ".webp" : ".jpg";
        String mime = webp ? "image/webp" : "image/jpeg";
        Bitmap.CompressFormat encoder;
        if (webp) {
            encoder = Build.VERSION.SDK_INT >= 30
                    ? Bitmap.CompressFormat.WEBP_LOSSY
                    : Bitmap.CompressFormat.WEBP;
        } else {
            encoder = Bitmap.CompressFormat.JPEG;
        }

        File temp = new File(context.getCacheDir(), "compress_" + System.nanoTime() + extension);

        int bestQuality = clampQuality(options.quality);
        long bestSize = Long.MAX_VALUE;
        int lastQuality = -1;
        int[] ladder = {
                clampQuality(options.quality),
                clampQuality(options.quality - 12),
                clampQuality(options.quality - 24),
                40
        };
        for (int quality : ladder) {
            if (quality == lastQuality) {
                continue;
            }
            lastQuality = quality;
            long size = encode(bitmap, encoder, quality, temp);
            if (size > 0 && size < bestSize) {
                bestSize = size;
                bestQuality = quality;
            }
            if (size > 0 && size < originalBytes) {
                break;
            }
        }
        if (bestQuality != lastQuality) {
            long size = encode(bitmap, encoder, bestQuality, temp);
            if (size > 0) {
                bestSize = size;
            }
        }

        int outWidth = bitmap.getWidth();
        int outHeight = bitmap.getHeight();
        bitmap.recycle();

        if (bestSize <= 0) {
            //noinspection ResultOfMethodCallIgnored
            temp.delete();
            throw new Exception("Encoding failed");
        }

        Trial trial = new Trial();
        trial.file = temp;
        trial.originalBytes = originalBytes;
        trial.compressedBytes = bestSize;
        trial.width = outWidth;
        trial.height = outHeight;
        return trial;
    }

    public static Bitmap thumbnail(Context context, Uri uri, int maxSize) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (InputStream in = open(context, uri)) {
                BitmapFactory.decodeStream(in, null, bounds);
            }
            int longest = Math.max(bounds.outWidth, bounds.outHeight);
            int sample = 1;
            while (longest / sample > maxSize * 2) {
                sample *= 2;
            }
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = sample;
            try (InputStream in = open(context, uri)) {
                return BitmapFactory.decodeStream(in, null, options);
            }
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * Decodes an image at a bounded resolution with EXIF rotation applied, for
     * previews and the before/after comparison.
     */
    public static Bitmap loadOriented(Context context, Uri uri, int maxSize) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (InputStream in = open(context, uri)) {
                BitmapFactory.decodeStream(in, null, bounds);
            }
            int longest = Math.max(bounds.outWidth, bounds.outHeight);
            int sample = 1;
            while (longest / (sample * 2) >= maxSize) {
                sample *= 2;
            }
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = sample;
            Bitmap bitmap;
            try (InputStream in = open(context, uri)) {
                bitmap = BitmapFactory.decodeStream(in, null, options);
            }
            if (bitmap == null) {
                return null;
            }
            int rotation = exifRotation(context, uri);
            if (rotation != 0) {
                Matrix matrix = new Matrix();
                matrix.postRotate(rotation);
                Bitmap rotated = Bitmap.createBitmap(bitmap, 0, 0,
                        bitmap.getWidth(), bitmap.getHeight(), matrix, true);
                if (rotated != bitmap) {
                    bitmap.recycle();
                }
                bitmap = rotated;
            }
            return bitmap;
        } catch (Exception ignored) {
            return null;
        }
    }

    public static long sizeOf(Context context, Uri uri) {
        try (Cursor cursor = context.getContentResolver()
                .query(uri, new String[]{OpenableColumns.SIZE}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.SIZE);
                if (index >= 0 && !cursor.isNull(index)) {
                    return cursor.getLong(index);
                }
            }
        } catch (Exception ignored) {
            // fall through
        }
        return 0;
    }

    public static String displayName(Context context, Uri uri) {
        try (Cursor cursor = context.getContentResolver()
                .query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0 && !cursor.isNull(index)) {
                    return cursor.getString(index);
                }
            }
        } catch (Exception ignored) {
            // fall through
        }
        return null;
    }

    private static int clampQuality(int quality) {
        if (quality < 40) {
            return 40;
        }
        if (quality > 95) {
            return 95;
        }
        return quality;
    }

    private static long encode(Bitmap bitmap, Bitmap.CompressFormat format, int quality, File target)
            throws Exception {
        try (FileOutputStream out = new FileOutputStream(target)) {
            if (!bitmap.compress(format, quality, out)) {
                return -1;
            }
            out.flush();
        }
        return target.length();
    }

    private static int exifRotation(Context context, Uri uri) {
        try (InputStream in = open(context, uri)) {
            ExifInterface exif = new ExifInterface(in);
            int orientation = exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_90:
                    return 90;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    return 180;
                case ExifInterface.ORIENTATION_ROTATE_270:
                    return 270;
                default:
                    return 0;
            }
        } catch (Exception ignored) {
            return 0;
        }
    }

    private static String outputName(Context context, Uri source, String extension) {
        String name = displayName(context, source);
        if (name == null || name.isEmpty()) {
            name = "photo";
        }
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            name = name.substring(0, dot);
        }
        if (name.length() > 60) {
            name = name.substring(0, 60);
        }
        return name + "_compressed" + extension;
    }

    private static InputStream open(Context context, Uri uri) throws Exception {
        InputStream in = context.getContentResolver().openInputStream(uri);
        if (in == null) {
            throw new Exception("Could not open the selected photo");
        }
        return in;
    }

    private static Uri publish(Context context, File source, String name, String mime) throws Exception {
        ContentResolver resolver = context.getContentResolver();
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
        values.put(MediaStore.MediaColumns.MIME_TYPE, mime);

        boolean scoped = Build.VERSION.SDK_INT >= 29;
        Uri collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        if (scoped) {
            collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
            values.put(MediaStore.MediaColumns.RELATIVE_PATH,
                    Environment.DIRECTORY_PICTURES + "/Compress");
            values.put(MediaStore.MediaColumns.IS_PENDING, 1);
        }

        Uri output = resolver.insert(collection, values);
        if (output == null) {
            throw new Exception("Could not create the output file");
        }
        try (OutputStream stream = resolver.openOutputStream(output)) {
            if (stream == null) {
                throw new Exception("Could not write the output file");
            }
            try (InputStream in = new java.io.FileInputStream(source)) {
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = in.read(buffer)) > 0) {
                    stream.write(buffer, 0, read);
                }
            }
            stream.flush();
        } catch (Exception failure) {
            resolver.delete(output, null, null);
            throw failure;
        }

        if (scoped) {
            ContentValues done = new ContentValues();
            done.put(MediaStore.MediaColumns.IS_PENDING, 0);
            resolver.update(output, done, null, null);
        }
        return output;
    }
}
