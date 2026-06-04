#!/usr/bin/env python3
"""
Verify the Lombok-blindness fix against phoenix_backend.
Checks that common Java API method names (get, stream, findFirst, etc.)
no longer produce spurious ACCESSOR_RETURN edges to unrelated components.
"""
import json, subprocess, sys

JAR = "target/spoon-mcp-server.jar"
PROJECT_ROOT = "."

proc = subprocess.Popen(
    ["java", "-jar", JAR],
    stdin=subprocess.PIPE,
    stdout=subprocess.PIPE,
    stderr=sys.stderr,
    cwd=PROJECT_ROOT,
)

_id = [0]

def call(method, params=None):
    _id[0] += 1
    msg = {"jsonrpc": "2.0", "id": _id[0], "method": method}
    if params:
        msg["params"] = params
    proc.stdin.write((json.dumps(msg) + "\n").encode())
    proc.stdin.flush()
    return json.loads(proc.stdout.readline()).get("result")

def notify(method, params=None):
    msg = {"jsonrpc": "2.0", "method": method}
    if params:
        msg["params"] = params
    proc.stdin.write((json.dumps(msg) + "\n").encode())
    proc.stdin.flush()

def tool(name, args):
    result = call("tools/call", {"name": name, "arguments": args})
    return result["content"][0]["text"]

# Handshake
call("initialize", {
    "protocolVersion": "2024-11-05",
    "capabilities": {},
    "clientInfo": {"name": "check-lombok-fix", "version": "1"},
})
notify("notifications/initialized", {})

# Index phoenix_backend
print("=== Indexing phoenix_backend ===")
print(tool("index_workspace", {"paths": ["/home/dominik/phoenix_backend"]}))

# Get all apps
apps_raw = tool("list_apps", {})
print("\n=== Apps ===")
print(apps_raw)
apps = json.loads(apps_raw)
phoenix_app_id = next((a["appId"] for a in apps if "phoenix" in a["appId"].lower()), None)
if not phoenix_app_id:
    print("ERROR: no phoenix app found")
    proc.stdin.close(); proc.wait(); sys.exit(1)
print(f"\nUsing appId: {phoenix_app_id}")

# Query the call graph for edges that look like Lombok false positives:
# edges where receiver evidence is ACCESSOR_RETURN going FROM a service TO AbsenceController or similar
# Use query_architecture_graph to inspect call edges
print("\n=== Querying call edges involving AccountService ===")
edges_raw = tool("query_architecture_graph", {
    "appId": phoenix_app_id,
    "query": "MATCH (from)-[e:CALLS]->(to) WHERE from.name CONTAINS 'AccountService' RETURN from, e, to LIMIT 30"
})
print(edges_raw)

print("\n=== Checking for suspicious edges FROM AccountService TO AbsenceController ===")
# Look for edges that would be a Lombok false positive
bad_edge_query = tool("query_architecture_graph", {
    "appId": phoenix_app_id,
    "query": "MATCH (from)-[e:CALLS]->(to) WHERE from.name CONTAINS 'AccountService' AND to.name CONTAINS 'AbsenceController' RETURN from, e, to"
})
print(bad_edge_query)

print("\n=== Checking runtime flow for AccountService (one endpoint) ===")
# Find account endpoints
account_eps = tool("find_entrypoints", {
    "appId": phoenix_app_id,
    "path": "/account"
})
print(account_eps[:2000])

proc.stdin.close()
proc.wait()
print("\nDone.")
