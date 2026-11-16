import { useEffect } from "react";
import { CommandSheet } from "./CommandSheet";
import { DeviceFrame } from "./DeviceFrame";
import { Dock } from "./Dock";
import { Inspector } from "./Inspector";
import { PanelSheet } from "./PanelSheet";
import { PluginCatalogue } from "./PluginCatalogue";
import { Presentation } from "./Presentation";
import { Stage } from "./Stage";
import { PluginHost } from "../twin/PluginHost";
import { useTwinStore } from "../twin/store";
import { usePageVisible } from "../twin/usePageVisible";

export function TwinShell() {
  const store = useTwinStore();
  const pageVisible = usePageVisible();
  const { layout, surface, catalogueOpen, activePluginId, nativeLens, toast, toastExiting, presenting, lastExport } = store.state;

  useEffect(() => {
    const editOnShortcut = (event: KeyboardEvent) => {
      if (!(event.metaKey || event.ctrlKey) || event.altKey || event.defaultPrevented) return;
      const key = event.key.toLowerCase();
      if (key === "z") {
        event.preventDefault();
        if (event.shiftKey) store.redo();
        else store.undo();
        return;
      }
      if (key === "y" && !event.shiftKey) {
        event.preventDefault();
        store.redo();
      }
    };
    window.addEventListener("keydown", editOnShortcut);
    return () => window.removeEventListener("keydown", editOnShortcut);
  }, [store]);

  useEffect(() => {
    const closeOnEscape = (event: KeyboardEvent) => {
      if (event.key !== "Escape" || event.defaultPrevented) return;
      if (presenting) {
        event.preventDefault();
        store.closePresentation();
        return;
      }
      const addMenu = document.querySelector<HTMLElement>('[data-testid="add-menu"]');
      if (addMenu && addMenu.getClientRects().length > 0 && getComputedStyle(addMenu).visibility !== "hidden") return;
      if (surface === "none") return;
      event.preventDefault();
      store.closeSurface();
    };
    window.addEventListener("keydown", closeOnEscape);
    return () => window.removeEventListener("keydown", closeOnEscape);
  }, [presenting, surface, store]);

  return (
    <main id="bmo-twin" data-native-lens={nativeLens} data-presenting={presenting} data-page-visible={pageVisible} className={`bmo-twin twin-app layout-${layout} ${nativeLens ? "native-lens-on" : ""}`}>
      <h1 className="sr-only">BMO Twin</h1>
      <header className="page-head">
        <span className="page-mark">BMO / TWIN</span>
        <span className="page-rule" />
        <span className="page-note">A printmaker’s workbench for the browser</span>
      </header>
      <div className="workbench">
        <DeviceFrame layout={layout}>
          <div className="device-ui" data-panel-open={surface !== "none"}>
            <header className="twin-header" inert={presenting}>
              <div className="wordmark" aria-label="ADD Twin">
                <span>ADD</span><i>/</i><strong>TWIN</strong>
              </div>
              <div className="header-actions">
                <div className="viewport-switch" role="group" aria-label="Viewport">
                  <button type="button" aria-pressed={layout === "compact"} onClick={() => store.setLayout("compact")}>411</button>
                  <button type="button" aria-pressed={layout === "expanded"} onClick={() => store.setLayout("expanded")}>840</button>
                </div>
                <button
                  type="button"
                  className="lens-button"
                  aria-pressed={nativeLens}
                  onClick={store.toggleNativeLens}
                  title="Label what still needs native work"
                >
                  <span className="lens-dot" />
                  Native lens
                </button>
              </div>
            </header>
            <div className="stage-stack">
              <Stage inert={presenting} />
              <Dock inert={presenting} />
            </div>
            <PanelSheet>
              {surface === "inspector" ? (
                <Inspector />
              ) : surface === "commands" ? (
                <CommandSheet />
              ) : (
                <div className="plugin-surface">
                  {surface === "plugins" && catalogueOpen ? <PluginCatalogue /> : null}
                  {surface === "plugins" || activePluginId ? <PluginHost isHidden={catalogueOpen} /> : null}
                </div>
              )}
            </PanelSheet>
            {presenting ? <Presentation /> : null}
            {lastExport ? (
              <span
                className="sr-only"
                data-testid="png-export-result"
                data-width={lastExport.width}
                data-height={lastExport.height}
                data-bytes={lastExport.bytes}
                data-filename={lastExport.filename}
                data-signature={lastExport.signature}
              >
                PNG exported, {lastExport.width} by {lastExport.height}, {lastExport.bytes} bytes.
              </span>
            ) : null}
            {toast ? <div className={`toast ${toastExiting ? "is-leaving" : ""}`} role="status">{toast}</div> : null}
          </div>
        </DeviceFrame>
        <aside className="desk-notes" aria-label="Workbench notes">
          <p className="desk-note-number">01</p>
          <p>Pick a ground. Add a PNG. Move the ink.</p>
          <span className="desk-note-rule" />
          <p className="desk-note-small">Eight local plugins. One surface. No shipping claims.</p>
        </aside>
      </div>
    </main>
  );
}
