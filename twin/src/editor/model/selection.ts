export type SelectionMode = "replace" | "add" | "toggle";

/** Pure selection reducer used by the canvas and the layer tree. */
export function nextSelection(
  current: readonly string[],
  id: string,
  mode: SelectionMode = "replace",
): string[] {
  if (!id) return mode === "replace" ? [] : [...current];
  if (mode === "replace") return [id];
  if (mode === "add") return current.includes(id) ? [...current] : [...current, id];
  return current.includes(id) ? current.filter((item) => item !== id) : [...current, id];
}

export function pruneSelection(ids: readonly string[], layers: readonly { id: string }[]): string[] {
  const available = new Set(layers.map((layer) => layer.id));
  const seen = new Set<string>();
  const next: string[] = [];
  for (const id of ids) {
    if (!available.has(id) || seen.has(id)) continue;
    seen.add(id);
    next.push(id);
  }
  return next;
}

export function selectAllIds(layers: readonly { id: string }[]): string[] {
  return layers.map((layer) => layer.id);
}
