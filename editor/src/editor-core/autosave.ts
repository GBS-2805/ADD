import { validateDocument } from "./project";
import type { EditorDocument, EditorLayer } from "./types";

/**
 * Autosave: keep the open document in the browser so reloading the page does not
 * lose work.
 *
 * Two stores, because documents and pictures have very different sizes. The
 * document itself is small JSON; an imported PNG can be megabytes. Base64 would
 * inflate the pictures by a third *and* force them through a string API, so the
 * document keeps a stable `asset:<id>` reference and the bytes live beside it as
 * Blobs. On load the references are turned back into blob URLs.
 *
 * Nothing here is allowed to throw into the editor: a browser with storage
 * disabled, a full quota or a private window must all behave as "no autosave"
 * rather than as a broken app.
 */

export const AUTOSAVE_VERSION = 1;
const DB_NAME = "bmo-editor";
const DB_VERSION = 1;
const DOCUMENT_STORE = "document";
const ASSET_STORE = "assets";
const DOCUMENT_KEY = "current";

/** How long the editor waits after the last edit before writing. */
export const AUTOSAVE_DELAY_MS = 500;

type StoredLayer = Omit<EditorLayer, "image"> & {
  image?: { assetId: string; width: number; height: number };
};

export interface StoredPayload {
  version: number;
  savedAt: string;
  document: Omit<EditorDocument, "layers" | "background"> & {
    background: unknown;
    layers: StoredLayer[];
  };
}

/** A stored picture, decoded back into something the document can draw. */
export type AssetResolver = (assetId: string) => Promise<Blob | null>;

const isRecord = (value: unknown): value is Record<string, unknown> =>
  typeof value === "object" && value !== null && !Array.isArray(value);

/**
 * Turns a live document into the shape that gets stored: picture sources become
 * `asset:<id>` references. Everything else is kept, including an effect kind this
 * build did not recognise, so autosave cannot quietly drop what a save would keep.
 */
export function serializeForStorage(doc: EditorDocument, savedAt = new Date()): StoredPayload {
  const layers: StoredLayer[] = doc.layers.map((layer) => {
    const { image, ...rest } = layer;
    if (!image) return { ...rest };
    const { assetId, width, height } = image;
    return { ...rest, image: { assetId, width, height } };
  });
  return {
    version: AUTOSAVE_VERSION,
    savedAt: savedAt.toISOString(),
    document: {
      ...doc,
      background: structuredClone(doc.background),
      layers,
    },
  };
}

/** A 1x1 PNG, used only to satisfy the validator's "must be an embedded image" rule. */
const PLACEHOLDER_PNG =
  "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8DwHwAFAAH/q842iQAAAABJRU5ErkJggg==";

/**
 * Rebuilds a live document from stored JSON.
 *
 * Image layers are resolved through `resolve`, which returns the stored bytes. A
 * picture whose bytes are gone cannot be drawn, so that layer is dropped and
 * counted rather than resurrected as a blank box the user cannot fix.
 *
 * Storage is not trusted more than a file on disk: the result goes through the
 * same validator an `.add` file goes through. That validator insists on an
 * embedded data URL for every picture, so it runs against a placeholder and the
 * real blob URL is substituted afterwards.
 */
export async function reviveFromStorage(
  payload: unknown,
  resolve: AssetResolver,
): Promise<{ document: EditorDocument; droppedImages: number } | null> {
  if (!isRecord(payload) || payload.version !== AUTOSAVE_VERSION) return null;
  if (!isRecord(payload.document) || !Array.isArray(payload.document.layers)) return null;

  const storedLayers = payload.document.layers.filter(isRecord);
  const validatedLayers: unknown[] = [];
  /** Document index -> position in `resolved`, for the image layers only. */
  const imagePosition = new Map<number, number>();
  for (const layer of storedLayers) {
    if (layer.kind === "image" && isRecord(layer.image)) {
      imagePosition.set(validatedLayers.length, imagePosition.size);
      validatedLayers.push({ ...layer, image: { ...layer.image, src: PLACEHOLDER_PNG } });
    } else {
      validatedLayers.push(layer);
    }
  }

  let document: EditorDocument;
  try {
    document = validateDocument({ ...payload.document, layers: validatedLayers });
  } catch {
    return null;
  }

  const resolved = await Promise.all(
    [...imagePosition.keys()].map(async (index) => {
      const assetId = document.layers[index]?.image?.assetId;
      const blob = assetId ? await resolve(assetId) : null;
      return blob ? URL.createObjectURL(blob) : null;
    }),
  );

  let droppedImages = 0;
  const layers: EditorLayer[] = [];
  document.layers.forEach((layer, index) => {
    const position = imagePosition.get(index);
    if (position === undefined) {
      layers.push(layer);
      return;
    }
    const src = resolved[position];
    if (!src || !layer.image) {
      droppedImages += 1;
      return;
    }
    layers.push({ ...layer, image: { ...layer.image, src } });
  });

  const available = new Set(layers.map((layer) => layer.id));
  return {
    document: {
      ...document,
      layers,
      selectedLayerIds: document.selectedLayerIds.filter((id) => available.has(id)),
    },
    droppedImages,
  };
}

export interface AutosaveBackend {
  read(): Promise<unknown | null>;
  write(payload: StoredPayload): Promise<void>;
  readAsset(assetId: string): Promise<Blob | null>;
  writeAsset(assetId: string, blob: Blob): Promise<void>;
  pruneAssets(keep: Iterable<string>): Promise<void>;
  clear(): Promise<void>;
}

/** True only when a real IndexedDB exists; jsdom and locked-down browsers have none. */
function hasIndexedDb(): boolean {
  return typeof indexedDB !== "undefined" && indexedDB !== null;
}

function openDatabase(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    const request = indexedDB.open(DB_NAME, DB_VERSION);
    request.onupgradeneeded = () => {
      const db = request.result;
      if (!db.objectStoreNames.contains(DOCUMENT_STORE)) db.createObjectStore(DOCUMENT_STORE);
      if (!db.objectStoreNames.contains(ASSET_STORE)) db.createObjectStore(ASSET_STORE);
    };
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error ?? new Error("IndexedDB refused to open."));
  });
}

function run<T>(
  db: IDBDatabase,
  store: string,
  mode: IDBTransactionMode,
  job: (store: IDBObjectStore) => IDBRequest<T>,
): Promise<T> {
  return new Promise((resolve, reject) => {
    const transaction = db.transaction(store, mode);
    const request = job(transaction.objectStore(store));
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error ?? new Error("IndexedDB request failed."));
    transaction.onabort = () => reject(transaction.error ?? new Error("IndexedDB transaction aborted."));
  });
}

export function createIndexedDbBackend(): AutosaveBackend | null {
  if (!hasIndexedDb()) return null;
  let dbPromise: Promise<IDBDatabase> | null = null;
  const db = () => {
    dbPromise ??= openDatabase().catch((error: unknown) => {
      dbPromise = null;
      throw error;
    });
    return dbPromise;
  };

  return {
    async read() {
      const handle = await db();
      const stored = await run<StoredPayload | undefined>(
        handle,
        DOCUMENT_STORE,
        "readonly",
        (store) => store.get(DOCUMENT_KEY),
      );
      return stored ?? null;
    },
    async write(payload) {
      const handle = await db();
      await run(handle, DOCUMENT_STORE, "readwrite", (store) => store.put(payload, DOCUMENT_KEY));
    },
    async readAsset(assetId) {
      const handle = await db();
      const blob = await run<Blob | undefined>(handle, ASSET_STORE, "readonly", (store) => store.get(assetId));
      return blob ?? null;
    },
    async writeAsset(assetId, blob) {
      const handle = await db();
      await run(handle, ASSET_STORE, "readwrite", (store) => store.put(blob, assetId));
    },
    async pruneAssets(keep) {
      const handle = await db();
      const wanted = new Set(keep);
      const keys = await run<IDBValidKey[]>(handle, ASSET_STORE, "readonly", (store) => store.getAllKeys());
      for (const key of keys) {
        if (typeof key !== "string" || wanted.has(key)) continue;
        await run(handle, ASSET_STORE, "readwrite", (store) => store.delete(key));
      }
    },
    async clear() {
      const handle = await db();
      await run(handle, DOCUMENT_STORE, "readwrite", (store) => store.clear());
      await run(handle, ASSET_STORE, "readwrite", (store) => store.clear());
    },
  };
}

/** An in-memory stand-in, so the autosave rules can be tested without a browser. */
export function createMemoryBackend(): AutosaveBackend & {
  assets: Map<string, Blob>;
  writes: StoredPayload[];
} {
  let payload: StoredPayload | null = null;
  const assets = new Map<string, Blob>();
  const writes: StoredPayload[] = [];
  return {
    assets,
    writes,
    async read() {
      return payload ? structuredClone(payload) : null;
    },
    async write(next) {
      payload = structuredClone(next);
      writes.push(payload);
    },
    async readAsset(assetId) {
      return assets.get(assetId) ?? null;
    },
    async writeAsset(assetId, blob) {
      assets.set(assetId, blob);
    },
    async pruneAssets(keep) {
      const wanted = new Set(keep);
      for (const key of [...assets.keys()]) if (!wanted.has(key)) assets.delete(key);
    },
    async clear() {
      payload = null;
      assets.clear();
    },
  };
}

/** Asset ids a stored payload refers to, so pictures nothing uses can be pruned. */
export function referencedAssets(payload: StoredPayload): string[] {
  const ids = new Set<string>();
  const visit = (value: unknown) => {
    if (isRecord(value) && typeof value.assetId === "string") ids.add(value.assetId);
  };
  visit(payload.document.background);
  for (const layer of payload.document.layers) {
    visit(layer.image);
    visit(layer.effect);
  }
  return [...ids];
}
