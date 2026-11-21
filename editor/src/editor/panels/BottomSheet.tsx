import { useEffect, useRef, type ReactNode } from "react";
import { Drawer, Portal } from "@ark-ui/react";
import { X } from "lucide-react";
import { useVisualViewport } from "../useVisualViewport";

export type SheetPlacement = "bottom" | "left" | "right";

export interface BottomSheetProps {
  id: string;
  title: string;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  placement?: SheetPlacement;
  fullHeight?: boolean;
  children: ReactNode;
}

export function BottomSheet({
  id,
  title,
  open,
  onOpenChange,
  placement = "bottom",
  fullHeight = false,
  children,
}: BottomSheetProps) {
  const viewport = useVisualViewport();
  const wasOpen = useRef(open);

  useEffect(() => {
    if (wasOpen.current && !open) {
      window.requestAnimationFrame(() => {
        document.querySelector<HTMLElement>(`[data-drawer-trigger="${id}"]`)?.focus();
      });
    }
    wasOpen.current = open;
  }, [id, open]);

  const style = placement === "bottom"
    ? {
        height: fullHeight ? `${viewport.height}px` : "min(62dvh, 560px)",
        maxHeight: `${viewport.height}px`,
        bottom: viewport.keyboardOffset,
        scrollPaddingBottom: viewport.keyboardOffset + 24,
      }
    : undefined;

  return (
    <Drawer.Root
      open={open}
      onOpenChange={({ open: next }) => onOpenChange(next)}
    >
      <Portal>
        <Drawer.Backdrop className="ed-sheet-backdrop" />
        <Drawer.Positioner className={`ed-sheet-positioner ed-sheet-${placement}`}>
          <Drawer.Content
            className="ed-sheet-content"
            data-testid={`bottom-sheet-${id}`}
            data-placement={placement}
            data-full-height={fullHeight ? "true" : "false"}
            data-visual-height={Math.round(viewport.height)}
            data-visual-offset={Math.round(viewport.offsetTop)}
            style={style}
            aria-label={title}
            onKeyDown={(event) => {
              if (event.key !== "Escape") return;
              event.preventDefault();
              event.stopPropagation();
              onOpenChange(false);
            }}
          >
            <header className="ed-sheet-head">
              <Drawer.Title>{title}</Drawer.Title>
              <button type="button" className="ed-sheet-close" aria-label={`Close ${title}`} onClick={() => onOpenChange(false)}>
                <X size={17} strokeWidth={1.7} />
              </button>
            </header>
            <div className="ed-sheet-body">{children}</div>
          </Drawer.Content>
        </Drawer.Positioner>
      </Portal>
    </Drawer.Root>
  );
}
