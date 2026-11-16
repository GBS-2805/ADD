import {
  ColorPicker,
  Editable,
  NumberInput,
  Portal,
  Select,
  Slider,
  Switch,
  Tabs,
  createListCollection,
  parseColor,
} from "@ark-ui/react";
import { ArrowDown, ArrowUp, Check, ChevronDown, Minus, Pencil, Plus, Trash2, X } from "lucide-react";
import { BLEND_MODES, DEFAULT_TEXT_FONT, TEXT_FONTS, textFontStack, type BlendMode, type TextFont, type TwinLayer } from "../twin/types";
import { useTwinStore } from "../twin/store";
import { MONO_COLORS } from "../twin/presets";
import { fromLegacy, toLegacy } from "./legacy";

const BLEND_LABELS: Record<BlendMode, string> = {
  normal: "Normal",
  multiply: "Multiply",
  screen: "Screen",
  overlay: "Overlay",
  darken: "Darken",
  lighten: "Lighten",
  "color-dodge": "Color dodge",
  "color-burn": "Color burn",
  "hard-light": "Hard light",
  "soft-light": "Soft light",
  difference: "Difference",
  exclusion: "Exclusion",
  hue: "Hue",
  saturation: "Saturation",
  color: "Color",
  luminosity: "Luminosity",
};

const BLEND_COLLECTION = createListCollection({
  items: BLEND_MODES.map((mode) => ({ label: BLEND_LABELS[mode], value: mode })),
});

const FONT_COLLECTION = createListCollection({
  items: TEXT_FONTS.map((font) => ({ label: font.name, value: font.id, note: font.note })),
});

function NumberField({
  label,
  value,
  min,
  max,
  step = 1,
  jump,
  onChange,
}: {
  label: string;
  value: number;
  min: number;
  max: number;
  step?: number;
  jump?: boolean;
  onChange: (value: number) => void;
}) {
  const nudge = (delta: number) => onChange(Math.max(min, Math.min(max, value + delta)));

  return (
    <NumberInput.Root
      className="inspector-number"
      value={String(value)}
      min={min}
      max={max}
      step={step}
      formatOptions={{ maximumFractionDigits: step < 1 ? 2 : 0 }}
      onValueChange={({ valueAsNumber }) => {
        if (Number.isFinite(valueAsNumber)) onChange(valueAsNumber);
      }}
    >
      <NumberInput.Label>{label}</NumberInput.Label>
      <NumberInput.Control>
        <NumberInput.DecrementTrigger aria-label={`Decrease ${label}`}>
          <Minus size={12} />
        </NumberInput.DecrementTrigger>
        <NumberInput.Input />
        <NumberInput.IncrementTrigger aria-label={`Increase ${label}`}>
          <Plus size={12} />
        </NumberInput.IncrementTrigger>
      </NumberInput.Control>
      {jump ? (
        <div className="inspector-number-jumps">
          <button type="button" onClick={() => nudge(-5)} aria-label={`Decrease ${label} by 5`}>−5</button>
          <button type="button" onClick={() => nudge(5)} aria-label={`Increase ${label} by 5`}>+5</button>
        </div>
      ) : null}
    </NumberInput.Root>
  );
}

function FillPicker({ layer }: { layer: TwinLayer }) {
  const store = useTwinStore();
  const value = layer.fill ?? "#111111";

  return (
    <ColorPicker.Root
      className="inspector-color"
      value={parseColor(value)}
      onValueChange={({ value: next }) => store.setLayerFill(layer.id, next.toString("hex"))}
    >
      <ColorPicker.Label>Fill</ColorPicker.Label>
      <ColorPicker.Control>
        <ColorPicker.Trigger className="color-trigger" aria-label={`Fill ${layer.name}`}>
          <ColorPicker.ValueSwatch className="color-value-swatch" />
          <ColorPicker.ValueText />
          <ChevronDown size={13} strokeWidth={1.7} />
        </ColorPicker.Trigger>
      </ColorPicker.Control>
      <Portal>
        <ColorPicker.Positioner>
          <ColorPicker.Content className="color-content">
            <ColorPicker.SwatchGroup className="color-swatches">
              {MONO_COLORS.map((hex) => (
                <ColorPicker.SwatchTrigger
                  key={hex}
                  value={hex}
                  className="color-swatch-trigger"
                  aria-label={`Use fill ${hex}`}
                >
                  <ColorPicker.Swatch value={hex} className="color-swatch">
                    <ColorPicker.SwatchIndicator>
                      <Check size={10} />
                    </ColorPicker.SwatchIndicator>
                  </ColorPicker.Swatch>
                </ColorPicker.SwatchTrigger>
              ))}
            </ColorPicker.SwatchGroup>
            <ColorPicker.HiddenInput />
          </ColorPicker.Content>
        </ColorPicker.Positioner>
      </Portal>
    </ColorPicker.Root>
  );
}

function FontSelect({ layer }: { layer: TwinLayer }) {
  const store = useTwinStore();
  const value = layer.text?.font ?? DEFAULT_TEXT_FONT;
  return (
    <Select.Root
      className="inspector-select inspector-font-select"
      collection={FONT_COLLECTION}
      value={[value]}
      onValueChange={({ value: next }) => {
        if (next[0]) store.setLayerFont(layer.id, next[0] as TextFont);
      }}
      positioning={{ placement: "bottom-start" }}
    >
      <Select.Label>Font</Select.Label>
      <Select.Control>
        <Select.Trigger>
          <Select.ValueText placeholder="Instrument Serif" />
          <Select.Indicator>
            <ChevronDown size={13} strokeWidth={1.7} />
          </Select.Indicator>
        </Select.Trigger>
      </Select.Control>
      <Select.HiddenSelect />
      <Portal>
        <Select.Positioner>
          <Select.Content className="inspector-select-content font-select-content">
            {FONT_COLLECTION.items.map((item) => (
              <Select.Item key={item.value} item={item} className="inspector-select-item font-select-item">
                <Select.ItemText>
                  <span style={{ fontFamily: textFontStack(item.value as TextFont) }}>{item.label}</span>
                  <small>{item.note}</small>
                </Select.ItemText>
                <Select.ItemIndicator>
                  <Check size={12} />
                </Select.ItemIndicator>
              </Select.Item>
            ))}
          </Select.Content>
        </Select.Positioner>
      </Portal>
    </Select.Root>
  );
}

function BlendSelect({ layer }: { layer: TwinLayer }) {
  const store = useTwinStore();
  return (
    <Select.Root
      className="inspector-select"
      collection={BLEND_COLLECTION}
      value={[layer.blend]}
      onValueChange={({ value }) => {
        if (value[0]) store.setLayerBlend(layer.id, value[0] as BlendMode);
      }}
      positioning={{ placement: "bottom-start" }}
    >
      <Select.Label>Blend mode</Select.Label>
      <Select.Control>
        <Select.Trigger>
          <Select.ValueText placeholder="Normal" />
          <Select.Indicator>
            <ChevronDown size={13} strokeWidth={1.7} />
          </Select.Indicator>
        </Select.Trigger>
      </Select.Control>
      <Select.HiddenSelect />
      <Portal>
        <Select.Positioner>
          <Select.Content className="inspector-select-content">
            {BLEND_COLLECTION.items.map((item) => (
              <Select.Item key={item.value} item={item} className="inspector-select-item">
                <Select.ItemText>{item.label}</Select.ItemText>
                <Select.ItemIndicator>
                  <Check size={12} />
                </Select.ItemIndicator>
              </Select.Item>
            ))}
          </Select.Content>
        </Select.Positioner>
      </Portal>
    </Select.Root>
  );
}

function LayerVisibility({ layer, compact = false }: { layer: TwinLayer; compact?: boolean }) {
  const store = useTwinStore();
  return (
    <Switch.Root
      className={`inspector-switch ${compact ? "is-compact" : ""}`}
      checked={layer.visible}
      onCheckedChange={({ checked }) => store.setLayerVisibility(layer.id, checked)}
    >
      <Switch.Control>
        <Switch.Thumb />
      </Switch.Control>
      <Switch.Label className={compact ? "sr-only" : undefined}>
        {compact ? `Toggle ${layer.name} visibility` : "Visible"}
      </Switch.Label>
      <Switch.HiddenInput />
    </Switch.Root>
  );
}

function Properties({ layer }: { layer: TwinLayer | undefined }) {
  const store = useTwinStore();
  if (!layer) {
    return <p className="inspector-empty">Select a layer on the artboard to edit it.</p>;
  }

  const kindLabel = layer.kind === "image" ? "PNG layer" : `${layer.kind[0].toUpperCase()}${layer.kind.slice(1)} layer`;
  const align = layer.text?.align ?? "center";

  return (
    <div className="inspector-properties">
      <div className="inspector-layer-head">
        <span className={`layer-thumb ${layer.kind}`} aria-hidden="true">
          {layer.image ? <img src={layer.image.src} alt="" /> : layer.kind === "text" ? "Aa" : "◇"}
        </span>
        <div>
          <p className="inspector-kicker" data-testid="inspector-kind">{kindLabel}</p>
          <Editable.Root
            className="inspector-editable"
            value={layer.name}
            onValueChange={({ value }) => store.renameLayer(layer.id, value)}
            activationMode="dblclick"
            submitMode="both"
          >
            <Editable.Label>Layer name</Editable.Label>
            <Editable.Area>
              <Editable.Preview data-testid="inspector-layer-name" />
              <Editable.Input />
            </Editable.Area>
            <Editable.Control>
              <Editable.EditTrigger className="icon-button" aria-label="Edit layer name">
                <Pencil size={14} />
              </Editable.EditTrigger>
              <Editable.SubmitTrigger className="icon-button" aria-label="Save layer name">
                <Check size={14} />
              </Editable.SubmitTrigger>
              <Editable.CancelTrigger className="icon-button" aria-label="Cancel layer name">
                <X size={14} />
              </Editable.CancelTrigger>
            </Editable.Control>
          </Editable.Root>
        </div>
      </div>

      <div className={`inspector-transform ${layer.kind === "image" ? "is-image" : ""}`}>
        <NumberField label="X" value={Math.round(toLegacy(layer.transform.x))} min={-400} max={400} jump onChange={(x) => store.setLayerTransform(layer.id, { x: fromLegacy(x) })} />
        <NumberField label="Y" value={Math.round(toLegacy(layer.transform.y))} min={-400} max={400} jump onChange={(y) => store.setLayerTransform(layer.id, { y: fromLegacy(y) })} />
        <NumberField label="Scale" value={layer.transform.scaleX} min={0.1} max={4} step={0.05} onChange={(scale) => store.setLayerTransform(layer.id, { scaleX: scale, scaleY: scale })} />
        <NumberField label="Rotation" value={Math.round(layer.transform.rotation)} min={-180} max={180} onChange={(rotation) => store.setLayerTransform(layer.id, { rotation })} />
      </div>

      <Slider.Root
        className="inspector-slider"
        min={0}
        max={100}
        step={1}
        value={[Math.round(layer.opacity * 100)]}
        onValueChange={({ value }) => store.setLayerOpacity(layer.id, value[0] / 100)}
      >
        <div className="inspector-label-row">
          <Slider.Label>Opacity</Slider.Label>
          <span><Slider.ValueText />%</span>
        </div>
        <Slider.Control>
          <Slider.Track>
            <Slider.Range />
          </Slider.Track>
          <Slider.Thumb index={0} aria-label="Opacity" />
        </Slider.Control>
      </Slider.Root>

      <div className="inspector-toggle-row">
        <LayerVisibility layer={layer} />
        <BlendSelect layer={layer} />
      </div>

      {layer.kind === "text" && layer.text ? (
        <section className="inspector-section" aria-label="Text">
          <div className="inspector-text-row">
            <FontSelect layer={layer} />
            <NumberField
              label="Text size"
              value={Math.round(toLegacy(layer.text.size))}
              min={12}
              max={140}
              onChange={(size) => {
                const nextSize = Math.round(fromLegacy(size));
                if (nextSize >= 12) store.setLayerText(layer.id, layer.text?.content ?? "", nextSize);
              }}
            />
          </div>
          <label className="inspector-textarea">
            <span>Text content</span>
            <textarea
              rows={3}
              value={layer.text.content}
              style={{ fontFamily: textFontStack(layer.text.font) }}
              onChange={(event) => store.setLayerText(layer.id, event.target.value)}
            />
          </label>
          <span className="inspector-static-value">Align: {align}</span>
        </section>
      ) : null}

      {layer.kind === "shape" ? (
        <p className="inspector-note">
          {layer.shape?.variant ?? "star"} shape · every edit is undoable.
        </p>
      ) : null}

      {layer.kind === "image" && layer.image ? (
        <div className="inspector-image-meta">
          <span>Source</span>
          <strong>{layer.image.width} × {layer.image.height} px</strong>
        </div>
      ) : null}

      {layer.kind !== "image" ? <FillPicker layer={layer} /> : null}
    </div>
  );
}

function LayerList({ layers, selectedId }: { layers: TwinLayer[]; selectedId: string }) {
  const store = useTwinStore();
  return (
    <div className="inspector-layers" role="list" aria-label="Layers">
      {layers.length === 0 ? (
        <p className="inspector-empty">No layers yet. Add text, a shape or a PNG.</p>
      ) : (
        [...layers].reverse().map((layer) => (
          <div
            key={layer.id}
            className={`inspector-layer-row ${layer.id === selectedId ? "is-selected" : ""} ${layer.visible ? "" : "is-hidden"}`}
            role="listitem"
          >
            <button
              type="button"
              className="inspector-layer-select"
              onClick={() => store.selectLayer(layer.id)}
              aria-label={`Select ${layer.name}`}
            >
              <span className={`layer-thumb ${layer.kind}`} aria-hidden="true">
                {layer.image ? <img src={layer.image.src} alt="" /> : layer.kind === "text" ? "Aa" : layer.shape?.variant === "orbit" ? "○" : "◇"}
              </span>
              <span>
                <strong>{layer.name}</strong>
                <small>
                  {layer.kind === "image"
                    ? `${layer.image?.width ?? 0} × ${layer.image?.height ?? 0}`
                    : layer.kind === "shape"
                      ? layer.shape?.variant ?? "shape"
                      : "text"}
                </small>
              </span>
            </button>
            <div className="inspector-layer-actions">
              <LayerVisibility layer={layer} compact />
              <button type="button" className="icon-button" aria-label={`Move ${layer.name} up`} onClick={() => store.moveLayer(layer.id, "up")}>
                <ArrowUp size={14} />
              </button>
              <button type="button" className="icon-button" aria-label={`Move ${layer.name} down`} onClick={() => store.moveLayer(layer.id, "down")}>
                <ArrowDown size={14} />
              </button>
              <button type="button" className="icon-button danger" aria-label={`Remove ${layer.name}`} onClick={() => store.removeLayer(layer.id)}>
                <Trash2 size={14} />
              </button>
            </div>
          </div>
        ))
      )}
    </div>
  );
}

export function Inspector() {
  const store = useTwinStore();
  const { doc } = store.state;
  const selected = doc.layers.find((layer) => layer.id === doc.selectedLayerIds[0]);

  return (
    <Tabs.Root className="inspector" defaultValue="properties" data-testid="inspector">
      <Tabs.List className="inspector-tabs" aria-label="Inspector">
        <Tabs.Trigger value="properties">Properties</Tabs.Trigger>
        <Tabs.Trigger value="layers">Layers</Tabs.Trigger>
        <Tabs.Indicator className="inspector-tab-indicator" />
      </Tabs.List>
      <Tabs.Content value="properties" className="inspector-tab-content">
        <Properties layer={selected} />
      </Tabs.Content>
      <Tabs.Content value="layers" className="inspector-tab-content">
        <LayerList layers={doc.layers} selectedId={doc.selectedLayerIds[0] ?? ""} />
      </Tabs.Content>
    </Tabs.Root>
  );
}
