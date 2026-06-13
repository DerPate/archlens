# Branch-Aware Data Flow Design

## Problem

`DataFlowPath.steps` is a flat ordered list. `DataFlowTracer` appends steps as DFS visits
methods, so mutually exclusive branches can appear as one linear execution story. Agents
then receive output that intermingles alternatives, sinks, and continuation links. Renderer
deduplication or simple conditional flags would only hide the symptom; the model itself
needs to represent branch topology.

Issue #17 is one visible example: a method called in an `if` branch and another method
called in the `else` branch can both appear in the same path as if they ran sequentially.

## Goals

- Represent data-flow as a branch-aware graph, not only as a flat step list.
- Make branch points explicit for agents, renderers, and graph consumers.
- Treat graph metadata as the canonical middleware layer for agent consumption.
- Keep Spoon-specific AST logic inside extraction code.
- Keep `DataFlowTracer` consuming neutral model metadata, not Spoon classes.
- Preserve backward compatibility for existing tools that read `DataFlowPath.steps`.
- Make tool output easier for agents to read without requiring branch reconstruction.

## Non-Goals

- No full Java symbolic execution.
- No boolean condition solving or path feasibility proof beyond source structure.
- No attempt to prove that a branch must or must not execute at runtime.
- No removal of the existing `steps` field in the first implementation.
- No renderer-only fix that leaves the model flat.

## Model Changes

Add topology fields to `DataFlowPath`:

- `flowNodes`: ordered list of graph nodes.
- `flowEdges`: directed edges between nodes.
- `branches`: branch groups that explain fan-out structure.

Suggested model classes:

```java
public class DataFlowNode {
    public String id;
    public NodeKind kind; // ROOT, METHOD, SINK
    public ComponentId componentId;
    public String componentName;
    public String method;
    public String localName;
    public SourceInfo source;
}

public class DataFlowEdge {
    public String fromNodeId;
    public String toNodeId;
    public EdgeKind kind; // UNCONDITIONAL, CONDITIONAL, EXCEPTION
    public String branchId;
    public String label;
}

public class DataFlowBranch {
    public String id;
    public BranchKind kind; // IF, SWITCH, TERNARY, TRY
    public SourceInfo source;
    public List<DataFlowBranchArm> arms;
}

public class DataFlowBranchArm {
    public String id;
    public String branchId;
    public String label; // then, else, case X, default, catch IOException, finally
    public String entryNodeId;
}
```

`DataFlowStep` remains as a compatibility projection. When topology exists, tool docs
should tell agents to prefer `flowNodes` / `flowEdges` / `branches` over `steps`.

## Call-Site Metadata

`CallGraphExtractor` should classify each `CtInvocation` while Spoon context is available.
This metadata belongs on `CallEdge` because a branch is source evidence about a call site.

Add neutral fields to `CallEdge`:

- `controlFlowKind`: `UNCONDITIONAL`, `IF_THEN`, `IF_ELSE`, `SWITCH_CASE`, `SWITCH_DEFAULT`,
  `TERNARY_THEN`, `TERNARY_ELSE`, `CATCH`, `FINALLY`.
- `branchGroupId`: stable id for the enclosing branch structure.
- `branchArmId`: stable id for the specific arm.
- `branchLabel`: human label such as `then`, `else`, `case ACTIVE`, `catch IOException`.
- `controlSource`: `SourceInfo` for the branch construct.

`CallGraphExtractor` can compute this by walking from the `CtInvocation` to the enclosing
method and checking ancestors:

- `CtIf`: then / else.
- `CtSwitch` and `CtCase`: case labels and default.
- `CtConditional`: then / else expression.
- `CtTry`, `CtCatch`, finalizer: catch / finally.

If no branch ancestor is found, the edge is `UNCONDITIONAL`.

## DataFlowTracer Behavior

`DataFlowTracer` should build topology during traversal:

- Create one root node per traced parameter path.
- Create method nodes for traversed calls.
- Create sink nodes for persistence, messaging, store, HTTP, file, object-storage, and unknown sinks.
- Connect nodes with `DataFlowEdge`.
- When traversing a `CallEdge` with branch metadata, attach the edge to the relevant branch group and arm.
- Emit separate outgoing edges for each branch arm instead of appending branch alternatives into one flat sequence.

The existing `steps` list should be derived as a best-effort linear projection for older
consumers. It must be documented as a compatibility view, not the canonical representation.

## Tool Output

`trace_data_flow` should render topology when present.

Example:

```text
flow graph:
  n1 OrderResource.create [root]
  n2 OrderService.validate [if then: value != null]
  n3 OrderService.reject [if else]
  n4 OrderRepository.save

branches:
  b1 IF OrderService.java:42
    then -> n2
    else -> n3

edges:
  n1 -> n2 [then]
  n1 -> n3 [else]
  n2 -> n4

sinks:
  n4 -> [persistence] OrderRepository.save
```

Agent guidance in `docs/TOOLS.md`, `llms.txt`, and example agent instructions should say:

- Prefer branch topology over flat `steps`.
- Treat each branch arm as an alternative path unless the tool marks it unconditional.
- Do not narrate branch arms as sequential execution.
- If topology is absent, treat `steps` as a compatibility projection.

## Renderer Behavior

`MermaidPipelineRenderer` and related renderers should consume topology when available.

- Unconditional edges: solid arrows.
- Conditional branch edges: dashed or labeled arrows.
- Exception edges: distinct label such as `catch IOException` or `finally`.
- Branch groups: labeled fan-out points.
- No deduplication that hides branch evidence.

The renderer may still fall back to `steps` for old cached models.

## Graph Projection

`ArchitectureGraph` should project branch topology into property graph nodes and edges as
first-class metadata. This is not a secondary export concern: the graph is the middleware
layer agents should use when they need structured architecture facts.

Add graph labels:

- `DataFlowNode`: one vertex per `DataFlowNode`.
- `DataFlowBranch`: one vertex per branch group.
- `DataFlowBranchArm`: one vertex per branch arm when explicit arm traversal is useful.

Add graph edges:

- `HAS_FLOW_NODE`: `DataFlowPath` → `DataFlowNode`, carries `nodeIndex` and `nodeKind`.
- `FLOW_EDGE`: `DataFlowNode` → `DataFlowNode`, carries `kind`, `branchId`,
  `branchArmId`, `branchLabel`, `sourceFile`, and `sourceLine`.
- `HAS_BRANCH`: `DataFlowPath` → `DataFlowBranch`.
- `HAS_BRANCH_ARM`: `DataFlowBranch` → `DataFlowBranchArm`.
- `ARM_STARTS_AT`: `DataFlowBranchArm` → `DataFlowNode`.
- `REACHES_NODE`: `DataFlowNode` → `DataFlowSink` for sink nodes that correspond to
  existing `DataFlowSink` vertices.

Use properties, not Java-specific object references, so graph-only consumers can operate
without deserializing the full `ArchitectureModel`.

Recommended properties:

- On `DataFlowNode`: `pathId`, `nodeKind`, `componentId`, `componentName`, `method`,
  `localName`, `sourceFile`, `sourceLine`.
- On `DataFlowBranch`: `pathId`, `branchKind`, `sourceFile`, `sourceLine`, and a
  stable `branchGroupId`.
- On `DataFlowBranchArm`: `pathId`, `branchGroupId`, `branchArmId`, `branchLabel`.
- On `FLOW_EDGE`: `edgeKind`, `branchGroupId`, `branchArmId`, `branchLabel`,
  `sourceFile`, `sourceLine`.

This makes `query_architecture_graph` useful for agents that need to inspect branch
structure without calling `trace_data_flow`.

### TinkerPop Traversal Support

Branch topology should be queryable through existing `ArchitectureGraph` traversal
methods and `query_architecture_graph` actions.

Extend graph support so agents can:

- Find all branch points for a data-flow path:
  `DataFlowPath -> HAS_BRANCH -> DataFlowBranch`.
- Follow an arm from a branch to its first node:
  `DataFlowBranch -> HAS_BRANCH_ARM -> DataFlowBranchArm -> ARM_STARTS_AT -> DataFlowNode`.
- Walk path topology:
  `DataFlowNode -> FLOW_EDGE -> DataFlowNode`.
- Find sinks reached only through a branch:
  `DataFlowBranchArm -> ARM_STARTS_AT -> DataFlowNode -> FLOW_EDGE* -> REACHES_NODE -> DataFlowSink`.
- Compare unconditional and conditional portions of a path by filtering `FLOW_EDGE.edgeKind`.

`query_architecture_graph` should expose the labels and properties in the public catalog.
If new helper actions are added, keep them graph-shaped rather than renderer-shaped. Good
candidate actions:

- `data_flow_topology`: return nodes, flow edges, branches, and sinks for one path id.
- `branch_neighborhood`: return a branch group, its arms, entry nodes, and reached sinks.

These helpers should be thin TinkerPop-backed views over the property graph. They should
not duplicate branch reconstruction logic in MCP tool formatters.

### Graph Projection Parity

Graph consumers should have parity with text and Mermaid tools:

- `trace_data_flow` text output explains the same topology stored in graph metadata.
- `render_pipeline` renders from the same topology when available.
- `export_graph_data` and viewer projections can expose branch topology as an optional
  projection, while public viewer edge filtering can still hide internal scaffolding when
  needed.
- `WORKFLOW_LINK` remains the canonical cross-entrypoint continuation edge; branch
  topology explains what happens inside each `DataFlowPath` segment.

## Compatibility And Cache

Because the model shape changes, bump the model cache schema version. Old caches should
re-extract instead of serving topology-free data as if it were current.

Existing JSON consumers continue to see `steps`. New fields are additive.

## Documentation Updates

Update:

- `docs/TOOLS.md`: `trace_data_flow`, `render_pipeline`, and `query_architecture_graph`.
- `llms.txt`: notes for agents about branch topology.
- `examples/agents/AGENTS.md`, `examples/agents/CLAUDE.md`, and
  `examples/agents/copilot-instructions.md`: tell agents not to flatten branch arms.

## Testing Strategy

Add focused fixtures and tests:

- `CallGraphExtractorTest`: invocation in `if` then arm gets `IF_THEN`; invocation in
  else arm gets `IF_ELSE`; unconditional call remains `UNCONDITIONAL`.
- `CallGraphExtractorTest`: `switch` cases and default are classified.
- `CallGraphExtractorTest`: `catch` and `finally` are classified.
- `DataFlowTracerTest`: branch arms produce topology edges and branch groups, not one
  fake linear sequence.
- `TraceDataFlowToolTest`: text output shows `branches` and does not narrate arms as
  sequential steps.
- `MermaidPipelineRendererTest`: conditional branches render visibly distinct from
  unconditional flow.
- `ArchitectureGraphTest`: branch topology is projected and queryable.
- Cache schema test: stale topology-free cache is invalidated.

## Implementation Order

1. Add model classes and JSON compatibility.
2. Add branch metadata to `CallEdge`.
3. Extract branch metadata in `CallGraphExtractor`.
4. Build topology in `DataFlowTracer` while preserving `steps`.
5. Update `trace_data_flow` output.
6. Update Mermaid renderers to prefer topology.
7. Project topology in `ArchitectureGraph`.
8. Update docs and agent instructions.
9. Bump cache schema.

## Open Decisions

- Exact node/edge class names can follow existing project naming conventions.
- Mermaid visual styling should be chosen during implementation after seeing real output.
- `steps` projection order should remain deterministic but should not be treated as canonical.
