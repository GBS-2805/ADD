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
    return window.matchMedia && window.matchMedia("(prefers-color-scheme: light)").matches
      ? "light" : "dark";
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
  function applyPalette(id) {
    document.documentElement.setAttribute("data-palette", id);
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

  function setMenu(open) {
    menu.setAttribute("data-open", open ? "true" : "false");
    menuBtn.setAttribute("aria-expanded", open ? "true" : "false");
    menuBtn.setAttribute("aria-label", open ? "Close sections" : "Open sections");
  }

  function setDrawer(open) {
    drawer.setAttribute("data-open", open ? "true" : "false");
    burger.setAttribute("aria-expanded", open ? "true" : "false");
    burger.setAttribute("aria-label", open ? "Close side tab" : "Open side tab");
    if (scrim) scrim.setAttribute("data-open", open ? "true" : "false");
    document.body.classList.toggle("drawer-open", open);
    if (open) setMenu(false);
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

  // The theme control lives in the side tab now, under its links: the band keeps
  // only the page name, the mark and the burger at every width.
  var controls = document.getElementById("drawerControls");
  if (!controls) {
    controls = document.createElement("div");
    controls.id = "drawerControls";
    controls.className = "drawer-controls";
    drawer.appendChild(controls);
  }
  if (!document.getElementById("themeBtn")) {
    var themeBtn = document.createElement("button");
    themeBtn.type = "button";
    themeBtn.id = "themeBtn";
    themeBtn.className = "drawer-theme";
    themeBtn.innerHTML = '<span>Theme</span><span class="ico"></span>';
    controls.appendChild(themeBtn);
    applyTheme(document.documentElement.getAttribute("data-theme") || storedTheme());
    themeBtn.addEventListener("click", function () {
      var next = document.documentElement.getAttribute("data-theme") === "light" ? "dark" : "light";
      try { localStorage.setItem(THEME_KEY, next); } catch (error) { /* forget it */ }
      applyTheme(next);
    });
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
        try { localStorage.setItem(PALETTE_KEY, palette.id); } catch (error) { /* forget it */ }
        applyPalette(palette.id);
      });
      palRow.appendChild(chip);
    });
    applyPalette(document.documentElement.getAttribute("data-palette") || storedPalette());
  }

  // A phone has no room for the two-line descriptor, so the band carries the short
  // name over the long one instead. Injected here, so every page gets both.
  var who = document.querySelector(".who");
  if (who && !who.querySelector(".who-lines.phone")) {
    var phone = document.createElement("div");
    phone.className = "who-lines phone";
    phone.innerHTML = '<p class="big">ADD</p><p class="sub">Art &#8226; Design &#8226; Database</p>';
    who.insertBefore(phone, who.firstChild);
  }

  menuBtn.addEventListener("click", function () {
    var open = menu.getAttribute("data-open") !== "true";
    setMenu(open);
    if (open) setDrawer(false);
  });
  burger.addEventListener("click", function () {
    setDrawer(drawer.getAttribute("data-open") !== "true");
  });
  if (closer) closer.addEventListener("click", function () { setDrawer(false); });
  if (scrim) scrim.addEventListener("click", function () { setDrawer(false); });

  menu.addEventListener("click", function (event) {
    if (event.target.closest("a")) setMenu(false);
  });
  drawer.addEventListener("click", function (event) {
    if (event.target.closest("a")) setDrawer(false);
  });

  document.addEventListener("keydown", function (event) {
    if (event.key !== "Escape") return;
    setMenu(false);
    setDrawer(false);
  });
})();
