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
    button.innerHTML = theme === "light" ? MOON : SUN;
    button.setAttribute("aria-label",
      theme === "light" ? "Switch to dark theme" : "Switch to light theme");
  }
  applyTheme(storedTheme());

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

  var who = document.querySelector(".who");
  if (who && !document.getElementById("themeBtn")) {
    var themeBtn = document.createElement("button");
    themeBtn.type = "button";
    themeBtn.id = "themeBtn";
    themeBtn.className = "theme-btn";
    who.insertBefore(themeBtn, burger);
    applyTheme(document.documentElement.getAttribute("data-theme") || storedTheme());
    themeBtn.addEventListener("click", function () {
      var next = document.documentElement.getAttribute("data-theme") === "light" ? "dark" : "light";
      try { localStorage.setItem(THEME_KEY, next); } catch (error) { /* forget it */ }
      applyTheme(next);
    });
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
