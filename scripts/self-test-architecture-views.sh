#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

cd "$ROOT"
mvn package -q

python3 - <<'PY'
import json
import subprocess

root = "/home/dominik/archlens"
jar = f"{root}/target/archlens.jar"
proc = subprocess.Popen(
    ["java", "-jar", jar],
    stdin=subprocess.PIPE,
    stdout=subprocess.PIPE,
    stderr=subprocess.PIPE,
    text=True,
    bufsize=1,
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
    return response["result"]

call("initialize", {
    "protocolVersion": "2024-11-05",
    "capabilities": {},
    "clientInfo": {"name": "self-test", "version": "1"}
})

# notifications/initialized is a one-way message — no id, no response
proc.stdin.write(json.dumps({"jsonrpc": "2.0", "method": "notifications/initialized", "params": {}}) + "\n")
proc.stdin.flush()

def tool(name, arguments):
    result = call("tools/call", {"name": name, "arguments": arguments})
    return result["content"][0]["text"]

tool("index_workspace", {"paths": [root]})

diagram = tool("render_architecture_view", {
    "app": "archlens",
    "view": "component",
    "maxNodes": 50
})

likec4 = tool("export_likec4_model", {
    "app": "archlens",
    "view": "component",
    "maxNodes": 18
})

for expected in ["McpServer", "ArchitectureExtractor", "ModelCache"]:
    if expected not in diagram:
        raise AssertionError(f"Missing {expected} from architecture view")

if "specification" not in likec4 or "views" not in likec4:
    raise AssertionError("LikeC4 export missing expected sections")

print("SELF TEST PASS")
proc.terminate()
PY
