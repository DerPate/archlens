#!/usr/bin/env python3
"""Test the archlens against the phoenix_backend project."""

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


def tool(name, arguments):
    result = call("tools/call", {"name": name, "arguments": arguments})
    content = result["content"]
    return content[0]["text"] if content else ""


call("initialize", {
    "protocolVersion": "2024-11-05",
    "capabilities": {},
    "clientInfo": {"name": "phoenix-test", "version": "1"},
})

proc.stdin.write(
    json.dumps({"jsonrpc": "2.0", "method": "notifications/initialized", "params": {}}) + "\n"
)
proc.stdin.flush()

print("=== Indexing phoenix_backend ===")
index_result = tool("index_workspace", {"paths": [PHOENIX]})
print(index_result)

# Test workspace export with enough nodes to see component relationships
print("\n=== LikeC4 workspace export (view=workspace, maxNodes=20) ===")
likec4 = tool("export_likec4_model", {
    "app": "phoenix_backend",
    "view": "workspace",
    "maxNodes": 20,
})

# All 6 entrypoints should have STARTS_AT relationships
model_start = likec4.find("model {")
views_start = likec4.find("views {")
model_section = likec4[model_start:views_start]

rel_lines = [line.strip() for line in model_section.split("\n") if " -> " in line and "'starts at'" in line]
assert len(rel_lines) == 6, \
    f"Expected 6 entrypoint→component relationships, got {len(rel_lines)}: {rel_lines}"
print(f"OK: {len(rel_lines)} entrypoint→component relationships found")

# Container view should show the entrypoint-owning controllers
container_start = likec4.find("view container")
container_end = likec4.find("}", container_start)
container_section = likec4[container_start:container_end + 1]
includes = [l.strip() for l in container_section.split("\n") if "include" in l and "app_phoenix_backend" not in l]

assert "include de_homeinstead_phoenix_controller_absencecontroller" in container_section, \
    "AbsenceController should be forced in because its entrypoint was selected"
assert "include de_homeinstead_phoenix_controller_accountcontroller" in container_section, \
    "AccountController should be forced in because its entrypoint was selected"
assert "include de_homeinstead_phoenix_controller_employeevacationrequestcontroller" in container_section, \
    "EmployeeVacationRequestController should be forced in"
print(f"OK: {len(includes)} primary components in container view (forced+ranked)")

# Metadata fix: componentId should serialize to qualified name, not toString
for line in likec4.split("\n"):
    if "componentid" in line.lower() and "ComponentId[" in line:
        raise AssertionError(f"componentId stored as toString: {line.strip()}")
print("OK: no ComponentId[...] toString in metadata")

for line in likec4.split("\n"):
    if "module" in line.lower() and "AppId[" in line:
        raise AssertionError(f"module stored as AppId toString: {line.strip()}")
print("OK: no AppId[...] toString in module metadata")

# Spec should declare all three element kinds
assert "element system" in likec4
assert "element entrypoint" in likec4
assert "element component" in likec4
print("OK: specification has all three element kinds")

# All three views present
assert "view context" in likec4
assert "view container" in likec4
assert "view component" in likec4
print("OK: context, container, component views present")

print("\n=== All assertions passed ===")
proc.terminate()
