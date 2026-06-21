#!/usr/bin/env python3
"""Test conditional edge rendering against phoenix_backend pipeline."""

import json
import subprocess
import sys

ROOT = "/home/dominik/archlens"
PHOENIX = "/home/dominik/phoenix_backend"
JAR = f"{ROOT}/target/archlens.jar"

proc = subprocess.Popen(
    ["java", "-jar", JAR],
    stdin=subprocess.PIPE,
    stdout=subprocess.PIPE,
    stderr=sys.stderr,
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
        raise RuntimeError(f"MCP error: {response['error']}")
    return response["result"]


def notify(method, params=None):
    msg = {"jsonrpc": "2.0", "method": method}
    if params is not None:
        msg["params"] = params
    proc.stdin.write(json.dumps(msg) + "\n")
    proc.stdin.flush()


def tool(name, arguments):
    result = call("tools/call", {"name": name, "arguments": arguments})
    content = result["content"]
    return content[0]["text"] if content else ""


call("initialize", {
    "protocolVersion": "2024-11-05",
    "capabilities": {},
    "clientInfo": {"name": "cond-render-test", "version": "1"},
})
notify("notifications/initialized")

print("=== Indexing phoenix_backend ===", flush=True)
print(tool("index_workspace", {"paths": [PHOENIX]}), flush=True)

print("\n=== Finding entrypoints ===", flush=True)
# Single-app workspace here, so no appId filter needed. Note: FindEntrypointsTool's
# appId filter actually matches against the owning component's qualified name, not
# the application id reported by list_apps — passing "phoenix_backend" as appId
# silently matches zero entrypoints.
eps = tool("find_entrypoints", {})
print(eps[:2000], flush=True)

# PUT /employeeCandidature/{id} is an actual pipeline chain root (render_pipeline matches
# on entrypointName, not appId) and its chain calls into AccountService with conditional branches.
print("\n=== Tracing PUT /employeeCandidature (update path) ===", flush=True)
pipeline = tool("render_pipeline", {
    "entrypointName": "PUT /employeeCandidature",
})
print(pipeline, flush=True)

print("\n=== Checking for conditional (dashed) edges ===", flush=True)
if "-.->|" in pipeline:
    # Extract and show the dashed edge lines
    dashed_lines = [l.strip() for l in pipeline.split("\n") if "-.->|" in l]
    print(f"PASS: Found {len(dashed_lines)} dashed edge(s):")
    for l in dashed_lines:
        print(f"  {l}")
else:
    print("INFO: No dashed edges found.")
    print("      This means the account path has no CONDITIONAL topology edges recorded.")
    print("      Try a different entrypoint or re-check DataFlowTracer outputs.")

solid_lines = [l.strip() for l in pipeline.split("\n") if "-->|" in l and "-.->|" not in l]
print(f"\nSolid (unconditional) edges: {len(solid_lines)}")
for l in solid_lines[:8]:
    print(f"  {l}")

proc.terminate()
