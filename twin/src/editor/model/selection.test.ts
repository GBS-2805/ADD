import { describe, expect, it } from "vitest";
import { nextSelection, pruneSelection, selectAllIds } from "./selection";

const layer = (id: string, visible = true) => ({ id, visible });

describe("selection model", () => {
  it("replaceSelectsOne", () => {
    expect(nextSelection(["a", "b"], "c", "replace")).toEqual(["c"]);
  });

  it("addKeepsExisting", () => {
    expect(nextSelection(["a", "b"], "c", "add")).toEqual(["a", "b", "c"]);
    expect(nextSelection(["a", "b"], "b", "add")).toEqual(["a", "b"]);
  });

  it("toggleRemovesSelected", () => {
    expect(nextSelection(["a", "b"], "a", "toggle")).toEqual(["b"]);
    expect(nextSelection(["a"], "b", "toggle")).toEqual(["a", "b"]);
  });

  it("selectAllSkipsNothing", () => {
    expect(selectAllIds([layer("a"), layer("b", false), layer("c")])).toEqual(["a", "b", "c"]);
  });

  it("pruneDropsMissingIds", () => {
    expect(pruneSelection(["a", "gone", "b", "a"], [layer("a"), layer("b")])).toEqual(["a", "b"]);
  });

  it("emptySelectionIsValid", () => {
    expect(pruneSelection([], [layer("a")])).toEqual([]);
    expect(nextSelection([], "a", "toggle")).toEqual(["a"]);
  });

  it("selectAllOnEmptyDocumentIsNoOp", () => {
    expect(selectAllIds([])).toEqual([]);
  });
});
