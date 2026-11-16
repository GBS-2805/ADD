import {
  ColorPicker,
  Portal,
  Select,
  createListCollection,
  parseColor,
} from "@ark-ui/react";
import { Check, ChevronDown } from "lucide-react";
import {
  BLEND_MODES,
  DEFAULT_TEXT_FONT,
  TEXT_FONTS,
  textFontStack,
  type BlendMode,
  type TextFont,
  type TwinLayer,
} from "../../twin/types";
import { useTwinStore } from "../../twin/store";
import { MONO_COLORS } from "../../twin/presets";

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

export function FillPicker({ layer }: { layer: TwinLayer }) {
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

export function FontSelect({ layer }: { layer: TwinLayer }) {
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

export function BlendSelect({ layer }: { layer: TwinLayer }) {
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
