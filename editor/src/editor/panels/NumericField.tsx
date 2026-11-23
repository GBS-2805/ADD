import { NumberInput } from "@ark-ui/react";
import { useEffect, useRef, useState } from "react";

export interface NumericFieldProps {
  label: string;
  value: number;
  min?: number;
  max?: number;
  step?: number;
  suffix?: string;
  onCommit: (value: number) => void;
}

const format = (value: number) => {
  if (!Number.isFinite(value)) return "0";
  // One decimal is the precision a field needs to read cleanly; the model keeps
  // the full value, so snapping and export do not lose accuracy.
  return String(Math.round(value * 10) / 10);
};

export function NumericField({ label, value, min, max, step = 1, suffix, onCommit }: NumericFieldProps) {
  const [draft, setDraft] = useState(format(value));
  // The value this field last sent, not the last value React handed back. A
  // deferred inspector can still be showing the pre-commit value when Enter is
  // followed by a blur (clicking Undo does exactly that), and re-sending the
  // field's own commit would overwrite the undo.
  const committed = useRef(value);

  useEffect(() => {
    committed.current = value;
    setDraft(format(value));
  }, [value]);

  const commit = () => {
    const parsed = Number(draft);
    if (!Number.isFinite(parsed)) {
      setDraft(format(value));
      return;
    }
    const next = Math.max(min ?? -Infinity, Math.min(max ?? Infinity, parsed));
    setDraft(format(next));
    if (next !== committed.current) {
      committed.current = next;
      onCommit(next);
    }
  };

  return (
    <NumberInput.Root className="ed-num" min={min} max={max} step={step}>
      <NumberInput.Scrubber className="ed-num-scrubber">
        <NumberInput.Label>{label}</NumberInput.Label>
        {suffix ? <span className="ed-num-suffix">{suffix}</span> : null}
      </NumberInput.Scrubber>
      <input
        className="ed-num-input"
        data-testid={`numeric-${label.toLowerCase().replace(/\s+/g, "-")}`}
        inputMode="decimal"
        aria-label={label}
        value={draft}
        onChange={(event) => setDraft(event.target.value)}
        onBlur={commit}
        onKeyDown={(event) => {
          if (event.key !== "Enter") return;
          event.preventDefault();
          commit();
        }}
      />
    </NumberInput.Root>
  );
}
