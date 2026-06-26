#!/usr/bin/env python3
"""
spoolman-probe.py - read-only Moonraker/Spoolman contract probe for Jiib.

This is intentionally a host-side tool, not app code. It lets us capture the
real response shapes Jiib's later Spoolman phase will parse while unrelated app
work is active.

Default mode is read-only:
  - websocket JSON-RPC identify
  - server.info
  - server.spoolman.status
  - server.spoolman.get_spool_id
  - server.spoolman.proxy GET /v1/spool
  - server.spoolman.proxy GET /v1/spool/<active-or-requested-id>
  - optional notification listen window

The output is one JSON object suitable for saving as a fixture after inspection.
No API key is written to the output.
"""

from __future__ import annotations

import argparse
import asyncio
import copy
import json
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import dataclass
from typing import Any


CLIENT_NAME = "jiib Spoolman Probe"
CLIENT_VERSION = "0.1.0"
CLIENT_TYPE = "display"
CLIENT_URL = "https://github.com/mrmees/dinghy-display"

NOTIFY_ACTIVE_SPOOL_SET = "notify_active_spool_set"
NOTIFY_SPOOLMAN_STATUS_CHANGED = "notify_spoolman_status_changed"


@dataclass
class Args:
    host: str
    port: int
    api_key: str | None
    out: str | None
    timeout: float
    spool_id: int | None
    limit: int
    listen: float
    http_only: bool


class Ids:
    def __init__(self) -> None:
        self._n = 0

    def next(self) -> int:
        self._n += 1
        return self._n


def redact(value: Any) -> Any:
    """Deep-copy and redact known secret fields before logging."""
    copied = copy.deepcopy(value)

    def walk(node: Any) -> None:
        if isinstance(node, dict):
            for key in list(node.keys()):
                if key.lower() in {"api_key", "apikey", "token", "access_token"}:
                    node[key] = "<redacted>"
                else:
                    walk(node[key])
        elif isinstance(node, list):
            for item in node:
                walk(item)

    walk(copied)
    return copied


def json_body(data: dict[str, Any] | None) -> bytes | None:
    if data is None:
        return None
    return json.dumps(data, separators=(",", ":")).encode("utf-8")


def http_request(
    method: str,
    base: str,
    path: str,
    api_key: str | None,
    body: dict[str, Any] | None = None,
    timeout: float = 5.0,
) -> dict[str, Any]:
    url = base.rstrip("/") + path
    headers = {"Accept": "application/json"}
    data = json_body(body)
    if data is not None:
        headers["Content-Type"] = "application/json"
    if api_key:
        headers["X-Api-Key"] = api_key
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    started = time.monotonic()
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            raw = resp.read().decode("utf-8", errors="replace")
            parsed = json.loads(raw) if raw else None
            return {
                "ok": True,
                "status": resp.status,
                "elapsed_s": round(time.monotonic() - started, 4),
                "body": parsed,
            }
    except urllib.error.HTTPError as exc:
        raw = exc.read().decode("utf-8", errors="replace")
        parsed: Any
        try:
            parsed = json.loads(raw) if raw else None
        except json.JSONDecodeError:
            parsed = raw
        return {
            "ok": False,
            "status": exc.code,
            "elapsed_s": round(time.monotonic() - started, 4),
            "body": parsed,
        }
    except Exception as exc:  # noqa: BLE001 - probe should report, not crash.
        return {
            "ok": False,
            "elapsed_s": round(time.monotonic() - started, 4),
            "error": f"{type(exc).__name__}: {exc}",
        }


def unwrap_result(frame_or_body: Any) -> Any:
    """Return a useful body from either JSON-RPC or REST response shapes."""
    if isinstance(frame_or_body, dict):
        if "result" in frame_or_body:
            return frame_or_body.get("result")
        if "body" in frame_or_body:
            body = frame_or_body.get("body")
            if isinstance(body, dict) and "result" in body:
                return body.get("result")
            return body
    return frame_or_body


def get_path(obj: Any, *path: str) -> Any:
    cur = obj
    for key in path:
        if not isinstance(cur, dict):
            return None
        cur = cur.get(key)
    return cur


def summarize(calls: list[dict[str, Any]], notifications: list[dict[str, Any]]) -> dict[str, Any]:
    by_label = {call["label"]: unwrap_result(call.get("response")) for call in calls}
    server_info = by_label.get("server.info")
    status = by_label.get("server.spoolman.status")
    spool_id_body = by_label.get("server.spoolman.get_spool_id")
    spool_list_proxy = by_label.get("server.spoolman.proxy.list")

    components = get_path(server_info, "components")
    if not isinstance(components, list):
        components = []

    active_id = None
    if isinstance(status, dict):
        active_id = status.get("spool_id")
    if active_id is None and isinstance(spool_id_body, dict):
        active_id = spool_id_body.get("spool_id")

    pending_reports = []
    if isinstance(status, dict) and isinstance(status.get("pending_reports"), list):
        pending_reports = status["pending_reports"]

    proxy_response = None
    proxy_error = None
    if isinstance(spool_list_proxy, dict):
        proxy_response = spool_list_proxy.get("response")
        proxy_error = spool_list_proxy.get("error")

    sample_count = None
    if isinstance(proxy_response, list):
        sample_count = len(proxy_response)
    elif isinstance(proxy_response, dict):
        # Spoolman list responses are version/config dependent. Preserve this
        # without assuming a specific pagination envelope.
        for key in ("items", "results", "data"):
            if isinstance(proxy_response.get(key), list):
                sample_count = len(proxy_response[key])
                break

    return {
        "has_spoolman_component": "spoolman" in components,
        "spoolman_connected": status.get("spoolman_connected") if isinstance(status, dict) else None,
        "active_spool_id": active_id,
        "pending_reports_count": len(pending_reports),
        "proxy_v2_has_response_key": isinstance(spool_list_proxy, dict) and "response" in spool_list_proxy,
        "proxy_v2_error": proxy_error,
        "sample_spool_count": sample_count,
        "spoolman_notifications_seen": [
            note.get("method")
            for note in notifications
            if note.get("method") in {NOTIFY_ACTIVE_SPOOL_SET, NOTIFY_SPOOLMAN_STATUS_CHANGED}
        ],
    }


async def websocket_probe(args: Args) -> dict[str, Any]:
    try:
        import websockets
    except ImportError:
        raise SystemExit(
            "FATAL: websocket mode requires the `websockets` package.\n"
            "Install WSL-native: python3 -m pip install --user websockets\n"
            "Or rerun with --http-only for REST-only coverage."
        )

    uri = f"ws://{args.host}:{args.port}/websocket"
    ids = Ids()
    calls: list[dict[str, Any]] = []
    notifications: list[dict[str, Any]] = []

    async with websockets.connect(uri, ping_interval=None, close_timeout=args.timeout) as ws:
        pending: dict[int, str] = {}

        async def request(label: str, method: str, params: dict[str, Any] | None = None) -> Any:
            rid = ids.next()
            frame: dict[str, Any] = {"jsonrpc": "2.0", "method": method, "id": rid}
            if params is not None:
                frame["params"] = params
            pending[rid] = label
            started = time.monotonic()
            await ws.send(json.dumps(frame, separators=(",", ":")))

            while True:
                raw = await asyncio.wait_for(ws.recv(), timeout=args.timeout)
                parsed = json.loads(raw)
                if "id" not in parsed:
                    notifications.append(parsed)
                    continue
                if parsed.get("id") != rid:
                    # Unexpected response for another request. Keep it visible.
                    calls.append(
                        {
                            "label": pending.pop(parsed.get("id"), "unexpected_response"),
                            "request": None,
                            "response": parsed,
                            "elapsed_s": None,
                        }
                    )
                    continue
                pending.pop(rid, None)
                calls.append(
                    {
                        "label": label,
                        "request": redact(frame),
                        "response": parsed,
                        "elapsed_s": round(time.monotonic() - started, 4),
                    }
                )
                return parsed.get("result")

        identify_params: dict[str, Any] = {
            "client_name": CLIENT_NAME,
            "version": CLIENT_VERSION,
            "type": CLIENT_TYPE,
            "url": CLIENT_URL,
        }
        if args.api_key:
            identify_params["api_key"] = args.api_key

        await request("server.connection.identify", "server.connection.identify", identify_params)
        await request("server.info", "server.info")
        status = await request("server.spoolman.status", "server.spoolman.status")
        spool_id_result = await request("server.spoolman.get_spool_id", "server.spoolman.get_spool_id")

        await request(
            "server.spoolman.proxy.list",
            "server.spoolman.proxy",
            {
                "use_v2_response": True,
                "request_method": "GET",
                "path": "/v1/spool",
                "query": urllib.parse.urlencode({"allow_archived": "false", "limit": str(args.limit)}),
            },
        )

        detail_id = args.spool_id
        if detail_id is None and isinstance(status, dict):
            detail_id = status.get("spool_id")
        if detail_id is None and isinstance(spool_id_result, dict):
            detail_id = spool_id_result.get("spool_id")

        if isinstance(detail_id, int):
            await request(
                "server.spoolman.proxy.detail",
                "server.spoolman.proxy",
                {
                    "use_v2_response": True,
                    "request_method": "GET",
                    "path": f"/v1/spool/{detail_id}",
                },
            )

        if args.listen > 0:
            deadline = time.monotonic() + args.listen
            while time.monotonic() < deadline:
                try:
                    raw = await asyncio.wait_for(ws.recv(), timeout=max(0.0, deadline - time.monotonic()))
                except asyncio.TimeoutError:
                    break
                notifications.append(json.loads(raw))

    return {
        "transport": "websocket-jsonrpc",
        "calls": calls,
        "notifications": notifications,
        "summary": summarize(calls, notifications),
    }


def http_probe(args: Args) -> dict[str, Any]:
    base = f"http://{args.host}:{args.port}"
    calls: list[dict[str, Any]] = []

    def call(label: str, method: str, path: str, body: dict[str, Any] | None = None) -> Any:
        response = http_request(method, base, path, args.api_key, body=body, timeout=args.timeout)
        calls.append(
            {
                "label": label,
                "request": redact({"method": method, "url": base + path, "body": body}),
                "response": response,
            }
        )
        return response.get("body")

    call("server.info", "GET", "/server/info")
    status = call("server.spoolman.status", "GET", "/server/spoolman/status")
    spool_id_body = call("server.spoolman.get_spool_id", "GET", "/server/spoolman/spool_id")

    query = urllib.parse.urlencode({"allow_archived": "false", "limit": str(args.limit)})
    call(
        "server.spoolman.proxy.list",
        "POST",
        "/server/spoolman/proxy",
        {
            "use_v2_response": True,
            "request_method": "GET",
            "path": "/v1/spool",
            "query": query,
        },
    )

    detail_id = args.spool_id
    if detail_id is None and isinstance(status, dict):
        detail_id = status.get("spool_id")
    if detail_id is None and isinstance(spool_id_body, dict):
        detail_id = spool_id_body.get("spool_id")

    if isinstance(detail_id, int):
        call(
            "server.spoolman.proxy.detail",
            "POST",
            "/server/spoolman/proxy",
            {"use_v2_response": True, "request_method": "GET", "path": f"/v1/spool/{detail_id}"},
        )

    return {
        "transport": "http-rest",
        "calls": calls,
        "notifications": [],
        "summary": summarize(calls, []),
    }


def parse_args(argv: list[str]) -> Args:
    parser = argparse.ArgumentParser(
        description="Read-only Moonraker Spoolman bridge probe for Jiib planning fixtures.",
    )
    parser.add_argument("host", help="Moonraker host or IP")
    parser.add_argument("--port", type=int, default=7125, help="Moonraker port (default: 7125)")
    parser.add_argument("--api-key", default=None, help="Optional Moonraker API key; redacted from output")
    parser.add_argument("--out", default=None, help="Write JSON output to this file instead of stdout")
    parser.add_argument("--timeout", type=float, default=5.0, help="Per-call timeout in seconds")
    parser.add_argument("--spool-id", type=int, default=None, help="Specific spool id to fetch through proxy")
    parser.add_argument("--limit", type=int, default=5, help="List probe spool limit")
    parser.add_argument(
        "--listen",
        type=float,
        default=0.0,
        help="WebSocket-only: seconds to listen for Spoolman notifications after probes",
    )
    parser.add_argument("--http-only", action="store_true", help="Use REST endpoints only; no websocket dependency")
    ns = parser.parse_args(argv)
    return Args(
        host=ns.host,
        port=ns.port,
        api_key=ns.api_key,
        out=ns.out,
        timeout=ns.timeout,
        spool_id=ns.spool_id,
        limit=ns.limit,
        listen=ns.listen,
        http_only=ns.http_only,
    )


def write_output(doc: dict[str, Any], out: str | None) -> None:
    text = json.dumps(doc, indent=2, sort_keys=True) + "\n"
    if out:
        with open(out, "w", encoding="utf-8") as fh:
            fh.write(text)
    else:
        sys.stdout.write(text)


def main(argv: list[str]) -> int:
    args = parse_args(argv)
    started = time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime())
    try:
        probe = http_probe(args) if args.http_only else asyncio.run(websocket_probe(args))
    except Exception as exc:  # noqa: BLE001 - produce fixture-shaped failure output.
        probe = {
            "transport": "http-rest" if args.http_only else "websocket-jsonrpc",
            "calls": [],
            "notifications": [],
            "summary": {},
            "fatal_error": f"{type(exc).__name__}: {exc}",
        }

    doc = {
        "meta": {
            "tool": "tools/spoolman-probe.py",
            "started_at_utc": started,
            "host": args.host,
            "port": args.port,
            "api_key": "<provided-redacted>" if args.api_key else None,
            "read_only": True,
        },
        **probe,
    }
    write_output(doc, args.out)
    return 1 if "fatal_error" in probe else 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
