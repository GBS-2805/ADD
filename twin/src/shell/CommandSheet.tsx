import { useMemo, useState } from "react";
import { Search, X } from "lucide-react";
import { buildCommands } from "../twin/commands";
import { useTwinStore } from "../twin/store";

export function CommandSheet() {
  const store = useTwinStore();
  const [query, setQuery] = useState("");
  const commands = useMemo(() => buildCommands(store), [store]);
  const filtered = commands.filter((command) =>
    `${command.label} ${command.group} ${command.hint ?? ""}`.toLowerCase().includes(query.toLowerCase()),
  );

  return (
    <div className="command-sheet" data-testid="command-sheet">
      <div className="command-search">
        <Search size={18} />
        <input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Search actions" aria-label="Search actions" />
        {query ? <button type="button" className="icon-button" onClick={() => setQuery("")} aria-label="Clear search"><X size={15} /></button> : null}
      </div>
      <div className="command-list">
        {filtered.map((command) => (
          <button
            type="button"
            key={command.id}
            className="command-row"
            onClick={() => {
              command.run();
            }}
          >
            <span>
              <strong>{command.label}</strong>
              {command.hint ? <small>{command.hint}</small> : null}
            </span>
            <em>{command.group}</em>
          </button>
        ))}
        {filtered.length === 0 ? <p className="command-empty">No action matches “{query}”.</p> : null}
      </div>
    </div>
  );
}
