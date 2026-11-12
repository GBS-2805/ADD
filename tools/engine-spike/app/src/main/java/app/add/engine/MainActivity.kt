package app.add.engine

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import java.io.File
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        BootTrace.start(this)
        // Software-rendered x86 emulators run the pure-Kotlin field raster slowly enough
        // to miss Android's focus deadline. Keep phone output at full resolution and
        // lower only the emulator preview/export budget.
        if (Build.SUPPORTED_ABIS.any { it.startsWith("x86") }) {
            FieldBudget.previewWidth = 32
            FieldBudget.exportWidth = 64
            // The emulator's software compositor needs ~20 s for the first frame. Give
            // it that time instead of tripping InputDispatcher's 5 s focus deadline;
            // touch still reaches the window. Phone builds keep normal focus.
            window.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
            // Restore focus once the software-rendered first frame is safely on screen,
            // so accessibility-driven review can inspect and touch the real UI.
            window.decorView.postDelayed(
                { window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE) },
                40_000L,
            )
            // The emulator's compositor cannot read this window back (screencap is black),
            // so review capture draws the real windows on demand instead. A Compose
            // Dialog lives in its own window, so capture every root view and composite
            // them in window order rather than drawing only the activity decor view.
            registerReceiver(
                object : BroadcastReceiver() {
                    override fun onReceive(context: Context, intent: Intent) {
                        captureWindows()
                    }
                },
                IntentFilter("app.add.engine.CAPTURE"),
                Context.RECEIVER_EXPORTED,
            )
        }
        super.onCreate(savedInstanceState)
        BootTrace.mark(this, "activity created")
        setContent {
            BootTrace.mark(this, "content set")
            EngineTheme {
                EngineScreen()
            }
        }
    }

    /**
     * Draws each live window to its own PNG, plus a screen-sized composite. The
     * dialog is a separate window; [android.view.WindowManagerGlobal] exposes the
     * root views, and [View.getLocationOnScreen] places each one in the composite.
     */
    private fun captureWindows() {
        val roots = runCatching {
            val cls = Class.forName("android.view.WindowManagerGlobal")
            val global = cls.getMethod("getInstance").invoke(null)
            @Suppress("UNCHECKED_CAST")
            val all = (cls.getDeclaredField("mViews").apply { isAccessible = true }
                .get(global) as List<View>)
            all.filter { it.isShown && it.width > 0 && it.height > 0 }
        }.getOrElse { listOf(window.decorView) }
        if (roots.isEmpty()) return

        roots.forEachIndexed { index, view -> saveWindow(view, "device-ui-$index.png") }

        val bounds = resources.displayMetrics
        val composite = Bitmap.createBitmap(
            bounds.widthPixels.coerceAtLeast(1),
            bounds.heightPixels.coerceAtLeast(1),
            Bitmap.Config.ARGB_8888,
        )
        val canvas = Canvas(composite)
        roots.forEach { view ->
            val location = IntArray(2)
            view.getLocationOnScreen(location)
            canvas.save()
            canvas.translate(location[0].toFloat(), location[1].toFloat())
            view.draw(canvas)
            canvas.restore()
        }
        saveBitmap(composite, "device-ui.png")

        File(filesDir, "device-ui-roots.txt").writeText(
            roots.mapIndexed { index, view ->
                val location = IntArray(2)
                view.getLocationOnScreen(location)
                "$index ${view.javaClass.name} ${view.width}x${view.height} " +
                    "at=${location[0]},${location[1]} focus=${view.hasWindowFocus()}"
            }.joinToString("\n"),
        )
    }

    private fun saveWindow(view: View, name: String) {
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        saveBitmap(bitmap, name)
    }

    private fun saveBitmap(bitmap: Bitmap, name: String) {
        File(filesDir, name).outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
