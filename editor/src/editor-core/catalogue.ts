import type { EditorEffect, EditorPlugin } from "./types";

export function filterCatalogue(plugins: EditorPlugin[], query: string): EditorPlugin[] {
  const needle = query.trim().toLowerCase();
  if (!needle) return plugins;
  return plugins.filter((plugin) =>
    [plugin.name, plugin.blurb, plugin.group, ...plugin.tags]
      .join(" ")
      .toLowerCase()
      .includes(needle),
  );
}

export function sameEffect(a: EditorEffect | null, b: EditorEffect | null): boolean {
  if (a === null || b === null) return a === b;
  return JSON.stringify(a) === JSON.stringify(b);
}
