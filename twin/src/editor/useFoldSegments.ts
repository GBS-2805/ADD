import { useEffect, useState } from "react";
import type { FoldInsets } from "./types";

interface ViewportSegment {
  x: number;
  y: number;
  width: number;
  height: number;
}

interface ViewportWithSegments {
  segments?: ViewportSegment[];
}

interface NavigatorWithPosture extends Navigator {
  devicePosture?: { type?: string };
}

export function readFoldInsets(): FoldInsets {
  const viewport = (window as Window & { viewport?: ViewportWithSegments }).viewport;
  const segments = viewport?.segments;
  if (Array.isArray(segments) && segments.length >= 2) {
    const sorted = [...segments].sort((a, b) => a.y - b.y);
    const [first, second] = sorted;
    return {
      orientation: "vertical",
      top: first.height,
      bottom: second.height,
      gap: Math.max(0, second.y - (first.y + first.height)),
      source: "segments",
    };
  }
  const posture = (navigator as NavigatorWithPosture).devicePosture;
  if (posture?.type === "folded") {
    return { orientation: null, top: 0, bottom: 0, gap: 0, source: "posture" };
  }
  return { orientation: null, top: 0, bottom: 0, gap: 0 };
}

const sameInsets = (a: FoldInsets, b: FoldInsets) =>
  a.orientation === b.orientation && a.top === b.top && a.bottom === b.bottom && a.gap === b.gap && a.source === b.source;

export function useFoldSegments(): FoldInsets {
  const [insets, setInsets] = useState<FoldInsets>(readFoldInsets);

  useEffect(() => {
    const update = () => {
      const next = readFoldInsets();
      setInsets((current) => (sameInsets(current, next) ? current : next));
    };
    update();
    window.addEventListener("resize", update);
    window.addEventListener("orientationchange", update);
    return () => {
      window.removeEventListener("resize", update);
      window.removeEventListener("orientationchange", update);
    };
  }, []);

  return insets;
}
