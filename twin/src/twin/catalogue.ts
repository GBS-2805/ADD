import type { TwinEffect, TwinPlugin } from "./types";

export function filterCatalogue(plugins: TwinPlugin[], query: string): TwinPlugin[] {
  const needle = query.trim().toLowerCase();
  if (!needle) return plugins;
  return plugins.filter((plugin) =>
    [plugin.name, plugin.blurb, plugin.group, ...plugin.tags]
      .join(" ")
      .toLowerCase()
      .includes(needle),
  );
}

export function sameEffect(a: TwinEffect | null, b: TwinEffect | null): boolean {
  if (a === null || b === null) return a === b;
  return JSON.stringify(a) === JSON.stringify(b);
}
