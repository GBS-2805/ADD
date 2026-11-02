/* Band chrome. The chevrons on the left open a pull-down accordion with the four
   section links; the burger on the right pulls out a side tab with the other
   three. Opening one closes the other, Escape closes both, and the scrim behind
   the tab dismisses it. */
(function () {
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
