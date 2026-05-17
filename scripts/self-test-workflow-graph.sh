#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

mvn package -q

python3 - <<'PY'
import json
import subprocess

root = "/home/dominik/spoon-mcp-server"
fixture = f"{root}/src/test/resources/testprojects/quarkus-sample"
proc = subprocess.Popen(
    ["java", "-jar", f"{root}/target/spoon-mcp-server.jar"],
    stdin=subprocess.PIPE,
    stdout=subprocess.PIPE,
    stderr=subprocess.PIPE,
    text=True,
    bufsize=1,
)

request_id = 1

def call(method, params=None):
    global request_id
    req = {"jsonrpc": "2.0", "id": request_id, "method": method}
    request_id += 1
    if params is not None:
        req["params"] = params
    proc.stdin.write(json.dumps(req) + "\n")
    proc.stdin.flush()
    line = proc.stdout.readline()
    if not line:
        raise RuntimeError("MCP server exited before response")
    res = json.loads(line)
    if "error" in res:
        raise RuntimeError(res["error"])
    return res["result"]

def tool(name, arguments):
    result = call("tools/call", {"name": name, "arguments": arguments})
    return result["content"][0]["text"]

try:
    call("initialize", {
        "protocolVersion": "2024-11-05",
        "capabilities": {},
        "clientInfo": {"name": "workflow-self-test", "version": "1"}
    })
    proc.stdin.write(json.dumps({"jsonrpc": "2.0", "method": "notifications/initialized", "params": {}}) + "\n")
    proc.stdin.flush()

    tool("index_workspace", {"paths": [fixture]})

    pipeline = tool("render_pipeline", {"maxChains": 8})
    workflow_links = tool("query_architecture_graph", {
        "action": "find_edges",
        "app": "quarkus-sample",
        "label": "WORKFLOW_LINK",
        "limit": 50
    })

    open("/tmp/spoon-workflow-pipeline.mmd", "w").write(str(pipeline))
    open("/tmp/spoon-workflow-links.txt", "w").write(str(workflow_links))

    bad_markers = ["onShutdown", "onShutDown", "onStop", "predestroy"]
    for marker in bad_markers:
        if marker in str(pipeline):
            raise AssertionError(f"Lifecycle marker leaked into pipeline: {marker}")

    if "WORKFLOW_LINK" not in str(workflow_links):
        raise AssertionError("No WORKFLOW_LINK edges found")

    print("SELF TEST PASS")
finally:
    proc.terminate()
PY
