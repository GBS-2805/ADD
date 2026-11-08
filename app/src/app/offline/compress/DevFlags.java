package app.offline.compress;

import android.content.Context;
import android.content.pm.ApplicationInfo;

/** Development-only switches. The release build never exposes the engine lab. */
final class DevFlags {

    private DevFlags() {
    }

    /**
     * True only in a debuggable build. The integrated engine APK is built as a
     * debug variant; the release APK made by build.sh is not debuggable.
     */
    static boolean engineLab(Context context) {
        return (context.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
    }
}
