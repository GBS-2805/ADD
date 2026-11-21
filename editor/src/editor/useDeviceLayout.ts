import { useEffect, useState } from "react";
import type { DeviceMode } from "./types";

export interface DeviceLayout {
  mode: DeviceMode;
  isCoarse: boolean;
  width: number;
  height: number;
}

interface ViewportSegment {
  x: number;
  y: number;
  width: number;
  height: number;
}

interface ViewportWithSegments {
  segments?: ViewportSegment[];
}

function hasFoldSegments(): boolean {
  const viewport = (window as Window & { viewport?: ViewportWithSegments }).viewport;
  return Array.isArray(viewport?.segments) && viewport.segments.length >= 2;
}

export function readDeviceLayout(): DeviceLayout {
  const width = window.innerWidth || 0;
  const height = window.innerHeight || 0;
  const isCoarse = window.matchMedia?.("(pointer: coarse)").matches ?? false;
  let mode: DeviceMode;
  if (hasFoldSegments()) mode = "fold";
  else if (width <= 380 && height <= 380) mode = "cover";
  else if (width > height && height < 500) mode = "phone";
  else if (width < 600) mode = "phone";
  else if (width < 1024) mode = "tablet";
  else mode = "desktop";
  return { mode, isCoarse, width, height };
}

export function useDeviceLayout(): DeviceLayout {
  const [layout, setLayout] = useState<DeviceLayout>(readDeviceLayout);

  useEffect(() => {
    const update = () => setLayout(readDeviceLayout());
    window.addEventListener("resize", update);
    window.addEventListener("orientationchange", update);
    return () => {
      window.removeEventListener("resize", update);
      window.removeEventListener("orientationchange", update);
    };
  }, []);

  return layout;
}
