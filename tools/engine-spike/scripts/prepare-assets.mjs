#!/usr/bin/env node
/**
 * Build the two shipped 3D layers.
 *
 * This is a build-time path, not a runtime Android dependency:
 *   1. generate the source mesh with make_gem.py or make_ring.py
 *   2. let glTF-Transform dedupe, weld, prune and write a GLB
 *   3. run meshoptimizer's simplifier through glTF-Transform
 *
 * Keeping the decoder out of the APK is intentional. Filament's gltfio loads the
 * resulting plain GLBs; meshopt compression would require a runtime decoder.
 *
 * The gem was reviewed in an earlier APK. If a run would move its bytes, the
 * original file is restored and the run fails instead of silently shipping a
 * different gem.
 */
import { execFileSync } from "node:child_process";
import {
  copyFileSync,
  existsSync,
  mkdirSync,
  readFileSync,
  statSync,
  writeFileSync,
} from "node:fs";
import { createHash } from "node:crypto";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const here = dirname(fileURLToPath(import.meta.url));
const root = resolve(here, "..");
const outputDir = resolve(root, "app/src/main/assets/models");
mkdirSync(outputDir, { recursive: true });

const assets = [
  { name: "gem", label: "gem", script: "make_gem.py", ratio: "0.42", error: "0.002" },
  { name: "ring", label: "ring", script: "make_ring.py", ratio: "0.7", error: "0.001" },
];

const gemPath = resolve(outputDir, "engine_gem.glb");
const gemBefore = existsSync(gemPath) ? readFileSync(gemPath) : null;

for (const asset of assets) {
  const source = resolve(root, `assets-src/${asset.name}.gltf`);
  const optimized = resolve(root, `assets-src/${asset.name}.optimized.glb`);
  const simplified = resolve(root, `assets-src/${asset.name}.simplified.glb`);
  const output = resolve(outputDir, `engine_${asset.name}.glb`);

  console.log(`\n${asset.label}: 1/3 generating source mesh`);
  execFileSync("python3", [resolve(here, asset.script)], { stdio: "inherit" });

  console.log(`${asset.label}: 2/3 glTF-Transform: dedupe, weld, prune, GLB`);
  execFileSync(
    resolve(root, "node_modules/.bin/gltf-transform"),
    [
      "optimize",
      source,
      optimized,
      "--compress", "false",
      "--texture-compress", "false",
      "--simplify", "false",
      "--flatten", "false",
      "--join", "false",
    ],
    { stdio: "inherit" },
  );

  console.log(`${asset.label}: 3/3 meshoptimizer simplify via glTF-Transform`);
  execFileSync(
    resolve(root, "node_modules/.bin/gltf-transform"),
    [
      "simplify",
      optimized,
      simplified,
      "--ratio", asset.ratio,
      "--error", asset.error,
    ],
    { stdio: "inherit" },
  );

  copyFileSync(simplified, output);
  const bytes = statSync(output).size;
  const digest = createHash("sha256").update(readFileSync(output)).digest("hex");
  console.log(`wrote ${output} (${bytes} bytes, sha256 ${digest})`);
}

if (gemBefore) {
  const gemAfter = readFileSync(gemPath);
  if (!gemBefore.equals(gemAfter)) {
    writeFileSync(gemPath, gemBefore);
    throw new Error(
      "engine_gem.glb changed during the asset pipeline; restored the reviewed bytes. " +
        "Inspect the generator and simplify settings before changing the shipped gem.",
    );
  }
  console.log("engine_gem.glb bytes unchanged");
}

console.log("\n3D assets ready");
