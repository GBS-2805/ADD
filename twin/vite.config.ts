import { defineConfig } from "vitest/config";
import react from "@vitejs/plugin-react";

const allowedHost = process.env.VITE_ALLOWED_HOST;

export default defineConfig({
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
