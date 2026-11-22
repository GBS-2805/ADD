import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from "react";
import {
  AUTOSAVE_DELAY_MS,
  createIndexedDbBackend,
  referencedAssets,
  reviveFromStorage,
  serializeForStorage,
  type AutosaveBackend,
} from "./autosave";
import { sameEffect } from "./catalogue";
import { commitHistory, createHistory, pushEffect, redoHistory, undoHistory, updateLayer } from "./history";
import {
  downloadBlob,
  MAX_PROJECT_BYTES,
  parseProjectFile,
  projectFilename,
  ProjectFileError,
  serializeProject,
} from "./project";
import { renderArtboardPng, type PngExportResult } from "./pngExport";
import { saveArtboardNatively } from "./nativeExport";
import { canAddLayer, fitImageInsideArtboard, type Size } from "../editor/model/geometry";
import { reorderLayerIds } from "../editor/model/layerTree";
import { nextSelection, pruneSelection, selectAllIds, type SelectionMode } from "../editor/model/selection";
import type { EditorTool, RightTab, Viewport } from "../editor/types";
import type {
  BlendMode,
  EffectTarget,
  LayerTransform,
  Layout,
  NativeTab,
  ShapeVariant,
  Surface,
  TextFont,
  EditorDocument,
  EditorEffect,
  EditorLayer,
} from "./types";

const MIGRATION_FACTOR = 765 / 338;
const MAX_PNG_BYTES = 8 * 1024 * 1024;
const MAX_PNG_SIDE = 8192;
const MAX_PNG_PIXELS = 16_000_000;

const initialLayers: EditorLayer[] = [
  {
    id: "type-open-studio",
    name: "Type / OPEN STUDIO",
    kind: "text",
    effect: null,
    blend: "normal",
    opacity: 1,
    transform: { x: 0, y: -8 * MIGRATION_FACTOR, scaleX: 1, scaleY: 1, rotation: 0 },
    visible: true,
    text: { content: "OPEN\nSTUDIO", size: 68 * MIGRATION_FACTOR, align: "center", font: "instrument-serif" },
    fill: "#111111",
  },
  {
    id: "shape-star",
    name: "Shape / Star",
    kind: "shape",
    effect: null,
    blend: "normal",
    opacity: 1,
    transform: { x: -92 * MIGRATION_FACTOR, y: 86 * MIGRATION_FACTOR, scaleX: 1.05, scaleY: 1.05, rotation: -9 },
    visible: true,
    shape: { variant: "star" },
    fill: "#111111",
  },
  {
    id: "shape-orbit",
    name: "Shape / Orbit",
    kind: "shape",
    effect: null,
    blend: "normal",
    opacity: 1,
    transform: { x: 96 * MIGRATION_FACTOR, y: 76 * MIGRATION_FACTOR, scaleX: 1, scaleY: 1, rotation: 0 },
    visible: true,
    shape: { variant: "orbit" },
    fill: "#111111",
  },
];

let layerSequence = 0;
const makeLayerId = (kind: string) => `${kind}-${Date.now()}-${layerSequence += 1}`;

const initialDocument: EditorDocument = {
  background: {
    kind: "shader",
    shader: "PaperTexture",
    preset: "Paper stock",
    controls: { roughness: 0.2 },
  },
  layers: initialLayers,
  selectedLayerIds: ["type-open-studio"],
};

export interface EffectPulse {
  target: EffectTarget;
  layerId: string | null;
  nonce: number;
}

export interface AutosaveState {
  /** "off" when the browser has no usable storage; "idle" until the first write. */
  status: "idle" | "saved" | "off";
  savedAt: string | null;
  /** Layers restored from the last session, for the status line. Null until read. */
  restoredLayers: number | null;
  /** Pictures whose bytes were gone, so they could not be restored. */
  droppedImages: number;
}

export interface EditorState {
  history: ReturnType<typeof createHistory>;
  doc: EditorDocument;
  lastExport: Omit<PngExportResult, "blob"> | null;
  surface: Surface;
  activePluginId: string | null;
  catalogueOpen: boolean;
  pluginHidden: boolean;
  pluginSessionKey: number;
  nativeTab: NativeTab;
  layout: Layout;
  nativeLens: boolean;
  target: EffectTarget;
  toast: string;
  toastExiting: boolean;
  presenting: boolean;
  effectPulse: EffectPulse | null;
  transientTransforms: Record<string, LayerTransform> | null;
  tool: EditorTool;
  snapEnabled: boolean;
  rulersVisible: boolean;
  rightTab: RightTab;
  viewport: Viewport;
  canvasSize: Size;
  panels: { layers: boolean; inspector: boolean };
  autosave: AutosaveState;
}

export interface EditorActions {
  state: EditorState;
  canUndo: boolean;
  canRedo: boolean;
  openPlugin: (id: string) => void;
  openNative: (tab: NativeTab) => void;
  setNativeTab: (tab: NativeTab) => void;
  openCatalogue: () => void;
  hidePlugin: () => void;
  closePlugin: () => void;
  openCommands: () => void;
  openInspector: () => void;
  closeSurface: () => void;
  openPresentation: () => void;
  closePresentation: () => void;
  setLayout: (layout: Layout) => void;
  toggleNativeLens: () => void;
  setTarget: (target: EffectTarget) => void;
  applyEffect: (effect: EditorEffect, target?: EffectTarget) => void;
  undo: () => void;
  redo: () => void;
  selectLayer: (id: string) => void;
  selectLayers: (ids: string[], mode?: SelectionMode) => void;
  selectAllLayers: () => void;
  clearSelection: () => void;
  reorderLayers: (ids: string[], targetIndex: number) => void;
  removeSelectedLayers: (ids?: string[]) => void;
  addTextLayer: () => string | null;
  addShapeLayer: (variant: ShapeVariant) => string | null;
  renameLayer: (id: string, name: string) => void;
  setLayerText: (id: string, content: string, size?: number) => void;
  setLayerFont: (id: string, font: TextFont) => void;
  setLayerFill: (id: string, hex: string) => void;
  addImageLayer: (file: File) => Promise<string>;
  setLayerBlend: (id: string, blend: BlendMode) => void;
  setLayerOpacity: (id: string, opacity: number) => void;
  setLayerTransforms: (updates: Record<string, LayerTransform>) => void;
  setTransientTransforms: (updates: Record<string, LayerTransform> | null) => void;
  setLayerTransform: (id: string, transform: Partial<LayerTransform>) => void;
  setLayerVisibility: (id: string, visible: boolean) => void;
  moveLayer: (id: string, direction: "up" | "down") => void;
  setTool: (tool: EditorTool) => void;
  setSnapEnabled: (enabled: boolean) => void;
  setRulersVisible: (visible: boolean) => void;
  setRightTab: (tab: RightTab) => void;
  setViewport: (viewport: Viewport) => void;
  setCanvasSize: (size: Size) => void;
  togglePanel: (panel: "layers" | "inspector") => void;
  removeLayer: (id: string) => void;
  saveProject: () => Promise<void>;
  openProject: () => void;
  exportPng: () => Promise<PngExportResult | null>;
  exportNativePng: () => Promise<void>;
  printStage: () => void;
  toast: (message: string) => void;
}

const EditorContext = createContext<EditorActions | null>(null);

const cloneDoc = (doc: EditorDocument): EditorDocument => ({
  ...doc,
  layers: doc.layers.map((layer) => ({
    ...layer,
    effect: layer.effect ? structuredClone(layer.effect) : null,
    transform: { ...layer.transform },
  })),
  background: doc.background ? structuredClone(doc.background) : null,
});

export interface EditorProviderProps {
  children: ReactNode;
  /**
   * Storage to autosave into. Omitted in the app, where IndexedDB is chosen; a
   * test passes a memory backend, and `null` means "this browser has no storage".
   */
  autosaveBackend?: AutosaveBackend | null;
}

export function EditorProvider({ children, autosaveBackend }: EditorProviderProps) {
  const [history, setHistory] = useState(() => createHistory(cloneDoc(initialDocument)));
  const [surface, setSurface] = useState<Surface>("none");
  const [activePluginId, setActivePluginId] = useState<string | null>(null);
  const [catalogueOpen, setCatalogueOpen] = useState(true);
  const [pluginHidden, setPluginHidden] = useState(false);
  const [pluginSessionKey, setPluginSessionKey] = useState(0);
  const [nativeTab, setNativeTabState] = useState<NativeTab>("tone");
  const [layout, setLayoutState] = useState<Layout>("compact");
  const [nativeLens, setNativeLens] = useState(false);
  const [target, setTargetState] = useState<EffectTarget>("artboard");
  const [toastMessage, setToastMessage] = useState("");
  const [toastExiting, setToastExiting] = useState(false);
  const [presenting, setPresenting] = useState(false);
  const [lastExport, setLastExport] = useState<Omit<PngExportResult, "blob"> | null>(null);
  const [effectPulse, setEffectPulse] = useState<EffectPulse | null>(null);
  const [transientTransforms, setTransientTransformsState] = useState<Record<string, LayerTransform> | null>(null);
  const [tool, setToolState] = useState<EditorTool>("move");
  const [snapEnabled, setSnapEnabledState] = useState(true);
  const [rulersVisible, setRulersVisibleState] = useState(false);
  const [rightTab, setRightTabState] = useState<RightTab>("design");
  const [viewport, setViewportState] = useState<Viewport>({ zoom: 1, panX: 0, panY: 0 });
  const [canvasSize, setCanvasSizeState] = useState<Size>({ width: 0, height: 0 });
  const [panels, setPanels] = useState({ layers: true, inspector: true });
  const toastTimer = useRef<number | null>(null);
  const pulseSequence = useRef(0);
  const stateRef = useRef(history);
  const [autosave, setAutosave] = useState<AutosaveState>({
    status: "idle",
    savedAt: null,
    restoredLayers: null,
    droppedImages: 0,
  });
  /**
   * Created once, lazily. A null backend means this browser has no usable storage
   * (jsdom, private mode, storage switched off); the editor then stops autosaving
   * instead of failing.
   */
  const backendRef = useRef<AutosaveBackend | null | undefined>(undefined);
  const backend = () => {
    if (backendRef.current === undefined) {
      backendRef.current =
        autosaveBackend !== undefined ? autosaveBackend : createIndexedDbBackend();
    }
    return backendRef.current;
  };
  /** Pictures whose bytes still need writing, keyed by asset id. */
  const pendingAssets = useRef(new Map<string, Blob>());
  const autosaveTimer = useRef<number | null>(null);
  /**
   * True until the first read finishes. Autosave is suppressed meanwhile, so the
   * starter document can never overwrite the real one that is still loading.
   */
  const restoring = useRef(true);

  useEffect(() => {
    stateRef.current = history;
  }, [history]);

  /** Reads the stored bytes for a live image layer, if it has any. */
  const imageBlob = useCallback(async (layer: EditorLayer): Promise<Blob | null> => {
    if (!layer.image) return null;
    const known = pendingAssets.current.get(layer.image.assetId);
    if (known) return known;
    if (!layer.image.src.startsWith("blob:")) return null;
    try {
      const response = await fetch(layer.image.src);
      return response.ok ? await response.blob() : null;
    } catch {
      return null;
    }
  }, []);

  /** Writes the current document and the pictures it needs. Never throws. */
  const writeAutosave = useCallback(async () => {
    const handle = backend();
    if (!handle) {
      setAutosave((current) => (current.status === "off" ? current : { ...current, status: "off" }));
      return;
    }
    const doc = stateRef.current.present;
    try {
      for (const layer of doc.layers) {
        if (layer.kind !== "image" || !layer.image) continue;
        const blob = await imageBlob(layer);
        if (blob) await handle.writeAsset(layer.image.assetId, blob);
      }
      const payload = serializeForStorage(doc);
      await handle.write(payload);
      await handle.pruneAssets(referencedAssets(payload));
      pendingAssets.current.clear();
      setAutosave((current) => ({ ...current, status: "saved", savedAt: payload.savedAt, restoredLayers: null }));
    } catch {
      // A full quota or a blocked store must not interrupt editing.
      setAutosave((current) => (current.status === "off" ? current : { ...current, status: "off" }));
    }
  }, [imageBlob]);

  /** Coalesces a burst of edits into one write, `AUTOSAVE_DELAY_MS` after the last. */
  const scheduleAutosave = useCallback(() => {
    if (restoring.current) return;
    if (autosaveTimer.current) window.clearTimeout(autosaveTimer.current);
    autosaveTimer.current = window.setTimeout(() => {
      autosaveTimer.current = null;
      void writeAutosave();
    }, AUTOSAVE_DELAY_MS);
  }, [writeAutosave]);

  /**
   * Restore the last session once, before the first edit. A slow read leaves the
   * starter document in place rather than blocking the editor.
   */
  useEffect(() => {
    let cancelled = false;
    const handle = backend();
    if (!handle) {
      restoring.current = false;
      setAutosave((current) => ({ ...current, status: "off" }));
      return;
    }
    let revivedDocument = false;
    void (async () => {
      try {
        const stored = await handle.read();
        if (cancelled) return;
        const revived = stored
          ? await reviveFromStorage(stored, (id) => handle.readAsset(id))
          : null;
        if (cancelled) return;
        if (revived) {
          revivedDocument = true;
          setHistory(createHistory(revived.document));
          setAutosave((current) => ({
            ...current,
            status: "saved",
            savedAt:
              typeof (stored as { savedAt?: unknown }).savedAt === "string"
                ? (stored as { savedAt: string }).savedAt
                : null,
            restoredLayers: revived.document.layers.length,
            droppedImages: revived.droppedImages,
          }));
        }
      } catch {
        if (!cancelled) setAutosave((current) => ({ ...current, status: "off" }));
      } finally {
        if (!cancelled) {
          restoring.current = false;
          // Nothing was stored: keep the starter document straight away, so the
          // status line says "Saved" only after something really was written.
          if (!revivedDocument) scheduleAutosave();
        }
      }
    })();
    return () => {
      cancelled = true;
    };
    // Reads once: a backend that changes identity must not re-run the restore.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  /** Every committed document change is an autosave candidate. */
  useEffect(() => {
    scheduleAutosave();
  }, [history, scheduleAutosave]);

  /** Clear a pending write on unmount. */
  useEffect(() => () => {
    if (autosaveTimer.current) window.clearTimeout(autosaveTimer.current);
  }, []);

  const showToast = useCallback((message: string) => {
    setToastExiting(false);
    setToastMessage(message);
    if (toastTimer.current) window.clearTimeout(toastTimer.current);
    toastTimer.current = window.setTimeout(() => {
      setToastExiting(true);
      toastTimer.current = window.setTimeout(() => setToastMessage(""), 140);
    }, 2460);
  }, []);

  useEffect(() => () => {
    if (toastTimer.current) window.clearTimeout(toastTimer.current);
  }, []);

  const openPlugin = useCallback((id: string) => {
    setSurface("plugins");
    setActivePluginId(id);
    setCatalogueOpen(false);
    setPluginHidden(false);
    setRightTabState("effects");
    if (id === "native-parity") setNativeTabState("tone");
  }, []);

  const openNative = useCallback((tab: NativeTab) => {
    setSurface("plugins");
    setActivePluginId("native-parity");
    setCatalogueOpen(false);
    setPluginHidden(false);
    setRightTabState("effects");
    setNativeTabState(tab);
  }, []);

  const setNativeTab = useCallback((tab: NativeTab) => setNativeTabState(tab), []);

  const openCatalogue = useCallback(() => {
    setSurface("plugins");
    setCatalogueOpen(true);
    setPluginHidden(false);
    setRightTabState("effects");
  }, []);

  const hidePlugin = useCallback(() => {
    if (!activePluginId) return;
    setPluginHidden(true);
    setCatalogueOpen(true);
    setSurface("none");
  }, [activePluginId]);

  const closePlugin = useCallback(() => {
    setPluginHidden(true);
    setSurface("none");
    setActivePluginId(null);
    setCatalogueOpen(true);
    setPluginSessionKey((key) => key + 1);
  }, []);

  const openCommands = useCallback(() => {
    setSurface("commands");
  }, []);

  const openInspector = useCallback(() => {
    setSurface("inspector");
    setRightTabState("design");
  }, []);

  const closeSurface = useCallback(() => {
    setSurface("none");
  }, []);

  const openPresentation = useCallback(() => setPresenting(true), []);
  const closePresentation = useCallback(() => setPresenting(false), []);

  const setLayout = useCallback((next: Layout) => setLayoutState(next), []);
  const toggleNativeLens = useCallback(() => setNativeLens((value) => !value), []);
  const setTarget = useCallback((next: EffectTarget) => setTargetState(next), []);
  const setTool = useCallback((next: EditorTool) => setToolState(next), []);
  const setSnapEnabled = useCallback((enabled: boolean) => setSnapEnabledState(enabled), []);
  const setRulersVisible = useCallback((visible: boolean) => setRulersVisibleState(visible), []);
  const setRightTab = useCallback((tab: RightTab) => setRightTabState(tab), []);
  const setViewport = useCallback((next: Viewport) => setViewportState(next), []);
  const setCanvasSize = useCallback((size: Size) => setCanvasSizeState(size), []);
  const togglePanel = useCallback((panel: "layers" | "inspector") => {
    setPanels((current) => ({ ...current, [panel]: !current[panel] }));
  }, []);

  const commit = useCallback((update: (doc: EditorDocument) => EditorDocument) => {
    setHistory((current) => {
      const next = commitHistory(current, update(current.present));
      stateRef.current = next;
      return next;
    });
  }, []);

  const applyEffect = useCallback((effect: EditorEffect, nextTarget: EffectTarget = target) => {
    const currentDoc = stateRef.current.present;
    const primary = currentDoc.selectedLayerIds[0];
    if (nextTarget === "layer" && currentDoc.selectedLayerIds.length !== 1) {
      showToast("Select one layer for a layer effect.");
      return;
    }
    const currentEffect = nextTarget === "artboard"
      ? currentDoc.background
      : currentDoc.layers.find((layer) => layer.id === primary)?.effect ?? null;
    // Decide the toast from the state we are about to commit. `pushEffect` is a
    // no-op for the same effect, and React can replay a `setHistory` updater
    // later, so a toast dispatched inside it may report the wrong outcome.
    if (sameEffect(currentEffect, effect)) {
      showToast("That effect is already on the artboard.");
      return;
    }
    pulseSequence.current += 1;
    setEffectPulse({
      target: nextTarget,
      layerId: nextTarget === "layer" ? primary ?? null : null,
      nonce: pulseSequence.current,
    });
    setHistory((current) => {
      const next = pushEffect(current, nextTarget, effect);
      stateRef.current = next;
      return next;
    });
    showToast("Effect applied.");
  }, [showToast, target]);

  const undo = useCallback(() => {
    // Same rule as `applyEffect`: report the outcome from the state this call is
    // committing, not from a `setHistory` updater React may replay.
    if (undoHistory(stateRef.current) === stateRef.current) return;
    setHistory((current) => {
      const next = undoHistory(current);
      stateRef.current = next;
      return next;
    });
    showToast("Undid the last edit.");
  }, [showToast]);

  const redo = useCallback(() => {
    if (redoHistory(stateRef.current) === stateRef.current) return;
    setHistory((current) => {
      const next = redoHistory(current);
      stateRef.current = next;
      return next;
    });
    showToast("Redid the edit.");
  }, [showToast]);

  const selectLayers = useCallback((ids: string[], mode: SelectionMode = "replace") => {
    setHistory((current) => {
      let selected = current.present.selectedLayerIds;
      if (mode === "replace") selected = [...ids];
      else for (const id of ids) selected = nextSelection(selected, id, mode);
      const selectedLayerIds = pruneSelection(selected, current.present.layers);
      if (
        selectedLayerIds.length === current.present.selectedLayerIds.length &&
        selectedLayerIds.every((id, index) => id === current.present.selectedLayerIds[index])
      ) {
        return current;
      }
      const next = { ...current, present: { ...current.present, selectedLayerIds } };
      stateRef.current = next;
      return next;
    });
  }, []);

  const selectLayer = useCallback((id: string) => selectLayers([id], "replace"), [selectLayers]);

  const selectAllLayers = useCallback(() => {
    selectLayers(selectAllIds(stateRef.current.present.layers), "replace");
  }, [selectLayers]);

  const clearSelection = useCallback(() => selectLayers([], "replace"), [selectLayers]);

  const reorderLayers = useCallback((ids: string[], targetIndex: number) => {
    commit((doc) => {
      const layers = reorderLayerIds(doc.layers, ids, targetIndex);
      return layers === doc.layers ? doc : { ...doc, layers };
    });
  }, [commit]);

  const addTextLayer = useCallback(() => {
    if (!canAddLayer(stateRef.current.present.layers, "text")) {
      showToast("The document is capped at 64 layers.");
      return null;
    }
    const id = makeLayerId("text");
    commit((doc) => {
      const count = doc.layers.filter((layer) => layer.kind === "text").length;
      const layer: EditorLayer = {
        id,
        name: count === 0 ? "Text / Type something" : `Text / Type something ${count + 1}`,
        kind: "text",
        effect: null,
        blend: "normal",
        opacity: 1,
        transform: { x: 0, y: 0, scaleX: 1, scaleY: 1, rotation: 0 },
        visible: true,
        text: { content: "Type something", size: 40 * MIGRATION_FACTOR, align: "center", font: "instrument-serif" },
        fill: "#111111",
      };
      return { ...doc, selectedLayerIds: [id], layers: [...doc.layers, layer] };
    });
    setSurface("inspector");
    showToast("Text layer added.");
    return id;
  }, [commit, showToast]);

  const addShapeLayer = useCallback((variant: ShapeVariant) => {
    if (!canAddLayer(stateRef.current.present.layers, "shape")) {
      showToast("The document is capped at 64 layers.");
      return null;
    }
    const id = makeLayerId("shape");
    commit((doc) => {
      const count = doc.layers.filter(
        (layer) => layer.kind === "shape" && layer.shape?.variant === variant,
      ).length;
      const label = variant.charAt(0).toUpperCase() + variant.slice(1);
      const layer: EditorLayer = {
        id,
        name: count === 0 ? `Shape / ${label}` : `Shape / ${label} ${count + 1}`,
        kind: "shape",
        effect: null,
        blend: "normal",
        opacity: 1,
        transform: { x: 0, y: 0, scaleX: 1, scaleY: 1, rotation: 0 },
        visible: true,
        shape: { variant },
        fill: "#111111",
      };
      return { ...doc, selectedLayerIds: [id], layers: [...doc.layers, layer] };
    });
    setSurface("inspector");
    showToast("Shape layer added.");
    return id;
  }, [commit, showToast]);

  const renameLayer = useCallback((id: string, name: string) => {
    const nextName = name.trim();
    if (!nextName) return;
    commit((doc) => updateLayer(doc, id, (layer) => ({ ...layer, name: nextName })));
  }, [commit]);

  const setLayerText = useCallback((id: string, content: string, size?: number) => {
    commit((doc) =>
      updateLayer(doc, id, (layer) =>
        layer.text
          ? { ...layer, text: { ...layer.text, content, ...(size === undefined ? {} : { size }) } }
          : layer,
      ),
    );
  }, [commit]);

  const setLayerFont = useCallback((id: string, font: TextFont) => {
    commit((doc) =>
      updateLayer(doc, id, (layer) =>
        layer.text ? { ...layer, text: { ...layer.text, font } } : layer,
      ),
    );
  }, [commit]);

  const setLayerFill = useCallback((id: string, hex: string) => {
    const fill = hex.trim();
    if (!fill) return;
    commit((doc) => updateLayer(doc, id, (layer) => ({ ...layer, fill })));
  }, [commit]);

  const addImageLayer = useCallback(async (file: File) => {
    if (!canAddLayer(stateRef.current.present.layers, "image")) {
      showToast(
        stateRef.current.present.layers.length >= 64
          ? "The document is capped at 64 layers."
          : "The overlay stack is capped at 12 PNG layers.",
      );
      throw new Error("Layer cap reached");
    }
    if (file.type !== "image/png") {
      showToast("Overlay Lab accepts PNG files only.");
      throw new Error("PNG only");
    }
    if (file.size > MAX_PNG_BYTES) {
      showToast("That PNG is over 8 MB.");
      throw new Error("PNG too large");
    }
    if (typeof createImageBitmap !== "function") {
      showToast("PNG decoding is not available in this browser.");
      throw new Error("No image decoder");
    }
    let bitmap: ImageBitmap;
    try {
      bitmap = await createImageBitmap(file);
    } catch {
      showToast("That file could not be decoded as a PNG.");
      throw new Error("PNG decode failed");
    }
    if (bitmap.width > MAX_PNG_SIDE || bitmap.height > MAX_PNG_SIDE || bitmap.width * bitmap.height > MAX_PNG_PIXELS) {
      bitmap.close();
      showToast("That PNG is too large: max 8192 px per side and 16 MP.");
      throw new Error("PNG dimensions too large");
    }
    const src = URL.createObjectURL(file);
    const id = `png-${Date.now()}-${Math.random().toString(36).slice(2, 7)}`;
    const fitted = fitImageInsideArtboard(bitmap.width, bitmap.height);
    const layer: EditorLayer = {
      id,
      name: file.name.replace(/\.png$/i, "") || "PNG layer",
      kind: "image",
      effect: null,
      blend: "normal",
      opacity: 1,
      transform: { x: 0, y: 0, scaleX: 1, scaleY: 1, rotation: 0 },
      visible: true,
      image: {
        assetId: id,
        src,
        width: fitted.width,
        height: fitted.height,
      },
    };
    bitmap.close();
    commit((doc) => ({ ...doc, selectedLayerIds: [id], layers: [...doc.layers, layer] }));
    setSurface("inspector");
    showToast(`${file.name} added as a PNG layer.`);
    return id;
  }, [commit, showToast]);

  const setLayerBlend = useCallback((id: string, blend: BlendMode) => {
    commit((doc) => updateLayer(doc, id, (layer) => ({ ...layer, blend })));
  }, [commit]);

  const setLayerOpacity = useCallback((id: string, opacity: number) => {
    const value = Math.max(0, Math.min(1, opacity));
    commit((doc) => updateLayer(doc, id, (layer) => ({ ...layer, opacity: value })));
  }, [commit]);

  const setLayerTransforms = useCallback((updates: Record<string, LayerTransform>) => {
    if (Object.keys(updates).length === 0) return;
    commit((doc) => ({
      ...doc,
      layers: doc.layers.map((layer) =>
        updates[layer.id] ? { ...layer, transform: { ...updates[layer.id] } } : layer,
      ),
    }));
  }, [commit]);

  const setTransientTransforms = useCallback((updates: Record<string, LayerTransform> | null) => {
    setTransientTransformsState(updates);
  }, []);

  const setLayerTransform = useCallback((id: string, transform: Partial<LayerTransform>) => {
    const layer = stateRef.current.present.layers.find((item) => item.id === id);
    if (!layer) return;
    setLayerTransforms({ [id]: { ...layer.transform, ...transform } });
  }, [setLayerTransforms]);

  const setLayerVisibility = useCallback((id: string, visible: boolean) => {
    commit((doc) => updateLayer(doc, id, (layer) => ({ ...layer, visible })));
  }, [commit]);

  const moveLayer = useCallback((id: string, direction: "up" | "down") => {
    commit((doc) => {
      const layers = [...doc.layers];
      const index = layers.findIndex((layer) => layer.id === id);
      if (index < 0) return doc;
      const nextIndex = direction === "up" ? index + 1 : index - 1;
      if (nextIndex < 0 || nextIndex >= layers.length) return doc;
      [layers[index], layers[nextIndex]] = [layers[nextIndex], layers[index]];
      return { ...doc, layers };
    });
  }, [commit]);

  const removeSelectedLayers = useCallback((ids?: string[]) => {
    commit((doc) => {
      const targets = new Set(ids ?? doc.selectedLayerIds);
      if (targets.size === 0) return doc;
      const firstIndex = doc.layers.findIndex((layer) => targets.has(layer.id));
      if (firstIndex < 0) return doc;
      const remaining = doc.layers.filter((layer) => !targets.has(layer.id));
      const nearest = remaining[Math.min(firstIndex, remaining.length - 1)];
      // Keep PNG object URLs alive: an undo snapshot may still reference this layer.
      return {
        ...doc,
        layers: remaining,
        selectedLayerIds: nearest ? [nearest.id] : [],
      };
    });
    showToast("Layer removed.");
  }, [commit, showToast]);

  const removeLayer = useCallback((id: string) => {
    commit((doc) => {
      const index = doc.layers.findIndex((layer) => layer.id === id);
      if (index < 0) return doc;
      const remaining = doc.layers.filter((item) => item.id !== id);
      const nearest = remaining[Math.min(index, remaining.length - 1)];
      // Keep PNG object URLs alive: an undo snapshot may still reference this layer.
      return { ...doc, layers: remaining, selectedLayerIds: nearest ? [nearest.id] : [] };
    });
    showToast("Layer removed.");
  }, [commit, showToast]);

  const saveProject = useCallback(async () => {
    const doc = stateRef.current.present;
    try {
      const source = await serializeProject(doc);
      downloadBlob(new Blob([source], { type: "application/json" }), projectFilename(doc));
      showToast("Project file saved.");
    } catch (error) {
      showToast(error instanceof Error ? error.message : "The project could not be saved.");
    }
  }, [showToast]);

  const openProject = useCallback(() => {
    const input = document.createElement("input");
    input.type = "file";
    input.accept = ".add,.twin,application/json";
    input.hidden = true;
    input.addEventListener("change", () => {
      const file = input.files?.[0];
      input.remove();
      if (!file) return;
      void (async () => {
        if (file.size > MAX_PROJECT_BYTES) {
          showToast("That project file is too large.");
          return;
        }
        try {
          const document = parseProjectFile(await file.text());
          const previous = stateRef.current;
          const staleImages = new Set<string>();
          for (const snapshot of [...previous.past, previous.present, ...previous.future]) {
            for (const layer of snapshot.layers) {
              if (layer.image?.src.startsWith("blob:")) staleImages.add(layer.image.src);
            }
          }
          for (const src of staleImages) URL.revokeObjectURL(src);
          const next = createHistory(document);
          stateRef.current = next;
          setHistory(next);
          setSurface("none");
          setEffectPulse(null);
          showToast("Project opened.");
        } catch (error) {
          showToast(
            error instanceof ProjectFileError || error instanceof Error
              ? error.message
              : "That project could not be opened.",
          );
        }
      })();
    }, { once: true });
    document.body.append(input);
    input.click();
  }, [showToast]);

  const exportPng = useCallback(async () => {
    try {
      const result = await renderArtboardPng(stateRef.current.present);
      downloadBlob(result.blob, result.filename);
      const { blob: _blob, ...details } = result;
      setLastExport(details);
      showToast(`PNG exported · ${result.width} × ${result.height}.`);
      return result;
    } catch {
      setLastExport(null);
      showToast("The PNG could not be exported.");
      return null;
    }
  }, [showToast]);

  /**
   * The engine's painter, in the app. Kept beside the canvas export rather than
   * in front of it: the two can be compared on the same document, and a document
   * carrying an effect the native painter does not know is refused by name.
   */
  const exportNativePng = useCallback(async () => {
    try {
      const native = await saveArtboardNatively(stateRef.current.present);
      if (!native) {
        showToast("The native export runs inside ADD.");
        return;
      }
      if (native.status === "saved") {
        const { status: _status, message, ...details } = native;
        setLastExport(details);
        showToast(message);
        return;
      }
      if (native.status === "canvas") {
        showToast(`${native.reason} stays on the canvas renderer.`);
        return;
      }
      setLastExport(null);
      showToast(native.message);
    } catch {
      setLastExport(null);
      showToast("The native export could not run.");
    }
  }, [showToast]);

  const printStage = useCallback(() => {
    const host = (window as unknown as { IconsApp?: { print?: () => void } }).IconsApp;
    if (host && typeof host.print === "function") {
      host.print();
      return;
    }
    window.print();
  }, []);

  const actions = useMemo<EditorActions>(() => ({
    state: {
      history,
      doc: history.present,
      autosave,
      lastExport,
      surface,
      activePluginId,
      catalogueOpen,
      pluginHidden,
      pluginSessionKey,
      nativeTab,
      layout,
      nativeLens,
      target,
      toast: toastMessage,
      toastExiting,
      presenting,
      effectPulse,
      transientTransforms,
      tool,
      snapEnabled,
      rulersVisible,
      rightTab,
      viewport,
      canvasSize,
      panels,
    },
    canUndo: history.past.length > 0,
    canRedo: history.future.length > 0,
    openPlugin,
    openNative,
    setNativeTab,
    openCatalogue,
    hidePlugin,
    closePlugin,
    openCommands,
    openInspector,
    closeSurface,
    openPresentation,
    closePresentation,
    setLayout,
    toggleNativeLens,
    setTarget,
    applyEffect,
    undo,
    redo,
    selectLayer,
    selectLayers,
    selectAllLayers,
    clearSelection,
    reorderLayers,
    removeSelectedLayers,
    addTextLayer,
    addShapeLayer,
    renameLayer,
    setLayerText,
    setLayerFont,
    setLayerFill,
    addImageLayer,
    setLayerBlend,
    setLayerOpacity,
    setLayerTransforms,
    setTransientTransforms,
    setLayerTransform,
    setLayerVisibility,
    moveLayer,
    setTool,
    setSnapEnabled,
    setRulersVisible,
    setRightTab,
    setViewport,
    setCanvasSize,
    togglePanel,
    removeLayer,
    saveProject,
    openProject,
    exportPng,
    exportNativePng,
    printStage,
    toast: showToast,
  }), [
    history,
    lastExport,
    surface,
    activePluginId,
    catalogueOpen,
    pluginHidden,
    pluginSessionKey,
    nativeTab,
    layout,
    nativeLens,
    target,
    toastMessage,
    toastExiting,
    presenting,
    effectPulse,
    transientTransforms,
    tool,
    snapEnabled,
    rulersVisible,
    rightTab,
    viewport,
    canvasSize,
    panels,
    openPlugin,
    openNative,
    setNativeTab,
    openCatalogue,
    hidePlugin,
    closePlugin,
    openCommands,
    openInspector,
    closeSurface,
    openPresentation,
    closePresentation,
    setLayout,
    toggleNativeLens,
    setTarget,
    applyEffect,
    undo,
    redo,
    selectLayer,
    selectLayers,
    selectAllLayers,
    clearSelection,
    reorderLayers,
    removeSelectedLayers,
    addTextLayer,
    addShapeLayer,
    renameLayer,
    setLayerText,
    setLayerFont,
    setLayerFill,
    addImageLayer,
    setLayerBlend,
    setLayerOpacity,
    setLayerTransforms,
    setTransientTransforms,
    setLayerTransform,
    setLayerVisibility,
    moveLayer,
    removeLayer,
    saveProject,
    openProject,
    exportPng,
    exportNativePng,
    printStage,
    showToast,
    autosave,
  ]);

  return <EditorContext.Provider value={actions}>{children}</EditorContext.Provider>;
}

export function useEditorStore(): EditorActions {
  const value = useContext(EditorContext);
  if (!value) throw new Error("useEditorStore must be used inside EditorProvider");
  return value;
}
