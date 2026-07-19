#!/usr/bin/env python3
"""Run the M5 endpoint_context contract against the real phoenix_backend workspace."""

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
REPORT = ROOT / "target" / "phoenix-m5-report.json"
LOG = ROOT / "target" / "phoenix-m5.stderr.log"


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


def answer(client: Client, **arguments: Any) -> dict[str, Any]:
    result = client.tool("answer_architecture_question", arguments)
    required = {"family", "status", "interpretation", "queryPlan", "subject", "answer", "unresolved", "ambiguous"}
    missing = required - result.keys()
    if missing:
        raise AssertionError(f"answer_architecture_question lacks contract fields: {sorted(missing)}")
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
                "clientInfo": {"name": "phoenix-m5-test", "version": "1"},
            },
        )
        client.notify("notifications/initialized", {})
        started = time.perf_counter()
        report["index"] = client.tool("index_workspace", {"paths": [str(PHOENIX)]})
        report["indexDurationMs"] = round((time.perf_counter() - started) * 1000, 2)
        report["graph"] = client.tool("query_architecture_graph", {"action": "summary"})

        rest_entrypoints = nodes(client, "Entrypoint", {"entrypointType": "REST_ENDPOINT"})
        if not rest_entrypoints:
            raise AssertionError("No REST_ENDPOINT entrypoint found in phoenix_backend")
        target = rest_entrypoints[0]
        forward_typed = answer(client, family="endpoint_context", entrypoint=target["id"])
        report["answers"]["endpoint_context_forward_typed"] = forward_typed

        method = target.get("properties", {}).get("httpMethod")
        path = target.get("properties", {}).get("path")
        if method and path:
            forward_nl = answer(client, question=f"What happens on {method} {path}?")
            report["answers"]["endpoint_context_forward_natural_language"] = forward_nl
            if forward_nl["answer"].get("owningComponent") != forward_typed["answer"].get("owningComponent"):
                raise AssertionError("typed and natural-language endpoint_context disagree on owningComponent")

        repositories = nodes(client, "Component", {"componentType": "REPOSITORY"})
        if repositories:
            reverse = answer(client, family="endpoint_context", component=repositories[0]["id"], maxDepth=4)
            report["answers"]["endpoint_context_reverse"] = reverse

        unsupported = answer(client, question="What color is the sky?")
        if unsupported["status"] != "unsupported":
            raise AssertionError("expected 'unsupported' status for unrecognized wording")
        report["answers"]["unsupported"] = unsupported

        report["passed"] = True
    finally:
        client.close()
    REPORT.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(f"PASS phoenix_backend M5 acceptance: {REPORT}")
    print(
        f"Graph: {report['graph']['nodeCount']} nodes, {report['graph']['edgeCount']} edges; "
        f"index {report['indexDurationMs']} ms"
    )
    for name, result in report["answers"].items():
        print(f"  {name}: {result['status']}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
