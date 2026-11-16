import { TwinProvider } from "./twin/store";
import { EditorShell } from "./editor/EditorShell";

export default function App() {
  return (
    <TwinProvider>
      <EditorShell />
    </TwinProvider>
  );
}
