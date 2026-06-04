#!/usr/bin/env python3
import json, subprocess, sys

ROOT = "/home/dominik/spoon-mcp-server"
PHOENIX = "/home/dominik/phoenix_backend"
JAR = f"{ROOT}/target/spoon-mcp-server.jar"

proc = subprocess.Popen(
    ["java", "-jar", JAR],
    stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=sys.stderr,
    text=True, bufsize=1,
)
next_id = 1

def call(method, params=None):
    global next_id
    req = {"jsonrpc": "2.0", "id": next_id, "method": method}
    next_id += 1
    if params: req["params"] = params
    proc.stdin.write(json.dumps(req) + "\n"); proc.stdin.flush()
    line = proc.stdout.readline()
    resp = json.loads(line)
    if "error" in resp: raise RuntimeError(resp["error"])
    return resp["result"]

def tool(name, args):
    r = call("tools/call", {"name": name, "arguments": args})
    return r["content"][0]["text"] if r["content"] else ""

call("initialize", {"protocolVersion": "2024-11-05", "capabilities": {}, "clientInfo": {"name": "test", "version": "1"}})
proc.stdin.write(json.dumps({"jsonrpc": "2.0", "method": "notifications/initialized", "params": {}}) + "\n"); proc.stdin.flush()

tool("index_workspace", {"paths": [PHOENIX]})

# Check outgoing edges from AddressMessageListener
print("=== AddressMessageListener outgoing edges ===")
result = tool("query_architecture_graph", {
    "action": "neighborhood",
    "nodeId": "de.homeinstead.phoenix.inbound.AddressMessageListener",
    "direction": "out",
    "limit": 20,
})
# Filter to only DEPENDS_ON edges
for line in result.split("\n"):
    if "DEPENDS_ON" in line or "injection" in line or "AddressService" in line or "AddressMessage" in line or "GeoLocation" in line:
        print(line)

# Check if AddressService is in the graph
print("\n=== AddressService node ===")
result2 = tool("query_architecture_graph", {
    "action": "find_nodes",
    "query": "AddressService",
    "label": "Component",
    "limit": 5,
})
for line in result2.split("\n"):
    if "AddressService" in line or "componentType" in line or "qualifiedName" in line:
        print(line)

# Check DEPENDS_ON edges that involve AddressService
print("\n=== DEPENDS_ON edges involving AddressService ===")
result3 = tool("query_architecture_graph", {
    "action": "find_edges",
    "label": "DEPENDS_ON",
    "limit": 100,
})
for line in result3.split("\n"):
    if "addressservice" in line.lower() or "addressmessagelistener" in line.lower():
        print(line)

proc.terminate()
