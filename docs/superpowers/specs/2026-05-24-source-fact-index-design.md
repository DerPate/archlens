# Source Fact Index Foundation

Date: 2026-05-24

## Problem

The existing whole-repo stabilization plan reads broadly and searches for risky patterns, but it does not force the extractor to prove that its architecture facts are grounded in source code. The MCP output cannot become reliable while its base facts are uneven: some extractors walk Spoon directly, some build local indexes, and higher-level flows compose whatever those earlier passes happened to emit.

The next stabilization pass should start at the bottom of the extraction stack. Spoon-derived facts must become explicit, testable, observable, and reusable before object flow, call graph extraction, component classification, runtime flows, and pipeline rendering build on them.

## Goals

- Build a canonical source-derived fact base for each scanned module.
- Preserve what Spoon proves without silently inventing architecture edges from naming heuristics.
- Capture hidden data paths through injection, object receiver calls, aliases, factory/accessor methods, and methods that return member fields from other classes.
- Give downstream extractors a stable API instead of repeated ad-hoc `CtModel` and `getElements` walks.
- Add OpenTelemetry spans and fact counts so heavy phases are visible.
- Protect the base with truth-table tests over representative fixtures.

## Non-Goals

- Do not rewrite all extractors in one pass.
- Do not remove Spoon from extraction.
- Do not replace source proof with naming conventions.
- Do not change MCP tool output until the new fact base is wired underneath a consumer.
- Do not add broad style cleanup unrelated to the extraction foundation.

## Design

### 1. Source-Derived Fact Base

Add a `SourceFactIndex` built directly from one module's Spoon `CtModel`. It is not an architecture classifier. It records normalized facts that the source code proves:

- types, methods, constructors, fields, parameters, annotations, modifiers, and source locations;
- inheritance and interface implementation;
- field injection, constructor injection, method injection, and local variable declarations;
- invocation sites with receiver expression, executable name, arguments, assignment target, enclosing method, and source location;
- return facts such as method returns field, returns parameter, returns local, returns constructor result, or returns invocation result;
- assignment facts such as field writes, local aliases, constructor-created objects, and factory-returned objects where the source can be traced.

The index must distinguish `known`, `ambiguous`, and `unknown`. If a receiver can resolve to multiple implementations, all candidates are carried with evidence. If no source path proves a target, the fact remains unresolved instead of being guessed.

Naming conventions may exist only as explicit low-confidence hints. They must not silently create architecture edges.

### 2. Builder And API Boundaries

Introduce three small units:

- `SourceFactIndex`: immutable read model with stable IDs and source references.
- `SourceFactIndexBuilder`: the class that performs broad Spoon traversal and normalizes facts.
- `SourceEvidence` / `FactConfidence`: evidence metadata attached to facts.

The public API should be stable enough for downstream consumers:

```java
SourceType type(String qualifiedName);
List<SourceMethod> methods(String typeId);
List<SourceInvocation> invocations(String methodId);
List<SourceReturn> returns(String methodId);
List<SourceAssignment> assignments(String methodId);
List<SourceInjectionPoint> injectionPoints(String typeId);
List<SourceType> implementations(String qualifiedName);
```

Useful evidence kinds include:

- `DIRECT_TYPE_REFERENCE`
- `FIELD_INJECTION`
- `CONSTRUCTOR_INJECTION`
- `METHOD_INJECTION`
- `LOCAL_ASSIGNMENT`
- `FIELD_ASSIGNMENT`
- `METHOD_RETURNS_FIELD`
- `METHOD_RETURNS_PARAMETER`
- `METHOD_RETURNS_INVOCATION`
- `POLYMORPHIC_IMPLEMENTATION`
- `ANNOTATION_VALUE`
- `CONFIG_VALUE`
- `UNRESOLVED_NO_CLASSPATH`

The first implementation may keep Spoon identities internally where needed, but downstream extractors should prefer stable source fact IDs and source references over raw `CtElement` traversal.

### 3. Migration Strategy

Roll out bottom-up:

1. Add `SourceFactIndex`, `SourceFactIndexBuilder`, evidence records, and truth-table tests.
2. Build the index inside `ArchitectureExtractor` beside the current `CtModel`, with tracing, but do not change output yet.
3. Move `ObjectFlowIndexBuilder` to consume `SourceFactIndex` for receiver seeds, aliases, implementations, accessor returns, and factory returns.
4. Move `CallGraphExtractor` receiver and return tracking to consume `SourceFactIndex`.
5. Move framework and component extractors only where the fact base clearly removes duplicated annotation or method scanning.

This keeps existing behavior stable while the foundation becomes healthier.

### 4. OpenTelemetry

`SourceFactIndexBuilder` must emit a top-level span:

- `sourcefacts.build`

Required attributes:

- module name
- source root count
- type count
- method count
- field count
- invocation count
- assignment count
- return fact count
- injection point count
- unresolved receiver count
- ambiguous receiver count

Add child spans for heavy phases:

- `sourcefacts.types`
- `sourcefacts.members`
- `sourcefacts.annotations`
- `sourcefacts.inheritance`
- `sourcefacts.invocations`
- `sourcefacts.assignments`
- `sourcefacts.returns`
- `sourcefacts.injection`
- `sourcefacts.receiver-seeds`

Diagnostics should be structured as span attributes/events or index diagnostics. Normal MCP output must not be polluted by default `System.err` timing prints.

### 5. Truth-Table Tests

Tests should assert source facts before architecture projections.

Use existing fixtures first:

- `generic-object-flow`: aliases, factory/accessor returns, interface implementations, local variables, polymorphic targets.
- `quarkus-sample`: CDI injection, REST entrypoints, repositories, messaging calls, scheduler methods.
- `gradle-springboot-sample` or `spring-pipeline-sample`: constructor/field injection, Spring annotations, listener methods, repository calls.
- `war-modules-sample`: cross-module boundaries and module ownership.

Example assertions:

- method `A.foo` invokes `B.bar` through field injection;
- method `Factory.getStore` returns field `store`;
- call `service.handle(x)` has receiver candidates with evidence;
- constructor parameter `repo` is assigned to `this.repo`;
- listener annotation values resolve from constants or config where supported;
- unresolved facts are represented explicitly rather than silently dropped.

### 6. Completion Criteria

The design is complete when:

- source-fact tests pass for the selected fixtures;
- existing extractor tests still pass;
- `ArchitectureExtractor` builds the index with OTel spans and does not change output until a consumer migrates;
- object/call extraction uses source facts for injection, aliases, returns, and method receiver paths after migration;
- no unproven naming heuristic silently creates architecture edges;
- fact counts and phase timings are visible through OpenTelemetry.

## Recommended Implementation Plan Shape

The implementation plan should be staged around source-fact capability slices, not repository areas:

1. Define fact model and builder skeleton.
2. Index types, members, annotations, and source locations.
3. Index inheritance and implementations.
4. Index invocations, assignments, returns, and injection points.
5. Add OTel spans and diagnostics.
6. Add fixture truth-table tests.
7. Wire index creation into `ArchitectureExtractor` without output changes.
8. Migrate `ObjectFlowIndexBuilder`.
9. Migrate `CallGraphExtractor` receiver and return tracking.
