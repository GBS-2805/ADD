import { useEffect, useRef } from "react";
import { SegmentGroup } from "@ark-ui/react";
import { ArrowLeft } from "lucide-react";
import { ArtboardView } from "./canvas/ArtboardView";
import { DeviceFrame } from "../shell/DeviceFrame";
import { useTwinStore } from "../twin/store";
import type { Layout } from "../twin/types";
import { useDeviceLayout } from "./useDeviceLayout";

const LAYOUTS: { value: Layout; label: string; note: string }[] = [
  { value: "compact", label: "Compact 411", note: "411 × 891" },
  { value: "expanded", label: "Expanded 840", note: "840 × 1000" },
];

export interface PreviewModeProps {
  layout: Layout;
  onLayoutChange: (layout: Layout) => void;
  onExit: () => void;
}

export function PreviewMode({ layout, onLayoutChange, onExit }: PreviewModeProps) {
  const store = useTwinStore();
  const viewport = useDeviceLayout();
  const exitRef = useRef<HTMLButtonElement>(null);
  const returnFocus = useRef<HTMLElement | null>(null);

  useEffect(() => {
    returnFocus.current = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    const frame = window.requestAnimationFrame(() => exitRef.current?.focus());
    return () => {
      window.cancelAnimationFrame(frame);
      const target = returnFocus.current;
      window.requestAnimationFrame(() => {
        if (target?.isConnected) target.focus();
      });
    };
  }, []);

  const dimensions = layout === "compact" ? { width: 411, height: 891 } : { width: 840, height: 1000 };
  const innerWidth = dimensions.width - 18;
  const innerHeight = dimensions.height - 18;
  const fit = Math.min((innerWidth - 28) / 765, (innerHeight - 28) / 1000);
  const scale = Math.min(1, (viewport.width - 68) / dimensions.width, (viewport.height - 176) / dimensions.height);

  return (
    <section
      className="ed-preview-mode"
      data-testid="preview-mode"
      data-layout={layout}
      role="dialog"
      aria-modal="true"
      aria-label="Preview"
      onKeyDown={(event) => {
        if (event.key !== "Escape") return;
        event.preventDefault();
        onExit();
      }}
    >
      <div className="ed-preview-toolbar">
        <button ref={exitRef} type="button" className="ed-preview-exit" aria-label="Exit preview" onClick={onExit}>
          <ArrowLeft size={15} strokeWidth={1.7} /> Exit preview
        </button>
        <SegmentGroup.Root
          className="ed-preview-segments"
          value={layout}
          orientation="horizontal"
          onValueChange={({ value }) => {
            if (value) onLayoutChange(value as Layout);
          }}
          aria-label="Preview layout"
        >
          {LAYOUTS.map((item) => (
            <SegmentGroup.Item key={item.value} value={item.value} className="ed-preview-segment" title={item.note}>
              <SegmentGroup.ItemControl className="ed-preview-segment-control">
                <SegmentGroup.ItemText>{item.label}</SegmentGroup.ItemText>
              </SegmentGroup.ItemControl>
              <SegmentGroup.ItemHiddenInput />
            </SegmentGroup.Item>
          ))}
        </SegmentGroup.Root>
      </div>
      <div className="ed-preview-stage">
        <div className="ed-preview-device" style={{ width: dimensions.width * scale, height: dimensions.height * scale }}>
          <div style={{ transform: `scale(${scale})`, transformOrigin: "top left" }}>
            <DeviceFrame layout={layout}>
              <div className="ed-preview-artboard-fit" style={{ width: 765 * fit, height: 1000 * fit }}>
                <div style={{ transform: `scale(${fit})`, transformOrigin: "top left" }}>
                  <ArtboardView doc={store.state.doc} readOnly exposeLayerIds={false} testId="preview-artboard" />
                </div>
              </div>
            </DeviceFrame>
          </div>
        </div>
      </div>
      <p className="ed-preview-hint">
        <span>{store.state.doc.layers.length} layers</span>
        <span>Read-only · Esc to return</span>
      </p>
    </section>
  );
}
