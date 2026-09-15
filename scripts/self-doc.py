#!/usr/bin/env python3
"""Drive the archlens against its own source to regenerate docs."""

import glob
import json
import subprocess
import sys
import os
import re
from pathlib import Path

PROJECT_ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))

# Stay below Mermaid's default limits, including in hosted Markdown viewers.
MAX_DIAGRAM_TEXT = 45_000
MAX_DIAGRAM_EDGES = 400
EDGE = re.compile(r"^(\s*)(\w+)( (?:-->|-\.->)(?:\|.*\|)? )(\w+)\s*$")
NODE = re.compile(r"^(\s*)(\w+)(?=[\[(])")
GROUP = re.compile(r"^(\s*subgraph )(\w+)")
CLASS = re.compile(r"^(\s*class )(\w+)")


def compact_identifiers(source):
    """Shorten generated flowchart IDs without modifying labels or style names."""
    identifiers = {}

    def short(identifier):
        return identifiers.setdefault(identifier, f"n{len(identifiers)}")

    lines = []
    for line in source.splitlines(keepends=True):
        edge = EDGE.match(line)
        if edge:
            indent, start, connector, end = edge.groups()
            lines.append(f"{indent}{short(start)}{connector}{short(end)}\n")
            continue
        for pattern in (NODE, GROUP, CLASS):
            match = pattern.match(line)
            if match:
                line = line[:match.start(2)] + short(match[2]) + line[match.end(2):]
                break
        lines.append(line)
    return "".join(lines)


def renderable_markdown(markdown):
    """Compact oversized flowcharts and partition edges without losing graph data."""
    def replace(match):
        source = match[1]
        lines = source.splitlines(keepends=True)
        edge_count = sum(bool(EDGE.match(line)) for line in lines)
        if len(source) <= MAX_DIAGRAM_TEXT and edge_count <= MAX_DIAGRAM_EDGES:
            return match[0]
        if not re.search(r"^flowchart \w+", source, re.M):
            raise ValueError("Oversized non-flowchart Mermaid diagram in generated documentation")
        lines = compact_identifiers(source).splitlines(keepends=True)
        edges = [line for line in lines if EDGE.match(line)]
        first_edge = next((i for i, line in enumerate(lines) if EDGE.match(line)), len(lines))
        prefix = "".join(lines[:first_edge])
        suffix = "".join(line for line in lines[first_edge:] if not EDGE.match(line))
        base_size = len(prefix) + len(suffix)
        if base_size > MAX_DIAGRAM_TEXT:
            raise ValueError("Mermaid node declarations alone exceed the diagram size budget")
        parts, chunk, size = [], [], base_size
        for edge in edges:
            if base_size + len(edge) > MAX_DIAGRAM_TEXT:
                raise ValueError("Mermaid edge label exceeds the diagram size budget")
            if chunk and (len(chunk) >= MAX_DIAGRAM_EDGES or size + len(edge) > MAX_DIAGRAM_TEXT):
                parts.append(prefix + "".join(chunk) + suffix)
                chunk, size = [], base_size
            chunk.append(edge)
            size += len(edge)
        parts.append(prefix + "".join(chunk) + suffix)
        if len(parts) == 1:
            return "```mermaid\n" + parts[0] + "```"
        return "\n\n".join(
            f"Part {i} of {len(parts)} (nodes repeated; dependencies partitioned).\n\n```mermaid\n{part}```"
            for i, part in enumerate(parts, 1)
        )

    return re.sub(r"```mermaid\n(.*?)```", replace, markdown, flags=re.S)


def packaged_jar():
    """Build the server and return the current packaged jar."""
    # Template resources are compiled into Java by JStachio's annotation processor.
    # An incremental package can reuse renderers compiled from older templates.
    subprocess.run(["mvn", "-q", "clean", "package"], cwd=PROJECT_ROOT, check=True)
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

        if result.get("isError"):
            raise RuntimeError("Architecture documentation export failed")
        architecture = Path(PROJECT_ROOT, "docs/ARCHITECTURE.md")
        markdown = renderable_markdown(architecture.read_text())
        notes = Path(PROJECT_ROOT, "scripts/self-doc-notes.md").read_text()
        markdown = markdown.replace("## Source Overview\n", notes.rstrip() + "\n\n## Source Overview\n", 1)
        architecture.write_text(markdown)

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
