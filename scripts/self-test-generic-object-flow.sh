#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

cd "$ROOT"
mvn package -q

python3 - <<'PY'
import json
import os
import subprocess

root = "/home/dominik/spoon-mcp-server"
projects = [f"{root}/src/test/resources/testprojects/generic-object-flow"]
worksample = "/home/dominik/Worksample_Stein_Schere_Papier/worksample"
if os.path.isdir(worksample):
    projects.append(worksample)

def run_project(project):
    proc = subprocess.Popen(
        ["java", "-jar", f"{root}/target/spoon-mcp-server.jar"],
        stdin=subprocess.PIPE,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
        bufsize=1,
    )
    next_id = 1

    def call(method, params=None):
        nonlocal next_id
        req = {"jsonrpc": "2.0", "id": next_id, "method": method}
        next_id += 1
        if params is not None:
            req["params"] = params
        proc.stdin.write(json.dumps(req) + "\n")
        proc.stdin.flush()
        line = proc.stdout.readline()
        if not line:
            raise RuntimeError(proc.stderr.read())
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
            "clientInfo": {"name": "generic-object-flow-self-test", "version": "1"}
        })
        proc.stdin.write(json.dumps({"jsonrpc": "2.0", "method": "notifications/initialized", "params": {}}) + "\n")
        proc.stdin.flush()
        tool("index_workspace", {"paths": [project]})
        runtime = tool("get_runtime_flow", {"entrypointName": "main", "maxDepth": 12})
        call_flow = tool("render_call_flow", {"entrypointName": "main", "maxDepth": 12})
        graph = tool("query_architecture_graph", {
            "action": "find_edges",
            "label": "CALLS",
            "limit": 100
        })
        return runtime + "\n" + call_flow + "\n" + graph
    finally:
        proc.terminate()

for project in projects:
    output = run_project(project)
    if "Main" not in output:
        raise AssertionError(f"{project}: expected Main in runtime output")
    if "Game" not in output and "GameService" not in output:
        raise AssertionError(f"{project}: expected game component in runtime output")
    if "receiverEvidence" not in output:
        raise AssertionError(f"{project}: expected receiverEvidence in graph output")

print("SELF TEST PASS")
PY
