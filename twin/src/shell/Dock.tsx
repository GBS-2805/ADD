import { Command, MousePointer2, Shapes } from "lucide-react";
import { useTwinStore } from "../twin/store";
import { AddMenu } from "./AddMenu";

export function Dock({ inert = false }: { inert?: boolean }) {
  const store = useTwinStore();
  const { surface } = store.state;

  return (
    <nav className="dock" aria-label="Twin tools" inert={inert}>
      <button
        type="button"
        className={surface === "none" ? "dock-target is-active" : "dock-target"}
        onClick={store.closeSurface}
        aria-label="Select, close the panel"
      >
        <MousePointer2 size={19} strokeWidth={1.7} />
        <span>Select</span>
      </button>
      <AddMenu />
      <button
        type="button"
        className={surface === "commands" ? "dock-target is-active" : "dock-target"}
        onClick={store.openCommands}
        aria-label="Open actions"
      >
        <Command size={19} strokeWidth={1.7} />
        <span>Actions</span>
      </button>
      <button
        type="button"
        className={surface === "plugins" ? "dock-target is-active" : "dock-target"}
        onClick={store.openCatalogue}
        aria-label="Open the plugin catalogue"
      >
        <Shapes size={19} strokeWidth={1.7} />
        <span>Plugins</span>
      </button>
    </nav>
  );
}
