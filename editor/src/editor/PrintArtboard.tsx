import { ArtboardView } from "./canvas/ArtboardView";
import type { EditorDocument } from "../editor-core/types";

export function PrintArtboard({ doc, layerLimit }: { doc: EditorDocument; layerLimit?: number }) {
  return (
    <div className="ed-print-root" data-testid="print-root" aria-hidden="true">
      <ArtboardView
        doc={doc}
        readOnly
        exposeLayerIds={false}
        testId="print-artboard"
        layerLimit={layerLimit}
      />
    </div>
  );
}
