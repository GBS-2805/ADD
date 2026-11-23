import { useLayoutEffect } from "react";

export interface RenderSpan {
  id: string;
  ms: number;
  atMs: number;
}

interface PerfWindow extends Window {
  __perfProfile?: boolean;
  __perfSpans?: RenderSpan[];
}

/**
 * Test-only timing for the production perf run. The page enables it with
 * `window.__perfProfile = true` before load; without that flag this costs one
 * boolean check and a no-op layout effect per render.
 *
 * A span can cover synchronous work outside React (autosave serialization,
 * project parse/apply) or a component's render through its layout effect. React
 * finishes the whole render phase before running any layout effect, so a
 * component span can also include render work from components that render after
 * it; it is comparable across panels on the same commit, not an isolated subtree
 * time.
 */
export function recordPerfSpan(id: string, startedAt: number): void {
  if (typeof window === "undefined") return;
  const state = window as PerfWindow;
  if (state.__perfProfile !== true) return;
  const atMs = performance.now();
  (state.__perfSpans ??= []).push({
    id,
    ms: Math.round((atMs - startedAt) * 10) / 10,
    atMs: Math.round(atMs * 10) / 10,
  });
}

export function useRenderSpan(id: string): void {
  const enabled = typeof window !== "undefined" && (window as PerfWindow).__perfProfile === true;
  const startedAt = enabled ? performance.now() : 0;
  useLayoutEffect(() => {
    if (!enabled) return;
    recordPerfSpan(id, startedAt);
  });
}
