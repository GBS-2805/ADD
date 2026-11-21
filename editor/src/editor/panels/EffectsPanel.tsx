import { useRef } from "react";
import { useEditorStore } from "../../editor-core/store";
import { PluginCatalogue } from "../../shell/PluginCatalogue";
import { PluginHost } from "../../editor-core/PluginHost";

export function EffectsPanel() {
  const store = useEditorStore();
  const scrollRef = useRef<HTMLDivElement>(null);
  const showCatalogue = store.state.catalogueOpen || !store.state.activePluginId;

  return (
    <section className="ed-effects-panel" data-testid="effects-panel" aria-label="Effects panel">
      {showCatalogue ? <PluginCatalogue /> : null}
      {store.state.activePluginId ? (
        <div ref={scrollRef} className="ed-effects-plugin-scroll" data-testid="effects-plugin-scroll">
          <PluginHost isHidden={store.state.pluginHidden} scrollContainerRef={scrollRef} />
        </div>
      ) : null}
    </section>
  );
}
