import {
  AlignCenterHorizontal,
  AlignCenterVertical,
  AlignEndVertical,
  AlignHorizontalDistributeCenter,
  AlignLeft,
  AlignRight,
  AlignStartVertical,
  AlignVerticalDistributeCenter,
} from "lucide-react";
import { useEditorStore } from "../../editor-core/store";
import { alignTransforms, distributeTransforms, type AlignEdge } from "../model/align";
import type { EditorLayer } from "../../editor-core/types";

const ALIGNMENTS: { edge: AlignEdge; label: string; icon: typeof AlignLeft }[] = [
  { edge: "left", label: "Align left", icon: AlignLeft },
  { edge: "hcenter", label: "Align horizontal center", icon: AlignCenterHorizontal },
  { edge: "right", label: "Align right", icon: AlignRight },
  { edge: "top", label: "Align top", icon: AlignStartVertical },
  { edge: "vcenter", label: "Align vertical center", icon: AlignCenterVertical },
  { edge: "bottom", label: "Align bottom", icon: AlignEndVertical },
];

export function AlignSection({ layers, selectedIds }: { layers: EditorLayer[]; selectedIds: string[] }) {
  const store = useEditorStore();
  const noSelection = selectedIds.length === 0;
  const canDistribute = selectedIds.length >= 3;

  return (
    <section className="ed-inspector-section" data-testid="align-section" aria-label="Align">
      <div className="ed-inspector-section-head"><span>Align</span></div>
      <div className="ed-align-grid">
        {ALIGNMENTS.map(({ edge, label, icon: Icon }) => (
          <button
            key={edge}
            type="button"
            className="ed-icon-button"
            aria-label={label}
            title={label}
            disabled={noSelection}
            onClick={() => store.setLayerTransforms(alignTransforms(layers, selectedIds, edge))}
          >
            <Icon size={14} />
          </button>
        ))}
        <button
          type="button"
          className="ed-icon-button"
          aria-label="Distribute horizontally"
          title="Distribute horizontally"
          disabled={!canDistribute}
          onClick={() => store.setLayerTransforms(distributeTransforms(layers, selectedIds, "x"))}
        >
          <AlignHorizontalDistributeCenter size={14} />
        </button>
        <button
          type="button"
          className="ed-icon-button"
          aria-label="Distribute vertically"
          title="Distribute vertically"
          disabled={!canDistribute}
          onClick={() => store.setLayerTransforms(distributeTransforms(layers, selectedIds, "y"))}
        >
          <AlignVerticalDistributeCenter size={14} />
        </button>
      </div>
    </section>
  );
}
