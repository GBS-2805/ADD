/* Pull-down accordion. The chevrons beside the page name and the burger on the
   right both open the same panel; inside it one group is open at a time, and
   Escape or picking a destination closes the whole thing. */
(function () {
  var panel = document.getElementById("menu");
  var controls = [document.getElementById("menuBtn"), document.getElementById("burger")].filter(Boolean);
  if (!panel || !controls.length) return;

  var burger = document.getElementById("burger");
  var groups = Array.prototype.slice.call(panel.querySelectorAll(".group"));

  function setPanel(open) {
    panel.hidden = !open;
    controls.forEach(function (control) {
      control.setAttribute("aria-expanded", open ? "true" : "false");
    });
    if (burger) burger.setAttribute("aria-label", open ? "Close menu" : "Open menu");
  }

  controls.forEach(function (control) {
    control.addEventListener("click", function () { setPanel(panel.hidden); });
  });

  groups.forEach(function (group) {
    var button = group.querySelector(".group-btn");
    button.addEventListener("click", function () {
      var willOpen = group.getAttribute("data-open") !== "true";
      groups.forEach(function (other) {
        other.setAttribute("data-open", "false");
        other.querySelector(".group-btn").setAttribute("aria-expanded", "false");
      });
      if (willOpen) {
        group.setAttribute("data-open", "true");
        button.setAttribute("aria-expanded", "true");
      }
    });
  });

  panel.addEventListener("click", function (event) {
    if (event.target.closest("a")) setPanel(false);
  });

  document.addEventListener("keydown", function (event) {
    if (event.key === "Escape") setPanel(false);
  });
})();
