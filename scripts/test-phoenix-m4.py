#!/usr/bin/env python3
"""Run the M4 question contracts against the real phoenix_backend workspace."""

from __future__ import annotations

import json
from pathlib import Path
import subprocess
import sys
import time
from typing import Any


ROOT = Path(__file__).resolve().parent.parent
PHOENIX = Path("/home/dominik/phoenix_backend")
JAR = ROOT / "target" / "archlens.jar"
REPORT = ROOT / "target" / "phoenix-m4-report.json"
LOG = ROOT / "target" / "phoenix-m4.stderr.log"


class Client:
    def __init__(self) -> None:
        LOG.parent.mkdir(parents=True, exist_ok=True)
        self.stderr = LOG.open("wb")
        self.proc = subprocess.Popen(
            ["java", "-jar", str(JAR)],
            stdin=subprocess.PIPE,
            stdout=subprocess.PIPE,
            stderr=self.stderr,
            cwd=ROOT,
        )
        self.request_id = 0

    def call(self, method: str, params: dict[str, Any] | None = None) -> dict[str, Any]:
        self.request_id += 1
        message: dict[str, Any] = {"jsonrpc": "2.0", "id": self.request_id, "method": method}
        if params is not None:
            message["params"] = params
        assert self.proc.stdin is not None and self.proc.stdout is not None
        self.proc.stdin.write((json.dumps(message) + "\n").encode())
        self.proc.stdin.flush()
        line = self.proc.stdout.readline()
        if not line:
            raise RuntimeError("ArchLens closed stdout")
        response = json.loads(line)
        if "error" in response:
            raise RuntimeError(str(response["error"]))
        return response.get("result", {})

    def notify(self, method: str, params: dict[str, Any] | None = None) -> None:
        message: dict[str, Any] = {"jsonrpc": "2.0", "method": method}
        if params is not None:
            message["params"] = params
        assert self.proc.stdin is not None
        self.proc.stdin.write((json.dumps(message) + "\n").encode())
        self.proc.stdin.flush()

    def tool(self, name: str, arguments: dict[str, Any]) -> dict[str, Any]:
        result = self.call("tools/call", {"name": name, "arguments": arguments})
        if result.get("isError"):
            message = result.get("content", [{}])[0].get("text", "unknown tool error")
            raise RuntimeError(f"{name}: {message}")
        return result.get("structuredContent", {})

    def close(self) -> None:
        if self.proc.stdin is not None:
            self.proc.stdin.close()
        self.proc.wait(timeout=30)
        self.stderr.close()


def nodes(client: Client, label: str, filters: dict[str, str] | None = None) -> list[dict[str, Any]]:
    result = client.tool(
        "query_architecture_graph",
        {"action": "find_nodes", "label": label, "filters": filters or {}},
    )
    return result.get("nodes", [])


def answer(client: Client, family: str, **selectors: Any) -> dict[str, Any]:
    result = client.tool("answer_architecture_question", {"family": family, **selectors})
    required = {"family", "status", "subject", "answer", "unresolved", "ambiguous"}
    missing = required - result.keys()
    if missing:
        raise AssertionError(f"{family} lacks contract fields: {sorted(missing)}")
    if result["status"] not in {"resolved", "partial", "ambiguous"}:
        raise AssertionError(f"{family} returned invalid status {result['status']}")
    return result


def main() -> int:
    if not PHOENIX.is_dir() or not JAR.is_file():
        raise FileNotFoundError("phoenix_backend or packaged ArchLens jar is missing")
    client = Client()
    report: dict[str, Any] = {"workspace": str(PHOENIX), "answers": {}}
    try:
        client.call(
            "initialize",
            {
                "protocolVersion": "2025-11-25",
                "capabilities": {},
                "clientInfo": {"name": "phoenix-m4-test", "version": "1"},
            },
        )
        client.notify("notifications/initialized", {})
        started = time.perf_counter()
        report["index"] = client.tool("index_workspace", {"paths": [str(PHOENIX)]})
        report["indexDurationMs"] = round((time.perf_counter() - started) * 1000, 2)
        report["graph"] = client.tool("query_architecture_graph", {"action": "summary"})
        if report["graph"].get("nodeCount", 0) < 100:
            raise AssertionError("phoenix_backend graph unexpectedly contains fewer than 100 nodes")

        repositories = nodes(client, "Component", {"componentType": "REPOSITORY"})
        if not repositories:
            raise AssertionError("No Phoenix repository component found")
        report["answers"]["impact"] = answer(client, "impact", component=repositories[0]["id"], maxDepth=4)

        entrypoints = nodes(client, "Entrypoint")
        consumers = [
            item
            for item in entrypoints
            if "CONSUMER" in str(item.get("properties", {}).get("entrypointType", ""))
        ]
        if consumers:
            report["answers"]["consumer_context"] = answer(
                client, "consumer_context", entrypoint=consumers[0]["id"]
            )
        else:
            report["answers"]["consumer_context"] = {
                "status": "not-applicable",
                "unresolved": ["no-consumer-entrypoint-detected"],
                "ambiguous": [],
            }

        persistence_sinks = nodes(client, "DataFlowSink", {"sinkKind": "persistence"})
        persistence_entrypoint: str | None = None
        if persistence_sinks:
            path_id = persistence_sinks[0].get("properties", {}).get("pathId")
            path_matches = client.tool(
                "query_architecture_graph",
                {"action": "find_nodes", "label": "DataFlowPath", "query": path_id},
            ).get("nodes", [])
            if path_matches:
                persistence_entrypoint = path_matches[0].get("properties", {}).get("entrypointId")
        if persistence_entrypoint:
            report["answers"]["persistence_destination"] = answer(
                client, "persistence_destination", entrypoint=persistence_entrypoint
            )
            report["answers"]["transaction_context"] = answer(
                client, "transaction_context", entrypoint=persistence_entrypoint
            )
        else:
            report["answers"]["persistence_destination"] = answer(
                client, "persistence_destination", query="save"
            )
            report["answers"]["transaction_context"] = answer(
                client, "transaction_context", component=repositories[0]["id"]
            )

        report["passed"] = True
    finally:
        client.close()
    REPORT.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(f"PASS phoenix_backend M4 acceptance: {REPORT}")
    print(
        f"Graph: {report['graph']['nodeCount']} nodes, {report['graph']['edgeCount']} edges; "
        f"index {report['indexDurationMs']} ms"
    )
    for family, result in report["answers"].items():
        print(
            f"  {family}: {result['status']} "
            f"({len(result.get('unresolved', []))} unresolved, {len(result.get('ambiguous', []))} ambiguous)"
        )
    return 0


if __name__ == "__main__":
    sys.exit(main())
