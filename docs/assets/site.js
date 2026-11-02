/* Two pull-downs from one band: the chevrons on the left open the main
   sections, the burger on the right opens portfolio, renders and download.
   Only one is ever open, and both close on Escape or on picking a destination. */
(function () {
  var burger = document.getElementById("burger");
  var pairs = [
    { button: document.getElementById("menuBtn"), panel: document.getElementById("menu") },
    { button: burger, panel: document.getElementById("more") }
  ].filter(function (pair) { return pair.button && pair.panel; });

  if (!pairs.length) return;

  function setOpen(open, only) {
    pairs.forEach(function (pair) {
      var isOpen = open && pair === only;
      pair.panel.hidden = !isOpen;
      pair.button.setAttribute("aria-expanded", isOpen ? "true" : "false");
    });
    if (burger) burger.setAttribute("aria-label", open && only === pairs[1] ? "Close menu" : "Open menu");
  }

  pairs.forEach(function (pair) {
    pair.button.addEventListener("click", function () {
      setOpen(pair.panel.hidden, pair);
    });
    pair.panel.addEventListener("click", function (event) {
      if (event.target.closest("a")) setOpen(false);
    });
  });

  document.addEventListener("keydown", function (event) {
    if (event.key === "Escape") setOpen(false);
  });
})();
