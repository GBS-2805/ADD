import { useMemo, useState, type CSSProperties } from "react";
import { Search, X } from "lucide-react";
import { filterCatalogue } from "../editor-core/catalogue";
import { PLUGINS, preloadPlugin } from "../editor-core/registry";
import { useEditorStore } from "../editor-core/store";
import { NATIVE_LENS_LABELS, type EditorPlugin } from "../editor-core/types";
import { PatternSurface } from "../editor-core/ShaderView";

const GROUPS = ["All", "Effects", "Texture", "Overlay", "Motion", "Native"] as const;
type Group = (typeof GROUPS)[number];

function Preview({ plugin }: { plugin: EditorPlugin }) {
  switch (plugin.id) {
    case "shader-lab":
      return <span className="catalogue-shader catalogue-shader-mesh" aria-hidden="true" />;
    case "texture-lab":
      return <span className="catalogue-shader catalogue-shader-paper" aria-hidden="true" />;
    case "particle-field":
      return (
        <span className="catalogue-particles" aria-hidden="true">
          {Array.from({ length: 18 }).map((_, index) => <i key={index} style={{ ["--i" as string]: index }} />)}
        </span>
      );
    case "pattern-library":
      return <PatternSurface pattern="aztec" opacity={0.75} />;
    case "spatial-dock":
      return <span className="catalogue-spatial" aria-hidden="true"><i /><i /><i /></span>;
    case "native-parity":
      return <span className="catalogue-tones" aria-hidden="true"><i /><i /><i /></span>;
    case "asset-shelf":
      return <span className="catalogue-assets" aria-hidden="true"><i /><i /><i /><i /></span>;
    case "overlay-lab":
      return <span className="catalogue-layers" aria-hidden="true"><i /><i /><i /></span>;
    default:
      return <span className="catalogue-glyph">{plugin.glyph}</span>;
  }
}

export function PluginCatalogue() {
  const store = useEditorStore();
  const [query, setQuery] = useState("");
  const [group, setGroup] = useState<Group>("All");
  const filtered = useMemo(() => {
    const base = filterCatalogue(PLUGINS, query);
    return group === "All" ? base : base.filter((plugin) => plugin.group === group);
  }, [query, group]);

  return (
    <div className="catalogue" data-testid="plugin-catalogue">
      <div className="catalogue-head">
        <div>
          <span className="catalogue-kicker">THE SAMPLE TRAY</span>
          <h2>Eight ways to make a mark</h2>
        </div>
        <span className="catalogue-count">{filtered.length} / {PLUGINS.length}</span>
      </div>
      <div className="catalogue-search">
        <Search size={17} />
        <input
          value={query}
          onChange={(event) => setQuery(event.target.value)}
          placeholder="Search shaders, paper, layers…"
          aria-label="Search plugins"
          data-testid="catalogue-search"
        />
        {query ? <button type="button" className="icon-button" onClick={() => setQuery("")} aria-label="Clear plugin search"><X size={15} /></button> : null}
      </div>
      <div className="chip-row" role="group" aria-label="Plugin category">
        {GROUPS.map((item) => (
          <button type="button" key={item} aria-pressed={group === item} onClick={() => setGroup(item)}>
            {item}
          </button>
        ))}
      </div>
      <div className="plugin-card-grid">
        {filtered.map((plugin, index) => (
          <button
            type="button"
            key={`${group}-${plugin.id}`}
            className={`plugin-card ${store.state.nativeLens && plugin.reality !== "native-ready" ? "is-lens-dimmed" : ""}`}
            style={{ "--card-index": Math.min(index, 4) } as CSSProperties}
            onPointerEnter={() => preloadPlugin(plugin.id)}
            onFocus={() => preloadPlugin(plugin.id)}
            onClick={() => {
              preloadPlugin(plugin.id);
              store.openPlugin(plugin.id);
            }}
            data-testid={`catalogue-${plugin.id}`}
          >
            <span className="plugin-card-preview">
              <Preview plugin={plugin} />
              <span className="plugin-card-glyph">{plugin.glyph}</span>
            </span>
            <span className="plugin-card-copy">
              <span className="plugin-card-title">
                <strong>{plugin.name}</strong>
                <em>{plugin.group}</em>
              </span>
              <small>{plugin.blurb}</small>
              <span className={`reality-badge reality-${plugin.reality}`}>{NATIVE_LENS_LABELS[plugin.reality]}</span>
            </span>
          </button>
        ))}
      </div>
      {filtered.length === 0 ? (
        <div className="catalogue-empty">
          <strong>No sample matches “{query}”.</strong>
          <p>Try “paper”, “blend”, “shader” or clear the search.</p>
        </div>
      ) : null}
    </div>
  );
}
