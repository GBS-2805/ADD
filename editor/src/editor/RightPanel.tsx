import { Tabs } from "@ark-ui/react";
import { useEditorStore } from "../editor-core/store";
import { InspectorPanel } from "./panels/InspectorPanel";
import { EffectsPanel } from "./panels/EffectsPanel";
import type { RightTab } from "./types";

export function RightPanel() {
  const store = useEditorStore();
  return (
    <Tabs.Root
      className="ed-right-panel"
      value={store.state.rightTab}
      onValueChange={({ value }) => {
        if (value) store.setRightTab(value as RightTab);
      }}
    >
      <Tabs.List className="ed-right-tabs" aria-label="Right panel">
        <Tabs.Trigger value="design">Design</Tabs.Trigger>
        <Tabs.Trigger value="effects">Effects</Tabs.Trigger>
      </Tabs.List>
      <Tabs.Content value="design" className="ed-right-panel-content">
        <InspectorPanel />
      </Tabs.Content>
      <Tabs.Content value="effects" className="ed-right-panel-content">
        <EffectsPanel />
      </Tabs.Content>
    </Tabs.Root>
  );
}
