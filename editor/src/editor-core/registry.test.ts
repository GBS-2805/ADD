import { describe, expect, it } from "vitest";
import { getPlugin, PLUGINS, preloadPlugin } from "./registry";

describe("plugin registry", () => {
  it("resolves every catalogued id and nothing else", () => {
    expect(PLUGINS).toHaveLength(8);
    for (const plugin of PLUGINS) {
      expect(getPlugin(plugin.id)).toBe(plugin);
      // A lazy panel is still a component the host can render.
      expect(plugin.panel).toBeTypeOf("object");
    }
    expect(getPlugin("not-a-plugin")).toBeUndefined();
    expect(getPlugin(null)).toBeUndefined();
  });

  it("preloads the module a known panel renders, once per import", async () => {
    const first = preloadPlugin("native-parity");
    const second = preloadPlugin("native-parity");
    expect(first).toBeInstanceOf(Promise);
    // The dynamic import is cached, so both preloads settle on the same module.
    expect(await first).toBe(await second);
    expect(await first).toMatchObject({ default: expect.any(Function) });
    expect(preloadPlugin("not-a-plugin")).toBeUndefined();
  });
});
