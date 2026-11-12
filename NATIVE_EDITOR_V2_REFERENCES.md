# Native Editor v2 — Reference Audit

**Status:** Task 0b — 5 of 5 mandatory references complete; audit awaiting review.

**Group-scaling decision:** non-uniform group scaling is supported; child `skewX` is required.

**Date:** 2026-11-11

**Scope:** group transforms, group selection, group/ungroup and undo/redo. This is planning material only. No production code was changed.

**Method:** shallow clones under `artifacts/reference-src/` (gitignored scratch), read against the pinned commits below. Fabric.js, Excalidraw and Android-Image-Cropper are MIT or Apache-2.0; Glaxnimate is GPL-3.0+ and was read only; SceneView 2.3.0 came from its `v2.3.0` tag. The notes describe behaviour and formulas; no source code was copied into the app.

## 1. Pinned references

| Reference | Commit | Version | Licence | Files read |
|---|---|---|---|---|
| Fabric.js | `ab604137e04dd7d3d566462a6b27f5863e37c60b` | `7.5.0-beta.1` | MIT | `packages/core/src/shapes/Group.ts`, `shapes/Object/ObjectGeometry.ts`, `util/misc/objectTransforms.ts`, `util/misc/matrix.ts`, `LayoutManager/LayoutManager.ts`, `LayoutStrategy.ts`, `FitContentLayout.ts`, `Group.spec.ts` |
| Excalidraw | `701b901a0f1ba3d0b390d9cdf63d112e25abac4b` | `0.18.0` | MIT | `packages/element/src/groups.ts`, `selection.ts`, `packages/excalidraw/actions/actionGroup.tsx`, `history.ts`, `tests/history.test.tsx`, `tests/contextmenu.test.tsx` |
| Glaxnimate | `3d4c655a094c82fe69588ad73f594b26ec281e4e` | `0.6.0` line (`master`) | GPL-3.0+ | `src/core/glaxnimate/model/transform.cpp`, `model/transform.hpp`, `io/svg/animate_parser.hpp`, `src/gui/widgets/dialogs/follow_path_dialog.cpp` |
| Android-Image-Cropper | `e10601a1a8386efc7b36de1311259274c1a89956` | `4.7.0` | Apache-2.0 | `cropper/src/main/kotlin/com/canhub/cropper/CropImageView.kt`, `BitmapUtils.kt`, `CropImageOptions.kt`, `BitmapLoadingWorkerJob.kt` |
| SceneView | `cac3d95410cdacb577de199543a5a3c78c046d38` (tag `v2.3.0`) | `2.3.0` | Apache-2.0 | `sceneview/src/main/java/io/github/sceneview/Scene.kt`, `SceneView.kt`, `node/Node.kt`, `node/ModelNode.kt`, `node/CameraNode.kt`, `math/Math.kt` |

Pinned on 2026-11-11. Fabric commit date: 2026-11-10. Excalidraw commit date: 2026-11-05. SceneView was pinned to the tag the app actually resolves (`2.3.0`), not to the repository's current `main` (`9b33f31a0daab284f7255d69290b921ec5b0e05a`, `4.67.0`), which is a different API line and was read only to establish churn.

## 2. Fabric.js: a group is a real transform container

### 2.1 What the code does

- `Group` extends `FabricObject`; it owns its own position, size, scale and rotation and contains children in `_objects` (`packages/core/src/shapes/Group.ts:82`).
- `ObjectGeometry.calcTransformMatrix()` composes the parent group matrix with the object's own matrix (`ObjectGeometry.ts:487-507`):

  ```text
  world = groupWorld * own
  ```

- `getXY()` and `setXY()` convert between group-local and artboard coordinates using the group matrix and its inverse (`ObjectGeometry.ts:148-176`).
- On entry, Fabric moves a child into group space with:

  ```text
  local = inverse(groupWorld) * childWorld
  ```

  (`Group._enterGroup`, `Group.ts:378-389`).

- On exit, Fabric restores world space with:

  ```text
  world = groupWorld * childLocal
  ```

  (`Group._exitGroup`, `Group.ts:426-436`).

- `applyTransformToObject()` decomposes the resulting matrix through `qrDecompose`, then sets position by the object's centre (`objectTransforms.ts:54-67`; `matrix.ts:132-143`). This is the important caveat: `qrDecompose` can produce `skewX`, not only rotation and scale.
- The default layout is `FitContentLayout` (`LayoutManager.ts:51`). It computes a bounding box from transformed object bounds (`LayoutStrategy.calcBoundingBox`), and `LayoutManager.layoutObject()` offsets children into the new group centre (`LayoutManager.ts:287-299`).
- `Group.toObject()` serializes child objects plus the layout manager (`Group.ts:581`). Children serialise in group-local coordinates.
- Fabric's own tests assert fit-content position/size behaviour (`Group.spec.ts:192-210`). They also contain a known `width=31` vs expected `30` TODO around the clip-path layout (`Group.spec.ts:300-308`); do not copy those exact numbers as a spec.

### 2.2 What this means for native v2

**The plan's `GroupLayer` is closer to Fabric than to Excalidraw.** It should stay a real transform container with children in group-local coordinates. The group must be able to render and hit-test through its own matrix, not just draw its children at their old artboard coordinates.

The audit found two concrete gaps in the then-current plan; both are now addressed by the non-uniform decision.

1. **`GroupLayer` had no scale.** It had `x`, `y`, `w`, `h`, `rotation`, `opacity` and `style`, but no `scaleX`/`scaleY` or affine transform. A group could be moved and rotated, but the Review Focus also says “scaling the group”; resizing `w/h` alone cannot preserve child-relative geometry.
2. **`Layer` had no skew.** Fabric resolves a non-uniform group scale combined with rotation through `skewX`. The native `Layer` model had only `rotation`. A rotated child inside a non-uniformly scaled group cannot always be represented exactly by copying Fabric's decomposition.

**Decision:** native v2 supports non-uniform group scaling. `GroupLayer` gets `scaleX`/`scaleY`, and every layer gets `skewX`. The group transform is an affine transform, not a rotation-and-uniform-scale shortcut.

A non-uniformly scaled, rotated group produces shear in a child's local space. Without a skew term, ungrouping loses geometry. Fabric's `qrDecompose` confirms this: it returns `skewX`, and `applyTransformToObject` applies it. The native model must do the same.

For 3D layers, the group matrix must be applied to the Filament node as a matrix. If that cannot preserve the shear on the target stack, block non-uniform group scaling for that group with a clear message and test; do not silently approximate.

Fabric also reflows a group when children are added or removed. The plan must choose one policy:

- **Fixed after creation:** group bounds and transform stay fixed; adding/removing a child is an explicit layout operation.
- **Reflow:** group bounds recompute after every add/remove, and the group origin may move.

Task 2 currently implies a fixed union at grouping time. That is simpler; make it explicit and test it.

### 2.3 Borrow, don't copy

**Borrow:** the matrix order, group-local child coordinates, group-as-object, fit-content bounds at creation, and the inverse/forward entry/exit formulas.

**Do not copy:** the full `LayoutManager` abstraction, active-selection layout, dynamic reflow by default, or exact test dimensions. Also do not copy Fabric's QR decomposition without first deciding how to represent skew.

## 3. Excalidraw: groups are membership IDs, not containers

### 3.1 What the code does

- An element stores a `groupIds` array. Nested groups are ordered IDs; the last ID is the innermost group. There is no group object and no group transform (`packages/element/src/groups.ts`).
- `selectGroup()` requires at least two elements, sets `selectedGroupIds[groupId] = true`, and expands `selectedElementIds` to every member (`groups.ts:20-60`). It does not select a one-element group as a group.
- `editingGroupId` is the state that lets the user enter a group and edit its children. `getSelectedGroupForElement()` ignores the editing group, and `isSelectedViaGroup()` suppresses individual selection borders when the group is selected (`groups.ts:66-92`).
- `actionGroup` creates a new group ID, inserts it with `addToGroup()` (before the editing group or at the end), reorders the selected elements to the highest selected z-order, and calls `selectGroup()` (`actionGroup.tsx:87-200`). It performs **no coordinate transform**.
- `actionUngroup` removes the selected group IDs from each element's `groupIds` and updates selection (`actionGroup.tsx:218-304`). It also performs **no coordinate transform**.
- Selection is app state, not a group node: `selectedGroupIds`, `editingGroupId`, and `selectedElementIds` are separate fields. `getSelectedElements()` reads the element ID set (`selection.ts:157-216`).
- History stores inverse deltas. `record()` ignores empty deltas (`history.ts:124-139`). Redo is cleared only when the element delta is non-empty, not on selection-only changes (`history.ts:134-140`). Undo skips entries with no visible change (`history.ts:198-202`).
- The group-selection behaviour is covered by tests: double-click enters a group and undo/redo restores `editingGroupId` and `selectedGroupIds` (`tests/history.test.tsx:1310-1430`); context-menu group/ungroup tests assert group IDs and empty selection after ungroup (`tests/contextmenu.test.tsx:550-605`).

### 3.2 What this means for native v2

**Do not use Excalidraw's flat `groupIds` as the document model.** It cannot carry a group transform, group opacity, masks or a real layer-tree parent. It is also not a group layer in the render tree; it is a selection grouping.

**Do borrow four behaviours for Task 2:**

1. **Entering a group.** The plan has `selectedIds` but no `editingGroupId`/entered-group state. Without it, tapping a group can only select the whole group; the user cannot edit a child. Add a double-tap-to-enter model, with Escape/back to leave, and a visible group frame rather than per-child handles while the group is selected.
2. **Group selection versus multi-select.** “Two selected layers” and “one selected group” are not the same thing. Add group-selection state, or derive the selected group from the primary selection, so the stage can show one group frame and the layer tree can highlight the group row.
3. **No-op history.** Grouping already-grouped layers, ungrouping an empty selection, or changing selection should not create a document undo entry. Selection-only changes must not clear the redo stack. The plan's snapshot undo needs an explicit no-op/selection-only guard.
4. **Z-order.** Group at the highest selected child index; ungroup at the group's index. Excalidraw explicitly reorders to the highest selected element. The plan currently says “reinserting at the group's index” only for ungroup; add the group-side rule and a test.

### 3.3 Reject

- Flat `groupIds` as the only group representation.
- Excalidraw's delta-history implementation; the native document has few layers and an `EditorSnapshot` is simpler.
- Excalidraw's frame/bound-text/container rules; they solve problems v2 does not have.

## 4. Decision for Task 2

**Use a Fabric-style real `GroupLayer` for the document, with Excalidraw-style group entry and history semantics.**

The group transform should be explicit:

```text
world(child) = G * local(child)
local(child) = inverse(G) * world(child)
ungroup: local(child) = G * local(child)
```

`G` is `T(center) * R(rotation) * SkewX(skewX) * S(scaleX, scaleY)` around the group centre. It must include translation, rotation, non-uniform scale and skew. The group's visible bounds are the union of transformed child bounds, not the union of untransformed child rectangles. Grouping converts children into `G`-local space; ungrouping converts them back and preserves world positions, including `skewX`.

### Plan amendments

**Applied by the non-uniform decision:**

1. Add `scaleX`/`scaleY` and `skewX` to `GroupLayer`.
2. Add `skewX` to every `Layer` type so ungrouping can preserve shear.
3. Use one affine helper for world/local conversion and bounds in the painter, compositor and hit-testing.
4. Add tests for a rotated child under non-uniform group scale, ungroup skew preservation, nested groups, save/load and undo.
5. Treat a 3D layer under a skewed group as a device-gate case: apply the matrix or block the combination visibly.

**Applied after this read:**

6. `editingGroupId`, the derived `selectedGroupId` and the enter/exit commands are in `EngineSelection` (Task 1).
7. Group at the highest selected index, ungroup at the group index (Task 2).
8. The no-op/selection-only history guard, not Excalidraw's delta history (Task 4).

All eight amendments are reflected in `NATIVE_EDITOR_V2.md`, each with named tests.

## 5. Glaxnimate: position follows the path, orientation follows its tangent

### 5.1 What the code does

- `make_transform()` composes, in order, translation by `position`, rotation by `rotation`, an optional tangent rotation, then `scale.x`/`scale.y`, then translation by `-anchor_point` (`src/core/glaxnimate/model/transform.cpp:20-29`):

  ```text
  T(position) * R(rotation) * R(tangent?) * S(scale) * T(-anchor)
  ```

- Auto-orient is not a stored angle. `transform_matrix_with_anchor()` samples `position.derivative_at(f)` only when `auto_orient` is set, so the tangent is recomputed from the animated path at every frame (`transform.cpp:37-52`).
- The SVG importer reads `animateMotion`: a `path` attribute is mandatory, `rotate="auto"` sets `auto_orient`, and anything other than `auto` or `0` is warned about — `auto-reverse` ends up enabled without reversing direction (`src/core/glaxnimate/io/svg/animate_parser.hpp:40-47`, `296-320`).
- The **Follow Path** dialog is a one-shot bake, not a live attachment. It builds an arc-length table over the shape (`math::bezier::LengthData length(bezier, 20)`), maps each Bezier point to a keyframe time by `lerp(start, end, point_length / length.length())`, clears the property's keyframes, adds a smooth keyframe per point, then pushes `SetPositionBezier` — all inside `command::UndoMacroGuard("Follow Path")` (`src/gui/widgets/dialogs/follow_path_dialog.cpp:98-140`).
- The model cannot round-trip shear. `Transform` holds only position, rotation, scale and anchor, and `set_transform_matrix()` decomposes a matrix into position/rotation/scale, discarding the shear term (`transform.cpp:56-87`).

### 5.2 What this means for native v2

**The plan's rigid-translation rule is a deliberate subset of Glaxnimate, and should be recorded as one.** Glaxnimate moves a layer along the path and rotates it to the tangent; native v2 moves the layer only. That is a scope decision, not an oversight: rotation-along-path would have to be derived from the tangent at sample time and must never be written into `rotation`, or it will fight the user's own rotation and break re-anchoring. Keep it out of v2 and say so in Task 3.

**Do not bake, and do not store keyframes.** The Follow Path dialog is exactly the model to reject: it replaces the live property with sampled keyframes, so later edits to the shape or the layer centre leave the keyframes stale. Native v2 keeps the path as live artboard-space data and samples it per frame; the layer centre and the path start stay one thing (Task 3 already requires this).

**Read the arc-length parameterisation, not the keyframes.** The useful part is the mapping from distance along the curve to playback time — the sampler should advance by normalised arc length, not by parameter `t`, or the layer visibly speeds up on long segments. The 20-samples-per-segment table is a cheap way to estimate it; the native sampler can do better with more samples and no table rebuild per frame.

**Glaxnimate is the strongest evidence for `skewX`.** Its transform has no skew field, and `set_transform_matrix()` silently drops the shear component. A non-uniformly scaled, rotated group would come back wrong in that model. Native v2 decided against that loss; this is the second reference (after Fabric's `qrDecompose`) showing why.

Undo needs no change: `UndoMacroGuard` exists because Glaxnimate applies many commands per action, while the native snapshot undo makes "assign a path" one entry for free.

### 5.3 Licence and verdict

GPL-3.0+. **Read-only; no code and no formulas copied.** The affine order and arc-length mapping are standard mathematics; the value of the read is the decision to keep the path live and to leave auto-orient out of v2.

## 6. Android-Image-Cropper: a view matrix over a bitmap, not a document model

### 6.1 What the code does

- `CropImageView` is a `View` inside an `Activity` shell, configured by a large `CropImageOptions` value class. It owns the decoded bitmap and its display matrix.
- The crop overlay rectangle is moved into image space with the inverse of the display matrix (`mImageInverseMatrix.mapRect(cropRect)`, `CropImageView.kt:1358-1366`).
- `cropRect` returns the result in **source bitmap** coordinates: `cropPoints` (four rotated corners) are reduced to an axis-aligned rectangle by `BitmapUtils.getRectFromPoints()`, clamped to the image, and the getter notes the rectangle "may be larger than original if rotation is not straight" (`CropImageView.kt:506-529`, `BitmapUtils.kt:367-382`).
- `getCroppedImage()` returns a **new flattened `Bitmap`**, optionally re-decoded from the source URI at a sample size for memory, then resized (`CropImageView.kt:614-668`).
- EXIF is normalised at decode: `orientateBitmapByExif()` turns `TAG_ORIENTATION` into degrees (0/90/180/270) plus horizontal/vertical flip flags, and the caller applies them (`BitmapUtils.kt:98-122`, `BitmapLoadingWorkerJob.kt:46`).
- `CropImageOptions.noOutputImage` lets the UI run without writing an output file (`CropImageOptions.kt:80`), the closest the library comes to a non-destructive crop — but the crop still lives in the view, not in a document.

### 6.2 What this means for native v2

**Do not take the dependency for the editor.** It owns the image, returns a new bitmap, and has no place to keep a crop rect through save, undo, re-edit or a group transform. The plan's `ImageCrop` on `ImageLayer` is the right shape, and the reference confirms the two pieces worth copying: source-space normalisation and inverse-matrix handle mapping.

**Adopt:**

1. **Store the crop in the decoded, EXIF-normalised source space.** The app already applies EXIF rotation on decode, so the document rect is relative to the pixels the painter sees. Recording it pre-EXIF would rotate the crop on reload.
2. **Map handles with the inverse display matrix**, as `applyImageMatrix()` does, so the overlay and the painted result cannot disagree at any zoom.
3. **Apply crop as a source/destination rect pair** when drawing, never as a copied bitmap per edit — this is what keeps the crop non-destructive and what Task 6 already requires.
4. **Fill/fit operates on the cropped source**, so switching fill does not reveal pixels the crop removed.

**Reject:** the quad-to-bounding-box step. It is lossy the moment the crop is not axis-aligned, and v2's X/Y/W/H sliders are axis-aligned anyway. If rotated crop handles arrive later, store the four points and a rotation, not a `Rect`.

**Do not rely on the sample-size decode path.** The editor already caps at 2048 px; keep the single cached decode keyed by path and mtime (Task 6) rather than adding a second sampling pipeline.

### 6.3 Licence and verdict

Apache-2.0, so adaptation is possible, but the verdict is **read-only reference**: no dependency, no Activity, no bitmap-returning API. Apache-2.0 notice obligations would apply to any copied code; none was copied.

## 7. SceneView 2.3.0: SurfaceView behind a Compose host, Mat4 nodes

### 7.1 What the code does

- `SceneView` is a `SurfaceView` subclass (`SceneView.kt:225`). The Compose `Scene()` composable wraps it in an `AndroidView`, sets camera, scene, child nodes, gesture and frame callbacks in `update`, and calls `sceneView.destroy()` in `onRelease` (`Scene.kt:203-250`).
- `rememberEngine()` creates one EGL context and one `Engine` per composition and destroys both in `DisposableEffect` (`Scene.kt:317-331`).
- `setCameraNode()` binds the camera to the view (`view.camera = cameraNode.camera`) and swaps collision systems (`SceneView.kt:582-590`).
- Lifecycle: `onAttachedToWindow` resolves the `ViewTreeLifecycleOwner`; `onDetachedFromWindow` calls `destroy()` unless already destroyed; a lifecycle observer forwards `onResume`/`onPause` to the renderer (`SceneView.kt:751-766`, `894-910`).
- Node transforms are matrices: `Transform` is a `Mat4` (`math/Math.kt:37`), `Node.transform` is the local matrix, and `worldTransform` is set through the parent's inverse (`node/Node.kt:286-302`). Nodes can also be parented through Filament (`parentEntity`, `parentInstance`, `Node.kt:306-345`).
- `ModelNode` wraps a loaded `ModelInstance` with `scaleToUnits`/`center` helpers (`node/ModelNode.kt`).

### 7.2 What this means for native v2

**A skewed 2D group is representable in the 3D layer.** Because the node's transform is a `Mat4`, `T * R * SkewX * S` can be handed to Filament directly rather than approximated. So the plan's 3D-under-skew rule can be "apply the matrix", with the device gate covering whether Filament renders it as expected — not "block the combination" by default. Keep the visible-block fallback for the case the device gate fails.

**Do not map the document group tree onto Filament parents for v2.** The 2D compositor already flattens; adding `parentEntity` for 3D only would create two transform paths for one group. Apply the composed matrix to each 3D node instead, and revisit parenting only if double-transform bugs appear.

**The host contract is the part to copy:** one engine per stage composition, destroyed on release; the camera bound through the view rather than passed into render calls; gestures kept in the Compose layer above the `SurfaceView`, because `SceneView.onTouchEvent` hit-tests the collision system and then feeds its own camera gesture detector (`SceneView.kt:768-790`). The plan's touch arbitration must sit above that, not compete with it.

**Version churn is real and must stay out of v2.** Tags `v2.3.1`–`v2.3.4` exist, and the diff to `v2.3.4` is not cosmetic: Filament moves 1.66.0 → 1.68.2, and `v2.3.4` bounds the teardown so destroying a `SceneView` no longer waits for the render backend on the main thread — which is exactly the call Compose's `onRelease` makes. Current `main` is `4.67.0` on Filament 1.77+ with a different `Scene`/`rememberEngine` surface, so an upgrade is a migration.

### 7.3 Verdict

**Keep as a dependency at 2.3.0; do not fork.** Treat a 2.3.4 bump as its own time-boxed spike (60-test baseline plus a device check), because it changes Filament and the destroy path at once. Never jump to 4.x inside v2.

## 8. Task 0b status

All five mandatory references are pinned and reviewed: Fabric.js and Excalidraw for groups (sections 2–4), Glaxnimate for motion (5), Android-Image-Cropper for crop (6), SceneView for lifecycle and 3D transforms (7).

**Plan amendments applied by this round:** the SceneView reference row now names the audited tag and the 2.3.4 destroy fix; Task 3 records that v2 keeps rigid translation, no auto-orient and no baked keyframes; Task 6 records that the crop is stored post-EXIF in source space and stays axis-aligned.

**Applied after this round (Fabric/Excalidraw findings):**

1. `editingGroupId` and the derived `selectedGroupId` in `EngineSelection`, with tap/double-tap/Back entry and exit, tested by `tappingAChildOfAGroupSelectsTheGroup`, `doubleTapEntersTheGroupAndSelectsTheChild`, `backLeavesOneGroupAndSelectsIt`, `enteringOrLeavingAGroupIsNotADocumentEdit` and `selectedGroupIdNeedsTheWholeSelectionInOneGroup`.
2. Group/ungroup z-order, tested by `groupingInsertsTheGroupAtTheHighestSelectedIndex`, `ungroupingReinsertsChildrenAtTheGroupIndex`, `ungroupingKeepsTheChildrensRelativeOrder` and `groupingSelectsTheGroupAndUngroupingLeavesIt`.
3. The no-op/selection-only history guard, tested by `changingSelectionAddsNoUndoEntry`, `changingSelectionKeepsTheRedoStack`, `groupingAlreadyGroupedLayersIsANoOp`, `ungroupingWithNothingSelectedIsANoOp` and `undoRestoresTheEditingGroupAndSelection`.

Task 0b is complete on paper; the only thing left is the review of these two documents.

## Task 0c compatibility result

Compose BOM `2025.02.00` pins `androidx.compose.material3:material3` at **1.3.1**. Inspecting the published `material3-android-1.3.1.aar` shows `SegmentedButton` and `ModalBottomSheet`, but **no** `HorizontalFloatingToolbar`, `SplitButton` or `MotionScheme`. Those are the expressive components Task 7 needs, and they are not available at the compatible version. The spike therefore fails without upgrading Compose/Kotlin/SceneView, which this plan forbids. Decision: keep the current toolchain and implement `EngineFloatingToolbar`, `EngineSheet`, `EngineSplitButton` and `EngineSegmentedControl` as custom M3-behaviour components on the ADD tokens. No Material 3 dependency is added.

The local clones are disposable scratch. No dependencies were installed, no engine tests were run for this audit, and no app code or APK was changed.
