import { useEffect, useRef } from "react";
import { rulerTicks } from "../model/ruler";
import { docToScreen } from "../model/viewport";
import type { Viewport } from "../types";

export interface CanvasRulersProps {
  viewport: Viewport;
  width: number;
  height: number;
}

const RULER_SIZE = 22;
const TICK_COLOR = "rgba(233, 224, 203, 0.46)";
const LABEL_COLOR = "rgba(233, 224, 203, 0.62)";

function setupCanvas(canvas: HTMLCanvasElement, width: number, height: number): CanvasRenderingContext2D | null {
  const ratio = window.devicePixelRatio || 1;
  canvas.width = Math.max(1, Math.round(width * ratio));
  canvas.height = Math.max(1, Math.round(height * ratio));
  canvas.style.width = `${width}px`;
  canvas.style.height = `${height}px`;
  let context: CanvasRenderingContext2D | null = null;
  try {
    context = canvas.getContext("2d");
  } catch {
    return null;
  }
  if (!context) return null;
  context.setTransform(ratio, 0, 0, ratio, 0, 0);
  context.clearRect(0, 0, width, height);
  context.font = '10px "IBM Plex Mono", monospace';
  context.textBaseline = "top";
  return context;
}

export function CanvasRulers({ viewport, width, height }: CanvasRulersProps) {
  const xRef = useRef<HTMLCanvasElement>(null);
  const yRef = useRef<HTMLCanvasElement>(null);
  const size = { width, height };
  const originX = docToScreen({ x: 0, y: 0 }, viewport, size).x;
  const originY = docToScreen({ x: 0, y: 0 }, viewport, size).y;

  useEffect(() => {
    const canvas = xRef.current;
    if (!canvas) return;
    const context = setupCanvas(canvas, width, RULER_SIZE);
    if (!context) return;
    for (const tick of rulerTicks(viewport.zoom, originX, width)) {
      if (tick.screen < 0 || tick.screen > width) continue;
      context.strokeStyle = TICK_COLOR;
      context.beginPath();
      context.moveTo(Math.round(tick.screen) + 0.5, RULER_SIZE);
      context.lineTo(Math.round(tick.screen) + 0.5, tick.major ? 8 : 15);
      context.stroke();
      if (tick.major && tick.label) {
        context.fillStyle = LABEL_COLOR;
        context.fillText(tick.label, tick.screen + 3, 3);
      }
    }
    context.strokeStyle = "#dd4124";
    context.beginPath();
    context.moveTo(Math.round(originX) + 0.5, 0);
    context.lineTo(Math.round(originX) + 0.5, RULER_SIZE);
    context.stroke();
  }, [height, originX, viewport.zoom, width]);

  useEffect(() => {
    const canvas = yRef.current;
    if (!canvas) return;
    const context = setupCanvas(canvas, RULER_SIZE, height);
    if (!context) return;
    for (const tick of rulerTicks(viewport.zoom, originY, height)) {
      if (tick.screen < 0 || tick.screen > height) continue;
      context.strokeStyle = TICK_COLOR;
      context.beginPath();
      context.moveTo(RULER_SIZE, Math.round(tick.screen) + 0.5);
      context.lineTo(tick.major ? 8 : 15, Math.round(tick.screen) + 0.5);
      context.stroke();
      if (tick.major && tick.label) {
        context.save();
        context.translate(3, tick.screen + 3);
        context.rotate(-Math.PI / 2);
        context.fillStyle = LABEL_COLOR;
        context.fillText(tick.label, 0, 0);
        context.restore();
      }
    }
    context.strokeStyle = "#dd4124";
    context.beginPath();
    context.moveTo(0, Math.round(originY) + 0.5);
    context.lineTo(RULER_SIZE, Math.round(originY) + 0.5);
    context.stroke();
  }, [height, originY, viewport.zoom, width]);

  return (
    <>
      <canvas ref={xRef} className="ed-ruler ed-ruler-x" data-testid="ruler-x" aria-hidden="true" />
      <canvas ref={yRef} className="ed-ruler ed-ruler-y" data-testid="ruler-y" aria-hidden="true" />
    </>
  );
}
