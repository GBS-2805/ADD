# Engine spike — local rules

Read the repository-root `AGENTS.md` first. This file adds only engine-local commands
and traps; it never overrides the root contract.

- From this directory, run `bash verify-suite.sh` for the engine suite and
  `bash verify-chrome.sh` for the chrome goldens.
- The baseline is 209 tests and 24 chrome goldens. Report the current counts; do not
  copy a historical count.
- The build needs JDK 17 and the Android 35 SDK. If `java`, the Android SDK or a device
  is absent, report the check as not run; never substitute a VM or emulator for a phone.
- `app/build/` and staged assets are generated. Rebuild them; never hand-edit them.
- Keep the `app.add.engine` package and the existing renderer until the BMO integration
  task explicitly changes them.
