import { ArtboardView } from "./canvas/ArtboardView";
import type { EditorDocument } from "../editor-core/types";

export function PrintArtboard({ doc }: { doc: EditorDocument }) {
  return (
    <div className="ed-print-root" data-testid="print-root" aria-hidden="true">
      <ArtboardView doc={doc} readOnly exposeLayerIds={false} testId="print-artboard" />
    </div>
  );
}
