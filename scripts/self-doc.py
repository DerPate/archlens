#!/usr/bin/env python3
"""Drive the archlens against its own source to regenerate docs."""

import glob
import json
import subprocess
import sys
import os

PROJECT_ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))


def packaged_jar():
    """Build the server and return the current packaged jar."""
    subprocess.run(["mvn", "-q", "package"], cwd=PROJECT_ROOT, check=True)
    target = os.path.join(PROJECT_ROOT, "target")
    candidates = [
        j for j in glob.glob(os.path.join(target, "archlens*.jar"))
        if not any(x in os.path.basename(j) for x in ("-sources", "-javadoc", "original-"))
    ]
    if not candidates:
        raise FileNotFoundError(f"No archlens jar found in {target} after packaging.")
    return max(candidates, key=os.path.getmtime)


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


def notify(proc, method, params=None):
    msg = {"jsonrpc": "2.0", "method": method}
    if params is not None:
        msg["params"] = params
    proc.stdin.write((json.dumps(msg) + "\n").encode())
    proc.stdin.flush()


def main():
    jar = packaged_jar()
    proc = subprocess.Popen(
        ["java", "-jar", jar],
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
        notify(proc, "notifications/initialized", {})

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
