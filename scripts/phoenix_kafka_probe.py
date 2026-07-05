#!/usr/bin/env python3
import json
import subprocess
import sys
import tempfile

ROOT = "/home/dominik/archlens"
JAR = f"{ROOT}/target/archlens.jar"
PHOENIX = "/home/dominik/phoenix_backend"

cache_root = tempfile.TemporaryDirectory(prefix="archlens-kafka-probe-")
proc = subprocess.Popen(
    ["java", "-jar", JAR],
    stdin=subprocess.PIPE,
    stdout=subprocess.PIPE,
    stderr=sys.stderr,
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


try:
    call("initialize", {
        "protocolVersion": "2025-11-25",
        "capabilities": {},
        "clientInfo": {"name": "phoenix-kafka-probe", "version": "1"},
    })
    notify("notifications/initialized", {})

    index = structured("index_workspace", {"paths": [PHOENIX]})
    entrypoints = structured("query_architecture_graph", {
        "action": "find_nodes",
        "label": "Entrypoint",
        "filters": {"broker": "KAFKA"},
        "limit": 1000,
    }).get("nodes", [])
    sinks = structured("query_architecture_graph", {
        "action": "find_nodes",
        "label": "DataFlowSink",
        "filters": {"broker": "KAFKA"},
        "limit": 1000,
    }).get("nodes", [])
    links = structured("query_architecture_graph", {
        "action": "find_edges",
        "label": "WORKFLOW_LINK",
        "filters": {"kind": "MESSAGING"},
        "limit": 1000,
    }).get("edges", [])
    flow_paths = structured("trace_data_flow", {"sinkKind": "messaging"}).get("paths", [])

    kafka_paths = []
    for path in flow_paths:
        kafka_sinks = [sink for sink in path.get("sinks", []) if sink.get("broker") == "KAFKA"]
        if kafka_sinks:
            kafka_paths.append({
                "id": path.get("id"),
                "entrypointId": path.get("entrypointId"),
                "entrypoint": path.get("entrypoint"),
                "trackedParam": path.get("trackedParam"),
                "sinks": kafka_sinks,
            })

    channels = set()
    for node in entrypoints + sinks:
        props = node.get("properties", {})
        for key in ("channelName", "channel", "topic"):
            if props.get(key):
                channels.add(props[key])
    for edge in links:
        props = edge.get("properties", {})
        for key in ("channel", "topic", "destination"):
            if props.get(key):
                channels.add(props[key])

    pipelines = {}
    for channel in sorted(channels):
        pipelines[channel] = text("render_pipeline", {
            "channel": channel,
            "maxDepth": 12,
            "maxChains": 100,
        })

    routes = {}
    for sink in sinks:
        props = sink.get("properties", {})
        path_id = props.get("pathId", "")
        entrypoint_id, _, tracked_param = path_id.rpartition("#")
        channel = props.get("channel") or props.get("topic") or "<unknown>"
        key = (entrypoint_id, tracked_param, channel)
        route = routes.setdefault(key, {
            "entrypointId": entrypoint_id,
            "trackedParam": tracked_param,
            "channel": channel,
            "producer": props.get("componentId"),
            "payloadTypes": set(),
            "sinkRecords": 0,
        })
        if props.get("payloadType"):
            route["payloadTypes"].add(props["payloadType"])
        route["sinkRecords"] += 1

    route_list = []
    for route in routes.values():
        route["payloadTypes"] = sorted(route["payloadTypes"])
        route_list.append(route)
    route_list.sort(key=lambda item: (item["channel"], item["entrypointId"], item["trackedParam"]))

    rendered = {}
    for channel, pipeline in pipelines.items():
        headers = [line.removeprefix("%% ") for line in pipeline.splitlines() if line.startswith("%% chain")]
        rendered[channel] = {
            "linked": bool(headers),
            "chains": headers,
        }

    channel_routes = {}
    for route in route_list:
        group = channel_routes.setdefault(route["channel"], {
            "entrypoints": set(),
            "trackedRoutes": 0,
            "sinkRecords": 0,
            "producers": set(),
        })
        group["entrypoints"].add(route["entrypointId"])
        group["trackedRoutes"] += 1
        group["sinkRecords"] += route["sinkRecords"]
        group["producers"].add(route["producer"])
    for group in channel_routes.values():
        group["entrypoints"] = sorted(group["entrypoints"])
        group["producers"] = sorted(group["producers"])

    unique_links = {}
    for link in links:
        props = link.get("properties", {})
        key = (props.get("fromEntrypointId"), props.get("toEntrypointId"), props.get("channel"))
        unique_links[key] = {
            "fromEntrypointId": key[0],
            "toEntrypointId": key[1],
            "channel": key[2],
            "confidence": props.get("confidence"),
            "evidence": props.get("evidence"),
        }

    print(json.dumps({
        "index": index,
        "kafkaConsumers": entrypoints,
        "outboundByChannel": channel_routes,
        "uniqueWorkflowLinks": sorted(unique_links.values(), key=lambda item: item["fromEntrypointId"] or ""),
        "renderedPipelines": rendered,
    }, indent=2))
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
