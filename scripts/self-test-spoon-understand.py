#!/usr/bin/env python3
"""Self-test for the spoon-understand skill.

Walks every workflow documented in skills/spoon-understand/SKILL.md and
references/mcp-tool-map.md against a real fixture project, using the exact
tool names and argument names the skill tells an agent to use. Reports
PASS/FAIL per step instead of asserting exact content, since fixture data
can change.

Usage: python3 scripts/self-test-spoon-understand.py [workspace_path]
"""

import json
import subprocess
import sys

ROOT = "/home/dominik/archlens"
WORKSPACE = sys.argv[1] if len(sys.argv) > 1 else "/home/dominik/phoenix_backend"
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
results = []  # (label, passed, detail)


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
    return call("tools/call", {"name": name, "arguments": arguments})


def tool_text(result):
    content = result.get("content", [])
    return content[0].get("text", "") if content else ""


def check(label, fn):
    try:
        result = fn()
        text = tool_text(result)
        ok = bool(text) and not result.get("isError", False)
        results.append((label, ok, text[:90].replace("\n", " ")))
        return text
    except Exception as exc:  # noqa: BLE001
        results.append((label, False, f"EXCEPTION: {exc}"))
        return ""


call(
    "initialize",
    {
        "protocolVersion": "2024-11-05",
        "capabilities": {},
        "clientInfo": {"name": "spoon-understand-self-test", "version": "1"},
    },
)
notify("notifications/initialized")

# --- Quick Architecture Tour (SKILL.md) ---
check("index_workspace", lambda: tool("index_workspace", {"paths": [WORKSPACE]}))
apps_text = check("list_apps", lambda: tool("list_apps", {}))
check("find_entrypoints (no filter)", lambda: tool("find_entrypoints", {}))
check("find_components (no filter)", lambda: tool("find_components", {}))
check("query_architecture_graph summary", lambda: tool("query_architecture_graph", {"action": "summary"}))
check("render_architecture_view", lambda: tool("render_architecture_view", {}))
check("render_mermaid_flowchart level=system", lambda: tool("render_mermaid_flowchart", {"level": "system"}))

# --- Entrypoint / Use Case deep dive ---
eps = check("find_entrypoints type=REST_ENDPOINT", lambda: tool("find_entrypoints", {"type": "REST_ENDPOINT"}))
# pull a real entrypoint name out of the find_entrypoints output to drive the rest of the deep dive
sample_ep = None
for line in eps.splitlines():
    line = line.strip()
    if line.startswith("- [") and "]" in line:
        sample_ep = line.split("]", 1)[1].strip().split(" [")[0]
        break
if sample_ep:
    check(f"call_flow entrypointName={sample_ep!r}", lambda: tool("call_flow", {"entrypointName": sample_ep}))
    check(f"trace_data_flow entrypointName={sample_ep!r}", lambda: tool("trace_data_flow", {"entrypointName": sample_ep}))
check("render_use_case_timeline", lambda: tool("render_use_case_timeline", {"maxUseCases": 3}))

# --- Component Investigation deep dive ---
repos = check("find_components type=REPOSITORY", lambda: tool("find_components", {"type": "REPOSITORY"}))
sample_comp = None
for line in repos.splitlines():
    line = line.strip()
    if line.startswith("- [") and "]" in line:
        sample_comp = line.split("]", 1)[1].strip().split(" (")[0]
        break
if sample_comp:
    check(f"get_component_dependencies name={sample_comp!r}", lambda: tool("get_component_dependencies", {"name": sample_comp}))
    check(f"render_component_dependency_diagram name={sample_comp!r}",
          lambda: tool("render_component_dependency_diagram", {"name": sample_comp}))
    # neighborhood/impacted_by need a nodeId; reuse a qualified-name guess isn't reliable,
    # so resolve via find_nodes first.
    found = check(
        f"query_architecture_graph find_nodes Component name~{sample_comp!r}",
        lambda: tool("query_architecture_graph", {"action": "find_nodes", "label": "Component", "query": sample_comp}),
    )
    node_id = None
    for line in found.splitlines():
        line = line.strip()
        if line.startswith("- ") and " [" in line:
            node_id = line[2:].split(" [", 1)[0].strip()
            break
    if node_id:
        check(f"query_architecture_graph neighborhood nodeId={node_id!r}",
              lambda: tool("query_architecture_graph", {"action": "neighborhood", "nodeId": node_id}))
        check(f"query_architecture_graph impacted_by nodeId={node_id!r}",
              lambda: tool("query_architecture_graph", {"action": "impacted_by", "nodeId": node_id, "maxDepth": 4}))

# --- Pipeline and Workflow Handoffs deep dive ---
check("trace_data_flow sinkKind=store", lambda: tool("trace_data_flow", {"sinkKind": "store"}))
check("render_pipeline (no filter)", lambda: tool("render_pipeline", {}))
check("query_architecture_graph find_edges WORKFLOW_LINK",
      lambda: tool("query_architecture_graph", {"action": "find_edges", "label": "WORKFLOW_LINK"}))

# --- Graph Search and Impact (verbatim examples from SKILL.md) ---
check(
    "find_nodes Component workflowRelevant=true noiseScore=<4",
    lambda: tool("query_architecture_graph",
                 {"action": "find_nodes", "label": "Component",
                  "filters": {"workflowRelevant": "true", "noiseScore": "<4"}}),
)
check(
    "find_nodes Component componentType=REPOSITORY",
    lambda: tool("query_architecture_graph",
                 {"action": "find_nodes", "label": "Component", "filters": {"componentType": "REPOSITORY"}}),
)
check(
    "find_nodes Entrypoint entrypointType=MESSAGING_CONSUMER",
    lambda: tool("query_architecture_graph",
                 {"action": "find_nodes", "label": "Entrypoint", "filters": {"entrypointType": "MESSAGING_CONSUMER"}}),
)

proc.terminate()

# --- Report ---
print(f"Self-test of spoon-understand against {WORKSPACE}\n")
passed = 0
for label, ok, detail in results:
    status = "PASS" if ok else "FAIL"
    if ok:
        passed += 1
    print(f"[{status}] {label}")
    if not ok:
        print(f"       {detail}")

print(f"\n{passed}/{len(results)} steps passed.")
sys.exit(0 if passed == len(results) else 1)
