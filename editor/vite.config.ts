import { defineConfig } from "vitest/config";
import react from "@vitejs/plugin-react";

const allowedHost = process.env.VITE_ALLOWED_HOST;

/**
 * Where Editor is served from. A bare `vite` run and the test suite serve it at the
 * root, but on the shelf it lives at /editor/ next to the site's own /assets/ folder --
 * without the prefix its build would overwrite the shelf's asset directory. The APK
 * sets no EDITOR_BASE and loads the bundle from /editor/, so its default is relative
 * to index.html rather than the origin root.
 */
const base = process.env.EDITOR_BASE ?? "./";

export default defineConfig({
  base,
  plugins: [react()],
  server: {
    host: "0.0.0.0",
    port: 3000,
    allowedHosts: allowedHost ? [allowedHost] : [],
  },
  preview: {
    host: "0.0.0.0",
    port: 3000,
    allowedHosts: allowedHost ? [allowedHost] : [],
  },
  test: {
    environment: "jsdom",
    setupFiles: "./src/test/setup.ts",
    include: ["src/**/*.{test,spec}.{ts,tsx}"],
    css: true,
    globals: true,
  },
});
