import { useEffect, useState, type CSSProperties } from "react";
import { Splitter, useSplitter } from "@ark-ui/react";
import { ChevronLeft, ChevronRight, PanelLeftClose, PanelLeftOpen, PanelRightClose, PanelRightOpen } from "lucide-react";
import { useTwinStore } from "../twin/store";
import { Canvas } from "./canvas/Canvas";
import { LayersPanel } from "./panels/LayersPanel";
import { BottomSheet } from "./panels/BottomSheet";
import { RightPanel } from "./RightPanel";
import { StatusBar } from "./StatusBar";
import { Toolbar } from "./Toolbar";
import { TopChips } from "./TopChips";
import { CommandPalette } from "./CommandPalette";
import { PreviewMode } from "./PreviewMode";
import { PrintArtboard } from "./PrintArtboard";
import { useDeviceLayout } from "./useDeviceLayout";
import { useFoldSegments } from "./useFoldSegments";
import { useEditorHotkeys } from "./useEditorHotkeys";
import { PhoneToolDock } from "./mobile/PhoneToolDock";
import { CoverQuickBar } from "./mobile/CoverQuickBar";
import { FoldToolDeck } from "./mobile/FoldToolDeck";
import type { DeviceMode } from "./types";
import "./editor.css";

type DrawerId = "layers" | "inspector";

function PanelHeader({
  id,
  label,
  collapsed,
  onToggle,
}: {
  id: DrawerId;
  label: string;
  collapsed: boolean;
  onToggle: () => void;
}) {
  return (
    <div className="ed-panel-header">
      <span>{label}</span>
      <button
        type="button"
        className="ed-panel-collapse"
        aria-label={collapsed ? `Expand ${id}` : `Collapse ${id}`}
        aria-expanded={!collapsed}
        onClick={onToggle}
      >
        {id === "layers" ? (
          collapsed ? <PanelLeftOpen size={15} /> : <PanelLeftClose size={15} />
        ) : collapsed ? (
          <PanelRightOpen size={15} />
        ) : (
          <PanelRightClose size={15} />
        )}
      </button>
    </div>
  );
}

function DesktopWorkspace() {
  const store = useTwinStore();
  const [sizeTick, setSizeTick] = useState(0);
  const [fallbackSizes, setFallbackSizes] = useState<number[] | null>(null);
  const splitter = useSplitter({
    panels: [
      { id: "layers", minSize: "180px", maxSize: "320px", collapsible: true, collapsedSize: "44px" },
      { id: "canvas", minSize: "400px" },
      { id: "inspector", minSize: "240px", maxSize: "400px", collapsible: true, collapsedSize: "44px" },
    ],
    defaultSize: [16.5, 64, 19.5],
    onResize: () => setSizeTick((tick) => tick + 1),
  });

  const hasMeasuredLayout = () => {
    const root = document.querySelector<HTMLElement>('[data-testid="editor-splitter"]');
    return (root?.getBoundingClientRect().width ?? 0) > 0;
  };

  const sizes = fallbackSizes ?? splitter.getSizes();
  const layersOpen = store.state.panels.layers;
  const inspectorOpen = store.state.panels.inspector;

  return (
    <Splitter.RootProvider
      value={splitter}
      className="ed-splitter"
      data-testid="editor-splitter"
      data-sizes={sizes.join(",")}
      data-tick={sizeTick}
    >
      <Splitter.Panel id="layers" className="ed-splitter-panel" data-testid="splitter-panel-layers" data-collapsed={layersOpen ? "false" : "true"}>
        <PanelHeader id="layers" label="Layers" collapsed={!layersOpen} onToggle={() => store.togglePanel("layers")} />
        <div className="ed-panel-content">
          <LayersPanel collapsed={!layersOpen} />
        </div>
      </Splitter.Panel>
      <Splitter.ResizeTrigger
        id="layers:canvas"
        className="ed-resize-trigger"
        aria-label="Resize layers and canvas"
        data-testid="resize-trigger-layers-canvas"
        onKeyDown={(event) => {
          if (event.key !== "ArrowLeft" && event.key !== "ArrowRight") return;
          event.preventDefault();
          const delta = event.key === "ArrowRight" ? 16 : -16;
          if (hasMeasuredLayout()) {
            const current = splitter.getPanelSize("layers") || sizes[0] || 15.5;
            splitter.resizePanel("layers", current + delta);
            setSizeTick((tick) => tick + 1);
            return;
          }
          setFallbackSizes((current) => {
            const base = current ?? [15.5, 65, 19.5];
            return [Math.max(1, Math.min(99, base[0] + delta)), base[1], base[2]];
          });
        }}
      >
        <span className="ed-resize-grip" aria-hidden="true"><ChevronLeft size={10} /><ChevronRight size={10} /></span>
      </Splitter.ResizeTrigger>

      <Splitter.Panel id="canvas" className="ed-splitter-panel ed-canvas-panel" data-testid="splitter-panel-canvas">
        <Canvas />
      </Splitter.Panel>

      <Splitter.ResizeTrigger
        id="canvas:inspector"
        className="ed-resize-trigger"
        aria-label="Resize canvas and inspector"
        data-testid="resize-trigger-canvas-inspector"
      >
        <span className="ed-resize-grip" aria-hidden="true"><ChevronLeft size={10} /><ChevronRight size={10} /></span>
      </Splitter.ResizeTrigger>

      <Splitter.Panel id="inspector" className="ed-splitter-panel" data-testid="splitter-panel-inspector" data-collapsed={inspectorOpen ? "false" : "true"}>
        <PanelHeader id="inspector" label="Inspector" collapsed={!inspectorOpen} onToggle={() => store.togglePanel("inspector")} />
        <div className="ed-panel-content" data-collapsed={inspectorOpen ? "false" : "true"}>
          <RightPanel />
        </div>
      </Splitter.Panel>
    </Splitter.RootProvider>
  );
}

function TabletShell({ drawer, onDrawerChange }: { drawer: DrawerId | null; onDrawerChange: (drawer: DrawerId | null) => void }) {
  return (
    <section className="ed-tablet-shell" data-testid="tablet-shell">
      <div className="ed-tablet-canvas">
        <Canvas />
      </div>
      <div className="ed-tablet-rail" data-testid="tablet-drawer-triggers">
        <button
          type="button"
          className="ed-drawer-trigger"
          aria-label="Open layers"
          data-drawer-trigger="layers"
          onClick={() => onDrawerChange("layers")}
        >
          Layers
        </button>
        <button
          type="button"
          className="ed-drawer-trigger"
          aria-label="Open inspector"
          data-drawer-trigger="inspector"
          onClick={() => onDrawerChange("inspector")}
        >
          Inspector
        </button>
      </div>
      <BottomSheet id="layers" title="Layers" placement="left" fullHeight open={drawer === "layers"} onOpenChange={(open) => onDrawerChange(open ? "layers" : null)}>
        <LayersPanel />
      </BottomSheet>
      <BottomSheet id="inspector" title="Inspector" placement="right" open={drawer === "inspector"} onOpenChange={(open) => onDrawerChange(open ? "inspector" : null)}>
        <RightPanel />
      </BottomSheet>
    </section>
  );
}

function PhoneShell({ drawer, onDrawerChange }: { drawer: DrawerId | null; onDrawerChange: (drawer: DrawerId | null) => void }) {
  const layout = useDeviceLayout();
  const orientation = layout.width > layout.height ? "landscape" : "portrait";
  return (
    <section
      className="ed-phone-shell"
      data-testid="phone-shell"
      data-orientation={orientation}
      data-safe-area="true"
      style={{ height: "100dvh" }}
    >
      <div className="ed-phone-canvas" data-testid="phone-canvas">
        <Canvas />
      </div>
      <PhoneToolDock onOpenDrawer={onDrawerChange} sideRail={orientation === "landscape"} />
      <BottomSheet id="layers" title="Layers" placement="left" fullHeight open={drawer === "layers"} onOpenChange={(open) => onDrawerChange(open ? "layers" : null)}>
        <LayersPanel />
      </BottomSheet>
      <BottomSheet id="inspector" title="Inspector" placement="bottom" open={drawer === "inspector"} onOpenChange={(open) => onDrawerChange(open ? "inspector" : null)}>
        <RightPanel />
      </BottomSheet>
    </section>
  );
}

function CoverShell({ onPreview }: { onPreview: () => void }) {
  const [layersOpen, setLayersOpen] = useState(false);
  return (
    <section className="ed-cover-shell" data-testid="cover-shell">
      <div className="ed-cover-canvas">
        <Canvas />
      </div>
      <CoverQuickBar onOpenLayers={() => setLayersOpen(true)} onPreview={onPreview} />
      <BottomSheet id="layers" title="Layers" placement="bottom" fullHeight open={layersOpen} onOpenChange={setLayersOpen}>
        <LayersPanel compact />
      </BottomSheet>
    </section>
  );
}

function FoldShell() {
  const fold = useFoldSegments();
  const gap = Math.max(24, fold.gap);
  const reserve = gap - Math.max(0, fold.gap);
  const top = Math.max(0, fold.top - reserve / 2);
  const bottom = Math.max(0, fold.bottom - reserve / 2);
  const style = {
    "--fold-gap": `${gap}px`,
    "--fold-top": `${top}px`,
    "--fold-bottom": `${bottom}px`,
    gridTemplateRows: `minmax(0, ${top}px) ${gap}px minmax(0, ${bottom}px)`,
  } as CSSProperties;

  return (
    <section
      className="ed-fold-shell"
      data-testid="fold-shell"
      data-fold="vertical"
      data-fold-gap={gap}
      data-fold-top={top}
      data-fold-bottom={bottom}
      style={style}
    >
      <div className="ed-fold-canvas" data-testid="fold-canvas">
        <Canvas />
      </div>
      <div className="ed-fold-gutter" data-testid="fold-gutter" aria-hidden="true" />
      <FoldToolDeck />
    </section>
  );
}

export function EditorShell() {
  const store = useTwinStore();
  const layout = useDeviceLayout();
  const [paletteOpen, setPaletteOpen] = useState(false);
  const [drawer, setDrawer] = useState<DrawerId | null>(null);
  const presenting = store.state.presenting;

  useEffect(() => {
    setDrawer(null);
  }, [layout.mode]);

  useEditorHotkeys({
    enabled: !presenting && layout.mode !== "cover",
    onPreview: store.openPresentation,
    onPalette: () => setPaletteOpen(true),
    onEscape: () => {
      if (paletteOpen) setPaletteOpen(false);
      else if (presenting) store.closePresentation();
      else if (drawer) setDrawer(null);
      else store.clearSelection();
    },
  });

  let shell: React.ReactNode;
  if (layout.mode === "desktop") shell = <DesktopWorkspace />;
  else if (layout.mode === "tablet") shell = <TabletShell drawer={drawer} onDrawerChange={setDrawer} />;
  else if (layout.mode === "phone") shell = <PhoneShell drawer={drawer} onDrawerChange={setDrawer} />;
  else if (layout.mode === "cover") shell = <CoverShell onPreview={store.openPresentation} />;
  else shell = <FoldShell />;

  return (
    <>
      <main
        className="ed-shell"
        data-testid="editor-shell"
        data-mode={layout.mode}
        data-native-lens={store.state.nativeLens}
        data-previewing={presenting ? "true" : "false"}
        inert={presenting}
        aria-hidden={presenting || undefined}
      >
        {layout.mode !== "cover" ? <TopChips /> : null}
        <div className="ed-workspace">{shell}</div>
        {layout.mode === "desktop" || layout.mode === "tablet" ? (
          <>
            <StatusBar />
            <Toolbar />
          </>
        ) : null}
      </main>
      {presenting ? (
        <PreviewMode layout={store.state.layout} onLayoutChange={store.setLayout} onExit={store.closePresentation} />
      ) : null}
      {layout.mode !== "cover" ? <CommandPalette open={paletteOpen} onOpenChange={setPaletteOpen} /> : null}
      <PrintArtboard doc={store.state.doc} />
      {store.state.lastExport ? (
        <output
          hidden
          data-testid="png-export-result"
          data-width={store.state.lastExport.width}
          data-height={store.state.lastExport.height}
          data-bytes={store.state.lastExport.bytes}
          data-filename={store.state.lastExport.filename}
          data-signature={store.state.lastExport.signature}
        >
          PNG exported, {store.state.lastExport.width} by {store.state.lastExport.height}, {store.state.lastExport.bytes} bytes.
        </output>
      ) : null}
      {store.state.toast ? (
        <div className={`toast ed-toast ${store.state.toastExiting ? "is-leaving" : ""}`} role="status" aria-live="polite">
          {store.state.toast}
        </div>
      ) : null}
    </>
  );
}
