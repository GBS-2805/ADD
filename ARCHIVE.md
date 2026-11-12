# Switching the shelf to newsprint

**Status:** shipped. The newsprint site is live at
**https://artbase-olive.vercel.app**, and the original shelf is live beside it at
**`/classic/`** on the same host. The old shelf is frozen at the local tag
`classic-3.8` (commit `2c1cbff`). All eleven `tools/check_all.py` gates are green.

## The decision

The site moves to one printed edition: newsprint cream `#E9E0CB`, ink `#15120E`,
stamp red `#DD4124`. No palette chips, no light/dark switch, no acid green. The
wordmark is the masthead; the ring is a favicon, app icon or colophon, never a centred
header seal, poster subject or repeated hero object.

The archive is the insurance policy, not a second live redesign: the old shelf keeps
its own pages, assets and APK byte for byte under `/classic/`.

## What shipped

1. **Froze the OG shelf.** Tag `classic-3.8` at `2c1cbff`, the last commit of the
   palette design.
2. **Built the classic archive.** `tools/archive_classic.py` reads `docs/` out of that
   tag, never the dirty worktree, and writes `docs/classic/`. It adds a noindex meta and
   a small banner back to `/` on every page, and omits the old `robots.txt`,
   `sitemap.xml` and `vercel.json`.
3. **Shipped the newsprint site.** Deployed `docs/` with
   `python3 tools/deploy_vercel.py` (commit `6c3cff3`); the deploy verified 532 routes
   on the new host before rewriting the origin in `tools/site.json` and the app.
4. **Archived the previous release.** `docs/classic/ADD-3.7.apk` holds the 3.7 official
   APK recovered from git (commit `e6ca488`), beside the original 3.8 APK.
5. **Guarded the links.** `tools/check_links.py` resolves every internal `href`/`src`
   under `docs/`, including the classic tree; the Vercel deploy refuses a dead link
   (commit `234446f`) and every push runs the same gate through `check_all.py`
   (commit `ecf5c1a`).
6. **Guarded the archive.** `tools/archive_classic.py --check` proves the snapshot is
   still 16 noindex pages with its banner and old APK (commit `2b1f4bc`).
7. **Published the handoff.** **https://artbase-handoff.vercel.app/** carries both
   builds, the archive door and the gate results.

## Built and verified (10 November 2026)

- **The shelf is 32 pages:** 16 current pages and 16 classic pages. The deploy payload
  is 551 files / 58.07 MiB, and the route check covers 532 routes. The link check sees
  785 internal references across those 32 pages with 0 broken.
- **The classic archive is 33 MB on disk.** It has the original `ADD-3.8.apk`
  (6,181,148 bytes, SHA-256
  `fe0148e5e0904654b67ba03e24ac6e8522377828c2e7ca7645adee0e0778beed`, identical to
  the tag and to `docs/ADD-3.8.apk`) and the archived `ADD-3.7.apk` (5,979,036 bytes,
  SHA-256 `3bc5bcada95c4cd5c6193cb5879e602c7943aa9a3c9d3e4eb1c2abfd52ad2206`).
- **Every archive page** carries `<meta name="robots" content="noindex, nofollow">` and
  the `ADD · Classic shelf, 3.8` banner. `docs/vercel.json` adds
  `X-Robots-Tag: noindex, nofollow` for `/classic/(.*)` as a second lock.
- **The current generators ignore the archive.** `build_site.py` still generates 16
  current pages and excludes `/classic/` from `pages.json`, the sitemap and search, while
  its route gate still sees the archived files for deploy-time content-type checks.
  `hash_assets.py` skips `docs/classic/`, so the old hashed CSS/JS stay exactly as they
  shipped. `build_catalog.py` excludes the frozen tree from its count audit, because the
  archived release notes keep the numbers they shipped with.
- **Live checks:** `/` and `/classic/` return `200 text/html`; both APKs return `200`
  with `application/vnd.android.package-archive`; the handoff returns `200 text/html`.
- **The deploy gate** runs the storage report, the palette check, the asset-hash check
  and the link check before it uploads anything, and verifies every route afterwards.
- **`check_all.py`** covers palette, asset hashes, internal links, site tree, classic
  archive, catalog, packs, Figma, Pantone, note dating and the staged bundle: 11 gates,
  all green.

## The archive shape

The classic shelf lives at **`/classic/`** on the same domain. A second rented domain
is unnecessary and would be another thing to lose.

`docs/classic/` is a self-contained snapshot:

- every old page, including `icons/`, `fonts/`, `tools/`, `portfolio/`, `download/`,
  `renders/`, `figma/`, `releases/`, `search/` and `compress/`;
- the old hashed CSS and JS, old fonts, old portals, old packs and the old APKs;
- the old relative paths, preserved by keeping the same folder structure under
  `/classic/`.

The archive is never "modernised": the old pages keep their markup and numbers, the old
APK stays the old file, and only the banner and noindex meta are added. Rebuild or
re-verify it with:

```bash
python3 tools/archive_classic.py          # rebuild from tag classic-3.8
python3 tools/archive_classic.py --check  # prove the checked-in snapshot
```

## Rollback

- If the new site fails after a deploy, redeploy the `classic-3.8` tree or restore
  `docs/` from that tag. The old APK and pages come back with it.
- Do not delete the old Vercel deployment, the tag or the archive route.
- The archive remains available even if the new front page is rolled back.

## Open

- The shipped APK still embeds the `vercel.app` host in `classes.dex`. Repointing it at
  a future domain needs a version bump and a re-capture, because the version labels are
  visible ink; the archive APKs keep the host they shipped with.
- The `classic-3.8` tag is local. Push it only when the release is ready to keep it
  permanently.
