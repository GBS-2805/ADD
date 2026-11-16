import { fireEvent, render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { usePointerReorder } from "./usePointerReorder";

function Grip({ onReorder, target = "b" }: { onReorder: (from: string, to: string) => void; target?: string | null }) {
  const { gripProps } = usePointerReorder({
    rowId: "a",
    onReorder,
    resolveTarget: () => target,
  });
  return (
    <button data-testid="grip" {...gripProps}>
      Grip
    </button>
  );
}

describe("usePointerReorder", () => {
  beforeEach(() => {
    if (!Element.prototype.setPointerCapture) {
      Element.prototype.setPointerCapture = () => undefined;
    }
    if (!Element.prototype.releasePointerCapture) {
      Element.prototype.releasePointerCapture = () => undefined;
    }
  });

  it("touchReorderUsesPointerNotHtmlDrag", () => {
    const onReorder = vi.fn();
    render(<Grip onReorder={onReorder} />);
    const grip = screen.getByTestId("grip");
    expect(grip).not.toHaveAttribute("draggable");

    fireEvent.pointerDown(grip, { pointerId: 7, pointerType: "touch", clientX: 10, clientY: 10, button: 0 });
    fireEvent.pointerMove(grip, { pointerId: 7, pointerType: "touch", clientX: 12, clientY: 12 });
    expect(grip).toHaveAttribute("data-dragging", "false");
    fireEvent.pointerMove(grip, { pointerId: 7, pointerType: "touch", clientX: 30, clientY: 30 });
    expect(grip).toHaveAttribute("data-dragging", "true");
    fireEvent.pointerUp(grip, { pointerId: 7, pointerType: "touch", clientX: 30, clientY: 30 });

    expect(onReorder).toHaveBeenCalledWith("a", "b");
  });

  it("pointerReorderCancelLeavesOrderUnchanged", () => {
    const onReorder = vi.fn();
    render(<Grip onReorder={onReorder} />);
    const grip = screen.getByTestId("grip");

    fireEvent.pointerDown(grip, { pointerId: 2, pointerType: "touch", clientX: 0, clientY: 0, button: 0 });
    fireEvent.pointerMove(grip, { pointerId: 2, pointerType: "touch", clientX: 40, clientY: 40 });
    fireEvent.keyDown(window, { key: "Escape" });
    fireEvent.pointerUp(grip, { pointerId: 2, pointerType: "touch", clientX: 40, clientY: 40 });

    expect(onReorder).not.toHaveBeenCalled();
  });

  it("pointercancelLeavesOrderUnchanged", () => {
    const onReorder = vi.fn();
    render(<Grip onReorder={onReorder} />);
    const grip = screen.getByTestId("grip");

    fireEvent.pointerDown(grip, { pointerId: 3, pointerType: "touch", clientX: 0, clientY: 0, button: 0 });
    fireEvent.pointerMove(grip, { pointerId: 3, pointerType: "touch", clientX: 40, clientY: 40 });
    fireEvent.pointerCancel(grip, { pointerId: 3, pointerType: "touch", clientX: 40, clientY: 40 });

    expect(onReorder).not.toHaveBeenCalled();
  });
});
