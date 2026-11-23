import { useEffect, useMemo, useState } from "react";

/** Rows and artboard layers per frame while a replaced document fills in. */
const REVEAL_BATCH = 1;

interface RevealState {
  signature: string;
  count: number;
  ids: Set<string>;
}

function signatureOf(ids: string[]): string {
  return [...ids].sort().join("\u0000");
}

/**
 * Reveals a newly opened document's layers a few at a time, so opening a
 * 20-layer project does not mount every layers-panel row and artboard layer in
 * the same commit. Ordinary edits keep the current count; a document that
 * replaces most of the ids starts the reveal again.
 */
export function useRevealLayerCount(ids: string[]): number {
  const signature = useMemo(() => signatureOf(ids), [ids]);
  const [reveal, setReveal] = useState<RevealState>(() => ({
    signature,
    count: ids.length,
    ids: new Set(ids),
  }));

  const changed = reveal.signature !== signature;
  const replacement =
    changed &&
    ids.filter((id) => !reveal.ids.has(id)).length > Math.max(1, ids.length / 2);
  const count = Math.min(
    ids.length,
    changed ? (replacement ? 0 : reveal.count) : reveal.count,
  );

  useEffect(() => {
    if (!changed) return;
    setReveal({
      signature,
      count: replacement ? 0 : Math.min(reveal.count, ids.length),
      ids: new Set(ids),
    });
  }, [changed, ids, replacement, reveal.count, signature]);

  useEffect(() => {
    if (changed || reveal.count >= ids.length) return;
    const frame = requestAnimationFrame(() => {
      setReveal((current) =>
        current.signature === signature
          ? { ...current, count: Math.min(ids.length, current.count + REVEAL_BATCH) }
          : current,
      );
    });
    return () => cancelAnimationFrame(frame);
  }, [changed, ids.length, reveal, signature]);

  return count;
}
