import { useLayoutEffect } from "react";

export interface RenderSpan {
  id: string;
  ms: number;
  atMs: number;
}

interface PerfWindow extends Window {
  __perfProfile?: boolean;
  __perfSpans?: RenderSpan[];
  __shaderLossWatched?: boolean;
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

/**
 * Names a forced WebGL context loss in the perf artifact. `@paper-design/shaders`
 * disposes a mount by calling `WEBGL_lose_context.loseContext()`, and a document
 * swap that unmounts a shader host runs that inside the open. Only installed when
 * the page enabled the profile, so a normal session never patches the prototype.
 */
export function watchShaderContextLoss(): void {
  if (typeof window === "undefined") return;
  const state = window as PerfWindow;
  if (state.__perfProfile !== true || state.__shaderLossWatched) return;
  const contexts = [window.WebGLRenderingContext, window.WebGL2RenderingContext].filter(
    (context): context is typeof WebGLRenderingContext => Boolean(context),
  );
  if (contexts.length === 0) return;
  state.__shaderLossWatched = true;
  for (const context of contexts) {
    const proto = context.prototype as unknown as {
      getExtension: (this: WebGLRenderingContext, name: string) => unknown;
    };
    const getExtension = proto.getExtension;
    proto.getExtension = function (this: WebGLRenderingContext, name: string) {
      const extension = getExtension.call(this, name) as
        | { loseContext?: () => void; __perfSpan?: boolean }
        | null
        | undefined;
      if (
        name === "WEBGL_lose_context" &&
        extension &&
        typeof extension.loseContext === "function" &&
        !extension.__perfSpan
      ) {
        const loseContext = extension.loseContext.bind(extension);
        extension.loseContext = () => {
          const startedAt = performance.now();
          try {
            loseContext();
          } finally {
            recordPerfSpan("shader-context-loss", startedAt);
          }
        };
        extension.__perfSpan = true;
      }
      return extension;
    };
  }
}

export function useRenderSpan(id: string): void {
  const enabled = typeof window !== "undefined" && (window as PerfWindow).__perfProfile === true;
  const startedAt = enabled ? performance.now() : 0;
  useLayoutEffect(() => {
    if (!enabled) return;
    recordPerfSpan(id, startedAt);
  });
}
