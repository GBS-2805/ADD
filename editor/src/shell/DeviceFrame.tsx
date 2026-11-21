import type { ReactNode } from "react";
import type { Layout } from "../editor-core/types";

export function DeviceFrame({ layout, children }: { layout: Layout; children: ReactNode }) {
  return (
    <div className={`device-frame device-${layout}`} data-testid={`device-frame-${layout}`}>
      <div className="device-speaker" aria-hidden="true" />
      <div className="device-screen">{children}</div>
      <div className="device-hairline" aria-hidden="true" />
    </div>
  );
}
