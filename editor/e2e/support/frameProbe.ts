import type { Page } from "@playwright/test";

export interface SlowFrame {
  /** Absolute `performance.now()` when the interval closed. */
  atMs: number;
  ms: number;
}

export interface FrameStats {
  count: number;
  p95Ms: number;
  maxMs: number;
  /** Intervals above 25 ms, so a max at a vsync multiple can be located in time. */
  slowFrames: SlowFrame[];
}

interface ProbeState {
  frames: Array<{ at: number; ms: number }>;
  last: number;
  running: boolean;
  /** Intervals discarded before summarising; the drag uses three, the open case none. */
  warmup: number;
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

export async function startFrameProbe(page: Page, warmup = 3): Promise<void> {
  await page.evaluate((warmupCount) => {
    const target = window as unknown as ProbeWindow;
    const state: ProbeState = { frames: [], last: performance.now(), running: true, warmup: warmupCount };
    target.__frameProbe = state;
    const tick = (now: number) => {
      if (!state.running) return;
      state.frames.push({ at: now, ms: now - state.last });
      state.last = now;
      requestAnimationFrame(tick);
    };
    requestAnimationFrame(tick);
  }, warmup);
}

/** Stops sampling, discards the requested warm-up intervals and summarizes the rest. */
export async function stopFrameProbe(page: Page): Promise<FrameStats> {
  return page.evaluate(() => {
    const target = window as unknown as ProbeWindow;
    const state = target.__frameProbe;
    if (!state) return { count: 0, p95Ms: 0, maxMs: 0, slowFrames: [] };
    state.running = false;

    const frames = state.frames
      .slice(state.warmup)
      .filter((frame) => Number.isFinite(frame.ms) && frame.ms >= 0);
    const sorted = frames.map((frame) => frame.ms).sort((a, b) => a - b);
    const p95Ms = sorted.length
      ? sorted[Math.max(0, Math.ceil(sorted.length * 0.95) - 1)]
      : 0;
    const maxMs = sorted.length ? sorted[sorted.length - 1] : 0;
    const slowFrames = frames
      .filter((frame) => frame.ms > 25)
      .map((frame) => ({ atMs: Math.round(frame.at * 10) / 10, ms: Math.round(frame.ms * 10) / 10 }));
    return { count: frames.length, p95Ms, maxMs, slowFrames };
  });
}
