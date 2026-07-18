#!/usr/bin/env python3
"""Index all phoenix Java projects together and check whether archlens traces
workflows ACROSS project boundaries (cross-app WORKFLOW_LINKs, shared channels)."""
import json
import subprocess
import sys
import tempfile

ROOT = "/home/dominik/archlens"
JAR = f"{ROOT}/target/archlens.jar"
OUT = sys.argv[1] if len(sys.argv) > 1 else None  # optional result-file path (default: stdout)
LOG = open(sys.argv[2], "w", buffering=1) if len(sys.argv) > 2 else sys.stderr
PROJECTS = [
    "/home/dominik/phoenix_backend",
    "/home/dominik/HomeInsteadPhoenixBackgroundService",
    "/home/dominik/HomeInsteadPhoenixIDP",
    "/home/dominik/HomeInsteadPhoenixTemplateWorker",
    "/home/dominik/idp-conector",
]

cache_root = tempfile.TemporaryDirectory(prefix="archlens-cross-project-")
proc = subprocess.Popen(
    ["java", "-jar", JAR],
    stdin=subprocess.PIPE,
    stdout=subprocess.PIPE,
    stderr=LOG,
    text=True,
    bufsize=1,
    cwd=cache_root.name,
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


def notify(method, params=None):
    request = {"jsonrpc": "2.0", "method": method}
    if params is not None:
        request["params"] = params
    proc.stdin.write(json.dumps(request) + "\n")
    proc.stdin.flush()


def tool(name, args):
    result = call("tools/call", {"name": name, "arguments": args})
    if result.get("isError", False):
        text = result.get("content", [{}])[0].get("text", "")
        raise RuntimeError(f"{name}: {text}")
    return result


def structured(name, args):
    return tool(name, args).get("structuredContent")


def text(name, args):
    return tool(name, args).get("content", [{}])[0].get("text", "")


def find_nodes(label, limit=5000, filters=None):
    args = {"action": "find_nodes", "label": label, "limit": limit}
    if filters:
        args["filters"] = filters
    return structured("query_architecture_graph", args).get("nodes", [])


def find_edges(label, limit=5000, filters=None):
    args = {"action": "find_edges", "label": label, "limit": limit}
    if filters:
        args["filters"] = filters
    return structured("query_architecture_graph", args).get("edges", [])


try:
    call("initialize", {
        "protocolVersion": "2025-11-25",
        "capabilities": {},
        "clientInfo": {"name": "cross-project-probe", "version": "1"},
    })
    notify("notifications/initialized", {})

    print(f"=== Indexing {len(PROJECTS)} projects ===", file=LOG, flush=True)
    index = structured("index_workspace", {"paths": PROJECTS})
    print("=== Index done ===", file=LOG, flush=True)

    apps = find_nodes("Application")
    components = find_nodes("Component", limit=20000)
    entrypoints = find_nodes("Entrypoint", limit=5000)
    sinks = find_nodes("DataFlowSink", limit=20000)
    links = find_edges("WORKFLOW_LINK", limit=20000)

    # componentId -> appId (component vertices carry their owning module)
    component_app = {}
    for c in components:
        module = c.get("properties", {}).get("module")
        if module:
            component_app[c["id"]] = module

    # entrypointId -> appId via its componentId
    entrypoint_app = {}
    entrypoint_info = {}
    for ep in entrypoints:
        props = ep.get("properties", {})
        comp = props.get("componentId")
        entrypoint_app[ep["id"]] = component_app.get(comp)
        entrypoint_info[ep["id"]] = {
            "name": ep.get("name"),
            "type": props.get("type"),
            "channel": props.get("channelName") or props.get("topic"),
            "broker": props.get("broker"),
            "app": component_app.get(comp),
        }

    def app_of_entrypoint(ep_id):
        if ep_id in entrypoint_app:
            return entrypoint_app[ep_id]
        # pathId form "entrypointId#param" — strip tracked-param suffix
        base, _, _ = ep_id.rpartition("#")
        return entrypoint_app.get(base)

    cross_links, intra_links, unresolved_links = [], [], []
    for link in links:
        props = link.get("properties", {})
        from_ep = props.get("fromEntrypointId", "")
        to_ep = props.get("toEntrypointId", "")
        from_app = app_of_entrypoint(from_ep)
        to_app = app_of_entrypoint(to_ep)
        record = {
            "kind": props.get("kind"),
            "channel": props.get("channel"),
            "confidence": props.get("confidence"),
            "evidence": props.get("evidence"),
            "from": {"entrypoint": from_ep, "app": from_app},
            "to": {"entrypoint": to_ep, "app": to_app},
        }
        if from_app is None or to_app is None:
            unresolved_links.append(record)
        elif from_app != to_app:
            cross_links.append(record)
        else:
            intra_links.append(record)

    # channel-level view: which app produces / consumes each messaging channel
    channels = {}
    for sink in sinks:
        props = sink.get("properties", {})
        if props.get("sinkKind", props.get("kind", "")).lower() not in ("messaging", "datasink", "dataflowsink") \
                and not props.get("channel") and not props.get("topic"):
            continue
        channel = props.get("channel") or props.get("topic")
        if not channel:
            continue
        app = component_app.get(props.get("componentId"))
        channels.setdefault(channel, {"producers": set(), "consumers": set()})
        channels[channel]["producers"].add(app or "<unknown>")
    for ep_id, info in entrypoint_info.items():
        if info["channel"]:
            channels.setdefault(info["channel"], {"producers": set(), "consumers": set()})
            channels[info["channel"]]["consumers"].add(info["app"] or "<unknown>")

    cross_app_channels = {
        ch: {"producers": sorted(v["producers"]), "consumers": sorted(v["consumers"])}
        for ch, v in sorted(channels.items())
        if v["producers"] and v["consumers"]
        and (v["producers"] | v["consumers"]) - {"<unknown>"}
        and len({a for a in (v["producers"] | v["consumers"]) if a != "<unknown>"}) > 1
    }

    apps_summary = []
    for app in apps:
        aid = app["id"]
        apps_summary.append({
            "appId": aid,
            "name": app.get("name"),
            "technology": app.get("properties", {}).get("technology"),
            "rootPath": app.get("properties", {}).get("rootPath"),
            "components": sum(1 for a in component_app.values() if a == aid),
            "entrypoints": sum(1 for a in entrypoint_app.values() if a == aid),
        })

    # dedupe cross links for readability
    seen = {}
    for link in cross_links:
        key = (link["from"]["entrypoint"], link["to"]["entrypoint"], link["channel"])
        seen.setdefault(key, link)

    report = json.dumps({
        "index": index,
        "apps": apps_summary,
        "counts": {
            "components": len(components),
            "entrypoints": len(entrypoints),
            "dataFlowSinks": len(sinks),
            "workflowLinks": len(links),
            "crossAppLinks": len(cross_links),
            "crossAppLinksUnique": len(seen),
            "intraAppLinks": len(intra_links),
            "unresolvedAppLinks": len(unresolved_links),
        },
        "crossAppLinks": sorted(seen.values(), key=lambda l: (l["channel"] or "", l["from"]["entrypoint"])),
        "crossAppChannels": cross_app_channels,
        "unresolvedSample": unresolved_links[:10],
    }, indent=2)
    if OUT:
        with open(OUT, "w") as fh:
            fh.write(report)
        print("=== Report written ===", file=LOG, flush=True)
    else:
        print(report)
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
    cache_root.cleanup()
