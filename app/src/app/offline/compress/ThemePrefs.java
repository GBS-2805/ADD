package app.offline.compress;

import android.app.UiModeManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Build;

/**
 * Stores the dark-theme choice and pushes it into the resource configuration.
 *
 * The app carries a second palette in res/values-night, so switching is just a
 * matter of flipping UI mode before an activity inflates its layout. The choice
 * lives in SharedPreferences, so it survives restarts and is re-applied at the
 * start of every activity.
 */
final class ThemePrefs {

    private static final String FILE = "compress_settings";
    private static final String KEY_DARK = "dark_mode";

    private ThemePrefs() {
    }

    static boolean isDark(Context context) {
        return prefs(context).getBoolean(KEY_DARK, false);
    }

    /** Saves the choice and applies it to the whole app. */
    static void setDark(Context context, boolean dark) {
        prefs(context).edit().putBoolean(KEY_DARK, dark).apply();
        apply(context);
    }

    /** Applies the saved choice; call before setContentView() in every activity. */
    static void apply(Context context) {
        boolean dark = isDark(context);
        if (Build.VERSION.SDK_INT >= 31) {
            UiModeManager manager = context.getSystemService(UiModeManager.class);
            if (manager != null) {
                manager.setApplicationNightMode(
                        dark ? UiModeManager.MODE_NIGHT_YES : UiModeManager.MODE_NIGHT_NO);
                return;
            }
        }
        // API 26-30: no per-app night mode, so rewrite this process's config.
        Configuration config = new Configuration(context.getResources().getConfiguration());
        config.uiMode = (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK)
                | (dark ? Configuration.UI_MODE_NIGHT_YES : Configuration.UI_MODE_NIGHT_NO);
        context.getResources().updateConfiguration(config,
                context.getResources().getDisplayMetrics());
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }
}
