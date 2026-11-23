import type { Page } from "@playwright/test";

export interface FrameStats {
  count: number;
  p95Ms: number;
  maxMs: number;
}

interface ProbeState {
  frames: number[];
  last: number;
  running: boolean;
}

type ProbeWindow = Window & { __frameProbe?: ProbeState };

/**
 * Throttle the renderer to a phone-class CPU budget. The returned function
 * restores the normal rate and detaches the CDP session; call it in `finally`.
 */
export async function throttleCpu(page: Page, rate = 4): Promise<() => Promise<void>> {
  const session = await page.context().newCDPSession(page);
  await session.send("Emulation.setCPUThrottlingRate", { rate });
  return async () => {
    await session.send("Emulation.setCPUThrottlingRate", { rate: 1 });
    await session.detach();
  };
}

export async function startFrameProbe(page: Page): Promise<void> {
  await page.evaluate(() => {
    const target = window as unknown as ProbeWindow;
    const state: ProbeState = { frames: [], last: performance.now(), running: true };
    target.__frameProbe = state;
    const tick = (now: number) => {
      if (!state.running) return;
      state.frames.push(now - state.last);
      state.last = now;
      requestAnimationFrame(tick);
    };
    requestAnimationFrame(tick);
  });
}

/** Stops sampling, discards the first three warm-up intervals and summarizes the rest. */
export async function stopFrameProbe(page: Page): Promise<FrameStats> {
  return page.evaluate(() => {
    const target = window as unknown as ProbeWindow;
    const state = target.__frameProbe;
    if (!state) return { count: 0, p95Ms: 0, maxMs: 0 };
    state.running = false;

    const frames = state.frames
      .slice(3)
      .filter((frame) => Number.isFinite(frame) && frame >= 0);
    const sorted = [...frames].sort((a, b) => a - b);
    const p95Ms = sorted.length
      ? sorted[Math.max(0, Math.ceil(sorted.length * 0.95) - 1)]
      : 0;
    const maxMs = frames.reduce((max, frame) => Math.max(max, frame), 0);
    return { count: frames.length, p95Ms, maxMs };
  });
}
