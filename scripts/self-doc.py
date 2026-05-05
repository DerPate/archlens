#!/usr/bin/env python3
"""Drive the spoon-mcp-server against its own source to regenerate docs."""

import glob
import json
import subprocess
import sys
import os

_target = os.path.join(os.path.dirname(__file__), "..", "target")
_candidates = [
    j for j in glob.glob(os.path.join(_target, "spoon-mcp-server*.jar"))
    if not any(x in os.path.basename(j) for x in ("-sources", "-javadoc", "original-"))
]
if not _candidates:
    raise FileNotFoundError(f"No spoon-mcp-server jar found in {_target}. Run 'mvn package' first.")
JAR = max(_candidates, key=os.path.getmtime)
PROJECT_ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))


def call(proc, req_id, method, params=None):
    msg = {"jsonrpc": "2.0", "id": req_id, "method": method}
    if params is not None:
        msg["params"] = params
    line = json.dumps(msg) + "\n"
    proc.stdin.write(line.encode())
    proc.stdin.flush()
    raw = proc.stdout.readline()
    if not raw:
        print("ERROR: server closed stdout unexpectedly", file=sys.stderr)
        sys.exit(1)
    resp = json.loads(raw)
    if "error" in resp:
        print(f"ERROR [{method}]: {resp['error']}", file=sys.stderr)
        sys.exit(1)
    return resp.get("result")


def main():
    proc = subprocess.Popen(
        ["java", "-jar", JAR],
        stdin=subprocess.PIPE,
        stdout=subprocess.PIPE,
        stderr=sys.stderr,
        cwd=PROJECT_ROOT,
    )

    try:
        print("→ initialize")
        call(proc, 1, "initialize", {
            "protocolVersion": "2024-11-05",
            "capabilities": {},
            "clientInfo": {"name": "self-doc", "version": "1.0"},
        })

        print(f"→ index_workspace: {PROJECT_ROOT}")
        result = call(proc, 2, "tools/call", {
            "name": "index_workspace",
            "arguments": {"paths": [PROJECT_ROOT]},
        })
        print("  ", result.get("content", [{}])[0].get("text", result))

        print("→ export_architecture_docs")
        result = call(proc, 3, "tools/call", {
            "name": "export_architecture_docs",
            "arguments": {
                "outputPath": "docs/ARCHITECTURE.md",
                "focusComponent": "McpServer",
            },
        })
        print("  ", result.get("content", [{}])[0].get("text", result))

        print("→ export_graph_architecture_poc")
        result = call(proc, 4, "tools/call", {
            "name": "export_graph_architecture_poc",
            "arguments": {
                "outputPath": "docs/SOURCE_ARCHITECTURE_POC.md",
                "focusComponent": "McpServer",
            },
        })
        print("  ", result.get("content", [{}])[0].get("text", result))

    finally:
        proc.stdin.close()
        proc.wait()


if __name__ == "__main__":
    main()
