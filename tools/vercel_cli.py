#!/usr/bin/env python3
"""Run the pinned Vercel CLI with a deadline, and show what it is doing.

Every Vercel call used to run with its output captured and no deadline. When the
CLI stalled, the job printed nothing between the command and its timeout, and
cancelling the job killed the npx wrapper while the node process behind it kept
running. These helpers stream the output, kill the whole session at the deadline,
and raise `Timeout` with the last lines so the caller can name the failure.
"""

from __future__ import annotations

import os
import signal
import subprocess
import threading

CLI_TIMEOUT = 600  # seconds one CLI call gets before it is treated as stalled


class Timeout(RuntimeError):
    """The CLI stopped responding and was killed."""

    def __init__(self, timeout: int, tail: str):
        super().__init__("the Vercel CLI stopped responding for %d seconds" % timeout)
        self.timeout = timeout
        self.tail = tail


def _kill(proc: subprocess.Popen) -> None:
    """Kill the CLI session, not just the npx wrapper: its node child must go too."""
    try:
        if hasattr(os, "killpg"):
            os.killpg(os.getpgid(proc.pid), signal.SIGKILL)
        else:
            proc.kill()
    except (ProcessLookupError, PermissionError):
        proc.kill()


def run(cmd: list[str], env: dict[str, str], timeout: int = CLI_TIMEOUT,
        cwd: str | None = None) -> tuple[int, str]:
    """Run cmd, echo each line as it arrives, and return (exit code, output)."""
    print("$ " + " ".join(cmd), flush=True)
    proc = subprocess.Popen(cmd, cwd=cwd, stdout=subprocess.PIPE,
                            stderr=subprocess.STDOUT, text=True, env=env,
                            start_new_session=True)
    stalled = threading.Event()

    def stop() -> None:
        stalled.set()
        _kill(proc)

    watchdog = threading.Timer(timeout, stop)
    watchdog.start()
    lines = []
    try:
        assert proc.stdout is not None
        for line in proc.stdout:
            lines.append(line)
            print(line, end="", flush=True)
        code = proc.wait()
    finally:
        watchdog.cancel()
        if proc.stdout:
            proc.stdout.close()
    out = "".join(lines)
    if stalled.is_set():
        raise Timeout(timeout, out.rstrip()[-2_000:])
    return code, out


def capture(cmd: list[str], env: dict[str, str], timeout: int = CLI_TIMEOUT,
            cwd: str | None = None) -> tuple[int, str, str]:
    """Run cmd without echoing it and return (exit code, stdout, stderr)."""
    proc = subprocess.Popen(cmd, cwd=cwd, stdout=subprocess.PIPE,
                            stderr=subprocess.PIPE, text=True, env=env,
                            start_new_session=True)
    try:
        out, err = proc.communicate(timeout=timeout)
    except subprocess.TimeoutExpired:
        _kill(proc)
        out, err = proc.communicate()
        raise Timeout(timeout, (err or out).strip()[-2_000:]) from None
    return proc.returncode, out, err
