import { useEffect, useRef, useState } from "react";
import { ArrowLeft } from "lucide-react";
import { useTwinStore } from "../twin/store";
import { ARTBOARD_HEIGHT, ARTBOARD_WIDTH } from "../editor/model/geometry";
import { ArtboardView } from "../editor/canvas/ArtboardView";

export function Presentation() {
  const store = useTwinStore();
  const closeRef = useRef<HTMLButtonElement>(null);
  const stageRef = useRef<HTMLDivElement>(null);
  const returnFocus = useRef<HTMLElement | null>(null);
  const [fit, setFit] = useState(1);
  const { doc } = store.state;

  useEffect(() => {
    const stage = stageRef.current;
    if (!stage) return;
    const measure = () => {
      const rect = stage.getBoundingClientRect();
      if (rect.width <= 0 || rect.height <= 0) return;
      setFit(Math.min((rect.width - 36) / ARTBOARD_WIDTH, (rect.height - 36) / ARTBOARD_HEIGHT, 1));
    };
    measure();
    const observer = typeof ResizeObserver === "function" ? new ResizeObserver(measure) : null;
    observer?.observe(stage);
    window.addEventListener("resize", measure);
    return () => {
      observer?.disconnect();
      window.removeEventListener("resize", measure);
    };
  }, []);

  useEffect(() => {
    returnFocus.current = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    const frame = window.requestAnimationFrame(() => closeRef.current?.focus());
    return () => {
      window.cancelAnimationFrame(frame);
      const target = returnFocus.current;
      window.requestAnimationFrame(() => {
        if (target?.isConnected) target.focus();
        else document.querySelector<HTMLElement>(".dock-target")?.focus();
      });
    };
  }, []);

  return (
    <section
      className="presentation"
      data-testid="presentation"
      role="dialog"
      aria-modal="true"
      aria-label="Stage presentation"
      onKeyDown={(event) => {
        if (event.key === "Escape") {
          event.preventDefault();
          store.closePresentation();
          return;
        }
        if (event.key === "Tab") {
          event.preventDefault();
          closeRef.current?.focus();
        }
      }}
    >
      <header className="presentation-bar">
        <div>
          <span className="presentation-kicker">BMO / OPEN STUDIO</span>
          <strong>Stage</strong>
        </div>
        <button
          ref={closeRef}
          type="button"
          className="presentation-close"
          onClick={store.closePresentation}
          aria-label="Close presentation"
        >
          <ArrowLeft size={14} strokeWidth={1.8} />
          Close
        </button>
      </header>
      <div className="presentation-stage" ref={stageRef}>
        <div
          className="presentation-artboard-fit"
          style={{ width: ARTBOARD_WIDTH * fit, height: ARTBOARD_HEIGHT * fit }}
        >
          <div style={{ transform: `scale(${fit})`, transformOrigin: "top left" }}>
            <ArtboardView doc={doc} readOnly testId="presentation-artboard" />
          </div>
        </div>
      </div>
      <p className="presentation-hint">
        <span>{doc.layers.length} layers</span>
        <span>Esc to return</span>
      </p>
    </section>
  );
}
