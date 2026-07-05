# Agent Guide

This file gives coding agents the shortest reliable path through the project.

## Project Shape

ArchLens is a Java 25 Maven project. It runs as a stdio Model Context Protocol server and exposes architecture-analysis tools built on top of Spoon.

Important paths:

- `src/main/java/dev/dominikbreu/archlens/Main.java`: process entry point.
- `src/main/java/dev/dominikbreu/archlens/mcp/McpServer.java`: JSON-RPC loop, MCP initialize response, tool registry, and dispatch.
- `src/main/java/dev/dominikbreu/archlens/mcp/tools/`: individual MCP tool adapters.
- `src/main/java/dev/dominikbreu/archlens/cache/`: data-access layer for tools.
  - `ModelCache.java`: stores/loads the graph; exposes `graph()` which returns a `GraphQuery`.
  - `GraphStore.java`: owns the TinkerPop `TinkerGraph` instance. Low-level vertex/edge CRUD
    and GraphSON serialization. Not referenced by tools or renderers.
  - `GraphProjector.java`: projects an `ArchitectureModel` into `GraphStore` during
    `index_workspace`. Not referenced by tools or renderers.
  - `GraphQuery.java`: the only cache class tools and renderers import. Typed query methods
    (`findNodes`, `findEdges`, `neighborhood`, `paths`, `impactedBy`, `summary`) plus typed
    lookups `component(ComponentId)`, `entrypoint(EntrypointId)`, `app(AppId)`. Returns
    `GraphNode` sealed records — no raw TinkerPop types leak out.
- `src/main/java/dev/dominikbreu/archlens/extractor/`: Java/Spoon architecture extraction.
- `src/main/java/dev/dominikbreu/archlens/extractor/objectflow/`: source-derived
  receiver and object-flow analysis used by call graph and field access extraction.
- `src/main/java/dev/dominikbreu/archlens/workflow/`: shared workflow traversal and
  linking semantics used by pipeline, use-case, runtime-flow, and graph projections.
- `src/main/java/dev/dominikbreu/archlens/model/`: extraction-side data model only.
  Classes here are produced by extractors and consumed by `GraphProjector`. They are
  never imported in `mcp/tools/` or `renderer/` (except `model/ids/` which are value types).
- `src/main/java/dev/dominikbreu/archlens/merger/`: deployment metadata merging from Docker Compose and Ansible files.
- `src/main/java/dev/dominikbreu/archlens/renderer/`: Mermaid rendering.
- `src/test/java/`: unit tests.
- `src/test/resources/testprojects/`: fixture projects used by tests.

## Architectural Rules

These rules encode the deliberate design of the runtime layer. Do not work around them.

**TinkerPop is the single runtime source of truth.**
After `index_workspace` completes, `ArchitectureModel` is discarded. The graph is the only
thing that persists. Tools and renderers never see or hold a reference to `ArchitectureModel`,
`Component`, `DataFlowPath`, `CallEdge`, or any other class from `model/` (except ID types).

**The boundary is `GraphQuery`.**
`cache.graph()` returns a `GraphQuery`. That is the only data-access call tools make.
`cache.index()` does not exist. `rawModel()` does not exist. `ToolModelIndex` does not exist.
If you find yourself wanting to call these, the right fix is to add a typed lookup or query
method to `GraphQuery`, not to bypass the boundary.

**`GraphProjector` writes once; everything else reads.**
`GraphProjector` is called by `IndexWorkspaceTool` during indexing and then discarded.
No other class instantiates or imports `GraphProjector`. Tools are read-only consumers
of the graph via `GraphQuery`.

**`model/` classes do not cross into the tool or renderer layer.**
The import rule: files under `mcp/tools/` and `renderer/` may import from `model/ids/`
(value types used as query parameters) and nothing else from `model/`. If a tool or renderer
needs data that is currently only on a model class, the right fix is to ensure that data
is projected as a graph property and queried through `GraphQuery`.

## Commands

Use WSL/Linux paths when working from Windows UNC paths.

```sh
mvn test
mvn package
mvn dependency:analyze
mvn versions:display-dependency-updates
mvn versions:display-plugin-updates
```

Run the MCP server:

```sh
java -jar target/archlens.jar
```

## Conventions

- Keep Java source compatible with Java 25.
- Prefer entity/model changes that are covered by focused tests.
- Do not skip Spotless or SpotBugs to make `verify` pass. Fix formatting with
  `mvn spotless:apply` and address or explicitly justify SpotBugs findings.
- **Tool data-access pattern**: MCP tools call `cache.graph()` only. `IndexWorkspaceTool`
  is the only writer and keeps its direct `ModelCache` reference. No other tool calls
  `cache.load()`, `cache.index()`, or `cache.store()`.
- Update `docs/TOOLS.md` when adding or changing MCP tools.
- Update `docs/ARCHITECTURE.md` when changing major package responsibilities.
- When adding new graph vertex/edge labels in `GraphProjector.java`, also:
  - extend the property/edge catalog in `docs/TOOLS.md` (`query_architecture_graph` section);
  - update `llms.txt` "Notes For Agents";
  - extend the reachability propagation in `GraphProjector` if the new edge should carry
    `entrypointReachable=true` to the new vertices;
  - add a typed record to the `GraphNode` sealed interface in `GraphQuery.java`;
  - add coverage in `cache/GraphQueryTest.java`.
- When changing workflow continuation semantics (`WORKFLOW_LINK`, state handoffs,
  messaging/event-bus chaining, or utility traversal boundaries), update
  `workflow/WorkflowTraversalPolicy.java`, `workflow/WorkflowLinker.java`, relevant
  pipeline/use-case/runtime-flow tests, `docs/TOOLS.md`, and `llms.txt`.
- When extending `MessagingConfigResolver` (broker / topic / new connector kinds), also
  update the "Messaging entrypoints" paragraph in `docs/TOOLS.md` and the
  `Entrypoint` / `InterfaceEntry` model fields.
- When extending `DataFlowTracer` (new sink kind, new propagation rule, new sink
  metadata), also update the "trace_data_flow" section in `docs/TOOLS.md`,
  `DataFlowSink.Kind` Javadoc, and `llms.txt`.
- Do not commit generated output from `target/`, `.archlens-cache/`, or `dependency-reduced-pom.xml`.
- Do not add GitHub Actions, hooks, or release automation unless explicitly requested.

## Testing Notes

Most behavior has focused tests by package. When changing:

- extractor logic: run relevant tests under `dev.dominikbreu.archlens.extractor`.
- Mermaid output: run renderer tests.
- deployment merge behavior: run merger tests.
- MCP tool surface: run the full suite with `mvn test`.

## Driving the MCP Server in Scripts (Mandatory Pattern)

The server is a stdio JSON-RPC process. Violating this pattern causes silent hangs.

**Three rules that must all be followed:**

1. **Use `subprocess.Popen` with `stderr` directed to a file or `sys.stderr` — never `stderr=subprocess.PIPE`.**  
   Spoon logs flood stderr during indexing. If stderr is piped, the OS buffer fills and deadlocks the process.

2. **Send `notifications/initialized` after `initialize` before any tool call.**  
   The MCP protocol requires this notification. Without it the server never processes subsequent requests and `readline()` blocks forever.

3. **Send one request, read one response, repeat.** Do not batch requests without reading responses in between.

Canonical pattern (mirrors `scripts/self-doc.py`):

```python
import json, subprocess, sys

JAR = "target/archlens.jar"
PROJECT_ROOT = "."  # working directory for the jar process

proc = subprocess.Popen(
    ["java", "-jar", JAR],
    stdin=subprocess.PIPE,
    stdout=subprocess.PIPE,
    stderr=sys.stderr,   # <- never subprocess.PIPE
    cwd=PROJECT_ROOT,
)

_id = [0]

def call(method, params=None):
    _id[0] += 1
    msg = {"jsonrpc": "2.0", "id": _id[0], "method": method}
    if params:
        msg["params"] = params
    proc.stdin.write((json.dumps(msg) + "\n").encode())
    proc.stdin.flush()
    return json.loads(proc.stdout.readline()).get("result")

def notify(method, params=None):
    msg = {"jsonrpc": "2.0", "method": method}
    if params:
        msg["params"] = params
    proc.stdin.write((json.dumps(msg) + "\n").encode())
    proc.stdin.flush()

def tool(name, args):
    return call("tools/call", {"name": name, "arguments": args})

# Most tools also return structuredContent (JSON matching the tool's outputSchema from
# tools/list) alongside the text above -- read it directly when you need parsed data
# instead of re-parsing the text:
def tool_structured(name, args):
    return tool(name, args).get("structuredContent")

def tool_text(name, args):
    return tool(name, args)["content"][0]["text"]

# Handshake -- always in this order
call("initialize", {
    "protocolVersion": "2025-11-25",
    "capabilities": {},
    "clientInfo": {"name": "script", "version": "1"},
})
notify("notifications/initialized", {})  # <- mandatory, no response expected

# Now call tools
print(tool_text("index_workspace", {"paths": ["/path/to/project"]}))
apps = tool_structured("list_apps", {})
print(apps["apps"])

proc.stdin.close()
proc.wait()
```

Prefer `structuredContent` for field access, calculations, and follow-up tool arguments;
use `content[0].text` for human-facing summaries. Stable mode wraps collection arrays in
named objects (`entrypoints`, `components`, `dependencies`, `containers`, `paths`, or
`useCases`). Starting the server with `ARCHLENS_MCP_EXPERIMENTAL_DRAFT=true` opts into draft
top-level arrays. Always follow the shape declared by `tools/list`; negotiated older clients
may not expose structured output.

<!-- lean-ctx -->
## lean-ctx

Prefer lean-ctx MCP tools over native equivalents for token savings.
Full rules: @LEAN-CTX.md
<!-- /lean-ctx -->
