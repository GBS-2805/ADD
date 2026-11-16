import { useRef, useState } from "react";
import {
  ArrowUpRight,
  Circle,
  Eye,
  Heart,
  Hexagon,
  Image,
  Layers,
  Moon,
  Pause,
  PenTool,
  Play,
  Plus,
  Shapes,
  Sparkles,
  Square,
  Star,
  Sun,
  Triangle,
  Trash2,
  type LucideIcon,
} from "lucide-react";
import { importAsset, type ImportedAsset } from "../twin/imports";
import type { PluginPanelProps } from "../twin/types";
import { EmptyState, PluginIntro, TargetSwitch } from "./ui";

const ICONS: { name: string; Icon: LucideIcon }[] = [
  { name: "Circle", Icon: Circle },
  { name: "Square", Icon: Square },
  { name: "Star", Icon: Star },
  { name: "Heart", Icon: Heart },
  { name: "Triangle", Icon: Triangle },
  { name: "Hexagon", Icon: Hexagon },
  { name: "Sun", Icon: Sun },
  { name: "Moon", Icon: Moon },
  { name: "Eye", Icon: Eye },
  { name: "Play", Icon: Play },
  { name: "Pause", Icon: Pause },
  { name: "Plus", Icon: Plus },
  { name: "Pen", Icon: PenTool },
  { name: "Image", Icon: Image },
  { name: "Layers", Icon: Layers },
  { name: "Shapes", Icon: Shapes },
  { name: "Sparkles", Icon: Sparkles },
  { name: "Arrow", Icon: ArrowUpRight },
];

const PAPERS = [
  { id: "paper-006", name: "Paper 006", color: "#fafaf7", note: "Neutral stock" },
  { id: "paper-005", name: "Paper 005", color: "#e7e6e1", note: "Grey card" },
];

export default function AssetShelfPlugin({ target, setTarget, apply, toast }: PluginPanelProps) {
  const [category, setCategory] = useState<"all" | "paper" | "icon" | "imported">("all");
  const [imported, setImported] = useState<ImportedAsset[]>([]);
  const [busy, setBusy] = useState(false);
  const inputRef = useRef<HTMLInputElement>(null);

  const applyPaper = (paper: (typeof PAPERS)[number]) => {
    apply({ kind: "texture", preset: "PaperTexture", controls: { paper: paper.color, roughness: 0.46 } });
  };

  const applyImported = (asset: ImportedAsset) => {
    apply({ kind: "texture", preset: "PaperTexture", image: asset.src, controls: { roughness: 0.5 } });
  };

  const handleFile = async (file: File | undefined) => {
    if (!file) return;
    setBusy(true);
    try {
      const asset = await importAsset(file);
      setImported((items) => [asset, ...items].slice(0, 8));
      setCategory("imported");
      toast(`${file.name} added to the material shelf.`);
    } catch (error) {
      toast(error instanceof Error ? error.message : "That asset could not be imported.");
    } finally {
      setBusy(false);
      if (inputRef.current) inputRef.current.value = "";
    }
  };

  return (
    <div className="plugin-body">
      <PluginIntro>
        Paper and icon material for the artboard. This shelf changes the ground; it never adds a document layer. For PNG layers, use Overlay Lab.
      </PluginIntro>
      <TargetSwitch value={target} onChange={setTarget} />
      <div className="filter-row">
        {(["all", "paper", "icon", "imported"] as const).map((item) => (
          <button type="button" key={item} aria-pressed={category === item} onClick={() => setCategory(item)}>
            {item}
          </button>
        ))}
      </div>
      {(category === "all" || category === "paper") && (
        <section className="shelf-section" aria-labelledby="paper-shelf-title">
          <h3 id="paper-shelf-title">Paper stock</h3>
          <div className="paper-grid">
            {PAPERS.map((paper) => (
              <button type="button" key={paper.id} className="paper-card" onClick={() => applyPaper(paper)}>
                <span className="paper-card-swatch" style={{ background: paper.color }} />
                <span>
                  <strong>{paper.name}</strong>
                  <small>{paper.note}</small>
                </span>
              </button>
            ))}
          </div>
        </section>
      )}
      {(category === "all" || category === "icon") && (
        <section className="shelf-section" aria-labelledby="icon-shelf-title">
          <h3 id="icon-shelf-title">Lucide samples</h3>
          <div className="icon-grid">
            {ICONS.map(({ name, Icon }) => (
              <button
                type="button"
                key={name}
                className="icon-cell"
                aria-label={`Use ${name} ink`}
                onClick={() => {
                  apply({ kind: "solid", hex: "#111111" });
                  toast(`${name} set the ink to black.`);
                }}
              >
                <Icon size={20} strokeWidth={1.5} />
                <small>{name}</small>
              </button>
            ))}
          </div>
        </section>
      )}
      <section className="shelf-section" aria-labelledby="import-shelf-title">
        <div className="section-heading">
          <h3 id="import-shelf-title">Your material</h3>
          <button type="button" className="button small" onClick={() => inputRef.current?.click()} disabled={busy}>
            {busy ? "Reading…" : "Import"}
          </button>
        </div>
        <input
          ref={inputRef}
          type="file"
          accept="image/png,image/jpeg,image/webp,image/svg+xml"
          hidden
          data-testid="asset-import-input"
          onChange={(event) => void handleFile(event.target.files?.[0])}
        />
        {imported.length === 0 ? (
          <EmptyState title="No imported material" body="PNG, JPEG, WebP or a sanitised SVG. Files stay in this browser." />
        ) : (
          <div className="imported-list">
            {imported.map((asset) => (
              <div key={asset.id} className="imported-row">
                <button type="button" className="imported-apply" onClick={() => applyImported(asset)}>
                  <img src={asset.src} alt="" />
                  <span>
                    <strong>{asset.name}</strong>
                    <small>{asset.type === "image" ? "Imported image" : "Sanitised SVG"}</small>
                  </span>
                </button>
                <button type="button" className="icon-button" aria-label={`Remove ${asset.name}`} onClick={() => setImported((items) => items.filter((item) => item.id !== asset.id))}>
                  <Trash2 size={16} />
                </button>
              </div>
            ))}
          </div>
        )}
      </section>
    </div>
  );
}
