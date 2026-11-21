export interface RulerTick {
  doc: number;
  screen: number;
  major: boolean;
  label?: string;
}

const TARGET_MAJOR_PX = 72;

function niceStep(raw: number): number {
  if (!Number.isFinite(raw) || raw <= 0) return 100;
  const power = 10 ** Math.floor(Math.log10(raw));
  const normalized = raw / power;
  const multiple = normalized <= 1 ? 1 : normalized <= 2 ? 2 : normalized <= 5 ? 5 : 10;
  return multiple * power;
}

const formatLabel = (doc: number): string => {
  const rounded = Math.round(doc * 100) / 100;
  if (Number.isInteger(rounded)) return String(rounded);
  return String(rounded).replace(/\.?0+$/, "");
};

export function rulerTicks(zoom: number, originScreen: number, length: number): RulerTick[] {
  if (!Number.isFinite(zoom) || zoom <= 0 || length <= 0) return [];
  const majorStep = niceStep(TARGET_MAJOR_PX / zoom);
  const minorStep = majorStep / 5;
  const start = (0 - originScreen) / zoom;
  const end = (length - originScreen) / zoom;
  const first = Math.ceil(start / minorStep) * minorStep;
  const ticks: RulerTick[] = [];
  for (let index = 0; ; index += 1) {
    const doc = Math.round((first + index * minorStep) * 1e6) / 1e6;
    if (doc > end + 1e-6) break;
    const major = Math.abs(doc / majorStep - Math.round(doc / majorStep)) < 1e-6;
    ticks.push({
      doc,
      screen: originScreen + doc * zoom,
      major,
      ...(major ? { label: formatLabel(doc) } : {}),
    });
    if (ticks.length > 5000) break;
  }
  return ticks;
}
