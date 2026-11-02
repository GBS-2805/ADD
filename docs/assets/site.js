/* Pull-down menu. Both the chevrons on the left and the burger on the right
   toggle it, and it closes on Escape or on picking a destination. */
(function () {
  var menu = document.getElementById("menu");
  if (!menu) return;

  var controls = [document.getElementById("menuBtn"), document.getElementById("burger")];
  var burger = document.getElementById("burger");

  function setOpen(open) {
    menu.hidden = !open;
    controls.forEach(function (control) {
      if (control) control.setAttribute("aria-expanded", open ? "true" : "false");
    });
    if (burger) burger.setAttribute("aria-label", open ? "Close menu" : "Open menu");
  }

  controls.forEach(function (control) {
    if (!control) return;
    control.addEventListener("click", function () { setOpen(menu.hidden); });
  });

  menu.addEventListener("click", function (event) {
    if (event.target.closest("a")) setOpen(false);
  });

  document.addEventListener("keydown", function (event) {
    if (event.key === "Escape") setOpen(false);
  });
})();
