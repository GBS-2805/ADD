import { expect, test } from "@playwright/test";

const PLUGINS = [
  "shader-lab",
  "texture-lab",
  "particle-field",
  "pattern-library",
  "spatial-dock",
  "native-parity",
  "asset-shelf",
  "overlay-lab",
] as const;

function isNeutral(value: string): boolean {
  const channels = value.match(/\d+(?:\.\d+)?/g);
  if (!channels) return true;
  const [red, green, blue] = channels.map(Number);
  return Math.max(red, green, blue) - Math.min(red, green, blue) <= 10;
}

async function expectNeutral(page: import("@playwright/test").Page, selector: string) {
  const values = await page.locator(selector).first().evaluate((element) => {
    const style = getComputedStyle(element);
    return [style.color, style.backgroundColor, style.borderTopColor];
  });
  for (const value of values) {
    expect(isNeutral(value), `${selector}: ${value}`).toBe(true);
  }
}

function luminance(value: string): number {
  const channels = (value.match(/\d+(?:\.\d+)?/g) ?? []).slice(0, 3).map(Number).map((channel) => {
    const normalized = channel / 255;
    return normalized <= 0.03928 ? normalized / 12.92 : ((normalized + 0.055) / 1.055) ** 2.4;
  });
  const [red = 0, green = 0, blue = 0] = channels;
  return 0.2126 * red + 0.7152 * green + 0.0722 * blue;
}

function contrastRatio(a: string, b: string): number {
  const first = luminance(a);
  const second = luminance(b);
  return (Math.max(first, second) + 0.05) / (Math.min(first, second) + 0.05);
}

function maxDuration(value: string): number {
  return Math.max(...value.split(",").map((part) => {
    const amount = parseFloat(part);
    return part.trim().endsWith("ms") ? amount / 1000 : amount;
  }));
}

async function openCatalogue(page: import("@playwright/test").Page) {
  await page.getByRole("button", { name: "Open the plugin catalogue" }).click();
  await expect(page.getByRole("heading", { name: "Eight ways to make a mark" })).toBeVisible();
}

async function dragBy(
  page: import("@playwright/test").Page,
  locator: import("@playwright/test").Locator,
  dx: number,
  dy: number,
) {
  const box = await locator.boundingBox();
  if (!box) throw new Error("The drag target has no layout box.");
  await page.mouse.move(box.x + box.width / 2, box.y + box.height / 2);
  await page.mouse.down();
  await page.mouse.move(box.x + box.width / 2 + dx, box.y + box.height / 2 + dy, { steps: 12 });
  await page.mouse.up();
}

test("opens on the compact shell with labelled targets", async ({ page }) => {
  await page.setViewportSize({ width: 1280, height: 1100 });
  await page.goto("/");

  await expect(page.getByTestId("device-frame-compact")).toBeVisible();
  await expect(page.getByTestId("device-frame-compact")).toHaveCSS("width", "411px");
  await expect(page.getByTestId("device-frame-compact")).toHaveCSS("height", "891px");
  await expect(page.getByTestId("artboard")).toBeVisible();
  await expect(page.getByRole("button", { name: "Select, close the panel" })).toBeVisible();
  await expect(page.getByRole("button", { name: "Add a layer" })).toBeVisible();
  await expect(page.getByRole("button", { name: "Open actions" })).toBeVisible();
  await expect(page.getByRole("button", { name: "Open the plugin catalogue" })).toBeVisible();
  for (const name of ["Select, close the panel", "Add a layer", "Open actions", "Open the plugin catalogue"]) {
    const box = await page.getByRole("button", { name }).boundingBox();
    expect(box?.width ?? 0).toBeGreaterThanOrEqual(48);
    expect(box?.height ?? 0).toBeGreaterThanOrEqual(48);
  }
  await expect(page.getByRole("heading", { name: "BMO Twin" })).toBeAttached();
});

test("the shell and default artboard use the monochrome palette", async ({ page }) => {
  await page.setViewportSize({ width: 1280, height: 1100 });
  await page.goto("/");

  for (const selector of [".twin-header", ".device-frame", ".artboard", ".dock", ".sample-type", ".sample-orbit"]) {
    await expectNeutral(page, selector);
  }

  const starFill = await page.locator(".sample-star").first().evaluate((element) => getComputedStyle(element).fill);
  expect(isNeutral(starFill)).toBe(true);

  await openCatalogue(page);
  for (const selector of [".catalogue", ".plugin-card", ".reality-badge"]) {
    await expectNeutral(page, selector);
  }

  const statusMarks = await page.locator(".reality-badge").evaluateAll((elements) => elements.map((element) => {
    const style = getComputedStyle(element, "::before");
    return { borderStyle: style.borderStyle, backgroundColor: style.backgroundColor };
  }));
  expect(statusMarks.some((mark) => mark.borderStyle === "dashed")).toBe(true);
  expect(statusMarks.some((mark) => mark.backgroundColor !== "rgba(0, 0, 0, 0)")).toBe(true);
});

test("the headline is legible against its rendered ground", async ({ page }) => {
  await page.setViewportSize({ width: 1280, height: 1100 });
  await page.goto("/");

  const headline = page.getByLabel("Open Studio headline");
  const ground = page.locator('.artboard-ground [data-shader="PaperTexture"]');
  await expect(headline).toBeVisible();
  await expect(ground).toBeVisible();
  const rendered = await headline.evaluate((element) => {
    const style = getComputedStyle(element);
    return { color: style.color, shadow: style.textShadow, background: style.backgroundColor };
  });

  expect(rendered.color).toBe("rgb(17, 17, 17)");
  expect(rendered.background).toBe("rgba(0, 0, 0, 0)");
  expect(rendered.shadow).toContain("rgba(250, 250, 247");
  expect(contrastRatio(rendered.color, "rgb(250, 250, 247)")).toBeGreaterThan(7);

  const inverted = await page.locator(".dock").evaluate((dock) => {
    const label = dock.querySelector(".dock-target span");
    if (!label) throw new Error("The dock has no label.");
    return { color: getComputedStyle(label).color, background: getComputedStyle(dock).backgroundColor };
  });
  expect(contrastRatio(inverted.color, inverted.background)).toBeGreaterThan(4.5);
});

test("addMenuCreatesTextAndShape", async ({ page }) => {
  await page.setViewportSize({ width: 1280, height: 1100 });
  await page.goto("/");
  const add = page.getByRole("button", { name: "Add a layer" });

  await add.click();
  await page.getByRole("menuitem", { name: /^Text/ }).click();
  await expect(page.getByTestId("inspector")).toBeVisible();
  await expect(page.locator(".art-layer-text")).toHaveCount(2);
  await expect(add).toBeFocused();

  await add.click();
  await page.getByRole("menuitem", { name: /^Shape/ }).click({ position: { x: 8, y: 10 } });
  await page.getByRole("menuitem", { name: /^Star/ }).click();
  await expect(page.locator(".art-layer-shape")).toHaveCount(3);
  await expect(page.getByTestId("inspector-kind")).toHaveText("Shape layer");
  await expect(add).toBeFocused();

  await add.click();
  await page.getByRole("menuitem", { name: /^Shape/ }).click({ position: { x: 8, y: 10 } });
  await page.getByRole("menuitem", { name: /^Block/ }).click();
  await expect(page.locator(".sample-block")).toBeVisible();
});

test("compactInspectorIsABottomSheet", async ({ page }) => {
  await page.setViewportSize({ width: 1280, height: 1100 });
  await page.goto("/");
  const closedStage = await page.getByTestId("stage").boundingBox();
  await page.getByRole("button", { name: "Add a layer" }).click();
  await page.getByRole("menuitem", { name: /^Text/ }).click();

  const panelLocator = page.getByTestId("panel-sheet");
  await expect(panelLocator).toBeVisible();
  const stage = await page.getByTestId("stage").boundingBox();
  const panel = await panelLocator.boundingBox();
  const screen = await page.locator(".device-screen").boundingBox();
  const dock = await page.locator(".dock").boundingBox();
  if (!stage || !panel || !screen || !dock || !closedStage) throw new Error("The compact stage, bottom sheet or dock has no layout box.");
  expect(stage.height).toBeLessThan(closedStage.height - 100);
  expect(panel.x).toBeCloseTo(screen.x, 0);
  expect(panel.width).toBeCloseTo(screen.width, 0);
  expect(stage.y + stage.height).toBeCloseTo(panel.y, 0);
  expect(panel.y + panel.height).toBeCloseTo(screen.y + screen.height, 0);
  expect(dock.y).toBeGreaterThan(stage.y);
  expect(dock.y + dock.height).toBeLessThanOrEqual(panel.y + 1);
  expect(panel.height).toBeLessThanOrEqual(1100 * 0.4 + 1);
});

test("the dock is pinned low and the inspector is a bottom sheet on a phone", async ({ page }) => {
  await page.setViewportSize({ width: 411, height: 891 });
  await page.goto("/");

  const screen = await page.locator(".device-screen").boundingBox();
  const dock = await page.locator(".dock").boundingBox();
  const stageBefore = await page.getByTestId("stage").boundingBox();
  if (!screen || !dock || !stageBefore) throw new Error("The screen, dock or stage has no layout box.");
  expect(dock.y + dock.height).toBeGreaterThan(screen.y + screen.height - 24);
  expect(stageBefore.y + stageBefore.height).toBeCloseTo(screen.y + screen.height, 0);
  expect(dock.y).toBeGreaterThan(stageBefore.y);
  expect(dock.y + dock.height).toBeLessThanOrEqual(stageBefore.y + stageBefore.height);

  await page.getByRole("button", { name: "Add a layer" }).click();
  await page.getByRole("menuitem", { name: /^Text/ }).click();
  const panelLocator = page.getByTestId("panel-sheet");
  await expect(panelLocator).toBeVisible();
  const panel = await panelLocator.boundingBox();
  const stage = await page.getByTestId("stage").boundingBox();
  const openDock = await page.locator(".dock").boundingBox();
  if (!panel || !stage || !openDock) throw new Error("The phone bottom sheet, stage or open dock has no layout box.");
  expect(panel.x).toBeCloseTo(screen.x, 0);
  expect(panel.width).toBeCloseTo(screen.width, 0);
  expect(stage.y + stage.height).toBeCloseTo(panel.y, 0);
  expect(panel.y + panel.height).toBeCloseTo(screen.y + screen.height, 0);
  expect(openDock.y + openDock.height).toBeLessThanOrEqual(panel.y + 1);
  expect(stage.height).toBeLessThan(stageBefore.height - 100);
  expect(panel.height).toBeLessThanOrEqual(891 * 0.4 + 1);
});

const SHORT_VIEWPORTS = [
  { name: "390x667 portrait", width: 390, height: 667 },
  { name: "360x640 portrait", width: 360, height: 640 },
  { name: "844x390 landscape", width: 844, height: 390 },
];

for (const viewport of SHORT_VIEWPORTS) {
  test(`short viewport ${viewport.name} keeps the Stage, sheet and dock usable`, async ({ page }) => {
    const consoleErrors: string[] = [];
    const pageErrors: string[] = [];
    page.on("console", (message) => {
      if (message.type() === "error") consoleErrors.push(message.text());
    });
    page.on("pageerror", (error) => pageErrors.push(String(error)));

    await page.setViewportSize({ width: viewport.width, height: viewport.height });
    await page.goto("/");
    await page.getByRole("button", { name: "Add a layer" }).click();
    await page.getByRole("menuitem", { name: /^Text/ }).click();

    const panel = page.getByTestId("panel-sheet");
    await expect(panel).toBeVisible();
    const frame = await page.locator(".device-frame").boundingBox();
    const screen = await page.locator(".device-screen").boundingBox();
    const stage = await page.getByTestId("stage").boundingBox();
    const artboard = await page.getByTestId("artboard").boundingBox();
    const panelBox = await panel.boundingBox();
    const dock = await page.locator(".dock").boundingBox();
    const close = await page.getByRole("button", { name: "Close panel" }).boundingBox();
    if (!frame || !screen || !stage || !artboard || !panelBox || !dock || !close) {
      throw new Error("A short-viewport layout box is missing.");
    }

    expect(frame.width).toBeCloseTo(viewport.width, 0);
    expect(frame.height).toBeCloseTo(viewport.height, 0);
    expect(frame.x).toBeCloseTo(0, 0);
    expect(frame.y).toBeCloseTo(0, 0);
    expect(stage.height).toBeGreaterThanOrEqual(220);
    expect(artboard.height).toBeGreaterThanOrEqual(150);
    expect(panelBox.height).toBeGreaterThanOrEqual(220);

    const overlapWidth = Math.max(0, Math.min(stage.x + stage.width, panelBox.x + panelBox.width) - Math.max(stage.x, panelBox.x));
    const overlapHeight = Math.max(0, Math.min(stage.y + stage.height, panelBox.y + panelBox.height) - Math.max(stage.y, panelBox.y));
    expect(overlapWidth * overlapHeight).toBe(0);
    expect(panelBox.x).toBeGreaterThanOrEqual(screen.x - 1);
    expect(panelBox.x + panelBox.width).toBeLessThanOrEqual(screen.x + screen.width + 1);
    expect(panelBox.y + panelBox.height).toBeCloseTo(screen.y + screen.height, 0);
    expect(dock.y).toBeGreaterThan(stage.y);
    expect(dock.y + dock.height).toBeLessThanOrEqual(stage.y + stage.height);
    if (viewport.width > viewport.height) {
      expect(dock.x).toBeGreaterThanOrEqual(stage.x);
      expect(dock.x + dock.width).toBeLessThanOrEqual(stage.x + stage.width);
    } else {
      expect(dock.y + dock.height).toBeLessThanOrEqual(panelBox.y + 1);
    }
    expect(dock.y + dock.height).toBeLessThanOrEqual(screen.y + screen.height);
    expect(close.x).toBeGreaterThanOrEqual(panelBox.x);
    expect(close.y).toBeGreaterThanOrEqual(panelBox.y);
    expect(close.y + close.height).toBeLessThanOrEqual(panelBox.y + panelBox.height);

    const targets = await page.locator(".dock-target").evaluateAll((elements) =>
      elements.map((element) => {
        const box = element.getBoundingClientRect();
        return { width: box.width, height: box.height, top: box.top, bottom: box.bottom };
      }),
    );
    expect(targets).toHaveLength(4);
    for (const target of targets) {
      expect(target.width).toBeGreaterThanOrEqual(48);
      expect(target.height).toBeGreaterThanOrEqual(48);
      expect(target.top).toBeGreaterThanOrEqual(0);
      expect(target.bottom).toBeLessThanOrEqual(viewport.height);
    }

    const panelContent = page.locator(".panel-content");
    const contentMetrics = await panelContent.evaluate((element) => ({
      clientHeight: element.clientHeight,
      scrollHeight: element.scrollHeight,
      overflowY: getComputedStyle(element).overflowY,
    }));
    expect(contentMetrics.overflowY).toBe("auto");
    expect(contentMetrics.scrollHeight).toBeGreaterThan(contentMetrics.clientHeight);
    await panelContent.evaluate((element) => {
      element.scrollTop = element.scrollHeight;
    });
    const fill = page.locator(".color-trigger");
    await expect(fill).toBeVisible();
    const fillBox = await fill.boundingBox();
    if (!fillBox) throw new Error("The scrolled Fill control has no layout box.");
    expect(fillBox.y).toBeGreaterThanOrEqual(panelBox.y);
    expect(fillBox.y + fillBox.height).toBeLessThanOrEqual(panelBox.y + panelBox.height);

    await page.getByRole("button", { name: "Select, close the panel" }).click();
    await expect(panel).toBeHidden();
    if (viewport.width > viewport.height) {
      const closedStage = await page.getByTestId("stage").boundingBox();
      const closedArtboard = await page.getByTestId("artboard").boundingBox();
      if (!closedStage || !closedArtboard) throw new Error("The closed landscape stage has no layout box.");
      expect(closedStage.width).toBeCloseTo(viewport.width, 0);
      expect(closedArtboard.x + closedArtboard.width / 2).toBeCloseTo(viewport.width / 2, 0);
      const closedStar = await page.getByTestId("art-layer-shape-star").boundingBox();
      const closedOrbit = await page.getByTestId("art-layer-shape-orbit").boundingBox();
      if (!closedStar || !closedOrbit) throw new Error("The closed landscape sample layers have no layout box.");
      expect(closedStar.y).toBeGreaterThanOrEqual(closedArtboard.y);
      expect(closedStar.y + closedStar.height).toBeLessThanOrEqual(closedArtboard.y + closedArtboard.height);
      expect(closedOrbit.y).toBeGreaterThanOrEqual(closedArtboard.y);
      expect(closedOrbit.y + closedOrbit.height).toBeLessThanOrEqual(closedArtboard.y + closedArtboard.height);
    }
    expect(consoleErrors).toEqual([]);
    expect(pageErrors).toEqual([]);
  });
}

test("expandedInspectorIsASideDrawer", async ({ page }) => {
  await page.setViewportSize({ width: 1400, height: 1180 });
  await page.goto("/");
  await page.getByRole("button", { name: "840", exact: true }).click();
  await page.getByRole("button", { name: "Add a layer" }).click();
  await page.getByRole("menuitem", { name: /^Text/ }).click();

  const panelLocator = page.getByTestId("panel-sheet");
  await expect(panelLocator).toHaveCSS("transform", "matrix(1, 0, 0, 1, 0, 0)");
  const stage = await page.getByTestId("stage").boundingBox();
  const panel = await panelLocator.boundingBox();
  const screen = await page.locator(".device-screen").boundingBox();
  if (!stage || !panel || !screen) throw new Error("The expanded stage, drawer or screen has no layout box.");
  expect(stage.width).toBeCloseTo(screen.width, 0);
  expect(panel.x).toBeGreaterThan(stage.x + stage.width * 0.4);
  expect(panel.x + panel.width).toBeCloseTo(screen.x + screen.width, 0);
  expect(panel.width).toBeGreaterThanOrEqual(320);
  expect(panel.width).toBeLessThanOrEqual(344);
});

test("layer effects paint on the object and survive dragging", async ({ page }) => {
  await page.setViewportSize({ width: 1280, height: 1100 });
  await page.goto("/");
  await openCatalogue(page);
  await page.getByTestId("catalogue-shader-lab").click();
  await page.getByRole("button", { name: "Layer", exact: true }).click();
  await page.getByTestId("shader-preset-metal").click();
  await page.getByRole("button", { name: "Close panel" }).click();

  const effect = page.getByTestId("layer-effect-type-open-studio");
  const canvas = effect.locator("canvas");
  await expect(effect).toBeVisible();
  await expect(canvas).toHaveCount(1);
  await page.waitForTimeout(250);
  expect(await canvas.evaluate((element) => (element as HTMLCanvasElement).height)).toBeGreaterThan(0);
  expect(await effect.evaluate((element) => getComputedStyle(element).mixBlendMode)).toBe("multiply");

  const before = await effect.boundingBox();
  if (!before) throw new Error("The layer effect has no layout box.");
  await dragBy(page, page.getByTestId("art-layer-type-open-studio"), 40, 18);
  const after = await effect.boundingBox();
  if (!after) throw new Error("The layer effect disappeared after dragging.");
  expect(after.x).toBeGreaterThan(before.x + 20);
  expect(await canvas.evaluate((element) => (element as HTMLCanvasElement).height)).toBeGreaterThan(0);
});

test("applied particle presets and the aztec pattern paint distinct layer surfaces", async ({ page }) => {
  await page.setViewportSize({ width: 1280, height: 1100 });
  await page.goto("/");
  await openCatalogue(page);
  await page.getByTestId("catalogue-particle-field").click();
  await page.getByRole("button", { name: "Layer", exact: true }).click();

  const effect = page.getByTestId("layer-effect-type-open-studio");
  const expected = [
    ["dust", "80", "particle-mark-dot"],
    ["sparks", "64", "particle-mark-streak"],
    ["orbit", "48", "particle-mark-dot"],
    ["confetti", "42", "particle-mark-square"],
  ] as const;
  for (const [preset, count, mark] of expected) {
    await page.getByTestId(`particle-preset-${preset}`).click();
    const surface = effect.getByTestId("particle-effect-surface");
    await expect(surface).toHaveAttribute("data-preset", preset);
    await expect(surface).toHaveAttribute("data-count", count);
    await expect(effect.locator(`.${mark}`).first()).toBeVisible();
  }

  await page.getByRole("button", { name: "Shelf" }).click();
  await page.getByTestId("catalogue-pattern-library").click();
  await page.getByRole("button", { name: "Layer", exact: true }).click();
  await page.getByTestId("pattern-preset-aztec").click();
  const aztec = effect.locator(".pattern-surface.pattern-aztec");
  await expect(aztec).toBeVisible();
  await expect(aztec).toHaveCSS("background-size", /24px 24px/);
  await expect(aztec).toHaveCSS("opacity", "0.78");
});

test("font choices update the selected text layer", async ({ page }) => {
  await page.setViewportSize({ width: 1280, height: 1100 });
  await page.goto("/");
  await page.getByRole("button", { name: "Add a layer" }).click();
  await page.getByRole("menuitem", { name: /^Text/ }).click();

  await page.getByRole("combobox", { name: "Font" }).click();
  await page.getByRole("option", { name: /Spectral/ }).click();
  const text = page.getByLabel("Text / Type something 2 text");
  await expect(text).toHaveCSS("font-family", /Spectral/);
});

test("movement dials nudge by five", async ({ page }) => {
  await page.setViewportSize({ width: 1280, height: 1100 });
  await page.goto("/");
  await page.getByRole("button", { name: "Add a layer" }).click();
  await page.getByRole("menuitem", { name: /^Text/ }).click();

  await page.getByRole("button", { name: "Increase X by 5" }).click();
  await page.getByRole("button", { name: "Decrease Y by 5" }).click();
  const layer = page.locator(".art-layer-text").last();
  await expect(page.getByRole("spinbutton", { name: "X", exact: true })).toHaveValue("5");
  await expect(page.getByRole("spinbutton", { name: "Y", exact: true })).toHaveValue("-5");
  await expect(layer).toHaveAttribute("style", /translate\(calc\(-50% \+ 5px\), calc\(-50% - 5px\)\)/);
});

test("arkMenuIsKeyboardOperable", async ({ page }) => {
  await page.goto("/");
  const add = page.getByRole("button", { name: "Add a layer" });
  await add.focus();
  await page.keyboard.press("Enter");
  const menu = page.getByTestId("add-menu");
  await expect(menu).toBeVisible();
  await page.keyboard.press("Enter");
  await expect(page.getByTestId("inspector")).toBeVisible();

  await add.focus();
  await page.keyboard.press("Enter");
  await expect(menu).toBeVisible();
  await page.keyboard.press("ArrowDown");
  await expect(menu).toHaveAttribute("aria-activedescendant", /.+/);
  await page.keyboard.press("Escape");
  await expect(page.getByTestId("add-menu")).not.toBeVisible();
  await expect(add).toBeFocused();
});

test("Native Parity keeps its native colour swatches", async ({ page }) => {
  await page.goto("/");
  await openCatalogue(page);
  await page.getByTestId("catalogue-native-parity").click();
  await expect(page.getByTestId("plugin-native-parity")).toBeVisible();

  const colours = await page.locator(".tone-card > span").evaluateAll((elements) =>
    elements.map((element) => getComputedStyle(element).backgroundColor),
  );
  expect(new Set(colours).size).toBeGreaterThan(1);
  expect(colours.some((color) => {
    const [red, green, blue] = (color.match(/\d+/g) ?? []).map(Number);
    return red !== green || green !== blue;
  })).toBe(true);
});

test("texture previews are filtered without filtering the stage", async ({ page }) => {
  await page.goto("/");
  await openCatalogue(page);
  await page.getByTestId("catalogue-texture-lab").click();

  await expect(page.locator(".texture-feature")).toHaveCSS("filter", "grayscale(1)");
  await expect(page.locator(".stage")).toHaveCSS("filter", "none");
});

test("opens and searches the catalogue", async ({ page }) => {
  await page.goto("/");
  await openCatalogue(page);

  await page.getByTestId("catalogue-search").fill("shader");
  await expect(page.getByTestId("catalogue-shader-lab")).toBeVisible();
  await expect(page.locator(".plugin-card")).toHaveCount(1);

  await page.getByTestId("catalogue-search").fill("");
  await page.getByRole("button", { name: "Overlay", exact: true }).click();
  await expect(page.getByTestId("catalogue-overlay-lab")).toBeVisible();
  await expect(page.locator(".plugin-card")).toHaveCount(1);
});

test("opens every plugin without page errors", async ({ page }) => {
  const errors: string[] = [];
  page.on("pageerror", (error) => errors.push(String(error)));
  await page.goto("/");
  await openCatalogue(page);

  for (const id of PLUGINS) {
    await page.getByTestId(`catalogue-${id}`).click();
    await expect(page.getByTestId(`plugin-${id}`)).toBeVisible();
    await page.getByRole("button", { name: "Shelf" }).click();
    await expect(page.getByTestId("catalogue-search")).toBeVisible();
  }

  expect(errors).toEqual([]);
});

test("keeps a hidden panel mounted and resets a closed panel", async ({ page }) => {
  await page.goto("/");
  await openCatalogue(page);
  await page.getByTestId("catalogue-shader-lab").click();
  await page.getByTestId("shader-preset-metal").click();
  await page.getByRole("button", { name: "Hide" }).click();
  await expect(page.getByTestId("panel-sheet")).not.toHaveClass(/is-open/);

  await openCatalogue(page);
  await page.getByTestId("catalogue-shader-lab").click();
  await expect(page.getByTestId("shader-preset-metal")).toHaveAttribute("aria-pressed", "true");

  await page.getByTestId("plugin-shader-lab").getByRole("button", { name: "Close", exact: true }).click();
  await openCatalogue(page);
  await page.getByTestId("catalogue-shader-lab").click();
  await expect(page.getByTestId("shader-preset-mesh")).toHaveAttribute("aria-pressed", "true");
});

test("the Native lens dims but does not disable the catalogue", async ({ page }) => {
  await page.goto("/");
  await page.getByRole("button", { name: "Native lens" }).click();
  await openCatalogue(page);

  const card = page.getByTestId("catalogue-shader-lab");
  await expect(card).toHaveClass(/is-lens-dimmed/);
  await expect(card).toBeEnabled();
  await expect(page.locator(".plugin-card")).toHaveCount(8);
});

test("drags an artboard layer and closes the panel", async ({ page }) => {
  await page.setViewportSize({ width: 1280, height: 1100 });
  await page.goto("/");

  const layer = page.getByTestId("art-layer-type-open-studio");
  await dragBy(page, layer, 60, 30);
  await expect(layer).toHaveAttribute("style", /translate\(calc\(-50% \+ 60px\), calc\(-50% \+ 22px\)\)/);

  await openCatalogue(page);
  const panel = page.getByTestId("panel-sheet");
  await expect(panel).toHaveClass(/is-open/);
  await expect(panel).toBeVisible();
  await page.getByRole("button", { name: "Close panel" }).click();
  await expect(panel).not.toHaveClass(/is-open/);
  await expect(panel).toBeHidden();
});

test("touch drags an artboard layer and closes the panel", async ({ browser }) => {
  const context = await browser.newContext({
    viewport: { width: 390, height: 844 },
    hasTouch: true,
    isMobile: true,
    deviceScaleFactor: 1,
  });
  const page = await context.newPage();
  const client = await context.newCDPSession(page);
  const touchDrag = async (x: number, y: number, dx: number, dy: number) => {
    await client.send("Input.dispatchTouchEvent", { type: "touchStart", touchPoints: [{ x, y, id: 1 }] });
    for (let step = 1; step <= 10; step += 1) {
      await client.send("Input.dispatchTouchEvent", {
        type: "touchMove",
        touchPoints: [{ x: x + (dx * step) / 10, y: y + (dy * step) / 10, id: 1 }],
      });
    }
    await client.send("Input.dispatchTouchEvent", { type: "touchEnd", touchPoints: [] });
  };

  await page.goto("http://127.0.0.1:5173/");
  const layer = page.getByTestId("art-layer-type-open-studio");
  const box = await layer.boundingBox();
  if (!box) throw new Error("The artboard layer has no layout box.");
  await touchDrag(box.x + box.width / 2, box.y + box.height / 2, 30, 20);
  await expect(layer).toHaveAttribute("style", /translate\(calc\(-50% \+ 30px\), calc\(-50% \+ 12px\)\)/);

  await openCatalogue(page);
  const panel = page.getByTestId("panel-sheet");
  await expect(panel).toBeVisible();
  await page.getByRole("button", { name: "Close panel" }).click();
  await expect(panel).not.toHaveClass(/is-open/);
  await expect(panel).toBeHidden();
  await context.close();
});

test("the Add menu imports, drags and inspects a PNG layer", async ({ page }) => {
  await page.setViewportSize({ width: 1280, height: 1100 });
  await page.goto("/");
  await page.getByRole("button", { name: "Add a layer" }).click();
  await page.getByRole("menuitem", { name: /^PNG/ }).click();
  await expect(page.getByTestId("plugin-overlay-lab")).toBeVisible();

  await page.getByTestId("overlay-import-input").setInputFiles("e2e/fixtures/layer.png");
  await expect(page.getByTestId("inspector")).toBeVisible();
  await expect(page.getByTestId("inspector-kind")).toHaveText("PNG layer");
  await expect(page.locator(".art-layer-image")).toHaveCount(1);
  await expect(page.locator(".sample-image")).toHaveCSS("filter", "none");

  await page.getByRole("button", { name: "Select, close the panel" }).click();
  await expect(page.getByTestId("panel-sheet")).not.toHaveClass(/is-open/);
  const imageLayer = page.locator(".art-layer-image");
  await dragBy(page, imageLayer, 30, -20);
  await expect(imageLayer).toHaveAttribute("style", /translate\(calc\(-50% \+ 30px\), calc\(-50% - 20px\)\)/);
  await imageLayer.click();
  await expect(page.getByTestId("inspector")).toBeVisible();

  await page.getByRole("combobox", { name: "Blend mode" }).click();
  await page.getByRole("option", { name: "Multiply" }).click();
  await expect(imageLayer).toHaveAttribute("style", /mix-blend-mode: multiply/);

  const opacity = page.getByRole("slider", { name: "Opacity" });
  const track = page.locator(".inspector-slider [data-part='track']");
  const trackBox = await track.boundingBox();
  const thumbBox = await opacity.boundingBox();
  if (!trackBox || !thumbBox) throw new Error("The opacity slider has no layout box.");
  await page.mouse.move(thumbBox.x + thumbBox.width / 2, thumbBox.y + thumbBox.height / 2);
  await page.mouse.down();
  await page.mouse.move(trackBox.x + trackBox.width * 0.6, trackBox.y + trackBox.height / 2, { steps: 4 });
  await page.mouse.up();
  const opacityValue = Number(await opacity.getAttribute("aria-valuenow"));
  const arrow = opacityValue < 60 ? "ArrowRight" : "ArrowLeft";
  for (let step = 0; step < Math.abs(60 - opacityValue); step += 1) await page.keyboard.press(arrow);
  await expect(opacity).toHaveAttribute("aria-valuenow", "60");
  await expect(imageLayer).toHaveAttribute("style", /opacity: 0\.6/);

  const x = page.getByRole("spinbutton", { name: "X" });
  const scale = page.getByRole("spinbutton", { name: "Scale" });
  const rotation = page.getByRole("spinbutton", { name: "Rotation" });
  await x.fill("24");
  await x.press("Tab");
  await scale.fill("1.25");
  await scale.press("Tab");
  await rotation.fill("12");
  await rotation.press("Tab");
  await expect(imageLayer).toHaveAttribute("style", /translate\(calc\(-50% \+ 24px\).*scale\(1\.25\) rotate\(12deg\)/);

  await page.getByText("Visible", { exact: true }).click();
  await expect(imageLayer).toBeHidden();
  await page.getByRole("tab", { name: "Layers" }).click();
  const layerRow = page.locator(".inspector-layer-row").filter({ hasText: "×" });
  await expect(layerRow).toBeVisible();
  await layerRow.locator(".inspector-switch [data-part='control']").click();
  await expect(imageLayer).toBeVisible();

  await page.getByRole("button", { name: "Move layer down" }).click();
  await expect(imageLayer).toHaveCSS("z-index", "3");
  await page.getByRole("button", { name: "Move layer up" }).click();
  await expect(imageLayer).toHaveCSS("z-index", "4");

  await page.getByRole("button", { name: "Remove layer" }).click();
  await expect(page.locator(".art-layer-image")).toHaveCount(0);
});

test("sanitises SVG material and rejects external references", async ({ page }) => {
  const external: string[] = [];
  page.on("request", (request) => {
    const url = new URL(request.url());
    if (url.origin !== "http://127.0.0.1:5173") external.push(request.url());
  });

  await page.goto("/");
  await openCatalogue(page);
  await page.getByTestId("catalogue-asset-shelf").click();
  await expect(page.getByTestId("plugin-asset-shelf")).toBeVisible();

  const input = page.getByTestId("asset-import-input");
  await input.setInputFiles({
    name: "stamp.svg",
    mimeType: "image/svg+xml",
    buffer: Buffer.from('<svg xmlns="http://www.w3.org/2000/svg" width="80" height="80"><script>window.__svgPwned=1</script><rect width="80" height="80" fill="#5B2A4B" onload="window.__svgPwned=2"/></svg>'),
  });
  await expect(page.locator(".imported-row")).toHaveCount(1);
  await expect(page.locator(".imported-row")).toContainText("Sanitised SVG");

  const src = await page.locator(".imported-row img").getAttribute("src");
  expect(src).toMatch(/^data:image\/svg\+xml/);
  const decoded = src?.includes(";base64,")
    ? Buffer.from(src.split(",")[1], "base64").toString("utf8")
    : decodeURIComponent(src?.split(",")[1] ?? "");
  expect(decoded).not.toMatch(/script|onload|foreignObject/i);
  expect(await page.evaluate(() => (window as { __svgPwned?: number }).__svgPwned)).toBeUndefined();

  await input.setInputFiles({
    name: "remote.svg",
    mimeType: "image/svg+xml",
    buffer: Buffer.from('<svg xmlns="http://www.w3.org/2000/svg" width="80" height="80"><image href="https://example.com/tracker.png"/></svg>'),
  });
  await expect(page.getByRole("status")).toContainText("points outside the file");
  await expect(page.locator(".imported-row")).toHaveCount(1);
  expect(external).toEqual([]);
});

test("the Actions sheet searches and runs a command", async ({ page }) => {
  await page.goto("/");
  await page.getByRole("button", { name: "Open actions" }).click();
  await expect(page.getByTestId("command-sheet")).toBeVisible();
  await expect(page.getByTestId("panel-sheet")).toHaveAttribute("data-surface", "commands");

  await page.getByLabel("Search actions").fill("shader");
  await expect(page.locator(".command-row")).toHaveCount(1);
  await page.getByRole("button", { name: /Open Shader Lab/ }).click();
  await expect(page.getByTestId("plugin-shader-lab")).toBeVisible();

  await page.getByRole("button", { name: "Open actions" }).click();
  await page.getByRole("button", { name: /Open Prism/ }).click();
  await expect(page.getByTestId("plugin-native-parity")).toBeVisible();
  await expect(page.locator(".prism-row")).toHaveCount(8);

  await page.getByRole("button", { name: "Open actions" }).click();
  await page.getByRole("button", { name: /Open Tone/ }).click();
  await expect(page.locator(".tone-card")).toHaveCount(5);
});

test("the expanded layout keeps one panel and switches the frame", async ({ page }) => {
  await page.setViewportSize({ width: 1400, height: 1180 });
  await page.goto("/");
  await page.getByRole("button", { name: "840", exact: true }).click();
  await expect(page.getByTestId("device-frame-expanded")).toBeVisible();
  await expect(page.getByTestId("device-frame-expanded")).toHaveCSS("width", "840px");
  await expect(page.getByTestId("device-frame-expanded")).toHaveCSS("height", "1000px");
  await expect(page.locator(".panel-sheet")).toHaveCount(1);

  const star = page.getByTestId("art-layer-shape-star");
  await dragBy(page, star, 20, 10);
  await expect(star).toHaveAttribute("style", /translate\(calc\(-50% - 102px\), calc\(-50% \+ 158px\)\)/);

  await openCatalogue(page);
  await expect(page.locator(".panel-sheet")).toHaveCount(1);
  await expect(page.getByTestId("panel-sheet")).toHaveClass(/is-open/);
});

test("Escape closes the open surface while the Add menu stays mounted", async ({ page }) => {
  await page.goto("/");
  await expect(page.locator('[data-testid="add-menu"]')).toHaveCount(1);
  await page.getByRole("button", { name: "Open actions" }).click();
  await expect(page.getByTestId("panel-sheet")).toHaveAttribute("data-surface", "commands");
  await page.keyboard.press("Escape");
  await expect(page.getByTestId("panel-sheet")).not.toHaveClass(/is-open/);
  await expect(page.getByTestId("panel-sheet")).toHaveAttribute("data-surface", "none");
});

test("Native Parity mirrors the native Tone and eight Prism recipes", async ({ page }) => {
  await page.goto("/");
  await openCatalogue(page);
  await page.getByTestId("catalogue-native-parity").click();
  await expect(page.getByTestId("plugin-native-parity")).toBeVisible();
  await expect(page.locator(".tone-card")).toHaveCount(5);
  const toneText = (await page.locator(".tone-card").allTextContents()).join(" ");
  for (const hex of ["#5B2A4B", "#6D3558", "#1B1212", "#F5ECD8", "#8A3B00"]) {
    expect(toneText).toContain(hex);
  }
  await page.getByRole("button", { name: "Prism", exact: true }).click();
  await expect(page.locator(".prism-row")).toHaveCount(8);
  const prismText = (await page.locator(".prism-row").allTextContents()).join(" ");
  for (const name of ["Mica dawn", "Sea glass", "Plum smoke", "Citrus haze", "Cobalt rain", "Olive dusk", "Rose quartz", "Ink tide"]) {
    expect(prismText).toContain(name);
  }
  await page.getByRole("button", { name: "Apply Mica dawn gradient" }).click();
  await page.getByRole("button", { name: "Apply Mica dawn to artboard" }).click();
  await expect(page.locator(".artboard-ground .effect-field")).toBeVisible();
});

test("presents the stage as a focus-managed read-only view", async ({ page }) => {
  await page.setViewportSize({ width: 1280, height: 1100 });
  await page.goto("/");
  await page.getByRole("button", { name: "Open actions" }).click();
  const present = page.getByRole("button", { name: /Present the stage/ });
  await present.click();

  const dialog = page.getByRole("dialog", { name: "Stage presentation" });
  await expect(dialog).toBeVisible();
  await expect(page.getByRole("button", { name: "Close presentation" })).toBeFocused();
  await expect(page.getByTestId("presentation-artboard")).toBeVisible();
  await expect(page.getByTestId("presentation-artboard").locator(".art-layer")).toHaveCount(3);
  await expect(page.getByTestId("presentation-artboard").locator(".art-layer.is-selected")).toHaveCount(0);
  await expect(page.getByTestId("stage")).toHaveAttribute("inert", "");
  await expect(page.getByTestId("panel-sheet")).toHaveAttribute("inert", "");

  await page.keyboard.press("Escape");
  await expect(dialog).toBeHidden();
  await expect(present).toBeFocused();
  await expect(page.getByTestId("stage")).not.toHaveAttribute("inert", "");
  await expect(page.getByTestId("panel-sheet")).not.toHaveAttribute("inert", "");
});

test("presentation fits the compact and short-landscape frames", async ({ page }) => {
  for (const viewport of [{ width: 390, height: 844 }, { width: 844, height: 390 }]) {
    await page.setViewportSize(viewport);
    await page.goto("/");
    await page.getByRole("button", { name: "Open actions" }).click();
    await page.getByRole("button", { name: /Present the stage/ }).click();
    await expect(page.getByRole("dialog", { name: "Stage presentation" })).toBeVisible();

    const screen = await page.locator(".device-screen").boundingBox();
    const artboard = await page.getByTestId("presentation-artboard").boundingBox();
    if (!screen || !artboard) throw new Error("The presentation frame has no layout box.");
    expect(artboard.x).toBeGreaterThanOrEqual(screen.x - 1);
    expect(artboard.y).toBeGreaterThanOrEqual(screen.y - 1);
    expect(artboard.x + artboard.width).toBeLessThanOrEqual(screen.x + screen.width + 1);
    expect(artboard.y + artboard.height).toBeLessThanOrEqual(screen.y + screen.height + 1);
    expect(artboard.height).toBeGreaterThan(180);

    await page.getByRole("button", { name: "Close presentation" }).click();
    await expect(page.getByRole("dialog", { name: "Stage presentation" })).toBeHidden();
  }
});

test("applying an effect inks in the artboard", async ({ page }) => {
  await page.goto("/");
  await openCatalogue(page);
  await page.getByTestId("catalogue-shader-lab").click();
  await page.getByTestId("shader-preset-metal").click();

  const pulse = page.locator(".artboard-pulse");
  await expect(pulse).toHaveCount(1);
  await expect(pulse).toHaveCSS("animation-name", "artboard-pulse");
});

test("reduced motion keeps decorative feedback static", async ({ page }) => {
  await page.emulateMedia({ reducedMotion: "reduce" });
  await page.goto("/");
  await openCatalogue(page);

  const cardMetrics = await page.locator(".plugin-card").first().evaluate((element) => {
    const style = getComputedStyle(element);
    return {
      animationDuration: style.animationDuration,
      animationDelay: style.animationDelay,
      transitionDuration: style.transitionDuration,
    };
  });
  expect(maxDuration(cardMetrics.animationDuration)).toBeLessThanOrEqual(0.001);
  expect(maxDuration(cardMetrics.animationDelay)).toBe(0);
  expect(maxDuration(cardMetrics.transitionDuration)).toBeLessThanOrEqual(0.001);
  await expect(page.locator(".selection-pulse").first()).toHaveCSS("display", "none");
});

test("drags the compact sheet handle to dismiss", async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto("/");
  await openCatalogue(page);

  const panel = page.getByTestId("panel-sheet");
  const handle = page.getByTestId("panel-grabber");
  await expect(handle).toBeVisible();
  const box = await handle.boundingBox();
  if (!box) throw new Error("The panel grabber has no layout box.");
  await page.mouse.move(box.x + box.width / 2, box.y + box.height / 2);
  await page.mouse.down();
  await page.mouse.move(box.x + box.width / 2, box.y + box.height / 2 + 130, { steps: 8 });
  await page.mouse.up();
  await expect(panel).not.toHaveClass(/is-open/);
  await expect(panel).toBeHidden();

  await openCatalogue(page);
  await page.setViewportSize({ width: 844, height: 390 });
  await expect(handle).toBeHidden();
});

test("the app makes no external requests", async ({ page }) => {
  const external: string[] = [];
  page.on("request", (request) => {
    const url = new URL(request.url());
    if (url.protocol !== "http:" && url.protocol !== "https:") return;
    if (url.origin !== "http://127.0.0.1:5173") external.push(request.url());
  });

  await page.goto("/");
  await openCatalogue(page);
  await page.getByTestId("catalogue-shader-lab").click();
  await page.waitForTimeout(700);

  expect(external).toEqual([]);
});

test("undoes and redoes an applied effect from the keyboard", async ({ page }) => {
  await page.setViewportSize({ width: 1280, height: 1100 });
  await page.goto("/");
  await openCatalogue(page);
  await page.getByTestId("catalogue-shader-lab").click();
  await page.getByRole("button", { name: "Layer", exact: true }).click();
  await page.getByTestId("shader-preset-metal").click();
  await page.getByRole("button", { name: "Close panel" }).click();

  const effect = page.getByTestId("layer-effect-type-open-studio");
  await expect(effect).toBeVisible();
  await page.keyboard.press("Control+z");
  await expect(effect).toHaveCount(0);
  await page.keyboard.press("Control+Shift+z");
  await expect(effect).toBeVisible();
});

test("undoes and redoes a direct layer transform", async ({ page }) => {
  await page.setViewportSize({ width: 1280, height: 1100 });
  await page.goto("/");
  const layer = page.getByTestId("art-layer-type-open-studio");
  const initialStyle = await layer.getAttribute("style");
  if (!initialStyle) throw new Error("The layer has no inline style.");

  await dragBy(page, layer, 30, 12);
  await expect(layer).toHaveAttribute("style", /translate\(calc\(-50% \+ 30px\), calc\(-50% \+ 4px\)\)/);

  await page.keyboard.press("Control+z");
  await expect(layer).toHaveAttribute("style", initialStyle);
  await page.keyboard.press("Control+Shift+z");
  await expect(layer).toHaveAttribute("style", /translate\(calc\(-50% \+ 30px\), calc\(-50% \+ 4px\)\)/);

  await page.keyboard.press("Control+z");
  await page.keyboard.press("Control+y");
  await expect(layer).toHaveAttribute("style", /translate\(calc\(-50% \+ 30px\), calc\(-50% \+ 4px\)\)/);
});

test("undoes and redoes layer add, rename, opacity, visibility, reorder and remove", async ({ page }) => {
  test.slow();
  await page.setViewportSize({ width: 1280, height: 1100 });
  await page.goto("/");
  const textLayers = page.locator(".art-layer-text");
  const added = textLayers.last();

  await page.getByRole("button", { name: "Add a layer" }).click();
  await page.getByRole("menuitem", { name: /^Text/ }).click();
  await expect(textLayers).toHaveCount(2);
  await page.keyboard.press("Control+z");
  await expect(textLayers).toHaveCount(1);
  await page.keyboard.press("Control+Shift+z");
  await expect(textLayers).toHaveCount(2);

  await added.click();
  await expect(page.getByTestId("inspector-layer-name")).toHaveText("Text / Type something 2");
  await page.getByTestId("inspector-layer-name").dblclick();
  const nameInput = page.locator(".inspector-editable input");
  await nameInput.fill("Text / History");
  await nameInput.press("Enter");
  await expect(page.getByTestId("inspector-layer-name")).toHaveText("Text / History");

  await page.getByRole("tab", { name: "Properties" }).focus();
  await page.keyboard.press("Control+z");
  await expect(page.getByTestId("inspector-layer-name")).toHaveText("Text / Type something 2");
  await page.keyboard.press("Control+Shift+z");
  await expect(page.getByTestId("inspector-layer-name")).toHaveText("Text / History");
  await page.keyboard.press("Control+z");

  const opacity = page.getByRole("slider", { name: "Opacity" });
  await opacity.focus();
  await page.keyboard.press("Home");
  await expect(added).toHaveCSS("opacity", "0");
  await page.keyboard.press("Control+z");
  await expect(added).toHaveCSS("opacity", "1");

  await page.getByText("Visible", { exact: true }).click();
  await expect(added).toBeHidden();
  await page.keyboard.press("Control+z");
  await expect(added).toBeVisible();

  await page.getByRole("tab", { name: "Layers" }).click();
  await expect(added).toHaveCSS("z-index", "4");
  await page.getByRole("button", { name: "Move Text / Type something 2 down" }).click();
  await expect(added).toHaveCSS("z-index", "3");
  await page.keyboard.press("Control+z");
  await expect(added).toHaveCSS("z-index", "4");

  await page.getByRole("button", { name: "Remove Text / Type something 2" }).click();
  await expect(textLayers).toHaveCount(1);
  await page.keyboard.press("Control+z");
  await expect(textLayers).toHaveCount(2);
  await page.keyboard.press("Control+Shift+z");
  await expect(textLayers).toHaveCount(1);
});
