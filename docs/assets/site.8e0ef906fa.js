/* Band chrome. The chevrons on the left open a pull-down accordion with the four
   section links; the burger on the right pulls out a side tab with the other
   three. Opening one closes the other, Escape closes both, and the scrim behind
   the tab dismisses it. */
(function () {
  var THEME_KEY = "site:theme";
  var SUN = '<svg viewBox="0 0 24 24" fill="none" aria-hidden="true"><circle cx="12" cy="12" r="4" stroke="currentColor" stroke-width="2"/><path d="M12 2v2M12 20v2M2 12h2M20 12h2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M19.1 4.9l-1.4 1.4M6.3 17.7l-1.4 1.4" stroke="currentColor" stroke-width="2" stroke-linecap="round"/></svg>';
  var MOON = '<svg viewBox="0 0 24 24" fill="none" aria-hidden="true"><path d="M20 14.5A8 8 0 1 1 9.5 4a6.5 6.5 0 1 0 10.5 10.5Z" stroke="currentColor" stroke-width="2" stroke-linejoin="round"/></svg>';

  function storedTheme() {
    try {
      var value = localStorage.getItem(THEME_KEY);
      if (value === "light" || value === "dark") return value;
    } catch (error) { /* blocked store: fall through to the system */ }
    // Dark is the default now: a first visit opens on the off-black page whatever
    // the system asks for, and only a saved choice turns it over.
    return "dark";
  }
  function applyTheme(theme) {
    document.documentElement.setAttribute("data-theme", theme);
    var button = document.getElementById("themeBtn");
    if (!button) return;
    var icon = button.querySelector(".ico") || button;
    icon.innerHTML = theme === "light" ? MOON : SUN;
    button.setAttribute("aria-label",
      theme === "light" ? "Switch to dark theme" : "Switch to light theme");
  }
  applyTheme(storedTheme());

  // The palette rides beside the theme: same three keys, same store, and the
  // colour choice survives a reload the way the light/dark one does.
  var PALETTE_KEY = "site:palette";
  var PALETTES = [
    { id: "green", label: "Green", band: "#0fbf92", word: "#0f120f" },
    { id: "moss", label: "Moss", band: "#005043", word: "#e7ddce" },
    { id: "navy", label: "Navy", band: "#242d55", word: "#a1b0c3" }
  ];
  function storedPalette() {
    try {
      var saved = localStorage.getItem(PALETTE_KEY);
      for (var i = 0; i < PALETTES.length; i++) if (PALETTES[i].id === saved) return saved;
    } catch (error) { /* blocked store: fall through to green */ }
    return "green";
  }
  // A page that prints the palette's codes reads them back out of the stylesheet
  // instead of carrying a second copy: [data-code="band"] takes --pal-band, so the
  // values under "The system" follow the chosen palette rather than staying green.
  function syncCodes() {
    var slots = document.querySelectorAll("[data-code]");
    if (!slots.length) return;
    var live = getComputedStyle(document.documentElement);
    for (var i = 0; i < slots.length; i++) {
      var hex = live.getPropertyValue("--pal-" + slots[i].getAttribute("data-code")).trim();
      if (hex) slots[i].textContent = hex.toUpperCase();
    }
  }
  // A phone paints its status bar from the theme-color meta, which is otherwise stuck
  // on green whatever palette is live: give it the band the page is actually wearing.
  function syncBandMeta() {
    var meta = document.querySelector('meta[name="theme-color"]');
    if (!meta) return;
    var band = getComputedStyle(document.documentElement).getPropertyValue("--pal-band").trim();
    if (band) meta.setAttribute("content", band);
  }
  function applyPalette(id) {
    document.documentElement.setAttribute("data-palette", id);
    syncCodes();
    syncBandMeta();
    var row = document.getElementById("palRow");
    if (!row) return;
    var chips = row.querySelectorAll(".pal");
    for (var i = 0; i < chips.length; i++) {
      chips[i].setAttribute("aria-pressed",
        chips[i].getAttribute("data-palette") === id ? "true" : "false");
    }
  }
  applyPalette(storedPalette());

  var menu = document.getElementById("menu");
  var menuBtn = document.getElementById("menuBtn");
  var drawer = document.getElementById("drawer");
  var burger = document.getElementById("burger");
  var scrim = document.getElementById("scrim");
  var closer = document.getElementById("drawerClose");
  if (!menu || !menuBtn || !drawer || !burger) return;
  var menuOpener = menuBtn;

  function setMenu(open, returnFocus) {
    var wasOpen = menu.getAttribute("data-open") === "true";
    menu.setAttribute("data-open", open ? "true" : "false");
    menuBtn.setAttribute("aria-expanded", open ? "true" : "false");
    menuBtn.setAttribute("aria-label", open ? "Close sections" : "Open sections");
    var name = document.querySelector(".page-name");
    if (name) name.setAttribute("aria-expanded", open ? "true" : "false");
    if (!open && wasOpen && returnFocus) menuOpener.focus();
  }

  function setDrawer(open, returnFocus) {
    var wasOpen = drawer.getAttribute("data-open") === "true";
    drawer.setAttribute("data-open", open ? "true" : "false");
    burger.setAttribute("aria-expanded", open ? "true" : "false");
    burger.setAttribute("aria-label", open ? "Close side tab" : "Open side tab");
    if (scrim) scrim.setAttribute("data-open", open ? "true" : "false");
    document.body.classList.toggle("drawer-open", open);
    if (open) setMenu(false, false);
    else if (wasOpen) persistLook();
    if (!open && wasOpen && returnFocus) burger.focus();
  }

  // A route to /search/ from the side tab on every page, without editing each page.
  // The bundle carries the page too, so the app keeps the link.
  function rootPrefix() {
    var script = document.currentScript || document.querySelector('script[src*="assets/site"]');
    var src = script ? script.getAttribute("src") || "" : "";
    var cut = src.indexOf("assets/");
    return cut > -1 ? src.slice(0, cut) : "";
  }
  var drawerNote = drawer.querySelector(".drawer-note");
  if (drawerNote && !document.getElementById("searchLink")) {
    var searchLink = document.createElement("a");
    searchLink.id = "searchLink";
    searchLink.href = rootPrefix() + "search/";
    searchLink.innerHTML = "Search<span>pages</span>";
    drawer.insertBefore(searchLink, drawerNote);
  }

  // The note is the tab's About: a small heading over the sentence, so it reads as
  // a description of the work rather than a stray caption under the links.
  if (drawerNote && !drawerNote.closest(".drawer-about")) {
    var about = document.createElement("div");
    about.className = "drawer-about";
    var aboutLabel = document.createElement("p");
    aboutLabel.className = "drawer-about-label";
    aboutLabel.textContent = "About this";
    drawerNote.parentNode.insertBefore(about, drawerNote);
    about.append(aboutLabel, drawerNote);
  }

  // The look controls are one row at the foot of the tab: the three palette chips
  // with the light/dark switch at the end of them. The switch carries no word of its
  // own -- the sun or the moon says what it does -- and both choices are written to
  // the store when the tab closes, so closing it never drops one.
  var controls = document.getElementById("drawerControls");
  if (!controls) {
    controls = document.createElement("div");
    controls.id = "drawerControls";
    controls.className = "drawer-controls";
    drawer.appendChild(controls);
  }

  function persistLook() {
    var theme = document.documentElement.getAttribute("data-theme");
    var palette = document.documentElement.getAttribute("data-palette");
    try {
      if (theme === "light" || theme === "dark") localStorage.setItem(THEME_KEY, theme);
      if (palette) localStorage.setItem(PALETTE_KEY, palette);
    } catch (error) { /* blocked store: the page keeps the live choice */ }
  }

  if (!document.getElementById("palRow")) {
    var paletteRow = document.createElement("div");
    paletteRow.className = "drawer-palette";
    paletteRow.innerHTML = '<span>Palette</span><span class="pal-row" id="palRow" role="group" aria-label="Palette"></span>';
    controls.appendChild(paletteRow);
    var palRow = paletteRow.querySelector(".pal-row");
    PALETTES.forEach(function (palette) {
      var chip = document.createElement("button");
      chip.type = "button";
      chip.className = "pal";
      chip.setAttribute("data-palette", palette.id);
      chip.setAttribute("aria-label", palette.label + " palette");
      chip.title = palette.label;
      chip.style.background = palette.band;
      var dot = document.createElement("i");
      dot.style.background = palette.word;
      chip.appendChild(dot);
      chip.addEventListener("click", function () {
        applyPalette(palette.id);
        persistLook();
      });
      palRow.appendChild(chip);
    });

    var themeBtn = document.createElement("button");
    themeBtn.type = "button";
    themeBtn.id = "themeBtn";
    themeBtn.className = "pal-theme";
    themeBtn.innerHTML = '<span class="ico"></span>';
    themeBtn.addEventListener("click", function () {
      applyTheme(document.documentElement.getAttribute("data-theme") === "light" ? "dark" : "light");
      persistLook();
    });
    palRow.appendChild(themeBtn);
  }
  applyTheme(document.documentElement.getAttribute("data-theme") || storedTheme());
  applyPalette(document.documentElement.getAttribute("data-palette") || storedPalette());

  // A phone has no room for the two-line descriptor, so the band carries the short
  // name over the long one instead. Injected here, so every page gets both.
  var who = document.querySelector(".who");
  if (who && !who.querySelector(".who-lines.phone")) {
    var phone = document.createElement("div");
    phone.className = "who-lines phone";
    phone.innerHTML = '<p class="big">ADD</p><p class="sub">Art &#8226; Design &#8226; Database</p>';
    who.insertBefore(phone, who.firstChild);
  }

  // The name in the band names the pull-down sitting beside it, so tapping it opens
  // that instead of walking back to the index. The mark in the middle of the band is
  // the way home, from any depth; its href is set from the stylesheet's own path so a
  // page nested two folders deep still reaches the shelf.
  var markLink = document.querySelector(".bar .mark") || document.querySelector("header .mark");
  if (markLink) markLink.setAttribute("href", rootPrefix());

  var nameLink = document.querySelector(".page-name");
  if (nameLink) {
    nameLink.setAttribute("role", "button");
    nameLink.setAttribute("aria-controls", "menu");
    nameLink.setAttribute("aria-expanded",
      menu.getAttribute("data-open") === "true" ? "true" : "false");
    nameLink.addEventListener("click", function (event) {
      event.preventDefault();
      var open = menu.getAttribute("data-open") !== "true";
      menuOpener = nameLink;
      setMenu(open, true);
      if (open) setDrawer(false, false);
    });
  }

  menuBtn.addEventListener("click", function () {
    var open = menu.getAttribute("data-open") !== "true";
    menuOpener = menuBtn;
    setMenu(open, true);
    if (open) setDrawer(false, false);
  });
  burger.addEventListener("click", function () {
    setDrawer(drawer.getAttribute("data-open") !== "true", true);
  });
  if (closer) closer.addEventListener("click", function () { setDrawer(false, true); });
  if (scrim) scrim.addEventListener("click", function () { setDrawer(false, true); });

  menu.addEventListener("click", function (event) {
    if (event.target.closest("a")) setMenu(false, false);
  });
  drawer.addEventListener("click", function (event) {
    if (event.target.closest("a")) setDrawer(false, false);
  });

  // A Tools card that names a native tool asks the app for it. On the site nothing
  // answers -- IconsApp only exists inside the WebView -- so the card keeps its href
  // and stays a normal link to the download page.
  document.addEventListener("click", function (event) {
    var target = event.target && event.target.closest ? event.target.closest("[data-tool]") : null;
    if (!target || !window.IconsApp || typeof IconsApp.openTool !== "function") return;
    event.preventDefault();
    IconsApp.openTool(target.getAttribute("data-tool"));
  });

  document.addEventListener("keydown", function (event) {
    if (event.key !== "Escape") return;
    var menuOpen = menu.getAttribute("data-open") === "true";
    var drawerOpen = drawer.getAttribute("data-open") === "true";
    if (!menuOpen && !drawerOpen) return;
    setMenu(false, menuOpen);
    setDrawer(false, drawerOpen);
  });
})();
