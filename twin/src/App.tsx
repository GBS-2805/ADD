import { TwinProvider } from "./twin/store";
import { TwinShell } from "./shell/TwinShell";
import { EditorShell } from "./editor/EditorShell";

export default function App() {
  const workbench = new URLSearchParams(window.location.search).has("workbench");
  return (
    <TwinProvider>
      {workbench ? <EditorShell /> : <TwinShell />}
    </TwinProvider>
  );
}
