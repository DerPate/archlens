# Source Fact Index Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a source-derived fact index from Spoon so extractor output is grounded in explicit, testable code facts before higher-level architecture projections consume it.

**Architecture:** Add an immutable `SourceFactIndex` and a `SourceFactIndexBuilder` under `extractor/sourcefacts`. The builder normalizes Spoon facts for one module, emits OpenTelemetry phase spans, and is wired into `ArchitectureExtractor` without changing output before consumers migrate. `ObjectFlowIndexBuilder` and then `CallGraphExtractor` are migrated to consume source facts for receiver, alias, injection, and return-path evidence.

**Tech Stack:** Java 21, Maven, Spoon, OpenTelemetry, JUnit 5, AssertJ

**Spec:** `docs/superpowers/specs/2026-05-24-source-fact-index-design.md`

---

## Guard Rails

- Keep existing MCP output stable until a task explicitly migrates a consumer and updates tests.
- Do not create architecture edges from naming-only guesses. Naming hints must be represented as hints, not facts.
- Do not remove existing `ObjectFlowIndex` or `CallGraphExtractor` behavior until replacement tests cover the same source-derived cases.
- Keep new source-fact classes in `src/main/java/dev/dominikbreu/spoonmcp/extractor/sourcefacts/`.
- Keep tests in `src/test/java/dev/dominikbreu/spoonmcp/extractor/sourcefacts/`.
- Run the focused test command after each task. Run full extractor tests before final completion.

## File Structure

**Create:**

- `src/main/java/dev/dominikbreu/spoonmcp/extractor/sourcefacts/FactConfidence.java`
  - Enum for `KNOWN`, `AMBIGUOUS`, `UNKNOWN`, and `HINT`.
- `src/main/java/dev/dominikbreu/spoonmcp/extractor/sourcefacts/SourceEvidence.java`
  - Enum for evidence kinds such as injection, assignments, returns, and no-classpath unresolved facts.
- `src/main/java/dev/dominikbreu/spoonmcp/extractor/sourcefacts/SourceLocation.java`
  - Stable source filename and line metadata.
- `src/main/java/dev/dominikbreu/spoonmcp/extractor/sourcefacts/SourceType.java`
  - Type facts.
- `src/main/java/dev/dominikbreu/spoonmcp/extractor/sourcefacts/SourceMethod.java`
  - Method and constructor facts.
- `src/main/java/dev/dominikbreu/spoonmcp/extractor/sourcefacts/SourceField.java`
  - Field facts.
- `src/main/java/dev/dominikbreu/spoonmcp/extractor/sourcefacts/SourceAnnotation.java`
  - Annotation facts with resolved values.
- `src/main/java/dev/dominikbreu/spoonmcp/extractor/sourcefacts/SourceInjectionPoint.java`
  - Field, constructor, and method injection facts.
- `src/main/java/dev/dominikbreu/spoonmcp/extractor/sourcefacts/SourceInvocation.java`
  - Invocation facts with receiver, arguments, enclosing method, assignment target, and source.
- `src/main/java/dev/dominikbreu/spoonmcp/extractor/sourcefacts/SourceAssignment.java`
  - Local and field assignment facts.
- `src/main/java/dev/dominikbreu/spoonmcp/extractor/sourcefacts/SourceReturn.java`
  - Return-path facts.
- `src/main/java/dev/dominikbreu/spoonmcp/extractor/sourcefacts/SourceFactIndex.java`
  - Immutable query API.
- `src/main/java/dev/dominikbreu/spoonmcp/extractor/sourcefacts/SourceFactIndexBuilder.java`
  - Spoon-to-source-fact builder and OTel spans.
- `src/test/java/dev/dominikbreu/spoonmcp/extractor/sourcefacts/SourceFactIndexBuilderTest.java`
  - Fixture truth-table tests.
- `src/test/java/dev/dominikbreu/spoonmcp/extractor/sourcefacts/SourceFactTracingTest.java`
  - OTel span test.

**Modify in migration tasks:**

- `src/main/java/dev/dominikbreu/spoonmcp/extractor/ArchitectureExtractor.java`
  - Build source fact index beside `CtModel`.
- `src/main/java/dev/dominikbreu/spoonmcp/extractor/objectflow/ObjectFlowIndexBuilder.java`
  - Add overload that consumes `SourceFactIndex`.
- `src/main/java/dev/dominikbreu/spoonmcp/extractor/CallGraphExtractor.java`
  - Add constructor/logic that consumes source facts for receiver and return tracking.
- Existing object-flow and call-graph tests
  - Add coverage for source-fact-backed behavior while preserving existing assertions.

---

## Task 1: Fact Model Skeleton

**Files:**
- Create: `src/main/java/dev/dominikbreu/spoonmcp/extractor/sourcefacts/FactConfidence.java`
- Create: `src/main/java/dev/dominikbreu/spoonmcp/extractor/sourcefacts/SourceEvidence.java`
- Create: `src/main/java/dev/dominikbreu/spoonmcp/extractor/sourcefacts/SourceLocation.java`
- Create: `src/main/java/dev/dominikbreu/spoonmcp/extractor/sourcefacts/SourceType.java`
- Create: `src/main/java/dev/dominikbreu/spoonmcp/extractor/sourcefacts/SourceMethod.java`
- Create: `src/main/java/dev/dominikbreu/spoonmcp/extractor/sourcefacts/SourceField.java`
- Create: `src/main/java/dev/dominikbreu/spoonmcp/extractor/sourcefacts/SourceAnnotation.java`
- Create: `src/main/java/dev/dominikbreu/spoonmcp/extractor/sourcefacts/SourceInjectionPoint.java`
- Create: `src/test/java/dev/dominikbreu/spoonmcp/extractor/sourcefacts/SourceFactIndexBuilderTest.java`

- [ ] **Step 1: Write the failing skeleton compile test**

Create `SourceFactIndexBuilderTest` with:

```java
package dev.dominikbreu.spoonmcp.extractor.sourcefacts;

import static org.assertj.core.api.Assertions.assertThat;

import dev.dominikbreu.spoonmcp.extractor.ExtractorTestBase;
import org.junit.jupiter.api.Test;

class SourceFactIndexBuilderTest extends ExtractorTestBase {

    @Test
    void sourceFactTypesHaveStableIdsAndLocations() {
        SourceLocation location = new SourceLocation("Example.java", 7);
        SourceType type = new SourceType(
                "type:com.example.Example",
                "com.example.Example",
                "Example",
                "com.example",
                false,
                false,
                location);

        assertThat(type.id()).isEqualTo("type:com.example.Example");
        assertThat(type.qualifiedName()).isEqualTo("com.example.Example");
        assertThat(type.location()).isSameAs(location);
    }
}
```

- [ ] **Step 2: Run the test and confirm it fails to compile**

Run:

```bash
mvn -Dtest='dev.dominikbreu.spoonmcp.extractor.sourcefacts.SourceFactIndexBuilderTest' test
```

Expected: compilation fails because `SourceLocation` and `SourceType` do not exist.

- [ ] **Step 3: Add the fact enums and records**

Create the files with these definitions:

```java
package dev.dominikbreu.spoonmcp.extractor.sourcefacts;

public enum FactConfidence {
    KNOWN,
    AMBIGUOUS,
    UNKNOWN,
    HINT
}
```

```java
package dev.dominikbreu.spoonmcp.extractor.sourcefacts;

public enum SourceEvidence {
    DIRECT_TYPE_REFERENCE,
    FIELD_INJECTION,
    CONSTRUCTOR_INJECTION,
    METHOD_INJECTION,
    LOCAL_ASSIGNMENT,
    FIELD_ASSIGNMENT,
    METHOD_RETURNS_FIELD,
    METHOD_RETURNS_PARAMETER,
    METHOD_RETURNS_LOCAL,
    METHOD_RETURNS_INVOCATION,
    CONSTRUCTOR_CALL,
    POLYMORPHIC_IMPLEMENTATION,
    ANNOTATION_VALUE,
    CONFIG_VALUE,
    UNRESOLVED_NO_CLASSPATH,
    NAMING_HINT
}
```

```java
package dev.dominikbreu.spoonmcp.extractor.sourcefacts;

public record SourceLocation(String file, int line) {
    public static SourceLocation unknown() {
        return new SourceLocation("(unknown)", -1);
    }
}
```

```java
package dev.dominikbreu.spoonmcp.extractor.sourcefacts;

public record SourceType(
        String id,
        String qualifiedName,
        String simpleName,
        String packageName,
        boolean interfaceType,
        boolean abstractType,
        SourceLocation location) {}
```

```java
package dev.dominikbreu.spoonmcp.extractor.sourcefacts;

import java.util.List;

public record SourceMethod(
        String id,
        String typeId,
        String name,
        String signature,
        boolean constructor,
        List<String> parameterNames,
        List<String> parameterTypes,
        SourceLocation location) {}
```

```java
package dev.dominikbreu.spoonmcp.extractor.sourcefacts;

public record SourceField(
        String id,
        String typeId,
        String name,
        String fieldType,
        SourceLocation location) {}
```

```java
package dev.dominikbreu.spoonmcp.extractor.sourcefacts;

import java.util.Map;

public record SourceAnnotation(
        String ownerId,
        String qualifiedName,
        Map<String, String> values,
        SourceEvidence evidence,
        SourceLocation location) {}
```

```java
package dev.dominikbreu.spoonmcp.extractor.sourcefacts;

public record SourceInjectionPoint(
        String ownerTypeId,
        String targetType,
        String fieldName,
        String parameterName,
        SourceEvidence evidence,
        FactConfidence confidence,
        SourceLocation location) {}
```

- [ ] **Step 4: Run the test and confirm it passes**

Run:

```bash
mvn -Dtest='dev.dominikbreu.spoonmcp.extractor.sourcefacts.SourceFactIndexBuilderTest' test
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/dev/dominikbreu/spoonmcp/extractor/sourcefacts \
        src/test/java/dev/dominikbreu/spoonmcp/extractor/sourcefacts/SourceFactIndexBuilderTest.java
git commit -m "feat: add source fact model skeleton"
```

---

## Task 2: Immutable Index API

**Files:**
- Create: `src/main/java/dev/dominikbreu/spoonmcp/extractor/sourcefacts/SourceInvocation.java`
- Create: `src/main/java/dev/dominikbreu/spoonmcp/extractor/sourcefacts/SourceAssignment.java`
- Create: `src/main/java/dev/dominikbreu/spoonmcp/extractor/sourcefacts/SourceReturn.java`
- Create: `src/main/java/dev/dominikbreu/spoonmcp/extractor/sourcefacts/SourceFactIndex.java`
- Modify: `src/test/java/dev/dominikbreu/spoonmcp/extractor/sourcefacts/SourceFactIndexBuilderTest.java`

- [ ] **Step 1: Add failing index query test**

Append this test:

```java
@Test
void sourceFactIndexReturnsImmutableFactsByStableIds() {
    SourceType type = new SourceType(
            "type:example.Service",
            "example.Service",
            "Service",
            "example",
            false,
            false,
            SourceLocation.unknown());
    SourceMethod method = new SourceMethod(
            "method:example.Service#handle(java.lang.String)",
            type.id(),
            "handle",
            "handle(java.lang.String)",
            false,
            java.util.List.of("payload"),
            java.util.List.of("java.lang.String"),
            SourceLocation.unknown());
    SourceFactIndex index = new SourceFactIndex(
            java.util.List.of(type),
            java.util.List.of(method),
            java.util.List.of(),
            java.util.List.of(),
            java.util.List.of(),
            java.util.List.of(),
            java.util.List.of(),
            java.util.List.of(),
            java.util.Map.of());

    assertThat(index.type("example.Service")).isSameAs(type);
    assertThat(index.methods(type.id())).containsExactly(method);
    assertThat(index.method(method.id())).isSameAs(method);
    assertThat(index.methods("type:missing")).isEmpty();
}
```

- [ ] **Step 2: Run the test and confirm it fails to compile**

Run:

```bash
mvn -Dtest='dev.dominikbreu.spoonmcp.extractor.sourcefacts.SourceFactIndexBuilderTest' test
```

Expected: compilation fails because `SourceFactIndex`, `SourceInvocation`, `SourceAssignment`, and `SourceReturn` do not exist.

- [ ] **Step 3: Add invocation, assignment, return records**

```java
package dev.dominikbreu.spoonmcp.extractor.sourcefacts;

import java.util.List;

public record SourceInvocation(
        String id,
        String enclosingMethodId,
        String receiverExpression,
        String executableName,
        List<String> argumentExpressions,
        String assignedTo,
        SourceEvidence evidence,
        FactConfidence confidence,
        SourceLocation location) {}
```

```java
package dev.dominikbreu.spoonmcp.extractor.sourcefacts;

public record SourceAssignment(
        String id,
        String enclosingMethodId,
        String target,
        String valueExpression,
        String valueType,
        SourceEvidence evidence,
        FactConfidence confidence,
        SourceLocation location) {}
```

```java
package dev.dominikbreu.spoonmcp.extractor.sourcefacts;

public record SourceReturn(
        String id,
        String enclosingMethodId,
        String expression,
        String referencedField,
        String referencedParameter,
        SourceEvidence evidence,
        FactConfidence confidence,
        SourceLocation location) {}
```

- [ ] **Step 4: Add immutable SourceFactIndex**

Create `SourceFactIndex`:

```java
package dev.dominikbreu.spoonmcp.extractor.sourcefacts;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SourceFactIndex {
    private final Map<String, SourceType> typesByQualifiedName;
    private final Map<String, SourceMethod> methodsById;
    private final Map<String, List<SourceMethod>> methodsByTypeId;
    private final Map<String, List<SourceField>> fieldsByTypeId;
    private final Map<String, List<SourceAnnotation>> annotationsByOwnerId;
    private final Map<String, List<SourceInjectionPoint>> injectionPointsByTypeId;
    private final Map<String, List<SourceInvocation>> invocationsByMethodId;
    private final Map<String, List<SourceAssignment>> assignmentsByMethodId;
    private final Map<String, List<SourceReturn>> returnsByMethodId;
    private final Map<String, List<SourceType>> implementationsByQualifiedName;

    public SourceFactIndex(
            List<SourceType> types,
            List<SourceMethod> methods,
            List<SourceField> fields,
            List<SourceAnnotation> annotations,
            List<SourceInjectionPoint> injectionPoints,
            List<SourceInvocation> invocations,
            List<SourceAssignment> assignments,
            List<SourceReturn> returns,
            Map<String, List<SourceType>> implementationsByQualifiedName) {
        this.typesByQualifiedName = new LinkedHashMap<>();
        this.methodsById = new LinkedHashMap<>();
        this.methodsByTypeId = groupMethods(methods);
        this.fieldsByTypeId = groupBy(fields, SourceField::typeId);
        this.annotationsByOwnerId = groupBy(annotations, SourceAnnotation::ownerId);
        this.injectionPointsByTypeId = groupBy(injectionPoints, SourceInjectionPoint::ownerTypeId);
        this.invocationsByMethodId = groupBy(invocations, SourceInvocation::enclosingMethodId);
        this.assignmentsByMethodId = groupBy(assignments, SourceAssignment::enclosingMethodId);
        this.returnsByMethodId = groupBy(returns, SourceReturn::enclosingMethodId);
        for (SourceType type : types) {
            typesByQualifiedName.put(type.qualifiedName(), type);
        }
        for (SourceMethod method : methods) {
            methodsById.put(method.id(), method);
        }
        this.implementationsByQualifiedName = copyMap(implementationsByQualifiedName);
    }

    public SourceType type(String qualifiedName) {
        return typesByQualifiedName.get(qualifiedName);
    }

    public SourceMethod method(String methodId) {
        return methodsById.get(methodId);
    }

    public List<SourceMethod> methods(String typeId) {
        return methodsByTypeId.getOrDefault(typeId, List.of());
    }

    public List<SourceField> fields(String typeId) {
        return fieldsByTypeId.getOrDefault(typeId, List.of());
    }

    public List<SourceAnnotation> annotations(String ownerId) {
        return annotationsByOwnerId.getOrDefault(ownerId, List.of());
    }

    public List<SourceInjectionPoint> injectionPoints(String typeId) {
        return injectionPointsByTypeId.getOrDefault(typeId, List.of());
    }

    public List<SourceInvocation> invocations(String methodId) {
        return invocationsByMethodId.getOrDefault(methodId, List.of());
    }

    public List<SourceAssignment> assignments(String methodId) {
        return assignmentsByMethodId.getOrDefault(methodId, List.of());
    }

    public List<SourceReturn> returns(String methodId) {
        return returnsByMethodId.getOrDefault(methodId, List.of());
    }

    public List<SourceType> implementations(String qualifiedName) {
        return implementationsByQualifiedName.getOrDefault(qualifiedName, List.of());
    }

    public int typeCount() {
        return typesByQualifiedName.size();
    }

    public int methodCount() {
        return methodsById.size();
    }

    private static Map<String, List<SourceMethod>> groupMethods(List<SourceMethod> methods) {
        return groupBy(methods, SourceMethod::typeId);
    }

    private static <T> Map<String, List<T>> groupBy(List<T> values, java.util.function.Function<T, String> keyFn) {
        Map<String, List<T>> grouped = new LinkedHashMap<>();
        for (T value : values) {
            grouped.computeIfAbsent(keyFn.apply(value), ignored -> new ArrayList<>()).add(value);
        }
        return copyMap(grouped);
    }

    private static <T> Map<String, List<T>> copyMap(Map<String, List<T>> source) {
        Map<String, List<T>> copy = new LinkedHashMap<>();
        for (Map.Entry<String, List<T>> entry : source.entrySet()) {
            copy.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        return Map.copyOf(copy);
    }
}
```

- [ ] **Step 5: Run test and confirm it passes**

Run:

```bash
mvn -Dtest='dev.dominikbreu.spoonmcp.extractor.sourcefacts.SourceFactIndexBuilderTest' test
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/dev/dominikbreu/spoonmcp/extractor/sourcefacts \
        src/test/java/dev/dominikbreu/spoonmcp/extractor/sourcefacts/SourceFactIndexBuilderTest.java
git commit -m "feat: add immutable source fact index"
```

---

## Task 3: Build Types, Members, Annotations, And Locations

**Files:**
- Create: `src/main/java/dev/dominikbreu/spoonmcp/extractor/sourcefacts/SourceFactIndexBuilder.java`
- Modify: `src/test/java/dev/dominikbreu/spoonmcp/extractor/sourcefacts/SourceFactIndexBuilderTest.java`

- [ ] **Step 1: Add failing fixture truth-table test**

Append:

```java
@Test
void buildsTypesMembersAnnotationsAndLocationsFromQuarkusSample() {
    SourceFactIndex index = new SourceFactIndexBuilder().build(scan("quarkus-sample"), "quarkus-sample", 1);

    SourceType orderResource = index.type("com.example.api.OrderResource");
    assertThat(orderResource).isNotNull();
    assertThat(orderResource.simpleName()).isEqualTo("OrderResource");
    assertThat(orderResource.location().file()).endsWith("OrderResource.java");
    assertThat(orderResource.location().line()).isGreaterThan(0);

    assertThat(index.methods(orderResource.id()))
            .extracting(SourceMethod::name)
            .contains("get");
    assertThat(index.fields(orderResource.id()))
            .extracting(SourceField::name)
            .contains("orderService");
    assertThat(index.annotations(orderResource.id()))
            .extracting(SourceAnnotation::qualifiedName)
            .anyMatch(name -> name.endsWith(".Path") || name.equals("Path"));
}
```

- [ ] **Step 2: Run test and confirm it fails**

Run:

```bash
mvn -Dtest='dev.dominikbreu.spoonmcp.extractor.sourcefacts.SourceFactIndexBuilderTest#buildsTypesMembersAnnotationsAndLocationsFromQuarkusSample' test
```

Expected: compilation fails because `SourceFactIndexBuilder` does not exist.

- [ ] **Step 3: Implement builder for types, fields, methods, annotations**

Create `SourceFactIndexBuilder` with methods that:

- iterate `ctModel.getAllTypes()`;
- create type IDs as `type:` plus qualified name;
- create method IDs as `method:` plus type qualified name, `#`, and Spoon signature;
- create field IDs as `field:` plus type qualified name and field name;
- read source location from `CtElement.getPosition()`;
- extract annotation qualified name and simple literal/string values.

Use this shape:

```java
package dev.dominikbreu.spoonmcp.extractor.sourcefacts;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtExpression;
import spoon.reflect.declaration.CtAnnotation;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtField;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtParameter;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtTypeReference;

public class SourceFactIndexBuilder {
    private static Tracer tracer() {
        return GlobalOpenTelemetry.getTracer("dev.dominikbreu.spoonmcp");
    }

    public SourceFactIndex build(CtModel ctModel, String moduleName, int sourceRootCount) {
        Span span = tracer().spanBuilder("sourcefacts.build")
                .setAttribute("module", moduleName)
                .setAttribute("source-root-count", sourceRootCount)
                .startSpan();
        try (Scope scope = span.makeCurrent()) {
            List<SourceType> types = new ArrayList<>();
            List<SourceMethod> methods = new ArrayList<>();
            List<SourceField> fields = new ArrayList<>();
            List<SourceAnnotation> annotations = new ArrayList<>();

            buildMembers(ctModel, types, methods, fields, annotations);

            SourceFactIndex index = new SourceFactIndex(
                    types,
                    methods,
                    fields,
                    annotations,
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    Map.of());
            span.setAttribute("type-count", (long) types.size());
            span.setAttribute("method-count", (long) methods.size());
            span.setAttribute("field-count", (long) fields.size());
            return index;
        } finally {
            span.end();
        }
    }

    private void buildMembers(
            CtModel ctModel,
            List<SourceType> types,
            List<SourceMethod> methods,
            List<SourceField> fields,
            List<SourceAnnotation> annotations) {
        Span span = tracer().spanBuilder("sourcefacts.members").startSpan();
        try (Scope scope = span.makeCurrent()) {
            for (CtType<?> type : ctModel.getAllTypes()) {
                String typeId = typeId(type.getQualifiedName());
                SourceType sourceType = new SourceType(
                        typeId,
                        type.getQualifiedName(),
                        type.getSimpleName(),
                        packageName(type.getQualifiedName()),
                        type.isInterface(),
                        type.hasModifier(spoon.reflect.declaration.ModifierKind.ABSTRACT),
                        location(type));
                types.add(sourceType);
                annotations.addAll(annotations(typeId, type));

                for (CtField<?> field : type.getFields()) {
                    String fieldType = field.getType() == null ? null : field.getType().getQualifiedName();
                    String fieldId = "field:" + type.getQualifiedName() + "#" + field.getSimpleName();
                    fields.add(new SourceField(fieldId, typeId, field.getSimpleName(), fieldType, location(field)));
                    annotations.addAll(annotations(fieldId, field));
                }

                for (CtMethod<?> method : type.getMethods()) {
                    methods.add(methodFact(type, method, false));
                    annotations.addAll(annotations(methodId(type.getQualifiedName(), method.getSignature()), method));
                }
            }
            span.setAttribute("types", (long) types.size());
            span.setAttribute("methods", (long) methods.size());
            span.setAttribute("fields", (long) fields.size());
            span.setAttribute("annotations", (long) annotations.size());
        } finally {
            span.end();
        }
    }

    private SourceMethod methodFact(CtType<?> owner, CtMethod<?> method, boolean constructor) {
        List<String> parameterNames = method.getParameters().stream().map(CtParameter::getSimpleName).toList();
        List<String> parameterTypes = method.getParameters().stream()
                .map(CtParameter::getType)
                .map(type -> type == null ? null : type.getQualifiedName())
                .toList();
        return new SourceMethod(
                methodId(owner.getQualifiedName(), method.getSignature()),
                typeId(owner.getQualifiedName()),
                method.getSimpleName(),
                method.getSignature(),
                constructor,
                parameterNames,
                parameterTypes,
                location(method));
    }

    private List<SourceAnnotation> annotations(String ownerId, CtElement element) {
        List<SourceAnnotation> result = new ArrayList<>();
        for (CtAnnotation<?> annotation : element.getAnnotations()) {
            String qn = annotation.getAnnotationType() == null
                    ? annotation.getActualAnnotation().annotationType().getName()
                    : annotation.getAnnotationType().getQualifiedName();
            result.add(new SourceAnnotation(
                    ownerId,
                    qn,
                    annotationValues(annotation),
                    SourceEvidence.ANNOTATION_VALUE,
                    location(annotation)));
        }
        return result;
    }

    private Map<String, String> annotationValues(CtAnnotation<?> annotation) {
        Map<String, String> values = new LinkedHashMap<>();
        for (Map.Entry<String, CtExpression> entry : annotation.getValues().entrySet()) {
            values.put(entry.getKey(), entry.getValue() == null ? null : entry.getValue().toString());
        }
        return values;
    }

    static String typeId(String qualifiedName) {
        return "type:" + qualifiedName;
    }

    static String methodId(String qualifiedTypeName, String signature) {
        return "method:" + qualifiedTypeName + "#" + signature;
    }

    private static String packageName(String qualifiedName) {
        int dot = qualifiedName.lastIndexOf('.');
        return dot < 0 ? "" : qualifiedName.substring(0, dot);
    }

    static SourceLocation location(CtElement element) {
        if (element == null || element.getPosition() == null || !element.getPosition().isValidPosition()) {
            return SourceLocation.unknown();
        }
        return new SourceLocation(element.getPosition().getFile().getPath(), element.getPosition().getLine());
    }
}
```

- [ ] **Step 4: Run the source-facts tests**

Run:

```bash
mvn -Dtest='dev.dominikbreu.spoonmcp.extractor.sourcefacts.SourceFactIndexBuilderTest' test
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/dev/dominikbreu/spoonmcp/extractor/sourcefacts \
        src/test/java/dev/dominikbreu/spoonmcp/extractor/sourcefacts/SourceFactIndexBuilderTest.java
git commit -m "feat: build source type member and annotation facts"
```

---

## Task 4: Inheritance And Implementation Facts

**Files:**
- Modify: `src/main/java/dev/dominikbreu/spoonmcp/extractor/sourcefacts/SourceFactIndexBuilder.java`
- Modify: `src/test/java/dev/dominikbreu/spoonmcp/extractor/sourcefacts/SourceFactIndexBuilderTest.java`

- [ ] **Step 1: Add failing implementation truth-table test**

Append:

```java
@Test
void indexesImplementationsFromGenericObjectFlow() {
    SourceFactIndex index = new SourceFactIndexBuilder().build(scan("generic-object-flow"), "generic-object-flow", 1);

    assertThat(index.implementations("com.example.objectflow.Player"))
            .extracting(SourceType::qualifiedName)
            .contains("com.example.objectflow.RandomPlayer", "com.example.objectflow.SimplePlayer");
    assertThat(index.implementations("com.example.objectflow.Move"))
            .extracting(SourceType::qualifiedName)
            .contains("com.example.objectflow.Rock", "com.example.objectflow.Paper");
}
```

- [ ] **Step 2: Run test and confirm it fails**

Run:

```bash
mvn -Dtest='dev.dominikbreu.spoonmcp.extractor.sourcefacts.SourceFactIndexBuilderTest#indexesImplementationsFromGenericObjectFlow' test
```

Expected: assertion fails because implementations are empty.

- [ ] **Step 3: Build implementations map**

In `SourceFactIndexBuilder.build`, create `Map<String, List<SourceType>> implementations = buildImplementations(ctModel, types);` and pass it to `SourceFactIndex`.

Add:

```java
private Map<String, List<SourceType>> buildImplementations(CtModel ctModel, List<SourceType> sourceTypes) {
    Span span = tracer().spanBuilder("sourcefacts.inheritance").startSpan();
    try (Scope scope = span.makeCurrent()) {
        Map<String, SourceType> byQualifiedName = new LinkedHashMap<>();
        for (SourceType sourceType : sourceTypes) {
            byQualifiedName.put(sourceType.qualifiedName(), sourceType);
        }
        Map<String, List<SourceType>> implementations = new LinkedHashMap<>();
        for (CtType<?> type : ctModel.getAllTypes()) {
            SourceType concrete = byQualifiedName.get(type.getQualifiedName());
            if (concrete == null || concrete.interfaceType() || concrete.abstractType()) continue;
            for (CtTypeReference<?> superType : type.getSuperInterfaces()) {
                SourceType superFact = byQualifiedName.get(superType.getQualifiedName());
                if (superFact != null) {
                    implementations.computeIfAbsent(superFact.qualifiedName(), ignored -> new ArrayList<>()).add(concrete);
                }
            }
            CtTypeReference<?> superclass = type.getSuperclass();
            while (superclass != null) {
                SourceType superFact = byQualifiedName.get(superclass.getQualifiedName());
                if (superFact != null) {
                    implementations.computeIfAbsent(superFact.qualifiedName(), ignored -> new ArrayList<>()).add(concrete);
                }
                CtType<?> decl = superclass.getTypeDeclaration();
                superclass = decl == null ? null : decl.getSuperclass();
            }
        }
        span.setAttribute("implementation-groups", (long) implementations.size());
        span.setAttribute("implementation-links", implementations.values().stream().mapToLong(List::size).sum());
        return implementations;
    } finally {
        span.end();
    }
}
```

- [ ] **Step 4: Run tests**

Run:

```bash
mvn -Dtest='dev.dominikbreu.spoonmcp.extractor.sourcefacts.SourceFactIndexBuilderTest' test
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/dev/dominikbreu/spoonmcp/extractor/sourcefacts/SourceFactIndexBuilder.java \
        src/test/java/dev/dominikbreu/spoonmcp/extractor/sourcefacts/SourceFactIndexBuilderTest.java
git commit -m "feat: index source inheritance implementations"
```

---

## Task 5: Invocations, Assignments, Returns, And Injection Points

**Files:**
- Modify: `src/main/java/dev/dominikbreu/spoonmcp/extractor/sourcefacts/SourceFactIndexBuilder.java`
- Modify: `src/test/java/dev/dominikbreu/spoonmcp/extractor/sourcefacts/SourceFactIndexBuilderTest.java`

- [ ] **Step 1: Add failing truth-table test for code-flow facts**

Append:

```java
@Test
void indexesInvocationsAssignmentsReturnsAndInjectionFacts() {
    SourceFactIndex index = new SourceFactIndexBuilder().build(scan("generic-object-flow"), "generic-object-flow", 1);
    SourceType mainApp = index.type("com.example.objectflow.MainApp");
    SourceMethod play = index.methods(mainApp.id()).stream()
            .filter(method -> method.name().equals("play"))
            .findFirst()
            .orElseThrow();

    assertThat(index.invocations(play.id()))
            .extracting(SourceInvocation::executableName)
            .contains("run", "printStats");
    assertThat(index.assignments(play.id()))
            .anySatisfy(assignment -> {
                assertThat(assignment.target()).contains("localGame");
                assertThat(assignment.evidence()).isIn(SourceEvidence.LOCAL_ASSIGNMENT, SourceEvidence.CONSTRUCTOR_CALL);
            });

    SourceType provider = index.type("com.example.objectflow.StateStoreProvider");
    SourceMethod store = index.methods(provider.id()).stream()
            .filter(method -> method.name().equals("store"))
            .findFirst()
            .orElseThrow();
    assertThat(index.returns(store.id()))
            .anySatisfy(ret -> {
                assertThat(ret.referencedField()).isEqualTo("store");
                assertThat(ret.evidence()).isEqualTo(SourceEvidence.METHOD_RETURNS_FIELD);
            });
}
```

- [ ] **Step 2: Add failing truth-table test for injection**

Append:

```java
@Test
void indexesFieldInjectionFromQuarkusSample() {
    SourceFactIndex index = new SourceFactIndexBuilder().build(scan("quarkus-sample"), "quarkus-sample", 1);
    SourceType orderResource = index.type("com.example.api.OrderResource");

    assertThat(index.injectionPoints(orderResource.id()))
            .anySatisfy(injection -> {
                assertThat(injection.fieldName()).isEqualTo("orderService");
                assertThat(injection.targetType()).isEqualTo("com.example.service.OrderService");
                assertThat(injection.evidence()).isEqualTo(SourceEvidence.FIELD_INJECTION);
                assertThat(injection.confidence()).isEqualTo(FactConfidence.KNOWN);
            });
}
```

- [ ] **Step 3: Run tests and confirm they fail**

Run:

```bash
mvn -Dtest='dev.dominikbreu.spoonmcp.extractor.sourcefacts.SourceFactIndexBuilderTest#indexesInvocationsAssignmentsReturnsAndInjectionFacts,dev.dominikbreu.spoonmcp.extractor.sourcefacts.SourceFactIndexBuilderTest#indexesFieldInjectionFromQuarkusSample' test
```

Expected: assertions fail because invocation, assignment, return, and injection lists are empty.

- [ ] **Step 4: Add code-flow collection**

In `SourceFactIndexBuilder`, collect each method body with a single `getElements(new TypeFilter<>(CtElement.class))` walk. For each method:

- `CtInvocation`: record `SourceInvocation`;
- `CtAssignment`: record `SourceAssignment`;
- `CtLocalVariable` with default expression: record `SourceAssignment`;
- `CtReturn`: record `SourceReturn`, classifying field reads as `METHOD_RETURNS_FIELD`;
- fields annotated with `Inject`, `Autowired`, or `Resource`: record `SourceInjectionPoint`.

Use helper methods:

```java
private boolean isInjectionAnnotation(SourceAnnotation annotation) {
    String qn = annotation.qualifiedName();
    return qn.endsWith(".Inject")
            || qn.equals("Inject")
            || qn.endsWith(".Autowired")
            || qn.endsWith(".Resource")
            || qn.equals("Resource");
}
```

```java
private String expressionText(Object expression) {
    return expression == null ? null : expression.toString();
}
```

Keep these helpers local to `SourceFactIndexBuilder` and keep all broad Spoon traversal in this builder.

- [ ] **Step 5: Run source-fact tests**

Run:

```bash
mvn -Dtest='dev.dominikbreu.spoonmcp.extractor.sourcefacts.SourceFactIndexBuilderTest' test
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/dev/dominikbreu/spoonmcp/extractor/sourcefacts/SourceFactIndexBuilder.java \
        src/test/java/dev/dominikbreu/spoonmcp/extractor/sourcefacts/SourceFactIndexBuilderTest.java
git commit -m "feat: index source invocation assignment return and injection facts"
```

---

## Task 6: OpenTelemetry Source-Fact Spans

**Files:**
- Modify: `src/main/java/dev/dominikbreu/spoonmcp/extractor/sourcefacts/SourceFactIndexBuilder.java`
- Create: `src/test/java/dev/dominikbreu/spoonmcp/extractor/sourcefacts/SourceFactTracingTest.java`

- [ ] **Step 1: Add failing tracing test**

Create `SourceFactTracingTest`:

```java
package dev.dominikbreu.spoonmcp.extractor.sourcefacts;

import static org.assertj.core.api.Assertions.assertThat;

import dev.dominikbreu.spoonmcp.extractor.ExtractorTestBase;
import dev.dominikbreu.spoonmcp.tracing.StdoutSpanExporter;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class SourceFactTracingTest extends ExtractorTestBase {

    @AfterEach
    void resetGlobalTracing() {
        GlobalOpenTelemetry.resetForTest();
    }

    @Test
    void emitsSourceFactPhaseSpansAndCounts() {
        PrintStream originalOut = System.out;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        System.setOut(new PrintStream(captured));
        try {
            GlobalOpenTelemetry.resetForTest();
            SdkTracerProvider provider = SdkTracerProvider.builder()
                    .addSpanProcessor(SimpleSpanProcessor.create(new StdoutSpanExporter()))
                    .build();
            GlobalOpenTelemetry.set(OpenTelemetrySdk.builder().setTracerProvider(provider).build());

            new SourceFactIndexBuilder().build(scan("quarkus-sample"), "quarkus-sample", 1);

            provider.forceFlush();
        } finally {
            System.setOut(originalOut);
        }

        assertThat(captured.toString())
                .contains("sourcefacts.build")
                .contains("sourcefacts.members")
                .contains("sourcefacts.inheritance")
                .contains("sourcefacts.invocations")
                .contains("sourcefacts.assignments")
                .contains("sourcefacts.returns")
                .contains("sourcefacts.injection")
                .contains("type-count=")
                .contains("method-count=")
                .contains("field-count=")
                .contains("invocation-count=")
                .contains("assignment-count=")
                .contains("return-fact-count=")
                .contains("injection-point-count=");
    }
}
```

- [ ] **Step 2: Run tracing test and confirm it fails**

Run:

```bash
mvn -Dtest='dev.dominikbreu.spoonmcp.extractor.sourcefacts.SourceFactTracingTest' test
```

Expected: assertion fails until all required span names and attributes are emitted.

- [ ] **Step 3: Add required spans and counts**

In `SourceFactIndexBuilder`:

- ensure top span `sourcefacts.build` has attributes `module`, `source-root-count`, `type-count`, `method-count`, `field-count`, `invocation-count`, `assignment-count`, `return-fact-count`, `injection-point-count`, `unresolved-receiver-count`, and `ambiguous-receiver-count`;
- ensure child spans exist for `sourcefacts.members`, `sourcefacts.inheritance`, `sourcefacts.invocations`, `sourcefacts.assignments`, `sourcefacts.returns`, and `sourcefacts.injection`;
- use zero counts for unresolved and ambiguous receivers until receiver candidate facts are added.

- [ ] **Step 4: Run tracing and source-fact tests**

Run:

```bash
mvn -Dtest='dev.dominikbreu.spoonmcp.extractor.sourcefacts.*Test' test
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/dev/dominikbreu/spoonmcp/extractor/sourcefacts/SourceFactIndexBuilder.java \
        src/test/java/dev/dominikbreu/spoonmcp/extractor/sourcefacts/SourceFactTracingTest.java
git commit -m "feat: trace source fact indexing phases"
```

---

## Task 7: Passive ArchitectureExtractor Wiring

**Files:**
- Modify: `src/main/java/dev/dominikbreu/spoonmcp/extractor/ArchitectureExtractor.java`
- Modify: `src/test/java/dev/dominikbreu/spoonmcp/extractor/ArchitectureExtractorTracingTest.java`

- [ ] **Step 1: Add failing tracing assertion**

In `ArchitectureExtractorTracingTest`, extend the existing tracing assertion to require:

```java
assertThat(captured.toString())
        .contains("sourcefacts.build")
        .contains("sourcefacts.members")
        .contains("sourcefacts.inheritance");
```

- [ ] **Step 2: Run tracing test and confirm it fails**

Run:

```bash
mvn -Dtest='dev.dominikbreu.spoonmcp.extractor.ArchitectureExtractorTracingTest' test
```

Expected: assertion fails because `ArchitectureExtractor` does not build source facts.

- [ ] **Step 3: Build SourceFactIndex passively**

In `ArchitectureExtractor`, import:

```java
import dev.dominikbreu.spoonmcp.extractor.sourcefacts.SourceFactIndex;
import dev.dominikbreu.spoonmcp.extractor.sourcefacts.SourceFactIndexBuilder;
```

Add field:

```java
private final SourceFactIndexBuilder sourceFactIndexBuilder = new SourceFactIndexBuilder();
```

In the pass-2 loop, after `CtModel ctModel` is available and before `ObjectFlowIndex objectFlowIndex`, add:

```java
SourceFactIndex sourceFacts =
        sourceFactIndexBuilder.build(ctModel, work.module().name(), work.module().sourceRoots().size());
```

Keep using the existing object-flow builder for this task:

```java
ObjectFlowIndex objectFlowIndex = new ObjectFlowIndexBuilder().build(ctModel, model);
```

Use the `sourceFacts` local immediately so the passive build is visible to the compiler without changing output:

```java
sourceFacts.typeCount();
```

immediately after construction.

- [ ] **Step 4: Run architecture tracing and extractor tests**

Run:

```bash
mvn -Dtest='dev.dominikbreu.spoonmcp.extractor.ArchitectureExtractorTracingTest,dev.dominikbreu.spoonmcp.extractor.ArchitectureExtractorTest' test
```

Expected: `BUILD SUCCESS`; existing architecture output remains unchanged.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/dev/dominikbreu/spoonmcp/extractor/ArchitectureExtractor.java \
        src/test/java/dev/dominikbreu/spoonmcp/extractor/ArchitectureExtractorTracingTest.java
git commit -m "feat: build source facts during extraction"
```

---

## Task 8: ObjectFlowIndexBuilder Source-Fact Overload

**Files:**
- Modify: `src/main/java/dev/dominikbreu/spoonmcp/extractor/objectflow/ObjectFlowIndexBuilder.java`
- Modify: `src/test/java/dev/dominikbreu/spoonmcp/extractor/objectflow/ObjectFlowIndexBuilderTest.java`
- Modify: `src/main/java/dev/dominikbreu/spoonmcp/extractor/ArchitectureExtractor.java`

- [ ] **Step 1: Add failing parity test**

In `ObjectFlowIndexBuilderTest`, add:

```java
@Test
void sourceFactBackedBuilderPreservesReceiverResolution() {
    ArchitectureModel architecture =
            new ArchitectureExtractor().extract(List.of(projectPath("generic-object-flow")));
    SourceFactIndex facts = new SourceFactIndexBuilder().build(ctModel, "generic-object-flow", 1);

    ObjectFlowIndex factBacked = new ObjectFlowIndexBuilder().build(ctModel, architecture, facts);

    assertThat(factBacked.resolveReceiver(invocation("provider.store().cache().put")))
            .extracting(ReceiverTarget::componentId)
            .contains("comp:com.example.objectflow.StateStore");
    assertThat(factBacked.expandDeclaredType("com.example.objectflow.Player", "nextMove"))
            .extracting(ReceiverTarget::componentId)
            .contains("comp:com.example.objectflow.RandomPlayer", "comp:com.example.objectflow.SimplePlayer");
}
```

Add imports:

```java
import dev.dominikbreu.spoonmcp.extractor.sourcefacts.SourceFactIndex;
import dev.dominikbreu.spoonmcp.extractor.sourcefacts.SourceFactIndexBuilder;
```

- [ ] **Step 2: Run test and confirm it fails to compile**

Run:

```bash
mvn -Dtest='dev.dominikbreu.spoonmcp.extractor.objectflow.ObjectFlowIndexBuilderTest#sourceFactBackedBuilderPreservesReceiverResolution' test
```

Expected: compilation fails because the overload does not exist.

- [ ] **Step 3: Add overload and reuse source facts for type/implementation seeds**

Add this overload:

```java
public ObjectFlowIndex build(CtModel ctModel, ArchitectureModel architecture, SourceFactIndex sourceFacts) {
    return build(ctModel, architecture);
}
```

Then replace the internals incrementally:

- build `types` from `sourceFacts.type(...)` data for architecture components;
- build `implementations` from `sourceFacts.implementations(...)`;
- keep existing receiver-target resolution for invocation identities in this task, because `ObjectFlowIndex` still keys receiver targets by `CtInvocation` identity.

This task is complete only when `sourceFacts` supplies the type and implementation maps used by `ObjectFlowIndexBuilder`.

- [ ] **Step 4: Wire ArchitectureExtractor to the overload**

Replace:

```java
ObjectFlowIndex objectFlowIndex = new ObjectFlowIndexBuilder().build(ctModel, model);
```

with:

```java
ObjectFlowIndex objectFlowIndex = new ObjectFlowIndexBuilder().build(ctModel, model, sourceFacts);
```

- [ ] **Step 5: Run object-flow and architecture tests**

Run:

```bash
mvn -Dtest='dev.dominikbreu.spoonmcp.extractor.objectflow.ObjectFlowIndexBuilderTest,dev.dominikbreu.spoonmcp.extractor.ArchitectureExtractorTest' test
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/dev/dominikbreu/spoonmcp/extractor/objectflow/ObjectFlowIndexBuilder.java \
        src/main/java/dev/dominikbreu/spoonmcp/extractor/ArchitectureExtractor.java \
        src/test/java/dev/dominikbreu/spoonmcp/extractor/objectflow/ObjectFlowIndexBuilderTest.java
git commit -m "feat: seed object flow from source facts"
```

---

## Task 9: CallGraphExtractor Source-Fact Receiver And Return Support

**Files:**
- Modify: `src/main/java/dev/dominikbreu/spoonmcp/extractor/CallGraphExtractor.java`
- Modify: `src/main/java/dev/dominikbreu/spoonmcp/extractor/ArchitectureExtractor.java`
- Modify: `src/test/java/dev/dominikbreu/spoonmcp/extractor/CallGraphExtractorTest.java`

- [ ] **Step 1: Add failing source-fact constructor parity test**

In `CallGraphExtractorTest`, add:

```java
@Test
void sourceFactBackedCallGraphPreservesFieldInjectionEdges() {
    CtModel ctModel = scan("quarkus-sample");
    ArchitectureModel sourceModel = emptyModel(QUARKUS_APP_ID);
    new QuarkusExtractor().extract(ctModel.getAllTypes(), sourceModel, QUARKUS_APP_ID);
    new DependencyExtractor().extract(ctModel, sourceModel);

    SourceFactIndex sourceFacts = new SourceFactIndexBuilder().build(ctModel, "quarkus-sample", 1);
    new CallGraphExtractor(ObjectFlowIndex.empty(), sourceFacts).extract(ctModel, sourceModel);

    assertThat(sourceModel.callEdges)
            .anySatisfy(edge -> {
                assertThat(edge.fromComponentId).isEqualTo("comp:com.example.api.OrderResource");
                assertThat(edge.fromMethod).isEqualTo("get");
                assertThat(edge.toComponentId).isEqualTo("comp:com.example.service.OrderService");
                assertThat(edge.toMethod).isEqualTo("find");
            });
}
```

Add imports:

```java
import dev.dominikbreu.spoonmcp.extractor.objectflow.ObjectFlowIndex;
import dev.dominikbreu.spoonmcp.extractor.sourcefacts.SourceFactIndex;
import dev.dominikbreu.spoonmcp.extractor.sourcefacts.SourceFactIndexBuilder;
```

- [ ] **Step 2: Run test and confirm it fails to compile**

Run:

```bash
mvn -Dtest='dev.dominikbreu.spoonmcp.extractor.CallGraphExtractorTest#sourceFactBackedCallGraphPreservesFieldInjectionEdges' test
```

Expected: compilation fails because the constructor does not exist.

- [ ] **Step 3: Add constructor and source-fact field map support**

Add field:

```java
private final dev.dominikbreu.spoonmcp.extractor.sourcefacts.SourceFactIndex sourceFacts;
```

Add constructors:

```java
public CallGraphExtractor(ObjectFlowIndex objectFlowIndex, SourceFactIndex sourceFacts) {
    this.objectFlowIndex = objectFlowIndex == null ? ObjectFlowIndex.empty() : objectFlowIndex;
    this.sourceFacts = sourceFacts;
}
```

Update existing constructors to pass `null`.

Use `sourceFacts.injectionPoints(typeId)` to seed the field-to-component map before falling back to existing Spoon-based field map logic. Keep old logic as fallback until tests prove full parity.

- [ ] **Step 4: Wire ArchitectureExtractor**

Replace:

```java
new CallGraphExtractor(objectFlowIndex).extract(ctModel, model);
```

with:

```java
new CallGraphExtractor(objectFlowIndex, sourceFacts).extract(ctModel, model);
```

- [ ] **Step 5: Run call-graph and source-fact tests**

Run:

```bash
mvn -Dtest='dev.dominikbreu.spoonmcp.extractor.CallGraphExtractorTest,dev.dominikbreu.spoonmcp.extractor.sourcefacts.*Test' test
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/dev/dominikbreu/spoonmcp/extractor/CallGraphExtractor.java \
        src/main/java/dev/dominikbreu/spoonmcp/extractor/ArchitectureExtractor.java \
        src/test/java/dev/dominikbreu/spoonmcp/extractor/CallGraphExtractorTest.java
git commit -m "feat: seed call graph extraction from source facts"
```

---

## Task 10: Final Regression And Documentation Check

**Files:**
- Read: `docs/TOOLS.md`
- Read: `docs/ARCHITECTURE.md`
- Read: `llms.txt`
- Modify only if MCP-visible tool behavior or package responsibilities changed.

- [ ] **Step 1: Run focused extractor suite**

Run:

```bash
mvn -Dtest='dev.dominikbreu.spoonmcp.extractor.*Test,dev.dominikbreu.spoonmcp.extractor.objectflow.*Test,dev.dominikbreu.spoonmcp.extractor.sourcefacts.*Test' test
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 2: Run full suite**

Run:

```bash
mvn test
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 3: Check docs impact**

Read `docs/ARCHITECTURE.md`, `docs/TOOLS.md`, and `llms.txt`.

If no MCP tool output changed and only internal extractor foundations changed, add no docs changes.

If package responsibilities changed in a user-visible way, update `docs/ARCHITECTURE.md` with one sentence under extractor responsibilities:

```markdown
- `extractor/sourcefacts/` normalizes Spoon-derived source facts into reusable indexes for object flow and call graph extraction.
```

- [ ] **Step 4: Commit docs if changed**

If docs changed:

```bash
git add docs/ARCHITECTURE.md docs/TOOLS.md llms.txt
git commit -m "docs: describe source fact extractor foundation"
```

If docs did not change, record in the final response that no MCP-visible documentation updates were needed.

- [ ] **Step 5: Final status**

Run:

```bash
git status --short
```

Expected: clean worktree, or only intentionally untracked local/generated files outside the task scope.
