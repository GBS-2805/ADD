import { useEffect, useRef, useState } from "react";
import { Splitter, useSplitter } from "@ark-ui/react";
import { ChevronLeft, ChevronRight, PanelLeftClose, PanelLeftOpen, PanelRightClose, PanelRightOpen } from "lucide-react";
import { useTwinStore } from "../twin/store";
import { ArtboardView } from "./canvas/ArtboardView";
import { ARTBOARD_HEIGHT, ARTBOARD_WIDTH } from "./model/geometry";
import { LayersPanel } from "./panels/LayersPanel";
import { InspectorPanel } from "./panels/InspectorPanel";
import { Presentation } from "../shell/Presentation";
import { StatusBar } from "./StatusBar";
import { Toolbar } from "./Toolbar";
import { TopChips } from "./TopChips";
import { useDeviceLayout } from "./useDeviceLayout";
import type { DeviceMode } from "./types";
import "./editor.css";

function EditorCanvas() {
  const store = useTwinStore();
  const { doc } = store.state;
  const boardRef = useRef<HTMLDivElement>(null);
  const [scale, setScale] = useState(0.5);

  useEffect(() => {
    const board = boardRef.current;
    if (!board) return;
    const measure = () => {
      const rect = board.getBoundingClientRect();
      if (rect.width <= 0 || rect.height <= 0) return;
      setScale(Math.min((rect.width - 64) / ARTBOARD_WIDTH, (rect.height - 64) / ARTBOARD_HEIGHT, 1));
    };
    measure();
    const observer = typeof ResizeObserver === "function" ? new ResizeObserver(measure) : null;
    observer?.observe(board);
    window.addEventListener("resize", measure);
    return () => {
      observer?.disconnect();
      window.removeEventListener("resize", measure);
    };
  }, []);

  return (
    <div
      ref={boardRef}
      className="ed-canvas"
      data-testid="canvas-surface"
      role="region"
      aria-label="Artboard canvas"
      tabIndex={0}
    >
      <div className="ed-canvas-crosshair" aria-hidden="true">
        <i />
        <i />
      </div>
      <div
        className="ed-canvas-board"
        style={{ width: ARTBOARD_WIDTH * scale, height: ARTBOARD_HEIGHT * scale }}
        onClick={() => store.clearSelection()}
      >
        <div style={{ transform: `scale(${scale})`, transformOrigin: "top left" }}>
          <ArtboardView
            doc={doc}
            onLayerSelect={(id, event) => {
              const mode = event.shiftKey ? "add" : event.metaKey || event.ctrlKey ? "toggle" : "replace";
              store.selectLayers([id], mode);
            }}
            testId="editor-artboard"
          />
        </div>
      </div>
      <div className="ed-canvas-caption">
        <span>765 × 1000</span>
        <span>100%</span>
      </div>
    </div>
  );
}

function PanelHeader({
  id,
  label,
  collapsed,
  onToggle,
}: {
  id: "layers" | "inspector";
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
  const [collapsed, setCollapsed] = useState({ layers: false, inspector: false });
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

  const toggle = (id: "layers" | "inspector") => {
    setCollapsed((current) => ({ ...current, [id]: !current[id] }));
  };

  const sizes = fallbackSizes ?? splitter.getSizes();

  return (
    <Splitter.RootProvider
      value={splitter}
      className="ed-splitter"
      data-testid="editor-splitter"
      data-sizes={sizes.join(",")}
      data-tick={sizeTick}
    >
      <Splitter.Panel id="layers" className="ed-splitter-panel" data-testid="splitter-panel-layers" data-collapsed={collapsed.layers ? "true" : "false"}>
        <PanelHeader id="layers" label="Layers" collapsed={collapsed.layers} onToggle={() => toggle("layers")} />
        <div className="ed-panel-content">
          <LayersPanel collapsed={collapsed.layers} />
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
        <EditorCanvas />
      </Splitter.Panel>

      <Splitter.ResizeTrigger
        id="canvas:inspector"
        className="ed-resize-trigger"
        aria-label="Resize canvas and inspector"
        data-testid="resize-trigger-canvas-inspector"
      >
        <span className="ed-resize-grip" aria-hidden="true"><ChevronLeft size={10} /><ChevronRight size={10} /></span>
      </Splitter.ResizeTrigger>

      <Splitter.Panel id="inspector" className="ed-splitter-panel" data-testid="splitter-panel-inspector" data-collapsed={collapsed.inspector ? "true" : "false"}>
        <PanelHeader id="inspector" label="Inspector" collapsed={collapsed.inspector} onToggle={() => toggle("inspector")} />
        <div className="ed-panel-content" data-collapsed={collapsed.inspector}>
          <div className="ed-right-tabs" role="tablist" aria-label="Right panel">
            <button
              type="button"
              role="tab"
              aria-selected={store.state.rightTab === "design"}
              onClick={() => store.setRightTab("design")}
            >
              Design
            </button>
            <button
              type="button"
              role="tab"
              aria-selected={store.state.rightTab === "effects"}
              onClick={() => store.setRightTab("effects")}
            >
              Effects
            </button>
          </div>
          {store.state.rightTab === "design" ? (
            <InspectorPanel />
          ) : (
            <p className="ed-panel-empty">Effects panel opens from the tool pill.</p>
          )}
        </div>
      </Splitter.Panel>
    </Splitter.RootProvider>
  );
}

function ModePlaceholder({ mode }: { mode: Exclude<DeviceMode, "desktop"> }) {
  const labels: Record<Exclude<DeviceMode, "desktop">, string> = {
    tablet: "Tablet",
    phone: "Phone",
    cover: "Cover",
    fold: "Fold",
  };
  return (
    <section className="ed-mode-placeholder" data-testid="mode-placeholder" data-mode={mode}>
      <div className="ed-mode-copy">
        <span className="ed-eyebrow">{labels[mode]} workbench</span>
        <h2>{labels[mode]} layout</h2>
        <p>The canvas and device controls arrive in the next workstream. Layers are live now.</p>
      </div>
      <div className="ed-mode-layers">
        <LayersPanel />
      </div>
    </section>
  );
}

export function EditorShell() {
  const store = useTwinStore();
  const layout = useDeviceLayout();

  return (
    <>
      <main
        className="ed-shell"
        data-testid="editor-shell"
        data-mode={layout.mode}
        data-native-lens={store.state.nativeLens}
      >
        <TopChips />
        <div className="ed-workspace">
          {layout.mode === "desktop" ? <DesktopWorkspace /> : <ModePlaceholder mode={layout.mode} />}
        </div>
        <StatusBar />
        <Toolbar />
      </main>
      {store.state.presenting ? <Presentation /> : null}
    </>
  );
}
