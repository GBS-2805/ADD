import { useEffect, useState } from "react";

const QUERY = "(orientation: landscape) and (max-height: 500px)";

export function useShortLandscape(): boolean {
  const [shortLandscape, setShortLandscape] = useState(() => {
    if (typeof window === "undefined" || typeof window.matchMedia !== "function") return false;
    return window.matchMedia(QUERY).matches;
  });

  useEffect(() => {
    if (typeof window.matchMedia !== "function") return;
    const media = window.matchMedia(QUERY);
    const update = () => setShortLandscape(media.matches);
    update();
    media.addEventListener("change", update);
    return () => media.removeEventListener("change", update);
  }, []);

  return shortLandscape;
}
