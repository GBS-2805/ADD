import type { CSSProperties, KeyboardEvent, MouseEvent } from "react";
import { EffectSurface } from "../../twin/ShaderView";
import { textFontStack, type LayerTransform, type TwinLayer } from "../../twin/types";
import { layerFrame } from "../model/geometry";

export interface LayerViewProps {
  layer: TwinLayer;
  transform?: LayerTransform;
  selected?: boolean;
  readOnly?: boolean;
  onLayerSelect?: (id: string, event: MouseEvent<HTMLElement> | KeyboardEvent<HTMLElement>) => void;
}

export function LayerView({
  layer,
  transform = layer.transform,
  selected = false,
  readOnly = false,
  onLayerSelect,
}: LayerViewProps) {
  const frame = layerFrame(layer, transform);
  const variant = layer.shape?.variant ?? (layer.id.includes("orbit") ? "orbit" : layer.id.includes("star") ? "star" : "block");
  const style: CSSProperties = {
    left: frame.x - frame.width / 2,
    top: frame.y - frame.height / 2,
    width: frame.width,
    height: frame.height,
    transform: `rotate(${frame.rotation}deg)`,
    transformOrigin: "center",
    opacity: layer.opacity,
    mixBlendMode: layer.blend,
    display: layer.visible ? undefined : "none",
    zIndex: 1,
  };

  return (
    <div
      className={`art-layer ed-layer ${selected && !readOnly ? "is-selected" : ""} ${layer.kind === "text" ? "art-layer-text" : ""}`}
      style={style}
      data-layer-id={layer.id}
      data-testid={`art-layer-${layer.id}`}
      role={readOnly ? undefined : "button"}
      tabIndex={readOnly ? undefined : 0}
      aria-label={readOnly ? undefined : `Select ${layer.name}`}
      onClick={
        readOnly
          ? undefined
          : (event) => {
              event.stopPropagation();
              onLayerSelect?.(layer.id, event);
            }
      }
      onKeyDown={
        readOnly
          ? undefined
          : (event) => {
              if (event.key !== "Enter" && event.key !== " ") return;
              event.preventDefault();
              onLayerSelect?.(layer.id, event);
            }
      }
    >
      {layer.kind === "text" && layer.text ? (
        <div
          className="ed-layer-text"
          style={{
            color: layer.fill ?? "#111111",
            fontFamily: textFontStack(layer.text.font),
            fontSize: `${layer.text.size}px`,
            lineHeight: 0.82,
            textAlign: layer.text.align,
          }}
        >
          {layer.text.content.split(/\r?\n/).map((line, index) => (
            <span key={`${layer.id}-line-${index}`}>{line || "\u00a0"}</span>
          ))}
        </div>
      ) : null}

      {layer.kind === "shape" && variant === "star" ? (
        <svg className="ed-layer-shape" viewBox="0 0 100 100" aria-hidden="true" style={{ fill: layer.fill ?? "#111111" }}>
          <path d="M50 4 61 34 93 35 68 55 77 86 50 68 23 86 32 55 7 35 39 34Z" />
        </svg>
      ) : null}
      {layer.kind === "shape" && variant === "orbit" ? (
        <span className="ed-layer-orbit" aria-hidden="true" style={{ borderColor: layer.fill ?? "#111111" }}>
          <i />
        </span>
      ) : null}
      {layer.kind === "shape" && variant === "block" ? (
        <span className="ed-layer-block" aria-hidden="true" style={{ background: layer.fill ?? "#111111" }} />
      ) : null}
      {layer.kind === "image" && layer.image ? (
        <img className="ed-layer-image" src={layer.image.src} alt={layer.name} draggable={false} />
      ) : null}

      {layer.effect ? (
        <span className="ed-layer-effect" data-testid="layer-effect-clip" aria-hidden="true" style={{ overflow: "hidden", position: "absolute", inset: 0, pointerEvents: "none" }}>
          <EffectSurface effect={layer.effect} />
        </span>
      ) : null}
    </div>
  );
}
