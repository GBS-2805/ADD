package app.add.engine

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun EngineScreen() {
    val context = LocalContext.current
    BootTrace.mark(context, "engine screen composed")
    val doc = rememberEngineDocument(context)
    // A field runs on the same software compositor as the page. The phase is a
    // draw-state, not document data, so it never enters undo or the saved file.
    val animatedField = doc.backgroundField?.takeIf { it.animated && it.speed > 0f }
        ?: doc.layers.flattenTree().firstNotNullOfOrNull { layer ->
            val paint = when (layer) {
                is VectorLayer -> layer.paint
                is TextLayer -> layer.paint
                else -> null
            }
            (paint as? PaintSpec.Field)?.field?.takeIf { it.animated && it.speed > 0f }
        }
    val fieldSpeed = animatedField?.speed ?: 0f
    val fieldPhase = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(animatedField != null, fieldSpeed) {
        if (animatedField == null) return@LaunchedEffect
        var last = withFrameNanos { it }
        var accumulated = 0f
        while (true) {
            withFrameNanos { now ->
                val seconds = ((now - last) / 1_000_000_000f).coerceAtMost(0.1f)
                last = now
                accumulated += seconds * (0.15f + fieldSpeed * 1.8f)
                if (accumulated >= 1f / 24f) {
                    fieldPhase.floatValue = (fieldPhase.floatValue + accumulated) % 60f
                    accumulated = 0f
                }
            }
        }
    }
    val sceneHandles = remember { mutableMapOf<String, SceneHandle>() }
    // The layer cache holds phone-sized bitmaps; let them go with the screen.
    DisposableEffect(Unit) {
        onDispose {
            // Flush on pause without waiting for the debounce, then release bitmaps.
            DocumentStore.save(context, doc)
            clearLayerBitmapCache()
            TextureRenderer.clear()
        }
    }
    val fallbackHandle = remember { SceneHandle() }
    fun handleFor(layer: ModelLayer?): SceneHandle =
        layer?.let { sceneHandles.getOrPut("${it.id}:${it.asset.name}") { SceneHandle() } } ?: fallbackHandle
    val primaryHandle = doc.modelLayers.firstOrNull()?.let { handleFor(it) } ?: fallbackHandle
    val selectedHandle = handleFor(doc.selectedModel)
    val typefaces = remember(context) { Typefaces(context) }
    // Photos are copied in, then decoded once and handed to the painter as a map.
    val images = remember(doc.layers) {
        doc.layers.filterIsInstance<ImageLayer>().mapNotNull { layer ->
            ImageStore.load(context, layer.path, ImageStore.PREVIEW_MAX_PX)
                ?.let { layer.path to it }
        }.toMap()
    }
    BootTrace.mark(context, "document, typefaces and photos ready")
    val photoPicker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val name = ImageStore.import(context, uri)
        if (name == null) {
            doc.status = "PHOTO IMPORT FAILED · could not copy the file"
        } else {
            doc.addImage(name)
        }
    }
    val scope = rememberCoroutineScope()
    var showInfo by remember { mutableStateOf(false) }
    var showDiagnostics by remember { mutableStateOf(false) }
    var showIcons by remember { mutableStateOf(false) }
    var showLegend by remember { mutableStateOf(false) }
    var shell by remember { mutableStateOf(EngineShellState()) }
    var sheet by remember { mutableStateOf(false) }
    var sheetTab by remember { mutableStateOf(StudioTab.Style) }
    var showCommands by remember { mutableStateOf(false) }
    var showPlugins by remember { mutableStateOf(false) }
    val pluginStateHolder = androidx.compose.runtime.saveable.rememberSaveableStateHolder()
    fun openSheet(tab: StudioTab) {
        sheetTab = tab
        sheet = true
        shell = shell.reduce(
            ShellEvent.Open(if (tab == StudioTab.Layers) EngineSurface.Layers else EngineSurface.Inspector)
        )
    }
    fun closeSheet() {
        sheet = false
        if (shell.surface == EngineSurface.Inspector || shell.surface == EngineSurface.Layers) {
            shell = shell.reduce(ShellEvent.Dismiss)
        }
    }
    var showAdd by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    var showExport by remember { mutableStateOf(false) }
    var showLibrary by remember { mutableStateOf(false) }
    var libraryKey by remember { mutableStateOf(0) }
    var exportScale by remember { mutableStateOf(1) }
    var exportFormat by remember { mutableStateOf(ExportFormat.Png) }
    var lastExport by remember { mutableStateOf<ExportResult?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    val stageFocus = remember { FocusRequester() }
    val toolbarFocus = remember { FocusRequester() }
    val sheetFocus = remember { FocusRequester() }
    val reduceMotion = prefersReducedMotion()
    // Every add ends in a commit whose action names the control (TYPE+, 3D+, ICON+ …).
    // The badge below the stage turns that into a place to settle instead of a mode the
    // reader has to discover how to leave.
    LaunchedEffect(doc.actionId) {
        if (doc.action in ADD_ACTIONS) {
            val added = doc.selected?.name ?: "LAYER"
            // The strip stays until DONE or INSPECT. A timed confirmation can vanish
            // before the reader has finished placing the layer, which is the same
            // "no way to settle" trap in a smaller form.
            notice = added
        }
    }
    val dialogOpen = showInfo || showDiagnostics || showIcons || showLegend ||
        showHistory || showExport || showLibrary || showAdd || showCommands || showPlugins
    BackHandler(enabled = dialogOpen || sheet || shell.surface != EngineSurface.None ||
        shell.chrome != ChromeMode.Full || doc.editingModelId != null ||
        doc.tool != Tool.Select || doc.maskMode || doc.inkMode) {
        when {
            showAdd -> showAdd = false
            showCommands -> showCommands = false
            showPlugins -> showPlugins = false
            showIcons -> showIcons = false
            showInfo -> showInfo = false
            showDiagnostics -> showDiagnostics = false
            showHistory -> showHistory = false
            showExport -> showExport = false
            showLibrary -> showLibrary = false
            showLegend -> showLegend = false
            notice != null -> notice = null
            sheet -> closeSheet()
            shell.surface != EngineSurface.None -> shell = shell.reduce(ShellEvent.Dismiss)
            shell.chrome != ChromeMode.Full -> shell = shell.reduce(ShellEvent.PeekTapped)
            else -> doc.settle()
        }
    }
    val basicEditor: (() -> Unit)? = remember(context) {
        runCatching {
            val cls = Class.forName("add.EditorActivity")
            val intent = android.content.Intent(context, cls)
                .putExtra("add.BASIC_EDITOR", true)
            val action: () -> Unit = {
                // A missing or refused editor must say so on the stage, not close the app.
                runCatching { context.startActivity(intent) }.onFailure { error ->
                    doc.status = "EDITOR UNAVAILABLE · " +
                        (error.message ?: error.javaClass.simpleName)
                }
            }
            action
        }.getOrNull()
    }
    val exportArtboard: (Int, ExportFormat) -> Unit = { scale, format ->
        if (!doc.exporting) {
            doc.exporting = true
            doc.status = "CAPTURING ARTBOARD + 3D LAYERS…"
            scope.launch {
                try {
                    val result = Exporter.export(
                        context, doc.layers, typefaces,
                        doc.modelLayers.associate { it.id to handleFor(it).view },
                        scale = scale,
                        texture = doc.texture,
                        ink = doc.ink,
                        backgroundField = doc.backgroundField,
                        fieldPhase = fieldPhase.value,
                        format = format,
                    )
                    lastExport = result
                    doc.status = result.status
                    Toast.makeText(context, result.status, Toast.LENGTH_SHORT).show()
                    Diagnostics.note(
                        "export " + result.width + " × " + result.height +
                            " · " + result.bytes + " bytes" +
                            " · 3d " + (result.models - result.missing) + "/" + result.models +
                            (if (result.missing > 0) " · 3D CAPTURE MISSING" else "") +
                            (if (result.black > 0) " · ${result.black} 3D BLACK" else "") +
                            (if (result.models > 0) " · ${result.litPercent}% lit" else "")
                    )
                } catch (error: Exception) {
                    doc.status = "EXPORT FAILED · ${error.message ?: "unknown error"}"
                    Toast.makeText(context, doc.status, Toast.LENGTH_LONG).show()
                    Diagnostics.note("export failed · ${error.message ?: error.javaClass.simpleName}")
                } finally {
                    doc.exporting = false
                }
            }
        }
    }
    val bmoCommands = buildList {
        add(EngineCommand("undo", "Undo", "Edit", "↶", enabled = doc.canUndo) { doc.undo() })
        add(EngineCommand("redo", "Redo", "Edit", "↷", enabled = doc.canRedo) { doc.redo() })
        add(EngineCommand("add.type", "Add type", "Create", "T", "text") { doc.addText() })
        add(EngineCommand("add.vector", "Add vector", "Create", "A", "shape") { doc.addVector() })
        add(EngineCommand("add.sticker", "Add sticker", "Create", "S", "shape") {
            doc.chooseTool(Tool.Sticker); openSheet(StudioTab.Style)
        })
        add(EngineCommand("add.icon", "Add icon", "Create", "I", "glyph") { showIcons = true })
        add(EngineCommand("add.3d", "Add 3D object", "Create", "3", "model gem ring") {
            doc.addModel(ModelAsset.Gem)
        })
        add(EngineCommand("add.photo", "Add photo", "Create", "▧", "image") {
            photoPicker.launch("image/*")
        })
        add(EngineCommand("group", "Group selection", "Arrange", "▣", enabled = doc.selectedIds.size >= 2) {
            doc.groupSelection()
        })
        add(EngineCommand("ungroup", "Ungroup", "Arrange", "▤", enabled = doc.selected is GroupLayer) {
            doc.ungroupSelection()
        })
        add(EngineCommand("duplicate", "Duplicate", "Edit", "⧉", enabled = doc.selected != null) {
            doc.duplicateSelected()
        })
        add(EngineCommand("delete", "Delete", "Edit", "×", enabled = doc.selected != null) {
            doc.deleteSelected()
        })
        add(EngineCommand("export", "Export", "Page", "↗") { showExport = true })
        add(EngineCommand("layers", "Layers", "View", "▤") { openSheet(StudioTab.Layers) })
        add(EngineCommand("inspector", "Inspector", "View", "i") { openSheet(StudioTab.Style) })
        add(EngineCommand("focus", "Focus canvas", "View", "◎") { doc.settle() })
        add(EngineCommand("documents", "Documents", "Page", "▧") { showLibrary = true })
        addAll(enginePluginCommands(EnginePlugins.builtIn) { id ->
            showPlugins = false
            shell = shell.reduce(ShellEvent.OpenPlugin(id))
        })
    }
    val activePlugin = EnginePlugins.builtIn.firstOrNull { it.id == shell.pluginId }
    // The dialog/host surface lives outside the shell Box, so the same width
    // breakpoint is resolved once here for every panel host.
    val layout = engineLayout(LocalConfiguration.current.screenWidthDp.dp)

    BoxWithConstraints(Modifier.fillMaxSize().background(StudioInk).safeDrawingPadding().imePadding()) {
        // The rail and the landed strip sit above the dock. At narrow widths the
        // dock adds a second row, so both need the taller clearance.
        val dockClearance = dockHeight(maxWidth - 8.dp) + 12.dp
        Stage(
            doc = doc,
            sceneHandles = sceneHandles,
            typefaces = typefaces,
            images = images,
            fieldPhase = fieldPhase,
            showZoom = shell.chrome == ChromeMode.Full,
            onCanvasTouch = {
                closeSheet()
                // Drawing a mask or an ink stroke is a working gesture, not a
                // request to hide the HUD. Only a plain canvas touch dismisses
                // the chrome; otherwise the rail would vanish mid-stroke.
                if (!doc.inkMode && !doc.maskMode) {
                    shell = shell.reduce(ShellEvent.CanvasTouched)
                }
                // Touching the stage means the reader is done reading the add
                // confirmation; clear it so the dock and selection rail cannot
                // stack underneath it.
                notice = null
            },
            sheetOpen = sheet,
            focusRequester = stageFocus,
            nextFocus = toolbarFocus,
            modifier = Modifier.fillMaxSize(),
        )
        if (shell.chrome != ChromeMode.Canvas) {
            BmoPageChip(
                doc = doc,
                onBack = { (context as? android.app.Activity)?.finish() },
                onDocuments = { showLibrary = true },
                onHideChrome = {
                    shell = shell.reduce(ShellEvent.ToggleChrome)
                    shell = shell.reduce(ShellEvent.ToggleChrome)
                },
                onInfo = { showInfo = true },
                onCommands = { showCommands = true },
                onToggleSafeArea = { doc.safeArea = !doc.safeArea },
                modifier = Modifier.align(Alignment.TopStart).padding(top = 4.dp, start = 6.dp),
                restoreOnTap = shell.chrome == ChromeMode.Peek,
                onRestore = { shell = shell.reduce(ShellEvent.PeekTapped) },
            )
        }
        if (shell.chrome == ChromeMode.Full) {
            // The open inspector already owns STYLE/FX/INK/LAYERS/DONE; a rail
            // behind the scrim would be a dimmed second copy of the same actions.
            if (doc.selected != null && !sheet && notice == null && !doc.maskMode &&
                !doc.inkMode && doc.editingModelId == null
            ) {
                BmoSelectionRail(
                    doc = doc,
                    onStyle = { openSheet(StudioTab.Style) },
                    onEffects = { openSheet(StudioTab.Effects) },
                    onLayers = { openSheet(StudioTab.Layers) },
                    onDone = { doc.settle() },
                    onTexture = { openSheet(StudioTab.Texture) },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = dockClearance),
                )
            }
            BmoToolDock(
                active = doc.tool,
                onTool = { tool ->
                    doc.chooseTool(tool)
                    if (tool == Tool.Icon) showIcons = true
                },
                onAdd = { showAdd = true },
                onCommands = { showCommands = true },
                onPlugins = { showPlugins = true },
                onPluginLongPress = { showPlugins = true },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    // Same canvas inset as the rail: an expanded inspector must not
                    // hide the add button or the page actions behind its panel.
                    .padding(
                        start = 4.dp,
                        end = 4.dp + if (layout == EngineLayout.Expanded && sheet) {
                            ExpandedPanelWidth
                        } else {
                            0.dp
                        },
                        top = 6.dp,
                        bottom = 6.dp,
                    )
                    .focusRequester(toolbarFocus),
            )
        }
        if (shell.chrome == ChromeMode.Canvas) {
            BmoCanvasRestore(
                onRestore = { shell = shell.reduce(ShellEvent.PeekTapped) },
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }
        StatusChip(
            doc = doc,
            handle = primaryHandle,
            visible = false,
            onDiagnostics = { showDiagnostics = true },
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 10.dp, bottom = if (doc.maskMode || doc.editingModelId != null) 14.dp else 84.dp),
        )
        AnimatedVisibility(
            visible = sheet,
            enter = fadeIn(tween(if (reduceMotion) 0 else 150)),
            exit = fadeOut(tween(if (reduceMotion) 0 else 120)),
        ) {
            SheetBackgroundScrim(onClose = { closeSheet() })
        }
        AnimatedVisibility(
            visible = sheet,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = if (reduceMotion) tween(0) else spring(dampingRatio = 0.78f, stiffness = 380f),
            ) + fadeIn(tween(if (reduceMotion) 0 else 150)),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = tween(if (reduceMotion) 0 else 190),
            ) + fadeOut(tween(if (reduceMotion) 0 else 110)),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            InspectorSheet(
                doc = doc,
                handle = selectedHandle,
                tab = sheetTab,
                layout = layout,
                onTab = { sheetTab = it },
                onIcons = { showIcons = true },
                onHistory = { showHistory = true },
                onClose = { closeSheet() },
                onMask = {
                    doc.toggleMaskMode()
                    closeSheet()
                    notice = null
                    shell = shell.reduce(ShellEvent.PeekTapped)
                },
                onTurn3D = {
                    doc.selectedModel?.let { model ->
                        if (doc.editingModelId != model.id) doc.toggleModelEdit(model.id)
                    }
                    closeSheet()
                    notice = null
                    shell = shell.reduce(ShellEvent.PeekTapped)
                },
                focusRequester = sheetFocus,
            )
        }
        AnimatedVisibility(
            visible = notice != null && !sheet && !doc.maskMode && doc.editingModelId == null,
            enter = slideInVertically(
                initialOffsetY = { it / 2 },
                animationSpec = if (reduceMotion) tween(0) else spring(dampingRatio = 0.76f, stiffness = 460f),
            ) + fadeIn(tween(if (reduceMotion) 0 else 140)),
            exit = slideOutVertically(targetOffsetY = { it / 2 }) + fadeOut(tween(if (reduceMotion) 0 else 110)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 10.dp, end = 10.dp, bottom = dockClearance),
        ) {
            LayerLanded(
                name = notice.orEmpty(),
                onDone = {
                    doc.settle()
                    notice = null
                },
                onInspect = {
                    notice = null
                    openSheet(StudioTab.Style)
                },
            )
        }
        AnimatedVisibility(
            visible = doc.inkMode && !sheet,
            enter = slideInVertically(
                initialOffsetY = { it / 2 },
                animationSpec = if (reduceMotion) tween(0) else spring(dampingRatio = 0.78f, stiffness = 420f),
            ) + fadeIn(tween(if (reduceMotion) 0 else 140)),
            exit = slideOutVertically(targetOffsetY = { it / 2 }) + fadeOut(tween(if (reduceMotion) 0 else 110)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 8.dp, vertical = 8.dp),
        ) {
            InkHud(doc = doc, onDone = { doc.settle() })
        }
        AnimatedVisibility(
            visible = doc.maskMode && !sheet,
            enter = slideInVertically(
                initialOffsetY = { it / 2 },
                animationSpec = if (reduceMotion) tween(0) else spring(dampingRatio = 0.78f, stiffness = 420f),
            ) + fadeIn(tween(if (reduceMotion) 0 else 140)),
            exit = slideOutVertically(targetOffsetY = { it / 2 }) + fadeOut(tween(if (reduceMotion) 0 else 110)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 8.dp, vertical = 8.dp),
        ) {
            MaskHud(doc = doc, onDone = { doc.settle() })
        }
        AnimatedVisibility(
            visible = doc.editingModelId != null && !sheet,
            enter = slideInVertically(
                initialOffsetY = { it / 2 },
                animationSpec = if (reduceMotion) tween(0) else spring(dampingRatio = 0.78f, stiffness = 420f),
            ) + fadeIn(tween(if (reduceMotion) 0 else 140)),
            exit = slideOutVertically(targetOffsetY = { it / 2 }) + fadeOut(tween(if (reduceMotion) 0 else 110)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 8.dp, vertical = 8.dp),
        ) {
            ModelTurnHud(doc = doc, onDone = { doc.settle() })
        }
    }
    if (showIcons) IconSheet(doc = doc, onClose = { showIcons = false })
    if (showAdd) {
        CreateDeck(
            onClose = { showAdd = false },
            onType = {
                doc.addText()
                showAdd = false
            },
            onShape = {
                doc.addVector()
                showAdd = false
            },
            onSticker = {
                showAdd = false
                doc.chooseTool(Tool.Sticker)
                openSheet(StudioTab.Style)
            },
            onIcon = {
                showAdd = false
                showIcons = true
            },
            onModel = {
                doc.addModel(ModelAsset.Gem)
                showAdd = false
            },
            onPhoto = {
                showAdd = false
                photoPicker.launch("image/*")
            },
        )
    }
    if (showHistory) HistoryDialog(doc = doc, onClose = { showHistory = false })
    if (showLibrary) {
        // The key forces the list to be read again after a save or a delete.
        key(libraryKey) {
            LibraryDialog(
                context = context,
                doc = doc,
                onChanged = { libraryKey++ },
                onClose = { showLibrary = false },
            )
        }
    }
    if (showExport) {
        ExportDialog(
            scale = exportScale,
            format = exportFormat,
            onScale = { exportScale = it },
            onFormat = { exportFormat = it },
            onExport = {
                showExport = false
                exportArtboard(exportScale, exportFormat)
            },
            onShare = {
                val result = lastExport
                val intent = result?.let { Exporter.shareIntent(it) }
                if (intent == null) {
                    Toast.makeText(context, "EXPORT SOMETHING FIRST", Toast.LENGTH_SHORT).show()
                } else {
                    context.startActivity(Intent.createChooser(intent, "Share export"))
                }
            },
            onClose = { showExport = false },
        )
    }
    if (showLegend) GestureLegend(onClose = { showLegend = false })
    if (showInfo) {
        InfoDialog(
            onClose = { showInfo = false },
            onDiagnostics = { showInfo = false; showDiagnostics = true },
            onBasicEditor = basicEditor,
        )
    }
    if (showDiagnostics) {
        DiagnosticsDialog(
            doc = doc,
            handle = primaryHandle,
            onExport = { exportArtboard(exportScale, exportFormat) },
            onClose = { showDiagnostics = false },
        )
    }
    if (showCommands) {
        EngineCommandSheet(
            commands = bmoCommands,
            onRun = { command ->
                showCommands = false
                command.run()
            },
            onClose = { showCommands = false },
        )
    }
    if (showPlugins) {
        EnginePluginShelf(
            plugins = EnginePlugins.builtIn,
            activeId = shell.pluginId,
            recentIds = listOfNotNull(shell.pluginId),
            onRun = { id ->
                showPlugins = false
                shell = shell.reduce(ShellEvent.OpenPlugin(id))
            },
            onOpen = { id ->
                showPlugins = false
                shell = shell.reduce(ShellEvent.OpenPlugin(id))
            },
            onHide = {
                showPlugins = false
                shell = shell.reduce(ShellEvent.HidePlugin)
            },
            onClose = {
                showPlugins = false
                shell.pluginId?.let { pluginStateHolder.removeState(it) }
                shell = shell.reduce(ShellEvent.ClosePlugin)
            },
        )
    }
    if (!showPlugins && activePlugin != null && shell.surface == EngineSurface.Plugins) {
        EnginePluginHost(
            plugin = activePlugin,
            doc = doc,
            layout = layout,
            onHide = {
                shell = shell.reduce(ShellEvent.HidePlugin)
            },
            onClose = {
                pluginStateHolder.removeState(activePlugin.id)
                shell = shell.reduce(ShellEvent.ClosePlugin)
            },
            onToast = { message -> doc.status = message },
            stateHolder = pluginStateHolder,
        )
    }
}
