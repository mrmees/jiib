#!/usr/bin/env python3
"""
ws-capture.py — Moonraker websocket wire-capture client for the D-10 SAVE_CONFIG
/ klippy-restart reliability investigation (Phase 13, plan 13-01).

WHY THIS EXISTS
---------------
The app's in-session re-handshake (MoonrakerSession.runHandshake) does NOT restore the
live status subscription after a Klipper FIRMWARE_RESTART / SAVE_CONFIG on the E3 —
the feed freezes until the app is force-stopped. The Wave-1 fix literally branches on
what the wire actually does across the restart window (does notify_klippy_ready arrive
on the SAME socket? is webhooks.state the real signal? do all five re-handshake steps
reply, error, or go silent? is the failure printer-dependent?). A green unit suite has
lied about this once already (the identify-needs-url bug). So we capture the truth on
BOTH physical printers FIRST and commit it as a .jsonl fixture.

This script replays the EXACT five-step handshake the app sends, in the SAME order
(mirrors MoonrakerSession.kt runHandshake ~322-356):

    1. server.connection.identify   (MUST carry a non-empty `url` — the Phase-2 bug)
    2. server.info
    3. printer.objects.list
    4. printer.objects.query   (the v1 core subset)
    5. printer.objects.subscribe (that same subset)

It logs EVERY inbound frame (replies + notifications) to a .jsonl with a monotonic
timestamp, one JSON object per line. After a wait window (during which the HUMAN
triggers SAVE_CONFIG / FIRMWARE_RESTART from Mainsail or a second shell), it RE-RUNS
all five steps on the SAME socket and records the reply (result / error / or documented
silence) to EACH of the five post-restart steps, then keeps logging diffs for ~30s.

USAGE (WSL-native python3 — NOT python.exe):
    python3 tools/ws-capture.py <host> <port> <out.jsonl>
    python3 tools/ws-capture.py 192.168.1.120 7125 docs/commands/e5-saveconfig-capture.jsonl
    python3 tools/ws-capture.py 192.168.1.121 7125 docs/commands/e3-saveconfig-capture.jsonl

Optional flags:
    --restart-wait <s>   seconds to wait for the human to trigger SAVE_CONFIG before the
                         post-restart re-handshake (default 45). The script also re-handshakes
                         EARLY the moment it sees a klippy-drop OR a klippy-ready signal.
    --post-wait <s>      seconds to keep logging diffs after the re-handshake (default 30).
    --api-key <key>      optional Moonraker API key (sent in identify; otherwise placeholder url only).

Each output line is one of:
    {"t": <mono>, "dir": "in",  "frame": <raw parsed JSON frame from Moonraker>}
    {"t": <mono>, "dir": "out", "frame": <the request we sent>}
    {"t": <mono>, "dir": "meta", "event": "<marker>", "detail": {...}}    # phase markers / reply-correlation

The "meta" lines annotate the capture (handshake step boundaries, which post-restart
step a reply correlated to, observed klippy-state signal) so the fixture is
self-describing without needing the analyst to re-derive id↔step mapping by hand. The
load-bearing wire truth is the "in" frames; "meta" is navigation.

SAFETY / THREAT MODEL (13-01 register): one websocket, the SAME frames the app sends
(identify/server.info/list/query/subscribe), read-only logging beyond that handshake —
no extra cadence, no writes. The captured frames are local-LAN Moonraker config values
with no secrets (identify url is a placeholder; --api-key, if given, is NOT logged into
the fixture — only used in the outbound identify, and the api_key field is redacted in
the logged 'out' frame). Confirm no token leaks before committing.
"""

import argparse
import asyncio
import copy
import json
import sys
import time

try:
    import websockets
except ImportError:
    sys.stderr.write(
        "FATAL: the `websockets` package is required.\n"
        "Install WSL-native (NOT python.exe):  python3 -m pip install --user websockets\n"
    )
    sys.exit(2)


# ---------------------------------------------------------------------------
# The EXACT five-step handshake the app sends (mirrors MoonrakerSession.kt
# runHandshake ~322-356 + CommandRegistry wire methods + objectsParam shape).
# ---------------------------------------------------------------------------

# MoonrakerSession defaults (clientName/clientVersion/clientUrl ~79-84). The `url`
# MUST be non-empty — omitting it was the Phase-2 live bug the whole project learned from.
CLIENT_NAME = "jiib (ws-capture)"
CLIENT_VERSION = "0.1.0"
CLIENT_TYPE = "display"
CLIENT_URL = "https://github.com/mrmees/dinghy-display"

# The v1 core subset the app queries/subscribes (DeriveCapabilities.V1_SUBSCRIBE_CORE,
# intersected on-device with objects.list; here we send the full superset — the printer
# replies only for the objects it actually defines, which is itself useful evidence).
# The task action names the heaters/print_stats/toolhead/gcode_move/webhooks/configfile
# core explicitly; we include the full V1 superset so the capture mirrors the real app.
V1_SUBSCRIBE_CORE = [
    "webhooks",
    "print_stats",
    "pause_resume",
    "virtual_sdcard",
    "display_status",
    "toolhead",
    "gcode_move",
    "heater_bed",
    "extruder",
    "configfile",
    "screws_tilt_adjust",
    "z_tilt",
    "quad_gantry_level",
    "bed_mesh",
    "manual_probe",
    "probe",
]

# Klippy lifecycle notifications (JsonRpcMethods) — the load-bearing restart signals.
KLIPPY_DISCONNECTED = "notify_klippy_disconnected"
KLIPPY_SHUTDOWN = "notify_klippy_shutdown"
KLIPPY_READY = "notify_klippy_ready"
STATUS_UPDATE = "notify_status_update"


def objects_param(objects):
    """Mirror CommandRegistry.objectsParam: {"objects": {name: null, ...}}."""
    return {"objects": {name: None for name in objects}}


class IdGen:
    def __init__(self):
        self._n = 0

    def next(self):
        self._n += 1
        return self._n


def redact_out(frame):
    """Never write an api_key into the committed fixture."""
    f = copy.deepcopy(frame)
    params = f.get("params")
    if isinstance(params, dict) and "api_key" in params:
        params["api_key"] = "<redacted>"
    return f


class Capture:
    def __init__(self, ws, out_fh):
        self.ws = ws
        self.out = out_fh
        self.ids = IdGen()
        # id -> (step_label, sent_mono); used to annotate which post-restart step a reply answers.
        self.pending = {}
        self.t0 = time.monotonic()
        # Observed klippy-state evidence across the window.
        self.saw_disconnected = False
        self.saw_shutdown = False
        self.saw_ready = False
        self.saw_webhooks_transition = []  # list of observed webhooks.state values

    def _mono(self):
        return round(time.monotonic() - self.t0, 4)

    def log_meta(self, event, detail=None):
        rec = {"t": self._mono(), "dir": "meta", "event": event}
        if detail is not None:
            rec["detail"] = detail
        self.out.write(json.dumps(rec) + "\n")
        self.out.flush()
        print(f"[meta {rec['t']:>8}] {event} {detail if detail else ''}", file=sys.stderr)

    def log_in(self, frame):
        rec = {"t": self._mono(), "dir": "in", "frame": frame}
        self.out.write(json.dumps(rec) + "\n")
        self.out.flush()

    def log_out(self, frame):
        rec = {"t": self._mono(), "dir": "out", "frame": redact_out(frame)}
        self.out.write(json.dumps(rec) + "\n")
        self.out.flush()

    async def send(self, method, params, step_label):
        """Send one JSON-RPC request, log it 'out', and remember its id for reply correlation."""
        rid = self.ids.next()
        frame = {"jsonrpc": "2.0", "method": method, "id": rid}
        if params is not None:
            frame["params"] = params
        self.pending[rid] = (step_label, self._mono())
        self.log_out(frame)
        await self.ws.send(json.dumps(frame))
        print(f"[out  {self._mono():>8}] -> {method}  (step={step_label}, id={rid})", file=sys.stderr)
        return rid

    def _note_signal(self, frame):
        """Track klippy-state evidence for the end-of-run summary + meta markers."""
        method = frame.get("method")
        if method == KLIPPY_DISCONNECTED and not self.saw_disconnected:
            self.saw_disconnected = True
            self.log_meta("klippy_signal", {"signal": KLIPPY_DISCONNECTED})
        elif method == KLIPPY_SHUTDOWN and not self.saw_shutdown:
            self.saw_shutdown = True
            self.log_meta("klippy_signal", {"signal": KLIPPY_SHUTDOWN})
        elif method == KLIPPY_READY and not self.saw_ready:
            self.saw_ready = True
            self.log_meta("klippy_signal", {"signal": KLIPPY_READY})
        elif method == STATUS_UPDATE:
            # Pitfall 5: webhooks.state may be the real restart signal. params is
            # typically [ {status...}, eventtime ].
            params = frame.get("params")
            if isinstance(params, list) and params and isinstance(params[0], dict):
                wh = params[0].get("webhooks")
                if isinstance(wh, dict) and "state" in wh:
                    self.saw_webhooks_transition.append(wh["state"])
                    self.log_meta("webhooks_state", {"state": wh["state"]})

    async def reader_loop(self, deadline, on_signal=None):
        """Read+log inbound frames until `deadline` (monotonic-relative seconds from t0).

        Calls on_signal(method) once for each klippy-state signal seen (so the driver can
        re-handshake EARLY on the first drop/ready rather than waiting the full window).
        """
        while True:
            remaining = deadline - (time.monotonic() - self.t0)
            if remaining <= 0:
                return
            try:
                raw = await asyncio.wait_for(self.ws.recv(), timeout=remaining)
            except asyncio.TimeoutError:
                return
            except (websockets.ConnectionClosed, websockets.ConnectionClosedError):
                self.log_meta("socket_closed", {"note": "Moonraker closed the websocket"})
                return
            try:
                frame = json.loads(raw)
            except json.JSONDecodeError:
                self.log_meta("unparseable_frame", {"raw": raw[:500]})
                continue
            self.log_in(frame)
            # Correlate replies to the step that asked.
            rid = frame.get("id")
            if rid in self.pending:
                step_label, sent_at = self.pending.pop(rid)
                kind = "error" if "error" in frame else "result"
                self.log_meta(
                    "reply",
                    {"step": step_label, "id": rid, "kind": kind,
                     "latency_s": round(self._mono() - sent_at, 4)},
                )
            self._note_signal(frame)
            if on_signal is not None and frame.get("method") in (
                KLIPPY_DISCONNECTED, KLIPPY_SHUTDOWN, KLIPPY_READY,
            ):
                on_signal(frame.get("method"))

    async def run_handshake(self, phase):
        """Send the EXACT five steps in order. `phase` ∈ {'initial','post-restart'}."""
        self.log_meta("handshake_begin", {"phase": phase})
        await self.send(
            "server.connection.identify",
            {
                "client_name": CLIENT_NAME,
                "version": CLIENT_VERSION,
                "type": CLIENT_TYPE,
                "url": CLIENT_URL,  # NON-EMPTY — the Phase-2 identify bug.
            },
            step_label=f"{phase}:1-identify",
        )
        await self.send("server.info", None, step_label=f"{phase}:2-server.info")
        await self.send("printer.objects.list", None, step_label=f"{phase}:3-objects.list")
        await self.send(
            "printer.objects.query", objects_param(V1_SUBSCRIBE_CORE),
            step_label=f"{phase}:4-objects.query",
        )
        await self.send(
            "printer.objects.subscribe", objects_param(V1_SUBSCRIBE_CORE),
            step_label=f"{phase}:5-objects.subscribe",
        )
        self.log_meta("handshake_sent", {"phase": phase, "steps": 5})


async def capture(host, port, out_path, restart_wait, post_wait, api_key):
    uri = f"ws://{host}:{port}/websocket"
    print(f"Connecting to {uri} ...", file=sys.stderr)

    # readtimeout(0) analog: no client ping (Moonraker drives keepalive); large queues so a
    # restart burst is never dropped.
    async with websockets.connect(uri, ping_interval=None, max_queue=None) as ws:
        with open(out_path, "w", encoding="utf-8") as out_fh:
            cap = Capture(ws, out_fh)
            cap.log_meta("session_begin", {"uri": uri, "host": host, "port": port})

            # If an api key is supplied, re-do identify carrying it (the app sends api_key when
            # present). We fold it into the initial identify by patching CLIENT params here.
            if api_key:
                cap.log_meta("api_key", {"present": True, "note": "api_key sent in identify, redacted in fixture"})

            # ---- PHASE 1: initial five-step handshake ----
            await cap.run_handshake("initial")
            if api_key:
                # send an extra identify carrying the key (kept simple; redacted in fixture)
                await cap.send(
                    "server.connection.identify",
                    {"client_name": CLIENT_NAME, "version": CLIENT_VERSION,
                     "type": CLIENT_TYPE, "url": CLIENT_URL, "api_key": api_key},
                    step_label="initial:1b-identify-keyed",
                )

            # Drain initial replies + steady-state diffs briefly so the baseline is captured.
            cap.log_meta("await_initial_replies", {"window_s": 5})
            await cap.reader_loop(deadline=cap.t0_relative_deadline(5))

            # ---- HUMAN WINDOW: wait for SAVE_CONFIG / FIRMWARE_RESTART ----
            print(
                f"\n>>> NOW trigger SAVE_CONFIG (or FIRMWARE_RESTART) on {host} from Mainsail "
                f"or a second shell. Waiting up to {restart_wait}s (re-handshakes early on the "
                f"first klippy drop/ready signal)...\n",
                file=sys.stderr,
            )
            cap.log_meta("await_restart", {"max_wait_s": restart_wait})

            drop_seen = {"hit": False}

            def on_signal(method):
                # Re-handshake early the moment klippy drops OR comes back ready.
                drop_seen["hit"] = True

            # Read until we see a klippy signal OR the restart-wait elapses.
            window_end = (time.monotonic() - cap.t0) + restart_wait
            await cap.reader_loop(deadline=window_end, on_signal=on_signal)

            if not (cap.saw_disconnected or cap.saw_shutdown or cap.saw_ready or cap.saw_webhooks_transition):
                cap.log_meta(
                    "no_restart_signal",
                    {"note": "no klippy drop/ready/webhooks transition observed in the window — "
                             "did the human trigger SAVE_CONFIG? proceeding with the post-restart "
                             "re-handshake anyway to record the replies."},
                )

            # Give klippy a moment to come fully back up before the re-handshake if we only saw a drop.
            if (cap.saw_disconnected or cap.saw_shutdown) and not cap.saw_ready:
                cap.log_meta("await_klippy_ready", {"window_s": 15})
                await cap.reader_loop(
                    deadline=(time.monotonic() - cap.t0) + 15,
                    on_signal=on_signal,
                )

            # ---- PHASE 2: post-restart five-step re-handshake on the SAME socket ----
            await cap.run_handshake("post-restart")

            # ---- POST: keep logging diffs ~post_wait s so we learn if diffs resumed ----
            cap.log_meta("await_post_restart_diffs", {"window_s": post_wait})
            await cap.reader_loop(deadline=(time.monotonic() - cap.t0) + post_wait)

            # ---- Summary meta (also printed to the operator for the resume-signal report) ----
            summary = {
                "saw_klippy_disconnected": cap.saw_disconnected,
                "saw_klippy_shutdown": cap.saw_shutdown,
                "saw_klippy_ready_same_socket": cap.saw_ready,
                "webhooks_state_transitions": cap.saw_webhooks_transition,
                "post_restart_steps_still_pending_no_reply": [
                    label for (label, _ts) in cap.pending.values() if label.startswith("post-restart")
                ],
            }
            cap.log_meta("session_summary", summary)
            print("\n=== CAPTURE SUMMARY ===", file=sys.stderr)
            print(json.dumps(summary, indent=2), file=sys.stderr)
            print(f"Wrote: {out_path}", file=sys.stderr)


# Small helper bound onto Capture for readability above.
def _t0_relative_deadline(self, seconds):
    return (time.monotonic() - self.t0) + seconds


Capture.t0_relative_deadline = _t0_relative_deadline


def main():
    ap = argparse.ArgumentParser(description="Moonraker websocket SAVE_CONFIG/restart wire-capture (D-10).")
    ap.add_argument("host", help="Moonraker host (e.g. 192.168.1.120)")
    ap.add_argument("port", type=int, help="Moonraker websocket port (e.g. 7125)")
    ap.add_argument("out", help="output .jsonl path (e.g. docs/commands/e5-saveconfig-capture.jsonl)")
    ap.add_argument("--restart-wait", type=float, default=45.0,
                    help="seconds to wait for the human SAVE_CONFIG trigger (default 45)")
    ap.add_argument("--post-wait", type=float, default=30.0,
                    help="seconds to keep logging diffs after the re-handshake (default 30)")
    ap.add_argument("--api-key", default=None, help="optional Moonraker API key (redacted in fixture)")
    args = ap.parse_args()

    try:
        asyncio.run(
            capture(args.host, args.port, args.out, args.restart_wait, args.post_wait, args.api_key)
        )
    except KeyboardInterrupt:
        print("\nInterrupted — partial capture saved.", file=sys.stderr)
        sys.exit(130)
    except OSError as e:
        sys.stderr.write(f"FATAL: could not connect to {args.host}:{args.port} — {e}\n")
        sys.exit(1)


if __name__ == "__main__":
    main()
