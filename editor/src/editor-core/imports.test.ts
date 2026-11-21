import { describe, expect, it } from "vitest";
import { MAX_ASSET_BYTES, sanitizeSvgMarkup, validateAssetFile } from "./imports";

const file = (type: string, bytes: number) => ({ type, size: bytes }) as File;

describe("validateAssetFile", () => {
  it("accepts the four local formats", () => {
    expect(validateAssetFile(file("image/png", 100))).toBeNull();
    expect(validateAssetFile(file("image/jpeg", 100))).toBeNull();
    expect(validateAssetFile(file("image/webp", 100))).toBeNull();
    expect(validateAssetFile(file("image/svg+xml", 100))).toBeNull();
  });

  it("rejects unsupported formats and oversized files", () => {
    expect(validateAssetFile(file("image/gif", 100))).toMatch(/PNG, JPEG, WebP or SVG/);
    expect(validateAssetFile(file("image/png", MAX_ASSET_BYTES + 1))).toMatch(/4 MB/);
  });
});

describe("sanitizeSvgMarkup", () => {
  it("removes scripts, event handlers and foreignObject", () => {
    const safe = sanitizeSvgMarkup(
      '<svg xmlns="http://www.w3.org/2000/svg"><script>alert(1)</script><rect width="10" height="10" onclick="alert(2)" /><foreignObject /></svg>',
    );

    expect(safe).not.toMatch(/script|onclick|foreignObject|alert/);
    expect(safe).toContain("<rect");
  });

  it("allows local fragment and data image references", () => {
    expect(() =>
      sanitizeSvgMarkup(
        '<svg xmlns="http://www.w3.org/2000/svg"><use href="#mark" /><image href="data:image/png;base64,AA==" /></svg>',
      ),
    ).not.toThrow();
  });

  it("rejects remote references instead of fetching them", () => {
    expect(() =>
      sanitizeSvgMarkup('<svg xmlns="http://www.w3.org/2000/svg"><image href="https://example.com/ink.png" /></svg>'),
    ).toThrow(/points outside the file/);
  });
});
