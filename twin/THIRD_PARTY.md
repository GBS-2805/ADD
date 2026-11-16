# Third-party material

The twin bundles third-party JavaScript and local font files. Every dependency is pinned in `package-lock.json`; every runtime local asset has a source, licence and SHA-256 in `asset-manifest.json`.

## Runtime packages

| Package | Version | Licence | Source |
|---|---:|---|---|
| `@ark-ui/react` | 5.40.0 | MIT | https://github.com/chakra-ui/ark |
| `@paper-design/shaders-react` | 0.0.82 | Apache-2.0 | https://github.com/paper-design/shaders |
| `@react-three/drei` | 10.7.9 | MIT | https://github.com/pmndrs/drei |
| `@react-three/fiber` | 9.8.2 | MIT | https://github.com/pmndrs/react-three-fiber |
| `@tsparticles/react` | 4.6.0 | MIT | https://github.com/tsparticles/react |
| `@tsparticles/slim` | 4.6.0 | MIT | https://github.com/tsparticles/tsparticles |
| `@tsparticles/engine` | 4.6.0 | MIT | https://github.com/tsparticles/tsparticles |
| `feral-fur` | 0.1.1 | MIT | https://github.com/feral-fur/feral-fur |
| `lucide-react` | 1.73.0 | ISC | https://github.com/lucide-icons/lucide |
| `motion` | 14.9.0 | MIT | https://github.com/motiondivision/motion |
| `roughjs` | 4.6.6 | MIT | https://github.com/rough-stuff/rough |
| `three` | 0.187.0 | MIT | https://github.com/mrdoob/three.js |
| `vanilla-tilt` | 1.8.1 | MIT | https://github.com/micku7zu/vanilla-tilt.js |

React, React DOM and the Vite/Vitest/TypeScript/Playwright toolchain are development or runtime dependencies recorded in the lockfile.

## Fonts

All fonts are copied into `src/assets/fonts/`; the app makes no font request.

| Family | File | Licence | Source |
|---|---|---|---|
| Archivo 600 | `archivo-600.woff2` | SIL Open Font License 1.1 | https://fonts.google.com/specimen/Archivo |
| IBM Plex Mono 400/600 | `plex-mono-400.woff2`, `plex-mono-600.woff2` | SIL Open Font License 1.1 | https://fonts.google.com/specimen/IBM+Plex+Mono |
| Instrument Serif | `instrument-serif-400.woff2` | SIL Open Font License 1.1 | https://fonts.google.com/specimen/Instrument+Serif |
| Libre Franklin | `libre-franklin-400.woff2` | SIL Open Font License 1.1 | https://fonts.google.com/specimen/Libre+Franklin |
| Poppins 400/500/600 | `poppins-400.woff2`, `poppins-500.woff2`, `poppins-600.woff2` | SIL Open Font License 1.1 | https://fonts.google.com/specimen/Poppins |
| Spectral | `spectral-regular.ttf` | SIL Open Font License 1.1 | https://fonts.google.com/specimen/Spectral |

The SIL Open Font License text is available at https://openfontlicense.org/open-font-license-official-text/.

## Imagery and patterns

No third-party photographs, paper scans, HDRIs, models, shader CDN URLs or pattern packs are bundled. Paper grain and patterns are CSS-generated. The sample PNG in `e2e/fixtures/` is a first-party synthetic fixture used only by the Playwright test and is never served as a runtime asset.
