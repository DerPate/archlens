# Agent Graph Classification Design

## Problem

The graph projection contains enough structural data to be useful, but some component
metadata is still too coarse for agents. In the Phoenix backend validation, real
business services, configuration classes, Redis/lock infrastructure, migration
initializers, converters, mappers, and application bootstrap classes can all appear as
generic `SERVICE` components with `infrastructureRole=business-service`.

That makes agent output worse in two ways:

- Agents over-rank supporting infrastructure as business workflow code.
- Agents have to guess which MCP tool and filter shape to use for common architecture
  tasks.

The fix should improve graph truth first, then expose that truth through clearer MCP
tool guidance.

## Goals

- Add richer, explainable component classification metadata to graph component nodes.
- Preserve existing fields such as `Component.type` and `infrastructureRole` for
  compatibility.
- Help agents separate core workflow code from support code without hiding support code.
- Add a small `tool_guidance` MCP tool so agents have an explicit starting point for
  common architecture tasks.
- Keep the design grounded in source evidence, package/name conventions, annotations,
  and graph behavior.

## Non-Goals

- Do not replace the existing `ComponentType` enum.
- Do not make classification configurable in the first implementation.
- Do not introduce a new workflow engine.
- Do not remove existing tools or prompts.
- Do not commit Phoenix backend data or generated validation output.

## Proposed Architecture

Add a dedicated component classification layer beside `ArchitectureRelevanceScorer`.
The scorer can keep producing compatibility fields, but it should delegate richer role
classification to a smaller unit that has one job: turn component evidence into stable
agent-facing metadata.

The graph should expose these additional component properties:

- `primaryRole`: broad architectural role, such as `entrypoint`, `business-service`,
  `data-access`, `domain-model`, `integration`, or `support`.
- `supportRole`: sharper support subtype when applicable, such as `configuration`,
  `mapper`, `converter`, `lock`, `redis-lock`, `migration-initializer`,
  `tenant-infrastructure`, `security-configuration`, `utility`, or `application-bootstrap`.
- `agentCategory`: opinionated agent routing category, such as `core-workflow`,
  `boundary`, `data`, `integration`, `supporting-infrastructure`, or `low-signal`.
- `classificationEvidence`: compact evidence string explaining the classification, for
  example `annotation:@Configuration,package:multiTenant.config,name:PersistenceConfig`.

Existing fields remain:

- `componentType`
- `workflowRelevant`
- `businessRelevant`
- `infrastructureRole`
- `noiseScore`
- `workflowBridgeScore`
- `architecturalWeight`

## Classification Rules

Classification should combine evidence in this order.

1. Strong annotations and stereotypes:
   `@RestController`, `@Repository`, `@Entity`, `@Configuration`, schedulers/listeners,
   mapper annotations, and existing extracted stereotypes.

2. Package role:
   Package names such as `.controller`, `.service`, `.repository`, `.bean`, `.mapping`,
   `.configuration`, `.infrastructure`, `.redis`, `.multiTenant`, `.authorization`,
   `.converter`, `.validation`, and `.client`.

3. Name role:
   Suffixes and keywords such as `Service`, `Controller`, `Repository`, `Mapper`,
   `Config`, `Configuration`, `Converter`, `Initializer`, `Lock`, `Tenant`,
   `Security`, `Filter`, `Client`, `Application`, `Listener`, and `Producer`.

4. Graph behavior:
   Entrypoint ownership, fan-in/fan-out, workflow reachability, state handoffs,
   sink participation, external systems, and messaging involvement.

Graph behavior may promote a support class when it truly bridges workflow, but it must
not erase support identity. For example, a Redis lock class can be workflow reachable
and still stay categorized as supporting infrastructure.

Example expected classifications:

| Class | Expected classification |
| --- | --- |
| `AbsenceService` | `primaryRole=business-service`, `agentCategory=core-workflow` |
| `FlywayClientDatabaseInitializer` | `supportRole=migration-initializer`, `agentCategory=supporting-infrastructure` |
| `OwnerAwareRedisLockRegistry` | `supportRole=redis-lock`, `agentCategory=supporting-infrastructure` |
| `AllowedUrlConfiguration` | `supportRole=security-configuration`, `agentCategory=supporting-infrastructure` |
| `IExtHolidayMapper` | `supportRole=mapper`, `agentCategory=supporting-infrastructure` |
| `AppAbsenceController` | `primaryRole=entrypoint`, `agentCategory=boundary` |
| `IAccountRepository` | `primaryRole=data-access`, `agentCategory=data` |
| `Account` | `primaryRole=domain-model`, `agentCategory=data` |

## MCP Tool Guidance

Add a separate `tool_guidance` MCP tool. This should be small, stable, and explicit,
so agents do not have to infer the right workflow from long documentation.

The tool should return short task-to-tool recipes for common agent tasks:

- Find high-signal business flow:
  `query_architecture_graph` with `action=find_nodes`, `label=Component`, and filters
  like `agentCategory=core-workflow`.
- Investigate one class:
  `find_components` to locate candidates, `get_component_dependencies` for dependency
  traversal, then `query_architecture_graph` with `action=neighborhood`.
- Trace request behavior:
  `find_entrypoints`, then `get_runtime_flow`, `trace_data_flow`, and `render_pipeline`.
- Avoid support noise:
  Prefer `agentCategory=core-workflow` or `boundary` for first-pass architecture
  summaries; inspect `supportRole` and `classificationEvidence` when support code
  matters.
- Debug graph metadata:
  Use `query_architecture_graph` with `find_nodes`, `find_edges`, `neighborhood`, and
  explicit filters before inventing relationships.

Also update existing tool schemas, `docs/TOOLS.md`, and `llms.txt` to advertise:

- `primaryRole`
- `supportRole`
- `agentCategory`
- `classificationEvidence`
- recommended filters for common graph queries

## Error Handling

Classification must be conservative:

- If evidence is weak, keep `primaryRole` aligned with `Component.type` and include
  `classificationEvidence=type:<type>`.
- If multiple support roles match, prefer the more specific one. For example,
  `security-configuration` beats generic `configuration`.
- If a class is support-like but graph behavior makes it important, keep the support
  role and raise relevance through existing workflow/weight fields.
- If no rule matches, use `primaryRole=unknown`, `agentCategory=low-signal`, and evidence
  explaining the fallback.

## Testing Strategy

1. Component classifier unit tests:
   Cover business services, repositories, entities, controllers, configuration,
   mappers, converters, Redis/lock infrastructure, migration initializers, tenant
   infrastructure, security configuration, and application bootstrap classes.

2. Architecture graph tests:
   Verify new classification fields are projected into `ComponentNode.properties()` and
   are filterable through `query_architecture_graph`.

3. Tool guidance tests:
   Verify `tool_guidance` returns stable recipes for common tasks and mentions the new
   role/category filters.

4. Phoenix validation:
   Keep validation as a manual or generated script, not a committed fixture. It should:
   reindex Phoenix, sample 20 or more real classes across packages and types, query each
   selected class through the graph, and compare source evidence to graph metadata.

## Success Criteria

- Agents can query `agentCategory=core-workflow` to get business workflow components
  without mapper/config/lock/migration noise dominating the result.
- Agents can query support code explicitly through `supportRole`.
- Important support classes remain discoverable and explainable.
- Every non-obvious classification exposes evidence.
- `tool_guidance` gives agents a clear first step for common architecture tasks.
- Existing tools and compatibility fields continue to work.

## Implementation Boundary

This design should be implemented as one focused feature set:

- Add the classifier and graph metadata.
- Update query rendering, schemas, docs, and agent notes.
- Add the `tool_guidance` MCP tool.
- Add focused tests.

Broader visualization changes, configurable classification rules, and new graph export
UI behavior should wait until the metadata proves useful in real Phoenix validation.
