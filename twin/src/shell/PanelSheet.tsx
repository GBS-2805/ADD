import { useRef, useState, type CSSProperties, type PointerEvent as ReactPointerEvent } from "react";
import { X } from "lucide-react";
import type { ReactNode } from "react";
import { useTwinStore } from "../twin/store";

export function PanelSheet({ children }: { children: ReactNode }) {
  const store = useTwinStore();
  const { surface, layout, presenting } = store.state;
  const open = surface !== "none";
  const title = surface === "inspector" ? "Inspector" : surface === "commands" ? "Actions" : "Plugin shelf";
  const [dragY, setDragY] = useState(0);
  const [dragging, setDragging] = useState(false);
  const dragRef = useRef<{
    pointerId: number;
    startY: number;
    lastY: number;
    panelHeight: number;
  } | null>(null);

  const beginPanelDrag = (event: ReactPointerEvent<HTMLDivElement>) => {
    if (!open || presenting) return;
    if (window.matchMedia("(orientation: landscape) and (max-height: 500px)").matches) return;
    event.preventDefault();
    const panel = event.currentTarget.closest<HTMLElement>(".panel-sheet");
    dragRef.current = {
      pointerId: event.pointerId,
      startY: event.clientY,
      lastY: event.clientY,
      panelHeight: panel?.getBoundingClientRect().height ?? 0,
    };
    event.currentTarget.setPointerCapture(event.pointerId);
    setDragging(true);
  };

  const movePanelDrag = (event: ReactPointerEvent<HTMLDivElement>) => {
    const drag = dragRef.current;
    if (!drag || drag.pointerId !== event.pointerId) return;
    const nextY = Math.max(0, event.clientY - drag.startY);
    drag.lastY = nextY;
    setDragY(nextY);
  };

  const finishPanelDrag = (event: ReactPointerEvent<HTMLDivElement>, cancelled = false) => {
    const drag = dragRef.current;
    if (!drag || drag.pointerId !== event.pointerId) return;
    const threshold = Math.min(88, drag.panelHeight * 0.24);
    const shouldClose = !cancelled && drag.lastY > threshold;
    dragRef.current = null;
    setDragging(false);
    setDragY(0);
    if (event.currentTarget.hasPointerCapture(event.pointerId)) {
      event.currentTarget.releasePointerCapture(event.pointerId);
    }
    if (shouldClose) store.closeSurface();
  };

  return (
    <section
      className={`panel-sheet ${open ? "is-open" : ""}`}
      data-surface={surface}
      data-layout={layout}
      data-dragging={dragging ? "true" : undefined}
      aria-hidden={!open || presenting}
      inert={!open || presenting}
      data-testid="panel-sheet"
      style={{ "--sheet-drag": `${dragY}px` } as CSSProperties}
    >
      <div
        className="panel-grabber"
        data-testid="panel-grabber"
        aria-hidden="true"
        onPointerDown={beginPanelDrag}
        onPointerMove={movePanelDrag}
        onPointerUp={finishPanelDrag}
        onPointerCancel={(event) => finishPanelDrag(event, true)}
      >
        <i />
      </div>
      <header className="panel-bar">
        <span>{title}</span>
        <button type="button" className="icon-button" onClick={store.closeSurface} aria-label="Close panel">
          <X size={17} />
        </button>
      </header>
      <div className="panel-content">{children}</div>
    </section>
  );
}
