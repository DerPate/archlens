#!/usr/bin/env python3
"""Render pipeline(s) for a phoenix_backend entrypoint as an HTML Mermaid page and open it."""

import json
import os
import subprocess
import sys
import tempfile

ROOT = "/home/dominik/archlens"
PHOENIX = "/home/dominik/phoenix_backend"
JAR = f"{ROOT}/target/archlens.jar"
ENTRYPOINT = sys.argv[1] if len(sys.argv) > 1 else "PUT /employeeCandidature/{id}"

proc = subprocess.Popen(
    ["java", "-jar", JAR],
    stdin=subprocess.PIPE,
    stdout=subprocess.PIPE,
    stderr=sys.stderr,
    text=True,
    bufsize=1,
)

next_id = 1


def call(method, params=None):
    global next_id
    req = {"jsonrpc": "2.0", "id": next_id, "method": method}
    next_id += 1
    if params:
        req["params"] = params
    proc.stdin.write(json.dumps(req) + "\n")
    proc.stdin.flush()
    line = proc.stdout.readline()
    if not line:
        raise RuntimeError("server closed stdout")
    resp = json.loads(line)
    if "error" in resp:
        raise RuntimeError(f"MCP error: {resp['error']}")
    return resp["result"]


def notify(method, params=None):
    msg = {"jsonrpc": "2.0", "method": method}
    if params:
        msg["params"] = params
    proc.stdin.write(json.dumps(msg) + "\n")
    proc.stdin.flush()


def tool(name, arguments):
    result = call("tools/call", {"name": name, "arguments": arguments})
    content = result["content"]
    return content[0]["text"] if content else ""


call("initialize", {
    "protocolVersion": "2024-11-05",
    "capabilities": {},
    "clientInfo": {"name": "pipeline-viewer", "version": "1"},
})
notify("notifications/initialized")

print(f"Indexing phoenix_backend...", flush=True)
tool("index_workspace", {"paths": [PHOENIX]})

print(f"Rendering pipeline for: {ENTRYPOINT}", flush=True)
pipeline = tool("render_pipeline", {"app": "phoenix_backend", "entrypoint": ENTRYPOINT})

# Split into individual chain blocks (each starts with flowchart TD)
blocks = []
current = []
for line in pipeline.split("\n"):
    if line.startswith("%% chain"):
        if current:
            blocks.append("\n".join(current))
        current = [line]
    else:
        current.append(line)
if current:
    blocks.append("\n".join(current))

# Build one diagram per chain block (strip the %% comment line)
diagrams = []
for block in blocks:
    lines = block.split("\n")
    comment = lines[0] if lines[0].startswith("%%") else ""
    mermaid = "\n".join(l for l in lines if not l.startswith("%%")).strip()
    if mermaid:
        diagrams.append((comment, mermaid))

proc.terminate()

# Build HTML
diagram_html = ""
for i, (comment, mermaid) in enumerate(diagrams):
    title = comment.lstrip("%% ") if comment else f"Chain {i+1}"
    diagram_html += f"""
    <div class="chain">
      <h2>{title}</h2>
      <div class="mermaid">
{mermaid}
      </div>
    </div>
"""

html = f"""<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <title>Pipeline: {ENTRYPOINT}</title>
  <script src="https://cdn.jsdelivr.net/npm/mermaid@10/dist/mermaid.min.js"></script>
  <style>
    body {{ font: 14px system-ui, sans-serif; background: #f8f8f6; margin: 0; padding: 20px; }}
    h1 {{ font-size: 18px; margin-bottom: 4px; }}
    .subtitle {{ color: #666; font-size: 12px; margin-bottom: 24px; }}
    .chain {{ background: #fff; border: 1px solid #ddd; border-radius: 6px;
              padding: 16px 24px; margin-bottom: 24px; }}
    h2 {{ font-size: 13px; color: #555; margin: 0 0 12px; font-family: monospace; }}
    .mermaid {{ overflow-x: auto; }}
    .legend {{ display: flex; gap: 16px; flex-wrap: wrap; margin-bottom: 20px; font-size: 12px; }}
    .leg {{ display: flex; align-items: center; gap: 6px; }}
    .dot {{ width: 32px; border-top: 2px dashed #666; }}
    .solid {{ width: 32px; border-top: 2px solid #666; }}
  </style>
</head>
<body>
  <h1>Pipeline: {ENTRYPOINT}</h1>
  <div class="subtitle">phoenix_backend &mdash; {len(diagrams)} chain(s)</div>
  <div class="legend">
    <div class="leg"><div class="dot"></div> conditional branch</div>
    <div class="leg"><div class="solid"></div> unconditional hop</div>
  </div>
  {diagram_html}
  <script>
    mermaid.initialize({{ startOnLoad: true, theme: 'neutral',
      flowchart: {{ curve: 'basis', useMaxWidth: false }} }});
  </script>
</body>
</html>"""

out = "/tmp/pipeline-viewer.html"
with open(out, "w") as f:
    f.write(html)

print(f"Written to {out}", flush=True)

# Open in browser (WSL2)
wsl_path = subprocess.run(["wslpath", "-w", out], capture_output=True, text=True).stdout.strip()
subprocess.run(["explorer.exe", wsl_path])
print(f"Opened in browser.", flush=True)
