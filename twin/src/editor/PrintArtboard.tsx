import { ArtboardView } from "./canvas/ArtboardView";
import type { TwinDocument } from "../twin/types";

export function PrintArtboard({ doc }: { doc: TwinDocument }) {
  return (
    <div className="ed-print-root" data-testid="print-root" aria-hidden="true">
      <ArtboardView doc={doc} readOnly exposeLayerIds={false} testId="print-artboard" />
    </div>
  );
}
