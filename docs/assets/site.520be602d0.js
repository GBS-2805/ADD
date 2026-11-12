/* The site is one printed edition. The old palette and light/dark controls are
   gone: the page always reads as newsprint, ink and stamp red. This script keeps
   the shared chrome — wordmark, pull-down, side tab, search row and hover rolls —
   in one place so every page gets the same masthead. */
(function () {
  function rootPrefix() {
    var script = document.currentScript || document.querySelector('script[src*="assets/site"]');
    var src = script ? script.getAttribute("src") || "" : "";
    var cut = src.indexOf("assets/");
    return cut > -1 ? src.slice(0, cut) : "";
  }

  function motion() {
    var reduce = window.matchMedia && window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    return window.gsap && !reduce ? window.gsap : null;
  }

  // The masthead lockup is the brand's own: ADD, its descriptor, and the promise.
  // The old mark stays in the favicon and app icon; on the site the wordmark is
  // a signature, not the composition. Build the lockup once for every page.
  var bar = document.querySelector(".bar");
  if (bar && !bar.querySelector(".brand-lockup")) {
    var wordmark = bar.querySelector(".site-wordmark");
    if (!wordmark) {
      wordmark = document.createElement("a");
      wordmark.className = "site-wordmark";
      wordmark.href = rootPrefix() || "./";
      wordmark.setAttribute("aria-label", "ADD — home");
      wordmark.textContent = "ADD";
    }
    var lockup = document.createElement("div");
    lockup.className = "brand-lockup";
    var descriptor = document.createElement("span");
    descriptor.className = "brand-descriptor";
    descriptor.textContent = "Art · Design · Database";
    lockup.append(wordmark, descriptor);
    // The lockup is the pull-down's label now: ADD and its descriptor take the
    // left cell where the page name used to sit, and still open the sections.
    var leadCell = bar.querySelector(".lead");
    var menuButton = bar.querySelector(".menu-btn");
    if (leadCell) leadCell.insertBefore(lockup, menuButton ? menuButton.nextSibling : null);
    else bar.insertBefore(lockup, bar.firstElementChild);
    var oldName = bar.querySelector(".page-name");
    if (oldName) oldName.remove();
  }
  if (bar) {
    var oldLines = bar.querySelectorAll(".who-lines");
    for (var i = 0; i < oldLines.length; i++) oldLines[i].remove();
  }

  // Band motion, kept quiet: the hairline draws in, the two ends settle, and the
  // menu panels animate their own height. Reduced motion and a missing library
  // both leave the finished CSS state.
  if (bar && !bar.querySelector(".band-rule")) {
    var bandRule = document.createElement("i");
    bandRule.className = "band-rule";
    bandRule.setAttribute("aria-hidden", "true");
    bar.appendChild(bandRule);
  }
  var g = motion();
  if (g) {
    g.fromTo(".band-rule", { scaleX: 0 }, { scaleX: 1, duration: .9, ease: "power3.inOut", delay: .12 });
    g.from(".bar .lead", { y: 7, opacity: 0, duration: .55, ease: "power3.out" });
    g.from(".bar .who", { y: 7, opacity: 0, duration: .55, ease: "power3.out" }, .05);
    g.from(".site-wordmark", { y: 5, opacity: 0, duration: .45, ease: "power2.out" });
  }

  var menu = document.getElementById("menu");
  // The folio under the page name is the label the menu already carries for the
  // current section, so the three-zone masthead stays data-driven.
  var pageLink = document.querySelector(".page-name");
  var sectionLabel = menu ? menu.querySelector('a[aria-current="page"] span') : null;
  if (pageLink && sectionLabel && !pageLink.parentNode.querySelector(".page-note")) {
    var pageNote = document.createElement("span");
    pageNote.className = "page-note";
    pageNote.textContent = sectionLabel.textContent.trim() === "shelf" ? "The shelf" : sectionLabel.textContent.trim();
    pageLink.parentNode.insertBefore(pageNote, pageLink.nextSibling);
  }
  var menuBtn = document.getElementById("menuBtn");
  var drawer = document.getElementById("drawer");
  var burger = document.getElementById("burger");
  var scrim = document.getElementById("scrim");
  var closer = document.getElementById("drawerClose");
  if (!menu || !menuBtn || !drawer || !burger) return;
  var menuOpener = menuBtn;
  var menuTween = null;

  function setMenu(open, returnFocus) {
    var wasOpen = menu.getAttribute("data-open") === "true";
    if (menuTween) { menuTween.kill(); menuTween = null; }
    menuBtn.setAttribute("aria-expanded", open ? "true" : "false");
    menuBtn.setAttribute("aria-label", open ? "Close sections" : "Open sections");
    if (open) {
      menu.setAttribute("data-open", "true");
      if (g) {
        var target = menu.scrollHeight;
        g.set(menu, { height: 0 });
        menuTween = g.timeline({ onComplete: function () {
          menu.style.height = "";
          menuTween = null;
        }});
        menuTween.to(menu, { height: target, duration: .42, ease: "power3.out" }, 0)
          .fromTo(menu.querySelectorAll("a"), { y: 10, opacity: 0 }, { y: 0, opacity: 1, duration: .4, stagger: .045, ease: "power2.out" }, .04);
      }
    } else if (wasOpen) {
      if (g) {
        menuTween = g.to(menu, { height: 0, duration: .22, ease: "power2.in", onComplete: function () {
          menu.setAttribute("data-open", "false");
          menu.style.height = "";
          menuTween = null;
        }});
      } else {
        menu.setAttribute("data-open", "false");
      }
    }
    var name = document.querySelector(".bar .site-wordmark");
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
    if (open) {
      setMenu(false, false);
      if (g) g.fromTo(drawer.querySelectorAll("a, .drawer-about"), { y: 8, opacity: 0 }, { y: 0, opacity: 1, duration: .4, stagger: .035, ease: "power3.out", overwrite: true });
    }
    if (!open && wasOpen && returnFocus) burger.focus();
  }

  // The search page is part of the shelf but not in the four section links.
  var drawerNote = drawer.querySelector(".drawer-note");
  if (drawerNote && !document.getElementById("searchLink")) {
    var searchLink = document.createElement("a");
    searchLink.id = "searchLink";
    searchLink.href = rootPrefix() + "search/";
    searchLink.innerHTML = "Search<span>pages</span>";
    drawer.insertBefore(searchLink, drawerNote);
  }

  // The note becomes the tab's About, a small heading over the sentence.
  if (drawerNote && !drawerNote.closest(".drawer-about")) {
    var about = document.createElement("div");
    about.className = "drawer-about";
    var aboutLabel = document.createElement("p");
    aboutLabel.className = "drawer-about-label";
    aboutLabel.textContent = "About this";
    drawerNote.parentNode.insertBefore(about, drawerNote);
    about.append(aboutLabel, drawerNote);
  }

  // Menu and tab labels roll over themselves on hover. The duplicate is hidden
  // from assistive tech, so the link still reads once.
  (function addRolls() {
    var links = document.querySelectorAll(".menu a, .drawer a");
    for (var i = 0; i < links.length; i++) {
      var link = links[i];
      if (link.querySelector(".roll")) continue;
      var node = link.firstChild;
      while (node && node.nodeType === 3 && !node.textContent.trim()) node = node.nextSibling;
      if (!node || node.nodeType !== 3) continue;
      var label = node.textContent.trim();
      if (!label) continue;
      var roll = document.createElement("span");
      roll.className = "roll";
      var top = document.createElement("span");
      top.className = "roll-a";
      top.textContent = label;
      var under = document.createElement("span");
      under.className = "roll-b";
      under.setAttribute("aria-hidden", "true");
      under.textContent = label;
      roll.appendChild(top);
      roll.appendChild(under);
      node.parentNode.replaceChild(roll, node);
    }
  })();

  var nameLink = document.querySelector(".bar .site-wordmark");
  if (nameLink) {
    nameLink.setAttribute("role", "button");
    nameLink.setAttribute("aria-label", "ADD — open sections");
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

  // A Tools card that names a native tool asks the app for it. On the site
  // nothing answers, so the card keeps its href and stays a normal link.
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
