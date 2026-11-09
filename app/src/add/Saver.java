package add;

import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Writes an editor export where pictures belong. On Android 10 and later MediaStore
 * needs no permission; on 8 and 9 the file goes to the app's own folder, which also
 * needs none. The app never asks for storage.
 */
final class Saver {

    private Saver() {
    }

    static String save(Context context, Bitmap bitmap, boolean jpeg) {
        String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date());
        String name = "editor-" + stamp + (jpeg ? ".jpg" : ".png");
        String mime = jpeg ? "image/jpeg" : "image/png";
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
                values.put(MediaStore.MediaColumns.MIME_TYPE, mime);
                values.put(MediaStore.MediaColumns.RELATIVE_PATH,
                        Environment.DIRECTORY_PICTURES + "/Editor");
                Uri uri = context.getContentResolver()
                        .insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                if (uri == null) {
                    return "Could not save " + name;
                }
                OutputStream out = context.getContentResolver().openOutputStream(uri);
                if (out == null) {
                    return "Could not save " + name;
                }
                write(bitmap, jpeg, out);
                return "Saved to Pictures/Editor/" + name;
            }
            File dir = new File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "Editor");
            if (!dir.exists() && !dir.mkdirs()) {
                return "Could not create the Editor folder";
            }
            File file = new File(dir, name);
            FileOutputStream out = new FileOutputStream(file);
            write(bitmap, jpeg, out);
            return "Saved " + file.getAbsolutePath();
        } catch (IOException error) {
            return "Could not save: " + error.getMessage();
        }
    }

    private static void write(Bitmap bitmap, boolean jpeg, OutputStream out) throws IOException {
        try {
            bitmap.compress(jpeg ? Bitmap.CompressFormat.JPEG : Bitmap.CompressFormat.PNG,
                    jpeg ? 92 : 100, out);
        } finally {
            out.close();
        }
    }
}
