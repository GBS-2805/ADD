package app.offline.compress;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;

/**
 * Stores the palette and the light/dark choice, and turns the pair into a theme.
 *
 * Both live in SharedPreferences so they survive restarts, and both are applied
 * with setTheme() before an activity inflates its layout: every colour in the
 * layouts and drawables is a theme attribute, so nothing has to be recoloured by
 * hand. Changing either recreates the activity, which is why the queue and the
 * finished files are carried across in onSaveInstanceState.
 */
final class ThemePrefs {

    private static final String FILE = "compress_settings";
    private static final String KEY_DARK = "dark_mode";
    private static final String KEY_PALETTE = "palette";

    private ThemePrefs() {
    }

    /** The page is dark unless the reader has said otherwise, as on the site. */
    static boolean isDark(Context context) {
        return prefs(context).getBoolean(KEY_DARK, true);
    }

    static void setDark(Context context, boolean dark) {
        prefs(context).edit().putBoolean(KEY_DARK, dark).apply();
    }

    static void toggleDark(Context context) {
        setDark(context, !isDark(context));
    }

    /** The stored palette, or green if nothing valid has been stored. */
    static String palette(Context context) {
        String saved = prefs(context).getString(KEY_PALETTE, Palette.GREEN);
        for (String id : Palette.IDS) {
            if (id.equals(saved)) {
                return saved;
            }
        }
        return Palette.GREEN;
    }

    static void setPalette(Context context, String id) {
        prefs(context).edit().putString(KEY_PALETTE, id).apply();
    }

    /** The theme for the stored pair. */
    static int style(Context context) {
        boolean dark = isDark(context);
        String palette = palette(context);
        if (Palette.MOSS.equals(palette)) {
            return dark ? R.style.AppTheme_Moss : R.style.AppTheme_Moss_Light;
        }
        if (Palette.NAVY.equals(palette)) {
            return dark ? R.style.AppTheme_Navy : R.style.AppTheme_Navy_Light;
        }
        return dark ? R.style.AppTheme : R.style.AppTheme_Light;
    }

    /** Call before setContentView(), in every activity. */
    static void applyTheme(Activity activity) {
        activity.setTheme(style(activity));
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }
}
