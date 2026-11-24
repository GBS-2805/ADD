#!/usr/bin/env node
/**
 * The 20-layer project-open frame case, on its own.
 *
 *   npm run perf:open               # rebuild only if a build input is newer than dist/
 *   npm run perf:open -- --build    # always rebuild
 *   npm run perf:open -- --no-build # fail rather than measure a stale dist/
 *
 * `test:e2e:perf` builds the app in its `webServer` command and runs every case,
 * which is right for CI and slow for a one-case loop. This serves `dist/` on a free
 * port and lets Playwright reuse it, so the build is paid only when a source changed
 * and only the open-frame test runs. The spec, config and thresholds are unchanged.
 */
import { spawn } from "node:child_process";
import fs from "node:fs/promises";
import { createServer } from "node:net";
import path from "node:path";
import { fileURLToPath } from "node:url";

const EDITOR = path.resolve(fileURLToPath(new URL("..", import.meta.url)));
const CASE = "twentyLayerProjectOpensWithinOneFrameAtFourTimesCpu";
const RESULT_CASE = "20-layers-open-1440x900";
const RESULT_FILE = path.join(EDITOR, "test-results", "editor-perf.json");
const DIST_INDEX = path.join(EDITOR, "dist", "index.html");
const BUILD_INPUTS = [
  "src",
  "public",
  "index.html",
  "vite.config.ts",
  "package.json",
  "package-lock.json",
  "tsconfig.json",
  "tsconfig.app.json",
  "tsconfig.node.json",
];

const delay = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

function run(command, commandArgs, options = {}) {
  return new Promise((resolve, reject) => {
    const child = spawn(command, commandArgs, { cwd: EDITOR, stdio: "inherit", ...options });
    child.on("error", reject);
    child.on("exit", (code, signal) => resolve(code ?? (signal ? 1 : 0)));
  });
}

/** Newest mtime at `target`, or 0 when it does not exist. */
async function newestMtime(target) {
  const stat = await fs.stat(target).catch(() => null);
  if (!stat) return 0;
  if (!stat.isDirectory()) return stat.mtimeMs;
  let newest = 0;
  for (const entry of await fs.readdir(target)) {
    if (entry === "node_modules" || entry === "dist") continue;
    newest = Math.max(newest, await newestMtime(path.join(target, entry)));
  }
  return newest;
}

/**
 * Why `dist/` predates a build input, or null when it is current. A content hash
 * would survive a restore that keeps old timestamps, but every restore here drops
 * `dist/` as build output, so timestamps are the signal that decides a rebuild.
 */
async function staleReason() {
  const built = await newestMtime(DIST_INDEX);
  if (built === 0) return "dist/index.html is missing";
  let sources = 0;
  for (const input of BUILD_INPUTS) {
    sources = Math.max(sources, await newestMtime(path.join(EDITOR, input)));
  }
  return sources > built ? "a build input is newer than dist/" : null;
}

function portIsFree(port) {
  return new Promise((resolve) => {
    const server = createServer();
    server.once("error", () => resolve(false));
    server.once("listening", () => server.close(() => resolve(true)));
    server.listen(port, "127.0.0.1");
  });
}

/** A port no other preview is holding, so a stale server is never measured. */
async function freePort() {
  for (let port = 4173; port < 4190; port += 1) {
    if (await portIsFree(port)) return port;
  }
  throw new Error("no free port between 4173 and 4189");
}

async function waitForPreview(url, child, timeoutMs = 60_000) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    if (child.exitCode !== null) {
      throw new Error(`vite preview exited with ${child.exitCode} before answering`);
    }
    try {
      const response = await fetch(url, { signal: AbortSignal.timeout(1_000) });
      if (response.ok) return;
    } catch {
      // Not listening yet.
    }
    await delay(250);
  }
  throw new Error(`vite preview did not answer at ${url} within ${timeoutMs / 1_000}s`);
}

async function stop(child) {
  if (!child || child.exitCode !== null) return;
  child.kill("SIGTERM");
  await Promise.race([new Promise((resolve) => child.once("exit", resolve)), delay(5_000)]);
  if (child.exitCode === null) child.kill("SIGKILL");
}

async function report() {
  let results;
  try {
    results = JSON.parse(await fs.readFile(RESULT_FILE, "utf8"));
  } catch {
    console.error("perf-open: no test-results/editor-perf.json; this run wrote no evidence.");
    return false;
  }
  const result = Array.isArray(results)
    ? results.find((entry) => entry.case === RESULT_CASE)
    : undefined;
  if (
    !result ||
    !Array.isArray(result.longTasks) ||
    ![result.count, result.p95Ms, result.maxMs].every(Number.isFinite)
  ) {
    console.error(`perf-open: this run wrote no ${RESULT_CASE} evidence in test-results/editor-perf.json.`);
    return false;
  }
  const longTasks = result.longTasks.length
    ? `${result.longTasks.length} (max ${Math.max(...result.longTasks).toFixed(1)} ms)`
    : "0";
  console.log(
    `\nopen frame  ${result.case} @ ${result.throttle}x CPU  ` +
      `${result.count} frames  p95 ${result.p95Ms.toFixed(1)} ms  max ${result.maxMs.toFixed(1)} ms  ` +
      `long tasks ${longTasks}`,
  );
  console.log(`evidence    test-results/editor-perf.json\n`);
  return true;
}

async function main() {
  const flags = new Set(process.argv.slice(2));
  const forceBuild = flags.has("--build");
  const noBuild = flags.has("--no-build");
  if (forceBuild && noBuild) {
    console.error("perf-open: --build and --no-build are mutually exclusive.");
    process.exitCode = 2;
    return;
  }

  const began = Date.now();
  let preview;
  try {
    const stale = await staleReason();
    if (noBuild) {
      if (stale) {
        console.error(`perf-open: refusing to measure a stale build (${stale}); drop --no-build.`);
        process.exitCode = 2;
        return;
      }
      console.log("perf-open: reusing the current dist/ (--no-build).");
    } else if (forceBuild || stale) {
      console.log(`perf-open: building (${stale ?? "--build"}).`);
      if ((await run("npm", ["run", "build"])) !== 0) {
        console.error("perf-open: the build failed; not measuring a partial bundle.");
        process.exitCode = 1;
        return;
      }
    } else {
      console.log("perf-open: dist/ is current; skipping the build.");
    }

    const port = await freePort();
    const url = `http://127.0.0.1:${port}/`;
    const vite = path.join(EDITOR, "node_modules", ".bin", process.platform === "win32" ? "vite.cmd" : "vite");
    preview = spawn(
      vite,
      ["preview", "--host", "127.0.0.1", "--port", String(port), "--strictPort"],
      { cwd: EDITOR, stdio: ["ignore", "pipe", "pipe"] },
    );
    let previewLog = "";
    preview.stdout.on("data", (chunk) => (previewLog += chunk));
    preview.stderr.on("data", (chunk) => (previewLog += chunk));
    try {
      await waitForPreview(url, preview);
    } catch (error) {
      if (previewLog.trim()) console.error(previewLog.trim());
      throw error;
    }

    // Evidence is read after the run, so a leftover file from an earlier run would
    // let one that dies before measuring print those numbers as its own.
    await fs.rm(RESULT_FILE, { force: true });
    const code = await run(
      "npx",
      ["--no-install", "playwright", "test", "--config=playwright.performance.config.ts", "--grep", CASE],
      { env: { ...process.env, PERF_PORT: String(port) } },
    );
    const measured = await report();
    // A green Playwright exit with no numbers is not a measured pass.
    const failed = code !== 0 || !measured;
    console.log(`perf-open: ${failed ? "FAIL" : "PASS"} in ${((Date.now() - began) / 1_000).toFixed(0)}s.`);
    process.exitCode = failed ? code || 1 : 0;
  } finally {
    await stop(preview);
  }
}

try {
  await main();
} catch (error) {
  console.error(`perf-open: ${error instanceof Error ? error.message : error}`);
  process.exitCode = 1;
}
