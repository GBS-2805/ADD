#!/usr/bin/env node
import { createHash } from "node:crypto";
import { existsSync, readFileSync, readdirSync, statSync } from "node:fs";
import { dirname, extname, join, relative, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const root = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const fail = [];
const note = [];
const allowedNamespace = /^https?:\/\/www\.w3\.org\//;

function read(path) {
  return readFileSync(join(root, path), "utf8");
}

function sha256(path) {
  return createHash("sha256").update(readFileSync(join(root, path))).digest("hex");
}

function walk(directory, extensions) {
  const out = [];
  for (const entry of readdirSync(join(root, directory), { withFileTypes: true })) {
    if (entry.name === "node_modules" || entry.name === "dist") continue;
    const path = `${directory}/${entry.name}`;
    if (entry.isDirectory()) out.push(...walk(path, extensions));
    else if (extensions.includes(extname(entry.name))) out.push(path);
  }
  return out;
}

const manifest = JSON.parse(read("asset-manifest.json"));
const seen = new Set();
const requiredEditorFonts = [
  "src/assets/fonts/archivo-600.woff2",
  "src/assets/fonts/plex-mono-400.woff2",
  "src/assets/fonts/plex-mono-600.woff2",
];
for (const path of requiredEditorFonts) {
  if (!manifest.assets.some((asset) => asset.path === path)) {
    fail.push(`Editor font missing from the manifest: ${path}`);
  }
}

for (const asset of manifest.assets) {
  if (/^[a-z]+:\/\//i.test(asset.path)) {
    fail.push(`Runtime asset is not local: ${asset.path}`);
    continue;
  }
  if (!existsSync(join(root, asset.path))) {
    fail.push(`Missing runtime asset: ${asset.path}`);
    continue;
  }
  if (!asset.source || !/^https:\/\//.test(asset.source)) fail.push(`Missing https source: ${asset.path}`);
  if (!asset.licence) fail.push(`Missing licence: ${asset.path}`);
  if (sha256(asset.path) !== asset.sha256) fail.push(`SHA-256 changed: ${asset.path}`);
  seen.add(asset.path);
}

for (const asset of walk("src/assets", [".woff2", ".ttf", ".otf", ".png", ".jpg", ".jpeg", ".webp", ".svg"])) {
  if (!seen.has(asset)) fail.push(`Local asset has no source/licence/hash entry: ${asset}`);
}

for (const exemption of manifest.exemptions ?? []) {
  if (!existsSync(join(root, exemption.path))) fail.push(`Recorded exemption is missing: ${exemption.path}`);
  if (!exemption.reason) fail.push(`Exemption has no reason: ${exemption.path}`);
}

const lock = JSON.parse(read("package-lock.json"));
if (lock.lockfileVersion !== 3) fail.push(`Unexpected lockfile version: ${lock.lockfileVersion}`);
for (const [name, meta] of Object.entries(lock.packages ?? {})) {
  if (!name.startsWith("node_modules/")) continue;
  if (!meta.version || !meta.integrity) fail.push(`Unpinned dependency: ${name}`);
}

const sourceFiles = [...walk("src", [".ts", ".tsx", ".css"]), "index.html"];
for (const file of sourceFiles) {
  if (file.includes(".test.")) continue;
  const text = read(file);
  for (const match of text.matchAll(/https?:\/\/[^\s"'`<>)]+/g)) {
    if (!allowedNamespace.test(match[0])) fail.push(`Remote runtime URL in ${file}: ${match[0]}`);
  }
  for (const match of text.matchAll(/(?:src|href)\s*=\s*["'](https?:\/\/[^"']+)/g)) {
    fail.push(`Remote document reference in ${file}: ${match[1]}`);
  }
}

for (const file of walk("src/styles", [".css"])) {
  for (const match of read(file).matchAll(/@import\s+(?:url\()?["']?(https?:\/\/[^"')\s]+)/g)) {
    fail.push(`Remote CSS import in ${file}: ${match[1]}`);
  }
  for (const match of read(file).matchAll(/url\(\s*["']?([^"')\s]+)["']?\s*\)/g)) {
    const value = match[1];
    if (/^data:/.test(value)) continue;
    if (/^https?:|^\/\//.test(value)) {
      fail.push(`Remote CSS asset in ${file}: ${value}`);
      continue;
    }
    const assetPath = join(dirname(join(root, file)), value.split(/[?#]/)[0]);
    if (!existsSync(assetPath)) fail.push(`Missing CSS asset in ${file}: ${value}`);
  }
}

if (existsSync(join(root, "dist"))) {
  const builtHtml = read("dist/index.html");
  for (const match of builtHtml.matchAll(/(?:src|href)\s*=\s*["'](https?:\/\/[^"']+)/g)) {
    fail.push(`Remote document reference in dist/index.html: ${match[1]}`);
  }
  note.push("dist/index.html checked for remote script and link references");
}

if (fail.length) {
  console.error("Asset check failed:");
  for (const message of fail) console.error(`  - ${message}`);
  process.exit(1);
}

console.log(`Asset check passed: ${manifest.assets.length} runtime assets have a local path, source, licence and SHA-256.`);
console.log(`Dependency lock: ${Object.keys(lock.packages ?? {}).length} pinned entries.`);
for (const message of note) console.log(message);
