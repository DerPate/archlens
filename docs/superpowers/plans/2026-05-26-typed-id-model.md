# Typed ID & Reference Model Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace all raw-string component ID patterns (`"comp:..."`, `compId+"#"+method`, `fieldOwnerComponentId+"@"+fieldName`) with typed Java 21 records, making cross-component references structurally sound, index lookups type-safe, and the store-linking bug (null `fieldOwnerComponentId`) impossible by construction.

**Architecture:** Five new records/sealed types in `model/ids` become the single source of truth for every identity and reference in the system. All model POJOs, index classes, extractors, graph serialization, and tool adapters migrate to these types in one branch. The `FieldBinding` sealed interface replaces the nullable two-field pattern on `FieldAccess`, structurally preventing the null-owner store-linking defect. Serialized graph node IDs change to use the qualified name without the `"comp:"/"ep:"` prefix convention.

**Tech Stack:** Java 21 records, sealed interfaces, Jackson (for cache JSON), Maven (`mvn test`, `mvn spotless:apply`)

---

## File Map

**New files:**
- `src/main/java/dev/dominikbreu/spoonmcp/model/ids/ComponentId.java`
- `src/main/java/dev/dominikbreu/spoonmcp/model/ids/MethodRef.java`
- `src/main/java/dev/dominikbreu/spoonmcp/model/ids/EntrypointId.java`
- `src/main/java/dev/dominikbreu/spoonmcp/model/ids/FieldRef.java`
- `src/main/java/dev/dominikbreu/spoonmcp/model/ids/FieldBinding.java`
- `src/test/java/dev/dominikbreu/spoonmcp/model/ids/ModelIdsTest.java`

**Model layer (replace String with typed IDs):**
- `src/main/java/dev/dominikbreu/spoonmcp/model/Component.java` — `id: String` → `id: ComponentId`
- `src/main/java/dev/dominikbreu/spoonmcp/model/Entrypoint.java` — `componentId: String` → `ComponentId`
- `src/main/java/dev/dominikbreu/spoonmcp/model/CallEdge.java` — `fromComponentId/toComponentId: String` → `ComponentId`
- `src/main/java/dev/dominikbreu/spoonmcp/model/FieldAccess.java` — replace `fieldOwnerComponentId`+`fieldName` with `FieldBinding fieldBinding`, `componentId` → `ComponentId`, `method` stays String
- `src/main/java/dev/dominikbreu/spoonmcp/model/DataFlowSink.java` — `componentId/fieldOwnerComponentId: String` → `ComponentId`
- `src/main/java/dev/dominikbreu/spoonmcp/model/DataFlowStep.java` — `componentId: String` → `ComponentId`
- `src/main/java/dev/dominikbreu/spoonmcp/model/DataFlowPath.java` — `entrypointId: String` → `EntrypointId`
- `src/main/java/dev/dominikbreu/spoonmcp/model/RuntimeFlow.java` — `entrypointId: String` → `EntrypointId`
- `src/main/java/dev/dominikbreu/spoonmcp/model/RuntimeFlowStep.java` — `componentId: String` → `ComponentId`
- `src/main/java/dev/dominikbreu/spoonmcp/model/InterfaceEntry.java` — `componentId: String` → `ComponentId`
- `src/main/java/dev/dominikbreu/spoonmcp/model/OutboundSinkSite.java` — `componentId: String` → `ComponentId`
- `src/main/java/dev/dominikbreu/spoonmcp/model/AppEntry.java` — `componentIds: List<String>` → `List<ComponentId>`
- `src/main/java/dev/dominikbreu/spoonmcp/model/Container.java` — `componentIds: List<String>` → `List<ComponentId>`
- `src/main/java/dev/dominikbreu/spoonmcp/model/UseCase.java` — `componentIds: List<String>` → `List<ComponentId>`

**Index layer (replace String keys with typed keys):**
- `src/main/java/dev/dominikbreu/spoonmcp/extractor/CallAdjacency.java` — map key `String` → `MethodRef`
- `src/main/java/dev/dominikbreu/spoonmcp/extractor/FieldAccessIndex.java` — map key `String` → `MethodRef`
- `src/main/java/dev/dominikbreu/spoonmcp/extractor/OutboundSinkIndex.java` — map key `String` → `MethodRef`
- `src/main/java/dev/dominikbreu/spoonmcp/extractor/ComponentIndex.java` — map key `String` → `ComponentId`

**Data-flow / workflow layer (fix DFS split patterns + null guard):**
- `src/main/java/dev/dominikbreu/spoonmcp/extractor/DataFlowTracer.java`
- `src/main/java/dev/dominikbreu/spoonmcp/extractor/CallGraphExtractor.java`
- `src/main/java/dev/dominikbreu/spoonmcp/extractor/RuntimeFlowInferrer.java`
- `src/main/java/dev/dominikbreu/spoonmcp/extractor/UseCaseDetector.java`
- `src/main/java/dev/dominikbreu/spoonmcp/workflow/WorkflowLinker.java`
- `src/main/java/dev/dominikbreu/spoonmcp/workflow/WorkflowTraversalPolicy.java`

**Extractor layer (constructing model objects):**
- `src/main/java/dev/dominikbreu/spoonmcp/extractor/QuarkusExtractor.java`
- `src/main/java/dev/dominikbreu/spoonmcp/extractor/JavaEEExtractor.java`
- `src/main/java/dev/dominikbreu/spoonmcp/extractor/SpringExtractor.java`
- `src/main/java/dev/dominikbreu/spoonmcp/extractor/GenericJavaExtractor.java`
- `src/main/java/dev/dominikbreu/spoonmcp/extractor/EventBusExtractor.java`
- `src/main/java/dev/dominikbreu/spoonmcp/extractor/DependencyExtractor.java`
- `src/main/java/dev/dominikbreu/spoonmcp/extractor/objectflow/ObjectFlowIndexBuilder.java`
- `src/main/java/dev/dominikbreu/spoonmcp/extractor/objectflow/ObjectFlowMethodAnalyzer.java`
- `src/main/java/dev/dominikbreu/spoonmcp/extractor/objectflow/ReceiverTarget.java`

**Graph / serialization layer:**
- `src/main/java/dev/dominikbreu/spoonmcp/cache/ArchitectureGraph.java`

**Tests (update string IDs → typed IDs):**
- All files under `src/test/java/dev/dominikbreu/spoonmcp/` that reference `"comp:"`, `"ep:"`, or call `component.id` as a string.

---

## Task 1: Create the `model/ids` package

**Files:**
- Create: `src/main/java/dev/dominikbreu/spoonmcp/model/ids/ComponentId.java`
- Create: `src/main/java/dev/dominikbreu/spoonmcp/model/ids/MethodRef.java`
- Create: `src/main/java/dev/dominikbreu/spoonmcp/model/ids/EntrypointId.java`
- Create: `src/main/java/dev/dominikbreu/spoonmcp/model/ids/FieldRef.java`
- Create: `src/main/java/dev/dominikbreu/spoonmcp/model/ids/FieldBinding.java`
- Create: `src/test/java/dev/dominikbreu/spoonmcp/model/ids/ModelIdsTest.java`

- [ ] **Step 1: Write the failing test**

```java
package dev.dominikbreu.spoonmcp.model.ids;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ModelIdsTest {

    @Test
    void componentId_serializesAsQualifiedName() {
        var id = ComponentId.of("com.example.MyService");
        assertThat(id.qualifiedName()).isEqualTo("com.example.MyService");
        assertThat(id.serialize()).isEqualTo("com.example.MyService");
        assertThat(ComponentId.deserialize("com.example.MyService")).isEqualTo(id);
    }

    @Test
    void methodRef_roundtrips() {
        var comp = ComponentId.of("com.example.MyService");
        var ref = new MethodRef(comp, "process");
        assertThat(ref.component()).isEqualTo(comp);
        assertThat(ref.method()).isEqualTo("process");
    }

    @Test
    void entrypointId_structuredAccess() {
        var comp = ComponentId.of("com.example.Consumer");
        var ep = new EntrypointId(comp, "onMessage", "msg-in:orders");
        assertThat(ep.component()).isEqualTo(comp);
        assertThat(ep.method()).isEqualTo("onMessage");
        assertThat(ep.suffix()).isEqualTo("msg-in:orders");
        assertThat(ep.serialize()).isEqualTo("com.example.Consumer#onMessage:msg-in:orders");
    }

    @Test
    void fieldBinding_own_doesNotHaveOwner() {
        var binding = new FieldBinding.Own("store");
        assertThat(binding.fieldName()).isEqualTo("store");
    }

    @Test
    void fieldBinding_crossComponent_hasNonNullOwner() {
        var owner = ComponentId.of("com.example.DataService");
        var ref = new FieldRef(owner, "cache");
        var binding = new FieldBinding.CrossComponent(ref);
        assertThat(binding.ref().owner()).isEqualTo(owner);
        assertThat(binding.ref().fieldName()).isEqualTo("cache");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
mvn test -pl . -Dtest=ModelIdsTest -q 2>&1 | tail -5
```
Expected: compilation error — types don't exist yet.

- [ ] **Step 3: Create `ComponentId.java`**

```java
package dev.dominikbreu.spoonmcp.model.ids;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Typed identity for an architecture component. Replaces the raw {@code "comp:<qualifiedName>"}
 * string convention. Serializes as the bare qualified name (no prefix).
 */
public record ComponentId(String qualifiedName) {

    public static ComponentId of(String qualifiedName) {
        return new ComponentId(qualifiedName);
    }

    @JsonCreator
    public static ComponentId deserialize(String value) {
        if (value == null) return null;
        // Accept old "comp:..." format from cached JSON
        return new ComponentId(value.startsWith("comp:") ? value.substring(5) : value);
    }

    @JsonValue
    public String serialize() {
        return qualifiedName;
    }
}
```

- [ ] **Step 4: Create `MethodRef.java`**

```java
package dev.dominikbreu.spoonmcp.model.ids;

/**
 * Typed key for a component method. Replaces {@code componentId + "#" + method} strings
 * used as index keys throughout {@code CallAdjacency}, {@code FieldAccessIndex}, etc.
 */
public record MethodRef(ComponentId component, String method) {

    public static MethodRef of(ComponentId component, String method) {
        return new MethodRef(component, method);
    }
}
```

- [ ] **Step 5: Create `EntrypointId.java`**

```java
package dev.dominikbreu.spoonmcp.model.ids;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Typed identity for an entrypoint. Structured as (component, method, suffix).
 * The suffix encodes the entrypoint kind and channel, e.g. {@code "msg-in:orders"},
 * {@code "scheduled"}, {@code "GET:/api/v1/devices"}.
 * Serializes as {@code "<qualifiedName>#<method>:<suffix>"} (no {@code "ep:"} prefix).
 */
public record EntrypointId(ComponentId component, String method, String suffix) {

    @JsonCreator
    public static EntrypointId deserialize(String value) {
        if (value == null) return null;
        // Accept old "ep:..." format from cached JSON
        String v = value.startsWith("ep:") ? value.substring(3) : value;
        int hash = v.indexOf('#');
        if (hash < 0) return new EntrypointId(ComponentId.of(v), "", "");
        String qualifiedName = v.substring(0, hash);
        String rest = v.substring(hash + 1);
        int colon = rest.indexOf(':');
        if (colon < 0) return new EntrypointId(ComponentId.of(qualifiedName), rest, "");
        return new EntrypointId(
                ComponentId.of(qualifiedName),
                rest.substring(0, colon),
                rest.substring(colon + 1));
    }

    @JsonValue
    public String serialize() {
        return suffix.isEmpty()
                ? component.qualifiedName() + "#" + method
                : component.qualifiedName() + "#" + method + ":" + suffix;
    }
}
```

- [ ] **Step 6: Create `FieldRef.java`**

```java
package dev.dominikbreu.spoonmcp.model.ids;

/**
 * Reference to a shared-state field on a specific component.
 * Both fields are non-null by construction — use {@link FieldBinding.CrossComponent}
 * to represent a cross-component field read; use {@link FieldBinding.Own} for reads
 * of a field on {@code this}.
 */
public record FieldRef(ComponentId owner, String fieldName) {}
```

- [ ] **Step 7: Create `FieldBinding.java`**

```java
package dev.dominikbreu.spoonmcp.model.ids;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Sealed representation of a shared-state field access on a {@code FieldAccess} record.
 *
 * <ul>
 *   <li>{@link Own} — the accessing component reads/writes its own field. No external owner.
 *   <li>{@link CrossComponent} — the accessing component reads a field owned by another
 *       injected component (detected via getter return analysis). The owner is always
 *       non-null, making it impossible to build an ownerless cross-component reference.
 * </ul>
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "bindingKind")
@JsonSubTypes({
    @JsonSubTypes.Type(value = FieldBinding.Own.class, name = "own"),
    @JsonSubTypes.Type(value = FieldBinding.CrossComponent.class, name = "cross")
})
public sealed interface FieldBinding permits FieldBinding.Own, FieldBinding.CrossComponent {

    String fieldName();

    record Own(String fieldName) implements FieldBinding {}

    record CrossComponent(FieldRef ref) implements FieldBinding {
        @Override
        public String fieldName() {
            return ref.fieldName();
        }
    }
}
```

- [ ] **Step 8: Run tests**

```bash
mvn test -pl . -Dtest=ModelIdsTest -q 2>&1 | tail -10
```
Expected: all 5 tests pass.

- [ ] **Step 9: Spotless + commit**

```bash
mvn spotless:apply -q
git add src/main/java/dev/dominikbreu/spoonmcp/model/ids/ \
        src/test/java/dev/dominikbreu/spoonmcp/model/ids/
git commit -m "feat(ids): introduce typed ComponentId, MethodRef, EntrypointId, FieldRef, FieldBinding records"
```

---

## Task 2: Migrate index classes to use `MethodRef`

> **Prerequisite: complete Tasks 3 and 4 first.** The index classes reference `edge.fromComponentId`
> (now `ComponentId` after Task 4) and `access.componentId` (now `ComponentId` after Task 3).
> Doing this task before the model changes compiles incorrectly.

**Files:**
- Modify: `src/main/java/dev/dominikbreu/spoonmcp/extractor/CallAdjacency.java`
- Modify: `src/main/java/dev/dominikbreu/spoonmcp/extractor/FieldAccessIndex.java`
- Modify: `src/main/java/dev/dominikbreu/spoonmcp/extractor/OutboundSinkIndex.java`

> These index classes use `String` map keys built as `componentId + "#" + method`.
> After this task they use `MethodRef`. The callers (`DataFlowTracer`, `RuntimeFlowInferrer`,
> `UseCaseDetector`, `WorkflowLinker`) will break at compile time — they are fixed in Task 6.

- [ ] **Step 1: Rewrite `CallAdjacency`**

```java
package dev.dominikbreu.spoonmcp.extractor;

import dev.dominikbreu.spoonmcp.model.CallEdge;
import dev.dominikbreu.spoonmcp.model.ids.ComponentId;
import dev.dominikbreu.spoonmcp.model.ids.MethodRef;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class CallAdjacency {

    private final Map<MethodRef, List<CallEdge>> index;

    public static CallAdjacency build(Collection<CallEdge> edges) {
        Map<MethodRef, List<CallEdge>> index = new HashMap<>();
        for (CallEdge edge : edges) {
            index.computeIfAbsent(
                    new MethodRef(edge.fromComponentId, edge.fromMethod),
                    k -> new ArrayList<>())
                    .add(edge);
        }
        return new CallAdjacency(index);
    }

    private CallAdjacency(Map<MethodRef, List<CallEdge>> index) {
        this.index = index;
    }

    public List<CallEdge> edges(ComponentId componentId, String method) {
        return index.getOrDefault(new MethodRef(componentId, method), List.of());
    }
}
```

- [ ] **Step 2: Rewrite `FieldAccessIndex`**

```java
package dev.dominikbreu.spoonmcp.extractor;

import dev.dominikbreu.spoonmcp.model.FieldAccess;
import dev.dominikbreu.spoonmcp.model.ids.ComponentId;
import dev.dominikbreu.spoonmcp.model.ids.MethodRef;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class FieldAccessIndex {

    private final Map<MethodRef, List<FieldAccess>> reads;
    private final Map<MethodRef, List<FieldAccess>> writes;

    public static FieldAccessIndex build(Collection<FieldAccess> accesses) {
        Map<MethodRef, List<FieldAccess>> reads = new HashMap<>();
        Map<MethodRef, List<FieldAccess>> writes = new HashMap<>();
        for (FieldAccess access : accesses) {
            var key = new MethodRef(access.componentId, access.method);
            var target = access.kind == FieldAccess.Kind.READ ? reads : writes;
            target.computeIfAbsent(key, k -> new ArrayList<>()).add(access);
        }
        return new FieldAccessIndex(reads, writes);
    }

    private FieldAccessIndex(Map<MethodRef, List<FieldAccess>> reads,
                              Map<MethodRef, List<FieldAccess>> writes) {
        this.reads = reads;
        this.writes = writes;
    }

    public List<FieldAccess> reads(ComponentId componentId, String method) {
        return reads.getOrDefault(new MethodRef(componentId, method), List.of());
    }

    public List<FieldAccess> writes(ComponentId componentId, String method) {
        return writes.getOrDefault(new MethodRef(componentId, method), List.of());
    }
}
```

- [ ] **Step 3: Rewrite `OutboundSinkIndex`** — same pattern as above. The current key is `componentId + "#" + method`. Replace with `MethodRef`:

Open `src/main/java/dev/dominikbreu/spoonmcp/extractor/OutboundSinkIndex.java`. Replace the `Map<String, ...>` field and `key(String, String)` helper with `Map<MethodRef, ...>` and lookups via `new MethodRef(site.componentId, site.method)`. The public `get(String componentId, String method)` signature becomes `get(ComponentId componentId, String method)`.

- [ ] **Step 4: Compile only (tests will still fail on callers)**

```bash
mvn compile -q 2>&1 | grep "ERROR\|error:" | head -20
```
Expected: errors in callers of `edges(String, String)` / `reads(String, String)` — tracked in Tasks 4–6. Zero errors inside the three index classes themselves.

- [ ] **Step 5: Commit**

```bash
mvn spotless:apply -q
git add src/main/java/dev/dominikbreu/spoonmcp/extractor/CallAdjacency.java \
        src/main/java/dev/dominikbreu/spoonmcp/extractor/FieldAccessIndex.java \
        src/main/java/dev/dominikbreu/spoonmcp/extractor/OutboundSinkIndex.java
git commit -m "refactor(index): use MethodRef as index key in CallAdjacency, FieldAccessIndex, OutboundSinkIndex"
```

---

## Task 3: Update `FieldAccess` model to use `FieldBinding`

**Files:**
- Modify: `src/main/java/dev/dominikbreu/spoonmcp/model/FieldAccess.java`

This task replaces the nullable pair `(fieldOwnerComponentId: String, fieldName: String)` with `FieldBinding fieldBinding`. It also changes `componentId: String` to `ComponentId`. The fix for the store-linking bug lives here — `CrossComponent` binding always has a non-null owner.

- [ ] **Step 1: Rewrite `FieldAccess.java`**

```java
package dev.dominikbreu.spoonmcp.model;

import dev.dominikbreu.spoonmcp.model.ids.ComponentId;
import dev.dominikbreu.spoonmcp.model.ids.FieldBinding;

/**
 * Read or write of a shared in-memory state field (e.g. a {@code ConcurrentHashMap}
 * cache) inside a component method. Used by {@code DataFlowTracer} to stitch together
 * pipelines whose phases communicate via shared state rather than direct method calls.
 *
 * <p>The {@link FieldBinding} on this record is either {@link FieldBinding.Own} (the
 * accessing component reads/writes its own field) or {@link FieldBinding.CrossComponent}
 * (the accessing component calls a getter on an injected dependency whose return value is
 * a shared-state field). The cross-component variant carries a {@code FieldRef} with a
 * guaranteed non-null owner, making it structurally impossible to represent a missing owner.
 */
public class FieldAccess {
    public enum Kind { READ, WRITE }

    /** Stable identifier. */
    public String id;
    /** Read or write. */
    public Kind kind;
    /** Component whose method performs the access. */
    public ComponentId componentId;
    /** Method that performs the access. */
    public String method;
    /**
     * What field is being accessed and, if cross-component, who owns it.
     * {@link FieldBinding.Own} for {@code this.field}; {@link FieldBinding.CrossComponent}
     * for a field read via a getter on another component.
     */
    public FieldBinding fieldBinding;
    /** For writes: name of the local variable or parameter whose value is stored. */
    public String sourceVarName;
    /** For writes: name of the source field on the same bean. */
    public String sourceFieldName;
    /** For keyed-write methods: name of the variable used as the map key. */
    public String keyVarName;
    /** Source location. */
    public SourceInfo source;

    public FieldAccess() {}
}
```

- [ ] **Step 2: Compile to surface all callers that break**

```bash
mvn compile -q 2>&1 | grep "error:" | grep -v "^$" | head -30
```
Expected: errors in `CallGraphExtractor` (constructs `FieldAccess` with old fields), `DataFlowTracer` (reads `fieldOwnerComponentId`/`fieldName`), `ArchitectureGraph` (reads both fields). These are fixed in Tasks 5 and 6.

- [ ] **Step 3: Commit the model change**

```bash
mvn spotless:apply -q
git add src/main/java/dev/dominikbreu/spoonmcp/model/FieldAccess.java
git commit -m "refactor(model): replace FieldAccess.fieldOwnerComponentId+fieldName with FieldBinding"
```

---

## Task 4: Migrate model POJOs to use `ComponentId` and `EntrypointId`

**Files:** All model classes listed in the File Map above that still hold `String componentId` / `String entrypointId`.

For each file, the change is the same pattern:
- `String componentId` → `ComponentId componentId`
- `List<String> componentIds` → `List<ComponentId> componentIds`
- `String entrypointId` → `EntrypointId entrypointId`
- `String id` on `Component` → `ComponentId id`

The Jackson `@JsonCreator`/`@JsonValue` on `ComponentId` and `EntrypointId` handle serialization automatically — no extra annotations needed on the POJO fields.

- [ ] **Step 1: Update `Component.java`**

```java
package dev.dominikbreu.spoonmcp.model;

import dev.dominikbreu.spoonmcp.model.ids.ComponentId;
import java.util.ArrayList;
import java.util.List;

public class Component {
    /** Stable identifier — the component's fully-qualified class name. */
    public ComponentId id;
    public ComponentType type;
    public String name;
    public String qualifiedName;
    public String module;
    public String technology;
    public List<String> stereotypes = new ArrayList<>();
    public SourceInfo source;

    public Component() {}
}
```

- [ ] **Step 2: Update `Entrypoint.java`** — change `public String componentId` to `public ComponentId componentId`. Keep all other fields unchanged.

- [ ] **Step 3: Update `CallEdge.java`** — change `public String fromComponentId` and `public String toComponentId` to `public ComponentId fromComponentId` and `public ComponentId toComponentId`. Keep all other fields.

- [ ] **Step 4: Update `DataFlowSink.java`** — change `public String componentId` and `public String fieldOwnerComponentId` to `public ComponentId componentId` and `public ComponentId fieldOwnerComponentId`. Keep `fieldName` as `String`.

Note: `fieldOwnerComponentId` on `DataFlowSink` stays as a nullable `ComponentId` (not `FieldBinding`) — `FieldBinding` is only on `FieldAccess` where the sealed type is needed for structural correctness.

- [ ] **Step 5: Update `DataFlowStep.java`** — `String componentId` → `ComponentId componentId`.

- [ ] **Step 6: Update `RuntimeFlowStep.java`** — `String componentId` → `ComponentId componentId`. Update the constructor parameter accordingly.

- [ ] **Step 7: Update `RuntimeFlow.java`** — `String entrypointId` → `EntrypointId entrypointId`.

- [ ] **Step 8: Update `DataFlowPath.java`** — `String entrypointId` → `EntrypointId entrypointId`.

- [ ] **Step 9: Update `InterfaceEntry.java`**, `OutboundSinkSite.java`, `AppEntry.java`, `Container.java`, `UseCase.java` — same `String componentId`/`List<String> componentIds` → typed equivalents.

- [ ] **Step 10: Update `ComponentIndex.java`** to use `ComponentId` as map key

```java
package dev.dominikbreu.spoonmcp.extractor;

import dev.dominikbreu.spoonmcp.model.Component;
import dev.dominikbreu.spoonmcp.model.ids.ComponentId;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public final class ComponentIndex {

    private final Map<ComponentId, Component> byId;

    public static ComponentIndex build(Collection<Component> components) {
        Map<ComponentId, Component> byId = new HashMap<>();
        for (Component c : components) {
            if (c.id != null) byId.put(c.id, c);
        }
        return new ComponentIndex(byId);
    }

    private ComponentIndex(Map<ComponentId, Component> byId) {
        this.byId = byId;
    }

    public Component get(ComponentId id) {
        return byId.get(id);
    }

    public Component getByQualifiedName(String qualifiedName) {
        return byId.get(ComponentId.of(qualifiedName));
    }
}
```

- [ ] **Step 11: Compile and note remaining errors**

```bash
mvn compile -q 2>&1 | grep "error:" | sed 's/.*\[ERROR\] //' | head -40
```
Expected: errors in all extractors and graph classes that construct these model objects with string IDs. Tracked in Tasks 5 and 6.

- [ ] **Step 12: Commit**

```bash
mvn spotless:apply -q
git add src/main/java/dev/dominikbreu/spoonmcp/model/ \
        src/main/java/dev/dominikbreu/spoonmcp/extractor/ComponentIndex.java
git commit -m "refactor(model): migrate all model POJOs to ComponentId/EntrypointId typed fields"
```

---

## Task 5: Update extractors to construct typed IDs

**Files:** All extractor files listed in the File Map.

The mechanical change in each extractor is:
- `"comp:" + type.getQualifiedName()` → `ComponentId.of(type.getQualifiedName())`
- `"ep:" + type.getQualifiedName() + "#" + method.getSimpleName() + ":scheduled"` → `new EntrypointId(ComponentId.of(type.getQualifiedName()), method.getSimpleName(), "scheduled")`
- `new FieldAccess()` construction in `CallGraphExtractor.emitCallerSideFieldReadIfGetter` changes to use `FieldBinding.CrossComponent`

- [ ] **Step 1: Fix `QuarkusExtractor.java`** — replace every `"comp:" + type.getQualifiedName()` with `ComponentId.of(type.getQualifiedName())`. Replace every `"ep:" + type.getQualifiedName() + "#" + method.getSimpleName() + ":<suffix>"` with `new EntrypointId(ComponentId.of(...), method.getSimpleName(), "<suffix>")`. The `.id` field assignments become typed. The `ep.id.serialize()` is used where the string form is still needed (e.g., for `id` fields that are still `String` in nodes like `InterfaceEntry.id`).

Run `grep -n '"comp:"\|"ep:"' src/main/java/dev/dominikbreu/spoonmcp/extractor/QuarkusExtractor.java` to find all sites before editing.

- [ ] **Step 2: Fix `JavaEEExtractor.java`** — same pattern. Run the grep first, then replace all occurrences.

- [ ] **Step 3: Fix `SpringExtractor.java`** — same pattern.

- [ ] **Step 4: Fix `GenericJavaExtractor.java`** — same pattern.

- [ ] **Step 5: Fix `EventBusExtractor.java`** — same pattern.

- [ ] **Step 6: Fix `DependencyExtractor.java`** — same pattern.

- [ ] **Step 7: Fix `objectflow/ObjectFlowIndexBuilder.java` and `ObjectFlowMethodAnalyzer.java`** — these return `List.of(new ReceiverTarget("comp:" + qualifiedName, ...))`. Change to `ComponentId.of(qualifiedName)`. Update `ReceiverTarget` if its first field is still a `String`.

Open `src/main/java/dev/dominikbreu/spoonmcp/extractor/objectflow/ReceiverTarget.java`. If the first constructor parameter is `String componentId`, change it to `ComponentId componentId` and update all construction sites.

- [ ] **Step 8: Fix `CallGraphExtractor.emitCallerSideFieldReadIfGetter`**

The method currently sets:
```java
fa.fieldOwnerComponentId = toComp.id;
fa.fieldName = fieldName;
```

Replace with:
```java
fa.fieldBinding = new FieldBinding.CrossComponent(new FieldRef(toComp.id, fieldName));
```

Also update the ID string to remove `@toComp.id#calleeMethod.getSimpleName()` and replace with the serialized form:
```java
fa.id = "field:" + fromComp.id.serialize() + "#" + fromMethod
        + "@" + toComp.id.serialize() + "#" + calleeMethod.getSimpleName()
        + ":" + fieldName + ":read:xcomp";
```

For direct field-access detection (own-field reads elsewhere in `CallGraphExtractor` or other extractors that produce `FieldAccess` with `fieldOwnerComponentId = null`): set `fa.fieldBinding = new FieldBinding.Own(fieldName)`.

- [ ] **Step 9: Compile**

```bash
mvn compile -q 2>&1 | grep "error:" | head -20
```
Expected: only remaining errors in `DataFlowTracer`, `ArchitectureGraph`, workflow classes, renderers. Zero errors in extractor files.

- [ ] **Step 10: Commit**

```bash
mvn spotless:apply -q
git add src/main/java/dev/dominikbreu/spoonmcp/extractor/
git commit -m "refactor(extractor): use ComponentId/EntrypointId/FieldBinding in all extractors"
```

---

## Task 6: Fix `DataFlowTracer` — remove split patterns and null guard

**Files:**
- Modify: `src/main/java/dev/dominikbreu/spoonmcp/extractor/DataFlowTracer.java`

This task removes the three patterns in `DataFlowTracer` that motivated the whole refactor:
1. `ep.componentId + "#" + ep.name` → `new MethodRef(ep.componentId, ep.name)`
2. `key.split("#", 2)` → no longer needed (use `MethodRef` directly)
3. `if (r.fieldOwnerComponentId != null && r.fieldName != null)` → pattern-match on `FieldBinding`

- [ ] **Step 1: Fix `collectReachableReadFieldKeys`**

Current:
```java
private Set<String> collectReachableReadFieldKeys(Entrypoint ep, ModelIndex index) {
    Set<String> keys = new LinkedHashSet<>();
    Deque<String> stack = new ArrayDeque<>();
    Set<String> seen = new HashSet<>();
    String start = ep.componentId + "#" + ep.name;
    stack.push(start);
    seen.add(start);
    int budget = 64;
    while (!stack.isEmpty() && budget-- > 0) {
        String key = stack.pop();
        String[] parts = key.split("#", 2);
        for (FieldAccess r : index.fieldAccess.reads(parts[0], parts[1])) {
            if (r.fieldOwnerComponentId != null && r.fieldName != null) {
                keys.add(r.fieldOwnerComponentId + "@" + r.fieldName);
            }
        }
        for (CallEdge edge : index.callAdj.edgesByKey(key)) {
            if (!traversalPolicy.canTraverseInline(edge)) continue;
            String next = edge.toComponentId + "#" + edge.toMethod;
            if (seen.add(next)) stack.push(next);
        }
    }
    return keys;
}
```

Replace with:
```java
private Set<FieldRef> collectReachableReadFieldKeys(Entrypoint ep, ModelIndex index) {
    Set<FieldRef> keys = new LinkedHashSet<>();
    Deque<MethodRef> stack = new ArrayDeque<>();
    Set<MethodRef> seen = new HashSet<>();
    var start = new MethodRef(ep.componentId, ep.name);
    stack.push(start);
    seen.add(start);
    int budget = 64;
    while (!stack.isEmpty() && budget-- > 0) {
        MethodRef current = stack.pop();
        for (FieldAccess r : index.fieldAccess.reads(current.component(), current.method())) {
            if (r.fieldBinding instanceof FieldBinding.CrossComponent cc) {
                keys.add(cc.ref());
            }
        }
        for (CallEdge edge : index.callAdj.edges(current.component(), current.method())) {
            if (!traversalPolicy.canTraverseInline(edge)) continue;
            var next = new MethodRef(edge.toComponentId, edge.toMethod);
            if (seen.add(next)) stack.push(next);
        }
    }
    return keys;
}
```

- [ ] **Step 2: Fix `collectReachableReadFields`** — same DFS, but collects only `fieldName`. Replace `String[]parts = key.split(...)` with typed `MethodRef`. For `FieldBinding`: collect `fieldName()` from both `Own` and `CrossComponent` variants (seeding doesn't distinguish ownership):

```java
private Set<String> collectReachableReadFields(Entrypoint ep, ModelIndex index) {
    Set<String> fields = new LinkedHashSet<>();
    Deque<MethodRef> stack = new ArrayDeque<>();
    Set<MethodRef> seen = new HashSet<>();
    var start = new MethodRef(ep.componentId, ep.name);
    stack.push(start);
    seen.add(start);
    int budget = 64;
    while (!stack.isEmpty() && budget-- > 0) {
        MethodRef current = stack.pop();
        for (FieldAccess r : index.fieldAccess.reads(current.component(), current.method())) {
            if (r.fieldBinding != null) fields.add(r.fieldBinding.fieldName());
        }
        for (CallEdge edge : index.callAdj.edges(current.component(), current.method())) {
            if (!traversalPolicy.canTraverseInline(edge)) continue;
            var next = new MethodRef(edge.toComponentId, edge.toMethod);
            if (seen.add(next)) stack.push(next);
        }
    }
    return fields;
}
```

- [ ] **Step 3: Fix `linkStoreSinksToFieldReaders`** — the key type changes from `String` to `FieldRef`:

```java
private void linkStoreSinksToFieldReaders(List<DataFlowPath> paths, ArchitectureModel model, ModelIndex index) {
    Map<EntrypointId, Set<FieldRef>> readsByEntrypoint = new HashMap<>();
    for (Entrypoint ep : model.entrypoints) {
        readsByEntrypoint.put(ep.id, collectReachableReadFieldKeys(ep, index));
    }

    Map<FieldRef, List<String>> readerPathsByKey = new HashMap<>();
    for (DataFlowPath p : paths) {
        Set<FieldRef> keys = readsByEntrypoint.get(p.entrypointId);
        if (keys == null) continue;
        for (FieldRef key : keys) {
            readerPathsByKey.computeIfAbsent(key, k -> new ArrayList<>()).add(p.id);
        }
    }

    Map<String, EntrypointId> pathIdToEpId = new HashMap<>();
    for (DataFlowPath p2 : paths) pathIdToEpId.put(p2.id, p2.entrypointId);

    for (DataFlowPath p : paths) {
        for (DataFlowSink s : p.sinks) {
            if (s.kind != DataFlowSink.Kind.STORE) continue;
            if (s.fieldOwnerComponentId == null || s.fieldName == null) continue;
            var key = new FieldRef(s.fieldOwnerComponentId, s.fieldName);
            List<String> readerIds = readerPathsByKey.get(key);
            if (readerIds == null) continue;
            for (String rid : readerIds) {
                EntrypointId readerEpId = pathIdToEpId.get(rid);
                if (!rid.equals(p.id)
                        && (readerEpId == null || !readerEpId.equals(p.entrypointId))
                        && !s.linkedPathIds.contains(rid)) {
                    s.linkedPathIds.add(rid);
                }
            }
        }
    }
}
```

- [ ] **Step 4: Fix the DFS method and other uses of `compId + "#" + method` inside `DataFlowTracer`** — scan for any remaining `String nodeKey = compId + "#" + method` patterns and replace with `MethodRef`. The DFS `onCurrentPath` set becomes `Set<MethodRef>`.

- [ ] **Step 5: Fix remaining callers in `RuntimeFlowInferrer`, `UseCaseDetector`, `WorkflowLinker`** — these also build `String key = compId + "#" + method` and call `callAdj.edgesByKey(key)`. Replace each with a `MethodRef` and the new typed `edges()` method.

For `RuntimeFlowInferrer.java` — find `String key = compId + "#" + method` (around line 110) and replace:
```java
// Before:
String key = compId + "#" + method;
for (CallEdge edge : index.callAdj.edgesByKey(key)) { ... }

// After:
var ref = new MethodRef(compId, method);
for (CallEdge edge : index.callAdj.edges(ref.component(), ref.method())) { ... }
```

For `UseCaseDetector.java` — same replacement for lines ~94 and ~187.

For `WorkflowLinker.java` — same replacement.

- [ ] **Step 6: Compile clean**

```bash
mvn compile -q 2>&1 | grep "error:" | head -10
```
Expected: zero errors.

- [ ] **Step 7: Run tests**

```bash
mvn test -q 2>&1 | tail -20
```
Fix any failures before continuing.

- [ ] **Step 8: Commit**

```bash
mvn spotless:apply -q
git add src/main/java/dev/dominikbreu/spoonmcp/extractor/DataFlowTracer.java \
        src/main/java/dev/dominikbreu/spoonmcp/extractor/RuntimeFlowInferrer.java \
        src/main/java/dev/dominikbreu/spoonmcp/extractor/UseCaseDetector.java \
        src/main/java/dev/dominikbreu/spoonmcp/workflow/
git commit -m "refactor(dataflow): remove split patterns, use MethodRef/FieldRef in DFS traversals; fixes store-linking null guard"
```

---

## Task 7: Update `ArchitectureGraph` serialization

**Files:**
- Modify: `src/main/java/dev/dominikbreu/spoonmcp/cache/ArchitectureGraph.java`

Node IDs in the graph currently use the raw `String` IDs from the model (`"comp:..."`, `"ep:..."`). After this task they use `ComponentId.serialize()` and `EntrypointId.serialize()` (which drop the `"comp:"/"ep:"` prefixes). All tests that assert on specific graph node IDs must be updated.

- [ ] **Step 1: Replace raw ID references**

Search for every occurrence of `component.id`, `entrypoint.id`, `entrypoint.componentId`, `sink.componentId`, `sink.fieldOwnerComponentId`, `flow.entrypointId`, `step.componentId` in `ArchitectureGraph.java`. Where these are used as graph node IDs or edge endpoints, call `.serialize()` on the typed ID. Where they are stored as graph vertex properties, store `.serialize()`.

Key replacements (grep first: `grep -n "\.id\|componentId\|entrypointId\|fieldOwnerComponentId" src/main/java/dev/dominikbreu/spoonmcp/cache/ArchitectureGraph.java`):

```java
// Component vertex ID
addVertex(component.id.serialize(), "Component", ...);

// Entrypoint vertex ID
addVertex(entrypoint.id.serialize(), "Entrypoint", ...);
set(vertex, "componentId", entrypoint.componentId.serialize());
addEdge(entrypoint.id.serialize(), entrypoint.componentId.serialize(), "STARTS_AT", ...);

// DataFlowSink: fieldOwnerComponentId is now ComponentId
if (sink.fieldOwnerComponentId != null) {
    set(sinkVertex, "fieldOwnerComponentId", sink.fieldOwnerComponentId.serialize());
    addEdge(sinkVertex, sink.fieldOwnerComponentId.serialize(), "AT_COMPONENT", ...);
}
```

- [ ] **Step 2: Fix the `FieldAccess`-to-graph edge** (around line 687 in the current file):

```java
// Before:
String key = access.fieldOwnerComponentId + "@" + access.fieldName;

// After: FieldBinding is now sealed — handle both variants
if (access.fieldBinding instanceof FieldBinding.CrossComponent cc) {
    String key = cc.ref().owner().serialize() + "@" + cc.ref().fieldName();
    // ... rest of edge creation
}
```

- [ ] **Step 3: Compile**

```bash
mvn compile -q 2>&1 | grep "error:" | head -10
```
Expected: zero errors.

- [ ] **Step 4: Run full test suite**

```bash
mvn test 2>&1 | tail -30
```
Expected: tests that assert on `"comp:..."` node IDs will fail. For each failing test, update the expected ID string to use the qualified name without prefix. E.g. `"comp:de.grob..."` → `"de.grob..."`.

- [ ] **Step 5: Fix all test assertions on node IDs**

Run:
```bash
grep -rn '"comp:\|"ep:' src/test/java/ --include="*.java" -l
```
For each file, replace `"comp:de.grob.gnet..."` with `"de.grob.gnet..."` and `"ep:..."` with the serialized `EntrypointId` format (`"qualifiedName#method:suffix"`).

- [ ] **Step 6: Run tests again**

```bash
mvn test 2>&1 | tail -20
```
Expected: all pass.

- [ ] **Step 7: Spotless + final commit**

```bash
mvn spotless:apply -q
git add src/main/java/dev/dominikbreu/spoonmcp/cache/ArchitectureGraph.java \
        src/test/
git commit -m "refactor(graph): use typed ID serialization in ArchitectureGraph; update test assertions"
```

---

## Task 8: Update renderers and MCP tool adapters

**Files:** Renderer and MCP tool adapter files listed in the File Map.

These files call model fields like `component.id` and `entrypoint.componentId` that are now typed. Every place that appended `.id` to get a `String` now needs `.id.serialize()`. Every place that compared `component.id.equals("comp:...")` now compares `component.id.qualifiedName().equals("...")`.

- [ ] **Step 1: Compile to find all remaining errors**

```bash
mvn compile -q 2>&1 | grep "error:" | grep "renderer\|tools" | head -20
```

- [ ] **Step 2: Fix each renderer and tool adapter** — for each error, change:
  - `component.id` (was `String`) → `component.id.serialize()` where a `String` is needed
  - `entrypoint.componentId` → `entrypoint.componentId.serialize()` where needed
  - Direct equality checks: `if (id.equals("comp:Foo"))` → `if (id.qualifiedName().equals("Foo"))`

- [ ] **Step 3: Compile clean**

```bash
mvn compile -q 2>&1 | grep "error:" | head -5
```
Expected: zero errors.

- [ ] **Step 4: Full test suite**

```bash
mvn test 2>&1 | tail -20
```
Expected: all pass.

- [ ] **Step 5: Spotless + commit**

```bash
mvn spotless:apply -q
git add src/main/java/dev/dominikbreu/spoonmcp/renderer/ \
        src/main/java/dev/dominikbreu/spoonmcp/mcp/
git commit -m "refactor(renderer/tools): update to typed ID serialization"
```

---

## Task 9: Verify pipeline fix and run final validation

After all the above tasks, the `FieldBinding.CrossComponent` path in `emitCallerSideFieldReadIfGetter` guarantees that `getLatestSnapshots() → return store` emits a `CrossComponent(FieldRef(DeviceStateDataService, "store"))` binding. `collectReachableReadFieldKeys` matches it via pattern matching, producing `FieldRef(DeviceStateDataService, "store")` in the keys set. `linkStoreSinksToFieldReaders` connects `ingest`'s STORE sink to `publishAll`'s paths.

- [ ] **Step 1: Build the final jar**

```bash
mvn package -DskipTests -q
```

- [ ] **Step 2: Run the pipeline check script**

```bash
python3 /tmp/pipeline-check.py 2>/dev/null
```
Expected output contains a chain: `ingest → [DeviceStateDataService.store] → publishAll → [snapshotInternal] → defaultStateCalculation`. If not, re-examine `emitCallerSideFieldReadIfGetter` for `publishAll → getLatestSnapshots`.

- [ ] **Step 3: Run full test suite**

```bash
mvn test 2>&1 | tail -5
```
Expected: `BUILD SUCCESS`.

- [ ] **Step 4: Update `docs/ARCHITECTURE.md`** — add a section describing the `model/ids` package and why it exists.

- [ ] **Step 5: Final commit**

```bash
git add docs/
git commit -m "docs: document typed ID model in ARCHITECTURE.md

Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>"
```

- [ ] **Step 6: Create feature branch PR**

```bash
gh pr create --title "refactor: typed ID model (ComponentId, MethodRef, EntrypointId, FieldBinding)" \
  --body "Replaces all raw-string component/entrypoint ID patterns with typed Java 21 records. Fixes store-linking null guard bug in DataFlowTracer by making cross-component field ownership structurally non-null via FieldBinding sealed interface."
```
