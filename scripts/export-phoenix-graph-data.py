#!/usr/bin/env python3
import json
import subprocess
import sys
from pathlib import Path

ROOT = Path("/home/dominik/git/archlens")
PHOENIX = Path("/home/dominik/git/g-net-device-state-bizzlogic")
JAR = ROOT / "target" / "archlens.jar"
OUT = ROOT / "viewer" / "public" / "phoenix-graph.json"

proc = subprocess.Popen(
    ["java", "-jar", str(JAR)],
    stdin=subprocess.PIPE,
    stdout=subprocess.PIPE,
    stderr=sys.stderr,
    text=True,
    bufsize=1,
    cwd=ROOT,
)
next_id = 1


def call(method, params=None):
    global next_id
    request = {"jsonrpc": "2.0", "id": next_id, "method": method}
    next_id += 1
    if params is not None:
        request["params"] = params
    proc.stdin.write(json.dumps(request) + "\n")
    proc.stdin.flush()
    line = proc.stdout.readline()
    if not line:
        raise RuntimeError("MCP server closed stdout")
    response = json.loads(line)
    if "error" in response:
        raise RuntimeError(response["error"])
    return response.get("result")


def notify(method, params=None):
    request = {"jsonrpc": "2.0", "method": method}
    if params is not None:
        request["params"] = params
    proc.stdin.write(json.dumps(request) + "\n")
    proc.stdin.flush()


def tool(name, args):
    result = call("tools/call", {"name": name, "arguments": args})
    return result["content"][0]["text"] if result.get("content") else ""


try:
    OUT.parent.mkdir(parents=True, exist_ok=True)
    call(
        "initialize",
        {
            "protocolVersion": "2024-11-05",
            "capabilities": {},
            "clientInfo": {"name": "phoenix-svelte-graph-data", "version": "1"},
        },
    )
    notify("notifications/initialized", {})
    print("=== Indexing phoenix_backend ===", flush=True)
    print(tool("index_workspace", {"paths": [str(PHOENIX)]}), flush=True)
    print("\n=== Exporting graph data ===", flush=True)
    print(tool("export_graph_data", {"outputPath": str(OUT), "limit": 5000}), flush=True)
finally:
    try:
        proc.stdin.close()
    except Exception:
        pass
    proc.terminate()
    try:
        proc.wait(timeout=5)
    except subprocess.TimeoutExpired:
        proc.kill()
