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
 * The span starts at render and ends in this component's layout effect, which
 * React runs after its children's, so it covers the subtree's render and commit.
 */
export function useRenderSpan(id: string): void {
  const enabled = typeof window !== "undefined" && (window as PerfWindow).__perfProfile === true;
  const startedAt = enabled ? performance.now() : 0;
  useLayoutEffect(() => {
    if (!enabled) return;
    const state = window as PerfWindow;
    const atMs = performance.now();
    (state.__perfSpans ??= []).push({
      id,
      ms: Math.round((atMs - startedAt) * 10) / 10,
      atMs: Math.round(atMs * 10) / 10,
    });
  });
}
