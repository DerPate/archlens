#!/usr/bin/env python3
"""Compare two packaged renderers against identical workspaces over MCP.

Usage: python3 scripts/compare-diagram-renderers.py BASELINE_JAR CURRENT_JAR [WORKSPACE ...]
Raw output and unified diffs are written under target/renderer-comparison/.
Both jars must already be built. No renderer differences are silently accepted.
"""

import difflib
import json
import os
from pathlib import Path
import subprocess
import sys


ROOT = Path(__file__).resolve().parent.parent
DEFAULT_FIXTURES = (
    "quarkus-sample", "javaee-sample", "spring-pipeline-sample",
    "eventbus-sample", "store-handoff-sample", "war-modules-sample",
)


class Server:
    """Sequential MCP client with logs kept out of the protocol pipe."""

    def __init__(self, jar, directory, c4):
        directory.mkdir(parents=True, exist_ok=True)
        self.log = (directory / "server.log").open("w")
        self.proc = subprocess.Popen(
            ["java", "-jar", str(jar)], cwd=directory,
            stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=self.log,
            text=True, env={**os.environ, "ARCHLENS_MCP_EXPERIMENTAL_C4": str(c4).lower()},
        )
        self.next_id = 0
        self.call("initialize", {
            "protocolVersion": "2025-11-25", "capabilities": {},
            "clientInfo": {"name": "renderer-comparison", "version": "1"},
        })
        self.proc.stdin.write(json.dumps({
            "jsonrpc": "2.0", "method": "notifications/initialized", "params": {},
        }) + "\n")
        self.proc.stdin.flush()

    def call(self, method, params):
        self.next_id += 1
        self.proc.stdin.write(json.dumps({
            "jsonrpc": "2.0", "id": self.next_id, "method": method, "params": params,
        }) + "\n")
        self.proc.stdin.flush()
        line = self.proc.stdout.readline()
        if not line:
            raise RuntimeError(f"Server closed stdout during {method}")
        response = json.loads(line)
        if "error" in response:
            raise RuntimeError(response["error"])
        return response["result"]

    def tool(self, name, arguments, allow_error=False):
        result = self.call("tools/call", {"name": name, "arguments": arguments})
        if result.get("isError") and not allow_error:
            raise RuntimeError(f"{name}: {result['content']}")
        return result

    def close(self):
        self.proc.stdin.close()
        try:
            self.proc.wait(timeout=15)
        except subprocess.TimeoutExpired:
            self.proc.kill()
            self.proc.wait()
        self.log.close()


def cases(server, c4):
    """Exercise static views plus discovered component and entrypoint selections."""
    for level in (("system", "container") if c4 else ("system", "container", "component", "module")):
        yield f"flowchart-{level}", "render_mermaid_flowchart", {"level": level}
    if c4:
        return
    yield "view-component", "render_architecture_view", {"view": "component", "maxNodes": 500}
    for view in ("workspace", "component"):
        yield f"likec4-{view}", "export_likec4_model", {"view": view, "maxNodes": 500}
    for name in ("render_source_overview", "render_dependency_map", "render_use_case_timeline", "render_pipeline"):
        yield name, name, {}
    components = server.tool("find_components", {}).get("structuredContent", {})
    for component in sorted(components.get("components", []), key=lambda c: c["id"])[:2]:
        yield "slice-" + component["id"], "render_component_dependency_diagram", {"name": component["id"]}
    entrypoints = server.tool("find_entrypoints", {}).get("structuredContent", {})
    for entrypoint in sorted(entrypoints.get("entrypoints", []), key=lambda e: e["id"])[:2]:
        yield "call-" + entrypoint["id"], "call_flow", {"entrypointId": entrypoint["id"]}


def capture(jar, directory, workspaces):
    outputs = {}
    for c4 in (False, True):
        dialect = "c4" if c4 else "universal"
        server = Server(jar, directory / dialect, c4)
        try:
            for workspace in workspaces:
                server.tool("index_workspace", {"paths": [str(workspace)]})
                for label, name, arguments in cases(server, c4):
                    result = server.tool(name, arguments, allow_error=True)
                    text = result["content"][0]["text"]
                    if result.get("isError"):
                        text = "[TOOL ERROR]\n" + text
                    outputs[f"{workspace.name}/{dialect}/{label}"] = text
        finally:
            server.close()
    (directory / "outputs.json").write_text(json.dumps(outputs, indent=2, ensure_ascii=False) + "\n")
    return outputs


def main():
    if len(sys.argv) < 3:
        raise SystemExit(__doc__)
    baseline, current = (Path(arg).resolve() for arg in sys.argv[1:3])
    workspaces = [Path(arg).resolve() for arg in sys.argv[3:]] or [
        ROOT / "src/test/resources/testprojects" / name for name in DEFAULT_FIXTURES
    ] + [ROOT]
    report = ROOT / "target/renderer-comparison"
    before = capture(baseline, report / "before", workspaces)
    after = capture(current, report / "after", workspaces)
    differences = []
    for key in sorted(before.keys() | after.keys()):
        if before.get(key) != after.get(key):
            differences.append(key)
            diff = "".join(difflib.unified_diff(
                before.get(key, "").splitlines(keepends=True),
                after.get(key, "").splitlines(keepends=True),
                fromfile="before/" + key, tofile="after/" + key,
            ))
            (report / f"difference-{len(differences)}.diff").write_text(diff)
    summary = {"baselineJar": str(baseline), "currentJar": str(current),
               "workspaces": [str(p) for p in workspaces],
               "cases": len(before.keys() | after.keys()), "differences": differences,
               "toolErrors": {"before": [k for k, v in before.items() if v.startswith("[TOOL ERROR]")],
                              "after": [k for k, v in after.items() if v.startswith("[TOOL ERROR]")]}}
    (report / "summary.json").write_text(json.dumps(summary, indent=2) + "\n")
    print(json.dumps(summary, indent=2))
    return bool(differences)


if __name__ == "__main__":
    sys.exit(main())
