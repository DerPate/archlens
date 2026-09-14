#!/usr/bin/env python3
"""Smoke-test source-backed Mermaid and LikeC4 rendering through MCP.

Usage: python3 scripts/self-test-diagram-renderers.py WORKSPACE [EXPECTED ...]
"""

import json
import subprocess
import sys
from pathlib import Path


if len(sys.argv) < 2:
    raise SystemExit(__doc__)

root = Path(__file__).resolve().parent.parent
workspace = str(Path(sys.argv[1]).expanduser().resolve())
expected = tuple(sys.argv[2:]) or ("JobController", "SpriteEasterEgg")
proc = subprocess.Popen(
    ["java", "-jar", str(root / "target" / "archlens.jar")],
    stdin=subprocess.PIPE,
    stdout=subprocess.PIPE,
    stderr=sys.stderr,
    text=True,
    cwd=root,
)
next_id = 0


def call(method, params=None):
    global next_id
    next_id += 1
    request = {"jsonrpc": "2.0", "id": next_id, "method": method}
    if params is not None:
        request["params"] = params
    proc.stdin.write(json.dumps(request) + "\n")
    proc.stdin.flush()
    line = proc.stdout.readline()
    if not line:
        raise RuntimeError(f"MCP server closed stdout while calling {method}")
    response = json.loads(line)
    if "error" in response:
        raise RuntimeError(response["error"])
    return response["result"]


def notify(method, params=None):
    message = {"jsonrpc": "2.0", "method": method}
    if params is not None:
        message["params"] = params
    proc.stdin.write(json.dumps(message) + "\n")
    proc.stdin.flush()


def tool(name, arguments):
    result = call("tools/call", {"name": name, "arguments": arguments})
    if result.get("isError"):
        raise RuntimeError(result.get("content", [{"text": name}])[0]["text"])
    return result


try:
    call(
        "initialize",
        {
            "protocolVersion": "2025-11-25",
            "capabilities": {},
            "clientInfo": {"name": "diagram-renderer-self-test", "version": "1"},
        },
    )
    notify("notifications/initialized", {})

    indexed = tool("index_workspace", {"paths": [workspace]})
    counts = indexed.get("structuredContent", {})
    for field in ("appCount", "componentCount", "entrypointCount"):
        if counts.get(field, 0) <= 0:
            raise AssertionError(f"index_workspace returned no {field}: {counts}")

    mermaid = tool("render_architecture_view", {})["content"][0]["text"]
    likec4 = tool("export_likec4_model", {})["content"][0]["text"]
    for name in expected:
        if name not in mermaid:
            raise AssertionError(f"Mermaid output is missing source symbol {name!r}")
        if name not in likec4:
            raise AssertionError(f"LikeC4 output is missing source symbol {name!r}")
    if "flowchart" not in mermaid:
        raise AssertionError("Mermaid output is not a flowchart")
    for section in ("specification", "model", "views"):
        if section not in likec4:
            raise AssertionError(f"LikeC4 output is missing {section!r} section")
    print(f"SELF TEST PASS: {workspace} ({counts['componentCount']} components, {len(expected)} symbols)")
finally:
    proc.stdin.close()
    proc.terminate()
    proc.wait(timeout=10)
