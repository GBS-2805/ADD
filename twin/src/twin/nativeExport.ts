import { ARTBOARD_EXPORT_HEIGHT, ARTBOARD_EXPORT_WIDTH } from "../editor/model/geometry";
import { artboardFilename, PNG_SIGNATURE } from "./pngExport";
import { serializeProject } from "./project";
import type { TwinDocument, TwinEffect } from "./types";

/**
 * The Twin's native export. Inside ADD the page can hand the whole project to the
 * app, which paints the artboard with Skia and writes the PNG itself; in a browser
 * the bridge is absent and the canvas exporter does the work.
 *
 * Only the effects the native painter knows are sent across. A document carrying a
 * pattern, particles or an image texture is answered with `canvas` and the caller
 * keeps its own renderer, because a native PNG that dropped an effect would be a
 * wrong export rather than a fast one.
 */

interface TwinHost {
  saveTwin?: (project: string, name: string, scale: number) => string;
}

/** The canvas exporter's own scale: 1530 x 2000 for a 765 x 1000 artboard. */
const NATIVE_SCALE = 2;

/** The bridge copies the project as one string, images inlined; keep it bounded. */
const MAX_NATIVE_PROJECT = 24 * 1024 * 1024;

const NATIVE_EFFECTS = new Set<TwinEffect["kind"]>(["solid", "field", "shader"]);

const EFFECT_LABELS: Record<string, string> = {
  texture: "Texture Lab",
  pattern: "Pattern Library",
  particles: "Particle Field",
};

export type NativeSave =
  | {
      status: "saved";
      bytes: number;
      filename: string;
      message: string;
      width: number;
      height: number;
      signature: typeof PNG_SIGNATURE;
    }
  | { status: "canvas"; reason: string }
  | { status: "error"; message: string };

function host(): TwinHost | null {
  if (typeof window === "undefined") return null;
  const bridge = (window as unknown as { IconsApp?: TwinHost }).IconsApp;
  return bridge && typeof bridge.saveTwin === "function" ? bridge : null;
}

/** True only inside the app: a browser has no native painter to call. */
export function nativeExportAvailable(): boolean {
  return host() !== null;
}

/** The effects in this document the native painter cannot draw, by shelf name. */
export function nativeBlockers(doc: TwinDocument): string[] {
  const blockers = new Set<string>();
  const inspect = (effect: TwinEffect | null) => {
    if (effect && !NATIVE_EFFECTS.has(effect.kind)) blockers.add(effect.kind);
  };
  inspect(doc.background);
  for (const layer of doc.layers) inspect(layer.effect);
  return [...blockers].map((kind) => EFFECT_LABELS[kind] ?? kind);
}

/**
 * Ask the app for the PNG. Returns null when there is no bridge, so the caller can
 * run the canvas exporter without knowing whether it is in the app.
 */
export async function saveArtboardNatively(doc: TwinDocument): Promise<NativeSave | null> {
  const bridge = host();
  if (!bridge?.saveTwin) return null;
  const blockers = nativeBlockers(doc);
  if (blockers.length > 0) return { status: "canvas", reason: blockers.join(", ") };
  const project = await serializeProject(doc);
  if (project.length > MAX_NATIVE_PROJECT) {
    return { status: "canvas", reason: "project too large" };
  }
  return read(bridge.saveTwin(project, artboardFilename(doc), NATIVE_SCALE));
}

function read(raw: string): NativeSave {
  try {
    const reply = JSON.parse(raw) as {
      status?: string;
      bytes?: number;
      filename?: string;
      reason?: string;
      message?: string;
    };
    if (
      reply.status === "saved" &&
      typeof reply.bytes === "number" &&
      typeof reply.filename === "string"
    ) {
      return {
        status: "saved",
        bytes: reply.bytes,
        filename: reply.filename,
        message: reply.message ?? `Saved ${reply.filename}`,
        width: ARTBOARD_EXPORT_WIDTH,
        height: ARTBOARD_EXPORT_HEIGHT,
        signature: PNG_SIGNATURE,
      };
    }
    if (reply.status === "canvas") {
      return { status: "canvas", reason: reply.reason ?? "unsupported effect" };
    }
    if (reply.status === "error") {
      return { status: "error", message: reply.message ?? "The native export failed." };
    }
  } catch {
    // A bridge that answers with something other than its own JSON is an error,
    // not a reason to fall back: the page cannot tell whether a file was written.
  }
  return { status: "error", message: "The native export returned no answer." };
}
