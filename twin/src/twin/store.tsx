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
import { canAddLayer, fitImageInsideArtboard } from "../editor/model/geometry";
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
  TwinDocument,
  TwinEffect,
  TwinLayer,
} from "./types";

const MIGRATION_FACTOR = 765 / 338;
const MAX_PNG_BYTES = 8 * 1024 * 1024;
const MAX_PNG_SIDE = 8192;
const MAX_PNG_PIXELS = 16_000_000;

const initialLayers: TwinLayer[] = [
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

const initialDocument: TwinDocument = {
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

export interface TwinState {
  history: ReturnType<typeof createHistory>;
  doc: TwinDocument;
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
}

export interface TwinActions {
  state: TwinState;
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
  applyEffect: (effect: TwinEffect, target?: EffectTarget) => void;
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
  removeLayer: (id: string) => void;
  saveProject: () => Promise<void>;
  openProject: () => void;
  exportPng: () => Promise<PngExportResult | null>;
  printStage: () => void;
  toast: (message: string) => void;
}

const TwinContext = createContext<TwinActions | null>(null);

const cloneDoc = (doc: TwinDocument): TwinDocument => ({
  ...doc,
  layers: doc.layers.map((layer) => ({
    ...layer,
    effect: layer.effect ? structuredClone(layer.effect) : null,
    transform: { ...layer.transform },
  })),
  background: doc.background ? structuredClone(doc.background) : null,
});

export function TwinProvider({ children }: { children: ReactNode }) {
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
  const toastTimer = useRef<number | null>(null);
  const pulseSequence = useRef(0);
  const stateRef = useRef(history);

  useEffect(() => {
    stateRef.current = history;
  }, [history]);

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
    if (id === "native-parity") setNativeTabState("tone");
  }, []);

  const openNative = useCallback((tab: NativeTab) => {
    setSurface("plugins");
    setActivePluginId("native-parity");
    setCatalogueOpen(false);
    setPluginHidden(false);
    setNativeTabState(tab);
  }, []);

  const setNativeTab = useCallback((tab: NativeTab) => setNativeTabState(tab), []);

  const openCatalogue = useCallback(() => {
    setSurface("plugins");
    setCatalogueOpen(true);
    setPluginHidden(false);
  }, []);

  const hidePlugin = useCallback(() => {
    if (!activePluginId) return;
    setPluginHidden(true);
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

  const commit = useCallback((update: (doc: TwinDocument) => TwinDocument) => {
    setHistory((current) => {
      const next = commitHistory(current, update(current.present));
      stateRef.current = next;
      return next;
    });
  }, []);

  const applyEffect = useCallback((effect: TwinEffect, nextTarget: EffectTarget = target) => {
    const currentDoc = stateRef.current.present;
    const primary = currentDoc.selectedLayerIds[0];
    if (nextTarget === "layer" && !primary) {
      showToast("Select a layer for a layer effect.");
      return;
    }
    const currentEffect = nextTarget === "artboard"
      ? currentDoc.background
      : currentDoc.layers.find((layer) => layer.id === primary)?.effect ?? null;
    if (JSON.stringify(currentEffect) !== JSON.stringify(effect)) {
      pulseSequence.current += 1;
      setEffectPulse({
        target: nextTarget,
        layerId: nextTarget === "layer" ? primary ?? null : null,
        nonce: pulseSequence.current,
      });
    }
    setHistory((current) => {
      const next = pushEffect(current, nextTarget, effect);
      stateRef.current = next;
      if (next === current) {
        showToast("That effect is already on the artboard.");
      } else {
        showToast("Effect applied.");
      }
      return next;
    });
  }, [showToast, target]);

  const undo = useCallback(() => {
    setHistory((current) => {
      const next = undoHistory(current);
      stateRef.current = next;
      if (next !== current) showToast("Undid the last edit.");
      return next;
    });
  }, [showToast]);

  const redo = useCallback(() => {
    setHistory((current) => {
      const next = redoHistory(current);
      stateRef.current = next;
      if (next !== current) showToast("Redid the edit.");
      return next;
    });
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
      const layer: TwinLayer = {
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
      const layer: TwinLayer = {
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
    const layer: TwinLayer = {
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
    input.accept = ".twin,application/json";
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

  const printStage = useCallback(() => {
    document.body.classList.add("is-printing");
    const cleanup = () => {
      document.body.classList.remove("is-printing");
      window.removeEventListener("afterprint", cleanup);
    };
    window.addEventListener("afterprint", cleanup);
    window.setTimeout(cleanup, 60_000);
    window.print();
  }, []);

  const actions = useMemo<TwinActions>(() => ({
    state: {
      history,
      doc: history.present,
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
    removeLayer,
    saveProject,
    openProject,
    exportPng,
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
    printStage,
    showToast,
  ]);

  return <TwinContext.Provider value={actions}>{children}</TwinContext.Provider>;
}

export function useTwinStore(): TwinActions {
  const value = useContext(TwinContext);
  if (!value) throw new Error("useTwinStore must be used inside TwinProvider");
  return value;
}
