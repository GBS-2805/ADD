import { useEffect, useState } from "react";

export interface VisualViewportState {
  height: number;
  offsetTop: number;
  keyboardOffset: number;
}

function readViewport(): VisualViewportState {
  const viewport = typeof window === "undefined" ? undefined : window.visualViewport;
  const height = viewport?.height ?? (typeof window === "undefined" ? 0 : window.innerHeight);
  const offsetTop = viewport?.offsetTop ?? 0;
  return {
    height,
    offsetTop,
    keyboardOffset: Math.max(0, (typeof window === "undefined" ? 0 : window.innerHeight) - height - offsetTop),
  };
}

export function useVisualViewport(): VisualViewportState {
  const [state, setState] = useState(readViewport);

  useEffect(() => {
    const update = () => setState(readViewport());
    update();
    const viewport = window.visualViewport;
    viewport?.addEventListener("resize", update);
    viewport?.addEventListener("scroll", update);
    window.addEventListener("resize", update);
    return () => {
      viewport?.removeEventListener("resize", update);
      viewport?.removeEventListener("scroll", update);
      window.removeEventListener("resize", update);
    };
  }, []);

  return state;
}
