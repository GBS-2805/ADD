package app.add.engine

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The engine's startup breadcrumb.
 *
 * A Filament fault aborts the process without running the Java crash handler, so the
 * review net saw nothing and every report came back as "ADD has a bug". This writes one
 * line per startup stage synchronously, with fd.sync(), so whatever the process dies of
 * the last line it reached survives on disk. The review APK reads it on the next launch
 * and shows it beside the stack, if there is one.
 *
 * It is a few file writes during startup, not a log: the whole file is rewritten on each
 * engine start and holds only the stages of that launch.
 */
object BootTrace {

    const val FILE = "engine-boot.txt"
    const val READY = "READY"

    @Volatile
    private var last = ""

    fun start(context: Context) {
        last = ""
        runCatching { File(context.filesDir, FILE).writeText("") }
        mark(context, "engine process started")
    }

    fun mark(context: Context, stage: String) {
        if (stage == last) return
        last = stage
        runCatching {
            val stamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.UK).format(Date())
            val out = FileOutputStream(File(context.filesDir, FILE), true)
            try {
                out.write("$stamp  $stage\n".toByteArray())
                out.flush()
                out.fd.sync()
            } finally {
                out.close()
            }
        }
    }

    /** The screen is up, so the next launch must not read this as a crash. */
    fun ready(context: Context) = mark(context, READY)
}
