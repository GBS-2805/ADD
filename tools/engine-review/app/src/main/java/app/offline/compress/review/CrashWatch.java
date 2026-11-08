package app.offline.compress.review;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * A crash net for the review APK only.
 *
 * The engine is a Compose + Filament surface that cannot be run on the build machine,
 * so a crash on the phone used to reach us as "ADD has a bug" and nothing else. This
 * writes the whole stack to a file before the process dies and shows it on the next
 * launch, with a COPY button, so one install answers the question instead of a guess.
 *
 * It also holds the engine after a crash: the editor entry redirects into the engine
 * in a debuggable build, so an engine that dies on launch would take the only door with
 * it. While the hold is set the door opens the basic editor instead, and a new build
 * lifts the hold by itself because the mark carries the version code that set it.
 *
 * None of this exists in the shipping app: the class lives in the review source set,
 * and app/src reaches it by name, so the release build has no crash net and no hold.
 */
public class CrashWatch extends Application {

    public static final String CRASH_FILE = "review-crash.txt";

    private static final String PREFS = "review_crash";
    private static final String KEY_HELD_VERSION = "engine_held_version";
    private static final String KEY_SHOWN = "report_shown";

    @Override
    public void onCreate() {
        super.onCreate();
        install();
        if (!isMainProcess() || !crashFile(this).exists() || shown(this)) {
            return;
        }
        markShown(this);
        try {
            startActivity(new Intent(this, CrashReportActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (RuntimeException ignored) {
            // A refused background start must not itself take the app down; the trace
            // is already on disk and the engine is held either way.
        }
    }

    /** True while the engine is held: the last launch of this build crashed. */
    public static boolean held(Context context) {
        return prefs(context).getInt(KEY_HELD_VERSION, 0) == versionCode(context)
                && crashFile(context).exists();
    }

    public static File crashFile(Context context) {
        return new File(context.getFilesDir(), CRASH_FILE);
    }

    /** The report is shown once per crash; the mark is dropped by a new build. */
    private static void markShown(Context context) {
        prefs(context).edit().putBoolean(KEY_SHOWN, true).apply();
    }

    private static boolean shown(Context context) {
        return prefs(context).getBoolean(KEY_SHOWN, false);
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static int versionCode(Context context) {
        try {
            PackageInfo info = context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0);
            return Build.VERSION.SDK_INT >= 28
                    ? (int) info.getLongVersionCode()
                    : info.versionCode;
        } catch (PackageManager.NameNotFoundException missing) {
            return 0;
        }
    }

    private static boolean isMainProcess() {
        if (Build.VERSION.SDK_INT < 28) {
            return true;
        }
        String process = Application.getProcessName();
        return process != null && !process.contains(":");
    }

    private void install() {
        final Thread.UncaughtExceptionHandler previous =
                Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            write(error);
            prefs(this).edit()
                    .putInt(KEY_HELD_VERSION, versionCode(this))
                    .putBoolean(KEY_SHOWN, false)
                    .apply();
            if (previous != null) {
                previous.uncaughtException(thread, error);
            }
        });
    }

    private void write(Throwable error) {
        StringWriter trace = new StringWriter();
        error.printStackTrace(new PrintWriter(trace));
        StringBuilder text = new StringBuilder();
        text.append("ADD review · ").append(versionName(this)).append('\n');
        text.append(new SimpleDateFormat("d MMM yyyy HH:mm:ss", Locale.UK).format(new Date()))
                .append(" · build ").append(Build.DISPLAY).append('\n');
        text.append(Build.MANUFACTURER).append(' ').append(Build.MODEL)
                .append(" · Android ").append(Build.VERSION.RELEASE)
                .append(" · API ").append(Build.VERSION.SDK_INT).append('\n');
        text.append("process ").append(processName()).append('\n');
        text.append('\n').append(trace).append('\n');
        try (FileOutputStream out = new FileOutputStream(crashFile(this))) {
            out.write(text.toString().getBytes("UTF-8"));
        } catch (Exception ignored) {
            // Nothing left to do: the process is already dying.
        }
    }

    /** Process name without the API-28-only call: a crash on 26 or 27 must not add one. */
    private static String processName() {
        if (Build.VERSION.SDK_INT >= 28) {
            return String.valueOf(Application.getProcessName());
        }
        return "app.offline.compress";
    }

    private static String versionName(Context context) {
        try {
            PackageInfo info = context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0);
            return info.versionName + " (" + versionCode(context) + ")";
        } catch (PackageManager.NameNotFoundException missing) {
            return "3.7";
        }
    }
}
