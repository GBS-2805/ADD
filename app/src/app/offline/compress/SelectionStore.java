package app.offline.compress;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import java.util.ArrayList;
import java.util.List;

/**
 * Remembers the selected photos so closing the app does not lose the queue.
 *
 * The URIs are stored as one ordered string rather than a StringSet because
 * the strip shows them in the order they were picked.
 */
final class SelectionStore {

    private static final String PREFS = "compress_settings";
    private static final String KEY_QUEUE = "queue";
    private static final String SEPARATOR = "\n";

    private SelectionStore() {
    }

    static List<String> load(Context context) {
        String stored = prefs(context).getString(KEY_QUEUE, "");
        ArrayList<String> values = new ArrayList<>();
        if (stored == null || stored.isEmpty()) {
            return values;
        }
        for (String part : stored.split(SEPARATOR)) {
            if (!part.isEmpty()) {
                values.add(part);
            }
        }
        return values;
    }

    static void save(Context context, List<Uri> queue) {
        StringBuilder builder = new StringBuilder();
        for (Uri uri : queue) {
            if (builder.length() > 0) {
                builder.append(SEPARATOR);
            }
            builder.append(uri.toString());
        }
        prefs(context).edit().putString(KEY_QUEUE, builder.toString()).apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
