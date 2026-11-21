export const MAX_ASSET_BYTES = 4 * 1024 * 1024;
export const MAX_PNG_BYTES = 8 * 1024 * 1024;
export const MAX_PNG_SIDE = 8192;
export const MAX_PNG_PIXELS = 16_000_000;

export interface ImportedAsset {
  id: string;
  name: string;
  src: string;
  type: "image" | "texture" | "icon";
  bytes: number;
}

export function validateAssetFile(file: File): string | null {
  if (!["image/png", "image/jpeg", "image/webp", "image/svg+xml"].includes(file.type)) {
    return "Use PNG, JPEG, WebP or SVG.";
  }
  if (file.size > MAX_ASSET_BYTES) return "That file is over 4 MB.";
  return null;
}

function readAsDataUrl(blob: Blob): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(String(reader.result));
    reader.onerror = () => reject(new Error("Could not read that file."));
    reader.readAsDataURL(blob);
  });
}

export function sanitizeSvgMarkup(markup: string): string {
  const parser = new DOMParser();
  const document = parser.parseFromString(markup, "image/svg+xml");
  if (document.querySelector("parsererror")) throw new Error("That SVG could not be parsed.");
  document.querySelectorAll("script, foreignObject").forEach((node) => node.remove());
  document.querySelectorAll("*").forEach((node) => {
    for (const attribute of Array.from(node.attributes)) {
      const name = attribute.name.toLowerCase();
      if (name.startsWith("on")) node.removeAttribute(attribute.name);
      if (name === "href" || name === "xlink:href") {
        const value = attribute.value.trim();
        if (value && !value.startsWith("#") && !value.startsWith("data:image/")) {
          throw new Error("That SVG points outside the file.");
        }
      }
    }
  });
  return new XMLSerializer().serializeToString(document.documentElement);
}

export async function importAsset(file: File): Promise<ImportedAsset> {
  const error = validateAssetFile(file);
  if (error) throw new Error(error);
  const id = `asset-${Date.now()}-${Math.random().toString(36).slice(2, 7)}`;
  if (file.type === "image/svg+xml") {
    const safe = sanitizeSvgMarkup(await file.text());
    const src = await readAsDataUrl(new Blob([safe], { type: "image/svg+xml" }));
    return { id, name: file.name, src, type: "texture", bytes: file.size };
  }
  return { id, name: file.name, src: await readAsDataUrl(file), type: "image", bytes: file.size };
}
