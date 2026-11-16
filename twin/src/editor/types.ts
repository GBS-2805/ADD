export type EditorTool = "move" | "hand" | "zoom";
export type RightTab = "design" | "effects";
export type DeviceMode = "desktop" | "tablet" | "phone" | "cover" | "fold";

export interface FoldInsets {
  /** null when the browser exposes no fold; do not guess. */
  orientation: "vertical" | null;
  top: number;
  bottom: number;
  gap: number;
  source?: "segments" | "posture";
}

export const TOUCH_TARGET = 44;
export const COARSE_SNAP_SCREEN_PX = 10;

export interface Viewport {
  zoom: number;
  panX: number;
  panY: number;
}
