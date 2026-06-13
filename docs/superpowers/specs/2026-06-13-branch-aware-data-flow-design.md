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

`ArchitectureGraph` should project branch topology into property graph nodes and edges:

- Optional `DataFlowNode` vertices.
- Optional `DataFlowBranch` vertices.
- `DATA_FLOW_EDGE` or specific typed edges for `UNCONDITIONAL`, `CONDITIONAL`, and
  `EXCEPTION`.
- Properties for branch id, arm id, branch label, source file, and source line.

This makes `query_architecture_graph` useful for agents that need to inspect branch
structure without calling `trace_data_flow`.

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
