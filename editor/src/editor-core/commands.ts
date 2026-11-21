import { fitViewport } from "../editor/model/viewport";
import { alignTransforms, distributeTransforms } from "../editor/model/align";
import type { EditorActions } from "./store";
import type { EditorCommand } from "./types";
import { ARTBOARD_EXPORT_HEIGHT, ARTBOARD_EXPORT_WIDTH } from "./pngExport";

export function buildCommands(store: EditorActions): EditorCommand[] {
  const { doc, canvasSize, panels } = store.state;
  const selected = doc.selectedLayerIds;
  const hasSelection = selected.length > 0;
  const canDistribute = selected.length >= 3;

  const align = (edge: "left" | "hcenter" | "right" | "top" | "vcenter" | "bottom") => () => {
    if (!hasSelection) return;
    store.setLayerTransforms(alignTransforms(doc.layers, selected, edge));
  };
  const distribute = (axis: "x" | "y") => () => {
    if (!canDistribute) return;
    store.setLayerTransforms(distributeTransforms(doc.layers, selected, axis));
  };

  return [
    {
      id: "save-project",
      label: "Save project file",
      group: "File",
      hint: "Download a portable .add document",
      run: () => void store.saveProject(),
    },
    {
      id: "open-project",
      label: "Open project file",
      group: "File",
      hint: "Replace this document from a .add file",
      run: store.openProject,
    },
    {
      id: "export-png",
      label: "Export PNG",
      group: "File",
      hint: `Download a flattened artboard PNG · ${ARTBOARD_EXPORT_WIDTH} × ${ARTBOARD_EXPORT_HEIGHT}`,
      run: () => void store.exportPng(),
    },
    {
      id: "print-stage",
      label: "Print / Save PDF",
      group: "File",
      hint: "Print the artboard from the browser",
      run: store.printStage,
    },
    {
      id: "undo",
      label: "Undo last edit",
      group: "Edit",
      hint: "Document history",
      shortcut: "⌘Z",
      disabled: !store.canUndo,
      run: store.undo,
    },
    {
      id: "redo",
      label: "Redo edit",
      group: "Edit",
      hint: "Document history",
      shortcut: "⇧⌘Z",
      disabled: !store.canRedo,
      run: store.redo,
    },
    {
      id: "select-all",
      label: "Select all layers",
      group: "Edit",
      hint: "Select every layer on the artboard",
      shortcut: "⌘A",
      disabled: doc.layers.length === 0,
      run: store.selectAllLayers,
    },
    {
      id: "clear-selection",
      label: "Clear selection",
      group: "Edit",
      hint: "Leave the document and its history untouched",
      disabled: !hasSelection,
      run: store.clearSelection,
    },
    {
      id: "align-left",
      label: "Align left",
      group: "Arrange",
      hint: "Align to the artboard or selection bounds",
      disabled: !hasSelection,
      run: align("left"),
    },
    {
      id: "align-hcenter",
      label: "Align horizontal centers",
      group: "Arrange",
      disabled: !hasSelection,
      run: align("hcenter"),
    },
    {
      id: "align-right",
      label: "Align right",
      group: "Arrange",
      disabled: !hasSelection,
      run: align("right"),
    },
    {
      id: "align-top",
      label: "Align top",
      group: "Arrange",
      disabled: !hasSelection,
      run: align("top"),
    },
    {
      id: "align-vcenter",
      label: "Align vertical centers",
      group: "Arrange",
      disabled: !hasSelection,
      run: align("vcenter"),
    },
    {
      id: "align-bottom",
      label: "Align bottom",
      group: "Arrange",
      disabled: !hasSelection,
      run: align("bottom"),
    },
    {
      id: "distribute-horizontal",
      label: "Distribute horizontally",
      group: "Arrange",
      disabled: !canDistribute,
      run: distribute("x"),
    },
    {
      id: "distribute-vertical",
      label: "Distribute vertically",
      group: "Arrange",
      disabled: !canDistribute,
      run: distribute("y"),
    },
    {
      id: "zoom-fit",
      label: "Zoom to fit",
      group: "View",
      hint: "Center the whole artboard",
      disabled: canvasSize.width <= 0 || canvasSize.height <= 0,
      run: () => store.setViewport(fitViewport(canvasSize)),
    },
    {
      id: "zoom-100",
      label: "Zoom to 100%",
      group: "View",
      run: () => store.setViewport({ zoom: 1, panX: 0, panY: 0 }),
    },
    {
      id: "toggle-rulers",
      label: "Toggle rulers",
      group: "View",
      hint: store.state.rulersVisible ? "Hide the rulers" : "Show the rulers",
      run: () => store.setRulersVisible(!store.state.rulersVisible),
    },
    {
      id: "toggle-snap",
      label: "Toggle snapping",
      group: "View",
      hint: store.state.snapEnabled ? "Disable snapping while moving" : "Enable snapping while moving",
      run: () => store.setSnapEnabled(!store.state.snapEnabled),
    },
    {
      id: "toggle-layers-panel",
      label: `${panels.layers ? "Hide" : "Show"} layers panel`,
      group: "View",
      run: () => store.togglePanel("layers"),
    },
    {
      id: "toggle-inspector-panel",
      label: `${panels.inspector ? "Hide" : "Show"} inspector panel`,
      group: "View",
      run: () => store.togglePanel("inspector"),
    },
    {
      id: "open-inspector",
      label: "Open design inspector",
      group: "View",
      run: store.openInspector,
    },
    {
      id: "open-effect-catalogue",
      label: "Open effects catalogue",
      group: "View",
      run: store.openCatalogue,
    },
    {
      id: "preview",
      label: "Preview the stage",
      group: "View",
      hint: "Read-only device preview",
      run: store.openPresentation,
    },
    {
      id: "open-shader",
      label: "Open Shader Lab",
      group: "Plugins",
      hint: "Mesh, grain, warp and six more",
      run: () => store.openPlugin("shader-lab"),
    },
    {
      id: "open-overlay",
      label: "Open Overlay Lab",
      group: "Add",
      hint: "PNG layers and blend modes",
      run: () => store.openPlugin("overlay-lab"),
    },
    {
      id: "open-tone",
      label: "Open Tone",
      group: "Plugins",
      hint: "Native fill swatches",
      run: () => store.openNative("tone"),
    },
    {
      id: "open-prism",
      label: "Open Prism",
      group: "Plugins",
      hint: "Native field presets",
      run: () => store.openNative("prism"),
    },
    {
      id: "open-texture",
      label: "Open Texture Lab",
      group: "Plugins",
      hint: "Paper, glass and fur",
      run: () => store.openPlugin("texture-lab"),
    },
    {
      id: "open-pattern",
      label: "Open Pattern Library",
      group: "Plugins",
      hint: "Eight printed grounds",
      run: () => store.openPlugin("pattern-library"),
    },
    {
      id: "open-particles",
      label: "Open Particle Field",
      group: "Plugins",
      hint: "Dust, sparks, orbit, confetti",
      run: () => store.openPlugin("particle-field"),
    },
    {
      id: "open-assets",
      label: "Open Asset Shelf",
      group: "Plugins",
      hint: "Paper and Lucide samples",
      run: () => store.openPlugin("asset-shelf"),
    },
    {
      id: "open-spatial",
      label: "Open Spatial Dock",
      group: "Plugins",
      hint: "Local 3D and motion presets",
      run: () => store.openPlugin("spatial-dock"),
    },
    {
      id: "open-native",
      label: "Open Native Parity",
      group: "Plugins",
      hint: "Tone and Prism",
      run: () => store.openPlugin("native-parity"),
    },
    {
      id: "open-plugins",
      label: "Open the plugin catalogue",
      group: "View",
      run: store.openCatalogue,
    },
  ];
}
