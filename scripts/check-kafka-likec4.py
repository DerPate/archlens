#!/usr/bin/env python3
import json, subprocess, sys

ROOT = "/home/dominik/archlens"
JAR = f"{ROOT}/target/archlens.jar"

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

for fixture in ["spring-pipeline-sample", "store-handoff-sample"]:
    path = f"{ROOT}/src/test/resources/testprojects/{fixture}"
    print(f"\n{'='*60}")
    print(f"=== {fixture} ===")
    print('='*60)

    idx = tool("index_workspace", {"paths": [path]})
    print(idx.strip())

    likec4 = tool("export_likec4_model", {"view": "workspace", "maxNodes": 20})

    # Relationships between components (not just STARTS_AT to entrypoints)
    model_section = likec4[likec4.find("model {"):likec4.find("views {")]
    rels = [l.strip() for l in model_section.split("\n") if " -> " in l]
    print(f"\nAll relationships ({len(rels)}):")
    for r in rels:
        print(f"  {r[:100]}")

    # Warnings
    for line in likec4.split("\n"):
        if "Warning" in line:
            print(f"  WARNING: {line.strip()}")

proc.terminate()
