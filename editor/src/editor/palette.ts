export const EDITOR_PALETTE = {
  studio: "#131513",
  panel: "#1B1D1A",
  raised: "#232622",
  rule: "#343832",
  cream: "#F2EEE3",
  muted: "#A9AA9F",
  red: "#DD4124",
  paper: "#E9E0CB",
  sheet: "#FAFAF7",
  ink: "#111111",
} as const;

export type EditorColor = keyof typeof EDITOR_PALETTE;
