import { defineConfig } from "@playwright/test";

const executablePath = process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE;

/**
 * The drag budget runs against the production bundle, not Vite's development
 * build. Development React adds StrictMode double renders and profiling overhead
 * that dwarf the editor's own commit work, so a release measured there would gate
 * the toolchain instead of the product.
 *
 * Build and preview are separate from the regression config on purpose: this
 * project runs one test at a time so another browser's CPU work cannot become a
 * long drag frame. `npm run test:e2e` covers the regression suite.
 */
export default defineConfig({
  testDir: "./e2e",
  testMatch: "**/editor-task5-performance.spec.ts",
  timeout: 240_000,
  fullyParallel: false,
  workers: 1,
  reporter: "list",
  use: {
    baseURL: "http://127.0.0.1:4173",
    viewport: { width: 1280, height: 1000 },
    trace: "off",
    launchOptions: executablePath ? { executablePath } : {},
  },
  webServer: {
    command: "npm run build && npm run preview -- --host 127.0.0.1 --port 4173",
    url: "http://127.0.0.1:4173",
    reuseExistingServer: true,
    timeout: 180_000,
  },
});
