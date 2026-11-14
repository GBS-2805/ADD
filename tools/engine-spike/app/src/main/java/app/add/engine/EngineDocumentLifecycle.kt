package app.add.engine

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Restores the page, traces boot and owns the debounced autosave. */
@Composable
internal fun rememberEngineDocument(context: Context): EngineDocument {
    val restored = remember { DocumentStore.restore(context) }
    val doc = remember {
        EngineDocument(
            restored?.layers,
            restored?.texture,
            restored?.textureWarning,
            restored?.ink ?: emptyList(),
            restored?.backgroundField,
        ).also { document ->
            restored?.let {
                (it.selectedId ?: it.layers.firstOrNull()?.id)?.let { id -> document.select(id) }
                document.zoom = it.zoom
                document.status = it.textureWarning?.let { warning ->
                    "RESTORED · $warning"
                } ?: "RESTORED · ${it.layers.size} LAYERS"
            }
        }
    }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        BootTrace.mark(context, "first frame drawn")
        BootTrace.ready(context)
    }
    LaunchedEffect(Diagnostics.recording) {
        while (Diagnostics.recording) { delay(Diagnostics.sampleMillis); Diagnostics.sample(doc) }
    }
    LaunchedEffect(Diagnostics.recording) {
        if (!Diagnostics.recording) return@LaunchedEffect
        var last = 0L
        while (Diagnostics.recording) {
            withFrameNanos { now ->
                if (last != 0L) Diagnostics.frame((now - last) / 1_000_000L)
                last = now
            }
        }
    }
    LaunchedEffect(
        doc.layers,
        doc.motion,
        doc.zoom,
        doc.selectedIds,
        doc.editingGroupId,
        doc.lockedIds,
        doc.texture,
        doc.ink,
        doc.backgroundField,
    ) {
        doc.markSaving()
        delay(300)
        if (DocumentStore.save(context, doc)) {
            doc.markSaved(SimpleDateFormat("HH:mm", Locale.UK).format(Date(EngineClock.nowMillis())))
        } else {
            doc.markSaveFailed()
        }
    }
    return doc
}
