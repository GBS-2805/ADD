import { EditorProvider } from "./editor-core/store";
import { EditorShell } from "./editor/EditorShell";

export default function App() {
  return (
    <EditorProvider>
      <EditorShell />
    </EditorProvider>
  );
}
