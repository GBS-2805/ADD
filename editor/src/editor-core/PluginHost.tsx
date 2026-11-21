import { useEffect, useRef, type RefObject } from "react";
import { getPlugin } from "./registry";
import { useEditorStore } from "./store";
import { NATIVE_LENS_LABELS } from "./types";

export function PluginHost({
  isHidden = false,
  scrollContainerRef,
}: {
  isHidden?: boolean;
  scrollContainerRef?: RefObject<HTMLElement | null>;
}) {
  const store = useEditorStore();
  const plugin = getPlugin(store.state.activePluginId);
  const sessionRef = useRef<HTMLElement>(null);

  useEffect(() => {
    const scroller = scrollContainerRef?.current ?? null;
    if (scroller instanceof HTMLElement) scroller.scrollTop = 0;
  }, [plugin?.id, isHidden, scrollContainerRef]);

  if (!plugin) {
    if (isHidden) return null;
    return (
      <div className="plugin-empty">
        <span className="plugin-empty-mark">+</span>
        <strong>Choose a plugin</strong>
        <p>Open the catalogue and pick a ground, a texture or a composition tool.</p>
      </div>
    );
  }

  const Panel = plugin.panel;
  return (
    <section ref={sessionRef} className={`plugin-session ${isHidden ? "is-hidden" : ""}`} aria-label={plugin.name} data-testid={`plugin-${plugin.id}`}>
      <header className="plugin-head">
        <div>
          <span className="plugin-kicker">{plugin.group} / {NATIVE_LENS_LABELS[plugin.reality]}</span>
          <h2>{plugin.name}</h2>
        </div>
        <div className="plugin-head-actions">
          <span className="running-dot"><i /> Running</span>
          <button type="button" className="text-button" onClick={store.openCatalogue}>Shelf</button>
          <button type="button" className="text-button" onClick={store.hidePlugin}>Hide</button>
          <button type="button" className="text-button" onClick={store.closePlugin}>Close</button>
        </div>
      </header>
      <div className="plugin-scroll" key={store.state.pluginSessionKey}>
        <Panel
          doc={store.state.doc}
          target={store.state.target}
          setTarget={store.setTarget}
          apply={(effect) => store.applyEffect(effect)}
          toast={store.toast}
        />
      </div>
    </section>
  );
}
