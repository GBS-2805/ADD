# GOT — Interactive Handoff

**Personal VNC companion.**<br>
Direction locked / prototype interactive / updated 09 Nov 2026.

This page is the handoff: the brand, the current split-deck move, the open checks, and the current files all live in one place.

## The position

GOT is personal infrastructure with a pulse. It should feel close enough to touch: calm when idle, clear when something matters, and confident when you take control.

A companion, not a dashboard. Warm, not cute. Direct, not clinical. Confident, not loud.

## The design system

| Token | Value | Role |
| --- | --- | --- |
| Tangerine Tango | `#DD4124` | Signal, action, the one loud thing |
| Black 6 | `#101820` | Machinery, depth, the room |
| Mac Cream | `#F2E7D3` | Warmth, paper, breathing room |
| Wild Lime | `#C3D363` | Live connection, confirmation |
| Silver Birch | `#C9C6BC` | Quiet structure and secondary data |

**Type:** Anton for display, Instrument Sans for interface, IBM Plex Mono for data and state.

**Voice:** Say less, mean more. Name the next move. Let the connection do the talking.

## The current move

The phone is a control surface, not a shrunken desktop.

- The live framebuffer stays above.
- The control deck stays below, under the thumb.
- Touchpad and keyboard share one rail and swap in place.
- Every deck state keeps the live frame visible.
- The desktop is never replaced by chrome.

**Goal:** one-handed control without losing the thing you are controlling.<br>
**Not this:** a miniature desktop toolbar floating over the framebuffer.

Try the keyboard icon in the deck rail. Arrow keys move between the two tool buttons.

## What is current

- `index.html` — the interactive handoff page.
- `handoff.css` — palette, type, layout, responsive deck, focus and reduced-motion states.
- `app.js` — deck toggle, palette copy, local to-do persistence, filters, reset, manifest copy, file inspector, active navigation.
- `HANDOFF.md` — this brief.
- `got-deck-touchpad.png` — touchpad state, desktop capture.
- `got-deck-keyboard.png` — keyboard state, desktop capture.
- `got-deck-mobile.png` — keyboard state, 390px capture.
- `got-deck-keyboard-320.png` — keyboard state, 320px capture.

The folder contains only the current source and the current captures listed above.

## Open checks

The to-do section is the live list. By default it starts with the direction and keyboard mapping checked; all checks save in the browser.

The next moves are grouped as design, build, and validate:

- Finish the clipboard and key-press states.
- Port the deck into session view without disturbing desktop breakpoints.
- Wire keyboard and clipboard transport over RFB.
- Restore deck state on reconnect.
- Validate 320 / 390 / 430, keyboard and screen-reader order, and mobile input latency.

## Guardrails

- This handoff is design reference only. The production app in `src/` is not changed by this page.
- Keep the handoff to current source and current captures.
- Keep the warm, direct, quiet voice. No dashboard language, no forced cuteness.
- Test the split deck with two hands on a phone and one hand on a desktop. If the frame leaves the eye, the move is wrong.
- The deck is reversible: every state has a visible way back.
