(() => {
  "use strict";

  const reduceMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
  const storageKey = "got-handoff-todos-v1";
  const defaultTasks = ["lock-direction", "map-keyboard"];
  const taskInputs = Array.from(document.querySelectorAll("[data-task]"));
  const taskLabels = Array.from(document.querySelectorAll(".todo-item"));
  const filterButtons = Array.from(document.querySelectorAll("[data-todo-filter]"));
  const todoGroups = Array.from(document.querySelectorAll("[data-lane]"));
  const doneOutput = document.querySelector("[data-todo-done]");
  const totalOutput = document.querySelector("[data-todo-total]");
  const openOutput = document.querySelector("[data-open-count]");
  const summaryOutput = document.querySelector("[data-todo-summary]");
  const progressBar = document.querySelector("[data-todo-bar]");
  const progressRoot = document.querySelector(".todo-progress");
  const savedStatus = document.querySelector("[data-todo-saved-status]");
  const resetButton = document.querySelector("[data-todo-reset]");
  const copyStatus = document.querySelector("[data-copy-status]");
  const manifestButton = document.querySelector("[data-copy-manifest]");
  const manifestStatus = document.querySelector("[data-manifest-status]");
  const deckTools = Array.from(document.querySelectorAll("[data-deck-tool]"));
  const deckPanels = Array.from(document.querySelectorAll("[data-deck-panel]"));
  const deckRig = document.querySelector("[data-deck-rig-label]");
  const deckStatus = document.querySelector("[data-deck-status]");
  const navLinks = Array.from(document.querySelectorAll("[data-nav]"));
  const sections = navLinks
    .map((link) => document.getElementById(link.dataset.nav))
    .filter(Boolean);

  const fileMap = {
    html: { type: "HTML", path: "index.html", title: "Handoff structure", mode: "text" },
    css: { type: "CSS", path: "handoff.css", title: "Design system", mode: "text" },
    js: { type: "JS", path: "app.js", title: "Handoff interactions", mode: "text" },
    brief: { type: "MD", path: "HANDOFF.md", title: "Written brief", mode: "text" },
    touchpad: { type: "PNG", path: "got-deck-touchpad.png", title: "Touchpad state", mode: "image" },
    keyboard: { type: "PNG", path: "got-deck-keyboard.png", title: "Keyboard state", mode: "image" },
    mobile: { type: "PNG", path: "got-deck-mobile.png", title: "Mobile keyboard state", mode: "image" },
    narrow: { type: "PNG", path: "got-deck-keyboard-320.png", title: "Narrow keyboard state", mode: "image" }
  };

  let copyTimer;
  let todoTimer;
  let statusTimer;
  let inspectorRequest = 0;

  /* Clipboard */

  async function writeClipboard(text) {
    if (navigator.clipboard && window.isSecureContext) {
      await navigator.clipboard.writeText(text);
      return;
    }

    const scratch = document.createElement("textarea");
    scratch.value = text;
    scratch.setAttribute("readonly", "");
    scratch.style.position = "fixed";
    scratch.style.top = "-9999px";
    scratch.style.opacity = "0";
    document.body.append(scratch);
    scratch.select();

    const copied = document.execCommand("copy");
    scratch.remove();

    if (!copied) throw new Error("Clipboard unavailable");
  }

  async function copyWithFeedback(text, statusElement, successMessage, errorMessage) {
    if (!statusElement) return;
    try {
      await writeClipboard(text);
      statusElement.textContent = successMessage;
      return true;
    } catch {
      statusElement.textContent = errorMessage;
      return false;
    }
  }

  /* Split-deck prototype */

  function activateDeckTool(tool, { focus = false } = {}) {
    const nextTool = tool === "keys" ? "keys" : "pad";

    deckTools.forEach((button) => {
      const isActive = button.dataset.deckTool === nextTool;
      button.classList.toggle("is-active", isActive);
      button.setAttribute("aria-pressed", String(isActive));
      if (isActive && focus) button.focus();
    });

    deckPanels.forEach((panel) => {
      const isActive = panel.dataset.deckPanel === nextTool;
      panel.hidden = !isActive;
      panel.setAttribute("aria-hidden", String(!isActive));
    });

    if (deckRig) deckRig.textContent = nextTool === "keys" ? "KBD-02" : "RIG-01";
    if (deckStatus) {
      deckStatus.textContent = nextTool === "keys"
        ? "Keyboard deck visible. Touchpad hidden."
        : "Touchpad deck visible. Keyboard hidden.";
    }
  }

  deckTools.forEach((button) => {
    button.addEventListener("click", () => activateDeckTool(button.dataset.deckTool));
  });

  deckTools.forEach((button, index) => {
    button.addEventListener("keydown", (event) => {
      let nextIndex = null;
      if (event.key === "ArrowRight" || event.key === "ArrowDown") {
        nextIndex = (index + 1) % deckTools.length;
      } else if (event.key === "ArrowLeft" || event.key === "ArrowUp") {
        nextIndex = (index - 1 + deckTools.length) % deckTools.length;
      } else if (event.key === "Home") {
        nextIndex = 0;
      } else if (event.key === "End") {
        nextIndex = deckTools.length - 1;
      }

      if (nextIndex === null) return;
      event.preventDefault();
      const nextButton = deckTools[nextIndex];
      activateDeckTool(nextButton.dataset.deckTool, { focus: true });
    });
  });

  /* Palette */

  document.querySelectorAll("[data-copy]").forEach((button) => {
    button.addEventListener("click", async () => {
      const hex = button.dataset.copy;
      window.clearTimeout(copyTimer);
      await copyWithFeedback(
        hex,
        copyStatus,
        `Copied ${hex}. Paste it into the next surface.`,
        "Copy was blocked. Select the hex and copy it manually."
      );
      copyTimer = window.setTimeout(() => {
        if (copyStatus) copyStatus.textContent = "Click a swatch to copy its hex.";
      }, 4200);
    });
  });

  /* To-do state */

  function numberWord(value) {
    return ["Zero", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine"][value] || String(value);
  }

  function readSavedTasks() {
    try {
      const saved = window.localStorage.getItem(storageKey);
      if (saved === null) return new Set(defaultTasks);
      const parsed = JSON.parse(saved);
      if (!Array.isArray(parsed)) return new Set(defaultTasks);
      return new Set(parsed.filter((key) => taskInputs.some((input) => input.dataset.task === key)));
    } catch {
      return new Set(defaultTasks);
    }
  }

  function saveTasks() {
    try {
      const checked = taskInputs.filter((input) => input.checked).map((input) => input.dataset.task);
      window.localStorage.setItem(storageKey, JSON.stringify(checked));
      if (savedStatus) savedStatus.textContent = "Saved in this browser.";
    } catch {
      if (savedStatus) savedStatus.textContent = "Could not save in this browser.";
    }

    window.clearTimeout(todoTimer);
    todoTimer = window.setTimeout(() => {
      if (savedStatus) savedStatus.textContent = "Ready.";
    }, 2400);
  }

  function updateLaneCounts() {
    todoGroups.forEach((group) => {
      const lane = group.dataset.lane;
      const laneInputs = Array.from(group.querySelectorAll("[data-task]"));
      const checked = laneInputs.filter((input) => input.checked).length;
      const count = group.querySelector("[data-group-count]");
      const filterCount = document.querySelector(`[data-filter-count="${lane}"]`);
      if (count) count.textContent = String(laneInputs.length);
      if (filterCount) filterCount.textContent = String(laneInputs.length);
      group.dataset.done = String(checked);
    });
  }

  function setFilter(filter) {
    const nextFilter = ["all", "design", "build", "validate"].includes(filter) ? filter : "all";
    filterButtons.forEach((button) => {
      const isActive = button.dataset.todoFilter === nextFilter;
      button.classList.toggle("is-active", isActive);
      button.setAttribute("aria-pressed", String(isActive));
    });
    todoGroups.forEach((group) => {
      group.hidden = nextFilter !== "all" && group.dataset.lane !== nextFilter;
    });
  }

  function updateTodo() {
    const total = taskInputs.length;
    const checked = taskInputs.filter((input) => input.checked).length;
    const open = total - checked;
    const percent = total ? (checked / total) * 100 : 0;

    taskInputs.forEach((input) => {
      const label = input.closest(".todo-item");
      if (label) label.classList.toggle("is-done", input.checked);
    });

    if (doneOutput) doneOutput.textContent = String(checked).padStart(2, "0");
    if (totalOutput) totalOutput.textContent = String(total);
    if (openOutput) openOutput.textContent = String(open).padStart(2, "0");
    if (progressBar) progressBar.style.width = `${percent}%`;
    if (progressRoot) {
      progressRoot.setAttribute("aria-valuenow", String(checked));
      progressRoot.setAttribute("aria-valuemax", String(total));
    }

    if (summaryOutput) {
      if (open === 0) {
        summaryOutput.textContent = "Clear. The direction is ready for the next hand.";
      } else if (open === 1) {
        summaryOutput.textContent = "One move left. Finish the last check.";
      } else if (open <= 9) {
        summaryOutput.textContent = `${numberWord(open)} moves left. Keep the deck moving toward real.`;
      } else {
        summaryOutput.textContent = `${open} moves left. Start with the steps that make the deck real.`;
      }
    }

    updateLaneCounts();
  }

  taskInputs.forEach((input) => {
    input.addEventListener("change", () => {
      updateTodo();
      saveTasks();
    });
  });

  filterButtons.forEach((button) => {
    button.addEventListener("click", () => setFilter(button.dataset.todoFilter));
  });

  if (resetButton) {
    resetButton.addEventListener("click", () => {
      taskInputs.forEach((input) => {
        input.checked = defaultTasks.includes(input.dataset.task);
      });
      setFilter("all");
      updateTodo();
      saveTasks();
      if (savedStatus) savedStatus.textContent = "Defaults restored.";
    });
  }

  const savedTasks = readSavedTasks();
  taskInputs.forEach((input) => {
    input.checked = savedTasks.has(input.dataset.task);
  });
  setFilter("all");
  updateTodo();
  if (savedStatus) savedStatus.textContent = "Ready.";

  /* Manifest */

  const manifest = [
    "GOT — INTERACTIVE HANDOFF",
    "Updated 09 Nov 2026",
    "",
    "SOURCE",
    "index.html — handoff structure",
    "handoff.css — design system and responsive layout",
    "app.js — interactions, persistence, file inspector",
    "HANDOFF.md — written brief",
    "",
    "CAPTURES",
    "got-deck-touchpad.png — touchpad state, desktop",
    "got-deck-keyboard.png — keyboard state, desktop",
    "got-deck-mobile.png — keyboard state, 390px",
    "got-deck-keyboard-320.png — keyboard state, 320px",
    "",
    "Direction: personal VNC companion / split-deck control surface."
  ].join("\n");

  if (manifestButton) {
    manifestButton.addEventListener("click", async () => {
      window.clearTimeout(copyTimer);
      await copyWithFeedback(
        manifest,
        manifestStatus,
        "Copied. The current file list is on your clipboard.",
        "Copy was blocked. Select the file list and copy it manually."
      );
      copyTimer = window.setTimeout(() => {
        if (manifestStatus) manifestStatus.textContent = "";
      }, 5200);
    });
  }

  /* File inspector */

  const dialog = document.getElementById("file-dialog");
  const dialogTitle = document.getElementById("file-dialog-title");
  const dialogType = dialog?.querySelector("[data-dialog-type]");
  const dialogPath = dialog?.querySelector("[data-dialog-path]");
  const dialogCode = dialog?.querySelector("[data-file-code]");
  const dialogImage = dialog?.querySelector("[data-file-image]");
  const dialogStatus = dialog?.querySelector("[data-dialog-status]");
  const dialogOpen = dialog?.querySelector("[data-dialog-open]");
  const dialogClose = dialog?.querySelector("[data-dialog-close]");

  function setDialogMode(mode) {
    if (dialogCode) dialogCode.hidden = mode !== "text";
    if (dialogImage) dialogImage.hidden = mode !== "image";
  }

  function closeDialog() {
    if (!dialog) return;
    inspectorRequest += 1;
    if (dialog.open) dialog.close();
  }

  async function openInspector(key) {
    const file = fileMap[key];
    if (!dialog || !file) return;

    const request = ++inspectorRequest;
    setDialogMode(file.mode);

    if (dialogTitle) dialogTitle.textContent = file.title;
    if (dialogType) dialogType.textContent = file.type;
    if (dialogPath) dialogPath.textContent = file.path;
    if (dialogStatus) dialogStatus.textContent = "Loading current file…";
    if (dialogOpen) {
      dialogOpen.href = `./${file.path}`;
      dialogOpen.textContent = file.mode === "image" ? "Open image" : "Open file";
    }

    if (dialogCode) dialogCode.textContent = "";
    if (dialogImage) {
      dialogImage.removeAttribute("src");
      dialogImage.alt = "";
    }

    if (!dialog.open) {
      dialog.showModal();
    } else {
      dialog.querySelector(".file-dialog-body")?.scrollTo(0, 0);
    }

    if (file.mode === "image") {
      if (dialogImage) {
        dialogImage.alt = `${file.title} — ${file.path}`;
        dialogImage.src = `./${file.path}`;
        dialogImage.onload = () => {
          if (request !== inspectorRequest) return;
          if (dialogStatus) dialogStatus.textContent = "Loaded from the current handoff folder.";
        };
        dialogImage.onerror = () => {
          if (request !== inspectorRequest) return;
          if (dialogStatus) dialogStatus.textContent = "Capture not found in the current folder.";
        };
      }
      return;
    }

    try {
      const response = await fetch(`./${file.path}`, { cache: "no-store" });
      if (!response.ok) throw new Error(`HTTP ${response.status}`);
      const text = await response.text();
      if (request !== inspectorRequest) return;
      if (dialogCode) dialogCode.textContent = text;
      if (dialogStatus) dialogStatus.textContent = "Loaded from the current handoff folder.";
    } catch {
      if (request !== inspectorRequest) return;
      if (dialogCode) dialogCode.textContent = `Could not read ${file.path}. Open the file to inspect the source directly.`;
      if (dialogStatus) dialogStatus.textContent = "The file could not be loaded here.";
    }
  }

  document.querySelectorAll("[data-file]").forEach((button) => {
    button.addEventListener("click", () => openInspector(button.dataset.file));
  });

  if (dialogClose) {
    dialogClose.addEventListener("click", closeDialog);
  }

  if (dialog) {
    dialog.addEventListener("click", (event) => {
      if (event.target === dialog) closeDialog();
    });
    dialog.addEventListener("close", () => {
      inspectorRequest += 1;
    });
  }

  /* Active section nav */

  if ("IntersectionObserver" in window && sections.length) {
    const visible = new Map();

    const navObserver = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          visible.set(entry.target.id, entry.isIntersecting ? entry.intersectionRatio : 0);
        });

        let activeId = "";
        let highest = 0;
        visible.forEach((ratio, id) => {
          if (ratio > highest) {
            highest = ratio;
            activeId = id;
          }
        });

        navLinks.forEach((link) => {
          if (link.dataset.nav === activeId) {
            link.setAttribute("aria-current", "location");
          } else {
            link.removeAttribute("aria-current");
          }
        });
      },
      {
        rootMargin: "-24% 0px -58% 0px",
        threshold: [0, 0.01, 0.25, 0.5, 0.75, 1]
      }
    );

    sections.forEach((section) => navObserver.observe(section));
  }

  /* Initial state */

  if (reduceMotion) {
    document.documentElement.classList.add("reduced-motion");
  }

  window.clearTimeout(statusTimer);
  statusTimer = window.setTimeout(() => {
    if (savedStatus && savedStatus.textContent === "Defaults restored.") {
      savedStatus.textContent = "Ready.";
    }
  }, 2400);
})();
