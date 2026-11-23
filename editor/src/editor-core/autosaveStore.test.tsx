import { act, render } from "@testing-library/react";
import { useEffect } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const pngMocks = vi.hoisted(() => ({ renderArtboardPng: vi.fn() }));
vi.mock("./pngExport", () => ({ renderArtboardPng: pngMocks.renderArtboardPng }));

import { AUTOSAVE_DELAY_MS, createMemoryBackend, type AutosaveBackend } from "./autosave";
import { EditorProvider, useEditorDocument, useEditorStore, type EditorActions, type EditorDocumentState } from "./store";

let current: EditorActions | null = null;
let currentDocument: EditorDocumentState | null = null;

function Bridge() {
  const store = useEditorStore();
  const documentState = useEditorDocument();
  useEffect(() => {
    current = store;
    currentDocument = documentState;
  }, [store, documentState]);
  return null;
}

/** Mounts a provider over `backend` and returns the live actions. */
function mount(backend: AutosaveBackend | null): EditorActions {
  current = null;
  currentDocument = null;
  const view = render(
    <EditorProvider autosaveBackend={backend}>
      <Bridge />
    </EditorProvider>,
  );
  return Object.assign(current as unknown as EditorActions, { unmount: view.unmount });
}

/**
 * Drains pending microtasks. The restore is an async chain of awaits, so one
 * tick is not enough; looping until it stops changing is what a browser would do
 * between frames.
 */
async function flush(rounds = 12) {
  await act(async () => {
    for (let i = 0; i < rounds; i += 1) await Promise.resolve();
  });
}

/** Lets the restore read settle, then the autosave debounce fire. */
async function settle() {
  await flush();
  await act(async () => {
    vi.advanceTimersByTime(AUTOSAVE_DELAY_MS + 1);
  });
  await flush();
}

describe("autosave", () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });
  afterEach(() => {
    vi.useRealTimers();
  });

  it("keeps an edit across a remount", async () => {
    const backend = createMemoryBackend();
    const first = mount(backend);
    await flush();
    const before = currentDocument!.doc.layers.length;

    act(() => first.selectAllLayers());
    act(() => first.removeSelectedLayers());
    await settle();
    expect(current!.state.autosave.status).toBe("saved");
    expect(backend.writes.length).toBeGreaterThan(0);
    (first as unknown as { unmount: () => void }).unmount();

    mount(backend);
    await flush();
    // The empty document survives the reload: the removed layers stay removed.
    expect(currentDocument!.doc.layers).toHaveLength(0);
    expect(current!.state.autosave.restoredLayers).toBe(0);
    expect(before).toBeGreaterThan(0);
  });

  it("claims Saved on a first visit only after a real write", async () => {
    const backend = createMemoryBackend();
    mount(backend);
    await flush();
    // Nothing had been stored, so the starter document is written once; before
    // that write the line must not say "Saved".
    expect(backend.writes.length).toBe(0);
    expect(current!.state.autosave.status).not.toBe("saved");
    await settle();
    expect(backend.writes.length).toBe(1);
    expect(current!.state.autosave.status).toBe("saved");
  });

  it("does not rewrite storage for a selection-only change", async () => {
    const backend = createMemoryBackend();
    const store = mount(backend);
    await settle();
    const writes = backend.writes.length;
    expect(writes).toBe(1);

    act(() => store.selectAllLayers());
    await settle();
    expect(backend.writes.length).toBe(writes);

    act(() => store.removeSelectedLayers());
    await settle();
    expect(backend.writes.length).toBeGreaterThan(writes);
  });

  it("does not let the starter document overwrite a stored one", async () => {
    const backend = createMemoryBackend();
    const first = mount(backend);
    await flush();
    act(() => first.selectAllLayers());
    act(() => first.removeSelectedLayers());
    await settle();
    (first as unknown as { unmount: () => void }).unmount();

    const writes = backend.writes.length;
    mount(backend);
    // Before the read resolves, the provider holds the starter document; it must
    // not be written, or a fast reload would erase what was stored.
    expect(backend.writes.length).toBe(writes);
  });

  it("reports storage as off, and still edits, when the browser has none", async () => {
    const store = mount(null);
    await flush();
    expect(current!.state.autosave.status).toBe("off");
    act(() => store.selectAllLayers());
    act(() => store.removeSelectedLayers());
    await settle();
    expect(currentDocument!.doc.layers).toHaveLength(0);
    expect(current!.state.autosave.status).toBe("off");
  });

  it("survives a store that refuses every write", async () => {
    const failing: AutosaveBackend = {
      async read() {
        return null;
      },
      async write() {
        throw new Error("QuotaExceededError");
      },
      async readAsset() {
        return null;
      },
      async writeAsset() {},
      async pruneAssets() {},
      async clear() {},
    };
    const store = mount(failing);
    await flush();
    act(() => store.selectAllLayers());
    act(() => store.removeSelectedLayers());
    await settle();
    // Editing continues and the status says honestly that nothing was kept.
    expect(currentDocument!.doc.layers).toHaveLength(0);
    expect(current!.state.autosave.status).toBe("off");
  });
});
