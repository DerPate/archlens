# Architecture Question OKF Compiler Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an explicit MCP compiler that turns a reviewed `answer_architecture_question` result into one safe, deterministic, evidence-bearing OKF v0.1 investigation concept.

**Architecture:** Extend the question result with a normalized semantic `request`, then pass that exact structured envelope into a separate compiler tool. Keep result parsing, semantic identity, path containment, rendering, validation, and staged bundle writes in focused `dev.dominikbreu.archlens.okf` units; the MCP adapter may query `GraphQuery` only for indexed application roots.

**Tech Stack:** Java 25, Maven, MCP Java SDK 2.0.0, `GraphQuery`, Jackson 3.2.1, SnakeYAML 2.6, JUnit 6.1.2, AssertJ 3.27.7.

## Global Constraints

- `answer_architecture_question` never writes; compiling always requires a second MCP call.
- The compiler receives the exact structured result and keeps no internal answer/session memory.
- Accept `resolved`, `partial`, and `ambiguous`; reject only `unsupported` and `needs-clarification` by status.
- Version 1 writes one self-contained investigation concept; shared/compound concepts are out of scope.
- `bundlePath` defaults to `docs/agent-wiki`; `bundlePath` and `templatePath` remain beneath the selected indexed project root.
- `projectPath` is required for multiple distinct application roots and is recorded in the concept.
- Built-in rendering is deterministic; a custom template is allowed only through project-local `templatePath`.
- Refresh fully replaces a generated concept. Existing generated concepts require `allowOverwrite: true`; non-generated files are never overwritten.
- Validate only the newly rendered concept and newly inserted index/log entries. Existing entries are out of scope.
- Tools and OKF classes never import extraction-side `model/` classes. The only graph access is through `cache.graph()` / `GraphQuery`.
- Add Javadocs to every added or modified public type, constructor, and method.
- Do not add dependencies, generated outputs, GitHub Actions, hooks, or release automation.

## File Structure

### New production files

- `src/main/java/dev/dominikbreu/archlens/mcp/tools/question/QuestionRequestNormalizer.java` — create canonical, family-specific request selectors for question results.
- `src/main/java/dev/dominikbreu/archlens/okf/ArchitectureQuestionResult.java` — validate and type the common structured question envelope.
- `src/main/java/dev/dominikbreu/archlens/okf/QuestionConceptIdentity.java` — derive canonical semantic JSON, SHA-256 key, slug, and relative concept path.
- `src/main/java/dev/dominikbreu/archlens/okf/ProjectPathResolver.java` — select an indexed root and safely resolve bundle/template paths beneath it.
- `src/main/java/dev/dominikbreu/archlens/okf/QuestionOkfRenderer.java` — render standard frontmatter and family-specific Markdown blocks, including custom templates.
- `src/main/java/dev/dominikbreu/archlens/okf/OkfEntryValidator.java` — validate the generated concept and inserted index/log snippets.
- `src/main/java/dev/dominikbreu/archlens/okf/OkfBundleWriter.java` — create/update root index/log and stage/promote/restore bundle files.
- `src/main/java/dev/dominikbreu/archlens/okf/QuestionOkfCompiler.java` — coordinate validation, identity, paths, rendering, overwrite policy, and writing.
- `src/main/java/dev/dominikbreu/archlens/mcp/tools/CompileArchitectureQuestionToOkfTool.java` — adapt MCP arguments and indexed graph roots to the compiler.

### New tests

- `src/test/java/dev/dominikbreu/archlens/mcp/tools/question/QuestionRequestNormalizerTest.java`
- `src/test/java/dev/dominikbreu/archlens/okf/ArchitectureQuestionResultTest.java`
- `src/test/java/dev/dominikbreu/archlens/okf/QuestionConceptIdentityTest.java`
- `src/test/java/dev/dominikbreu/archlens/okf/ProjectPathResolverTest.java`
- `src/test/java/dev/dominikbreu/archlens/okf/QuestionOkfRendererTest.java`
- `src/test/java/dev/dominikbreu/archlens/okf/OkfEntryValidatorTest.java`
- `src/test/java/dev/dominikbreu/archlens/okf/OkfBundleWriterTest.java`
- `src/test/java/dev/dominikbreu/archlens/mcp/tools/CompileArchitectureQuestionToOkfToolTest.java`

### Existing files to modify

- `src/main/java/dev/dominikbreu/archlens/mcp/tools/question/Answer.java` — include normalized request in the common envelope.
- `src/main/java/dev/dominikbreu/archlens/mcp/tools/AnswerArchitectureQuestionTool.java` — pass effective args into envelope construction and add empty requests to terminal results.
- `src/main/java/dev/dominikbreu/archlens/mcp/McpServer.java` — construct/register the compiler, schemas, required-object support, and prompt.
- `src/test/java/dev/dominikbreu/archlens/mcp/tools/AnswerArchitectureQuestionToolTest.java` — prove canonical request behavior.
- `src/test/java/dev/dominikbreu/archlens/mcp/McpServerTest.java` — assert both tool schemas and prompt registration.
- `docs/TOOLS.md`, `docs/ARCHITECTURE.md`, `llms.txt` — document the public contract and package responsibility.
- `skills/spoon-understand/SKILL.md`, `skills/spoon-understand/references/mcp-tool-map.md` — teach agents the explicit answer-then-compile workflow.
- `.agents/skills/spoon-understand/SKILL.md`, `.agents/skills/spoon-understand/references/mcp-tool-map.md` — resync the mirrored skill after editing the canonical copy.

---

### Task 1: Add the Canonical Question Request Envelope

**Files:**

- Create: `src/main/java/dev/dominikbreu/archlens/mcp/tools/question/QuestionRequestNormalizer.java`
- Create: `src/test/java/dev/dominikbreu/archlens/mcp/tools/question/QuestionRequestNormalizerTest.java`
- Modify: `src/main/java/dev/dominikbreu/archlens/mcp/tools/question/Answer.java`
- Modify: `src/main/java/dev/dominikbreu/archlens/mcp/tools/AnswerArchitectureQuestionTool.java`
- Modify: `src/test/java/dev/dominikbreu/archlens/mcp/tools/AnswerArchitectureQuestionToolTest.java`

**Interfaces:**

- Produces: `QuestionRequestNormalizer.normalize(String, Map<String,Object>, Map<String,Object>, List<Map<String,Object>>) -> Map<String,Object>`.
- Produces: `Answer.structured(String, Interpretation, QueryPlanRecorder, Map<String,Object>)` with a new `request` field.
- Consumes later: `ArchitectureQuestionResult.from(...)` in Task 2 relies on the exact `request` map.

- [ ] **Step 1: Write failing normalizer tests**

```java
package dev.dominikbreu.archlens.mcp.tools.question;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class QuestionRequestNormalizerTest {
    @Test
    void normalizesCanonicalSubjectAndMeaningfulSelectors() {
        Map<String, Object> request = QuestionRequestNormalizer.normalize(
                "persistence_destination",
                Map.of("entrypoint", "POST /orders", "param", "id", "maxDepth", 99),
                Map.of("id", "com.example.OrderResource#create", "label", "Entrypoint"),
                List.of());

        assertThat(request).containsExactly(
                Map.entry("entrypoint", "com.example.OrderResource#create"),
                Map.entry("param", "id"));
    }

    @Test
    void takesCanonicalRelationshipTargetFromRecordedPathsOperation() {
        Map<String, Object> request = QuestionRequestNormalizer.normalize(
                "relationship",
                Map.of("component", "Orders", "target", "Billing", "maxDepth", 3),
                Map.of("id", "com.example.Orders", "label", "Component"),
                List.of(Map.of("op", "paths", "from", "com.example.Orders", "to", "com.example.Billing")));

        assertThat(request).containsExactly(
                Map.entry("component", "com.example.Orders"),
                Map.entry("target", "com.example.Billing"),
                Map.entry("maxDepth", 3));
    }

    @Test
    void appliesScopeDefaultsOnlyToFamiliesThatUseThem() {
        assertThat(QuestionRequestNormalizer.normalize(
                        "impact",
                        Map.of("component", "Orders"),
                        Map.of("id", "com.example.Orders", "label", "Component"),
                        List.of()))
                .containsEntry("maxDepth", 4);
        assertThat(QuestionRequestNormalizer.normalize(
                        "consumer_context",
                        Map.of("entrypoint", "consume"),
                        Map.of("id", "com.example.Consumer#consume", "label", "Entrypoint"),
                        List.of()))
                .doesNotContainKey("maxDepth");
    }
}
```

- [ ] **Step 2: Run the normalizer test and verify RED**

Run: `mvn -Dtest=QuestionRequestNormalizerTest test`

Expected: FAIL during test compilation because `QuestionRequestNormalizer` does not exist.

- [ ] **Step 3: Implement the normalizer**

Create the class with this public contract and exact selector policy:

```java
package dev.dominikbreu.archlens.mcp.tools.question;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Normalizes the semantic request selectors persisted with an architecture question result. */
public final class QuestionRequestNormalizer {
    private static final Set<String> QUERY_FAMILIES = Set.of(
            "persistence_destination", "messaging_flow", "external_integration_context", "configuration_context");

    private QuestionRequestNormalizer() {}

    /**
     * Produces canonical selectors without raw question wording or family-ignored arguments.
     *
     * @param family resolved question family
     * @param effectiveArgs selectors used by the answerer
     * @param subject resolved subject map
     * @param queryPlan recorded graph operations
     * @return ordered canonical request map
     */
    public static Map<String, Object> normalize(
            String family,
            Map<String, Object> effectiveArgs,
            Map<String, Object> subject,
            List<Map<String, Object>> queryPlan) {
        Map<String, Object> request = new LinkedHashMap<>();
        Object subjectId = subject.get("id");
        String label = String.valueOf(subject.getOrDefault("label", ""));
        if (subjectId != null && "Entrypoint".equals(label)) request.put("entrypoint", subjectId);
        else if (subjectId != null && "Component".equals(label)) request.put("component", subjectId);
        else if (subject.containsKey("field")) request.put("field", subject.get("field"));
        else if (subject.containsKey("query")) request.put("query", subject.get("query"));
        else if (subjectId != null) request.put("subject", subjectId);

        copyIfPresent(request, effectiveArgs, "param");
        copyIfPresent(request, effectiveArgs, "method");
        copyIfPresent(request, effectiveArgs, "field");
        if (QUERY_FAMILIES.contains(family)) copyIfPresent(request, effectiveArgs, "query");

        if ("endpoint_context".equals(family)) {
            String mode = request.containsKey("entrypoint") ? "forward" : "reverse";
            request.put("mode", mode);
            if ("reverse".equals(mode)) request.put("maxDepth", intArg(effectiveArgs, "maxDepth", 4));
        } else if ("impact".equals(family)) {
            request.put("maxDepth", intArg(effectiveArgs, "maxDepth", 4));
        } else if ("relationship".equals(family)) {
            queryPlan.stream()
                    .filter(op -> "paths".equals(op.get("op")))
                    .map(op -> op.get("to"))
                    .filter(java.util.Objects::nonNull)
                    .findFirst()
                    .ifPresent(target -> request.put("target", target));
            if (!request.containsKey("target")) copyIfPresent(request, effectiveArgs, "target");
            request.put("maxDepth", intArg(effectiveArgs, "maxDepth", 2));
        }
        return Map.copyOf(request);
    }

    private static void copyIfPresent(Map<String, Object> target, Map<String, Object> source, String key) {
        Object value = source.get(key);
        if (value != null && !value.toString().isBlank()) target.put(key, value);
    }

    private static int intArg(Map<String, Object> args, String key, int fallback) {
        Object value = args.get(key);
        return value instanceof Number number ? number.intValue() : fallback;
    }
}
```

- [ ] **Step 4: Run the normalizer test and verify GREEN**

Run: `mvn -Dtest=QuestionRequestNormalizerTest test`

Expected: PASS, 3 tests.

- [ ] **Step 5: Add `request` to every question result**

Change `Answer.structured` to accept `effectiveArgs`, compute `queryPlan` once, and insert `request` before `interpretation`:

```java
public Map<String, Object> structured(
        String family,
        Interpretation interpretation,
        QueryPlanRecorder recorder,
        Map<String, Object> effectiveArgs) {
    List<Map<String, Object>> queryPlan = recorder == null ? List.of() : recorder.operations();
    Map<String, Object> structured = new LinkedHashMap<>();
    structured.put("family", family);
    structured.put("status", !ambiguous.isEmpty() ? "ambiguous" : !unresolved.isEmpty() ? "partial" : "resolved");
    structured.put("request", QuestionRequestNormalizer.normalize(family, effectiveArgs, subject, queryPlan));
    structured.put("interpretation", interpretation == null ? Map.of() : QuestionPlanner.interpretationMap(interpretation));
    structured.put("queryPlan", queryPlan);
    structured.put("subject", subject);
    structured.put("answer", answer);
    structured.put("evidenceChain", List.copyOf(evidenceChain));
    structured.put("unresolved", List.copyOf(unresolved));
    structured.put("ambiguous", List.copyOf(ambiguous));
    structured.put("clarifications", List.of());
    structured.put("suggestedQuestions", QuestionPlanner.suggestedQuestions(family));
    return structured;
}
```

Update the call to:

```java
Map<String, Object> structured = answer.structured(family, interpretation, recorder, effectiveArgs);
```

Add `structured.put("request", Map.of());` to `terminalStructured` so rejected statuses keep the common envelope shape.

- [ ] **Step 6: Extend the existing tool tests**

Add assertions to `AnswerArchitectureQuestionToolTest`:

```java
@Test
void structuredResultIncludesCanonicalSemanticRequest() {
    ToolResult result = tool("spring-pipeline-sample")
            .execute(Map.of(
                    "family", "persistence_destination",
                    "entrypoint", "POST /api/orders/{id}",
                    "param", "id"));

    assertThat(map(structured(result), "request"))
            .containsEntry("param", "id")
            .containsKey("entrypoint")
            .doesNotContainKey("question");
}

@Test
void rewordedNaturalQuestionKeepsSameSemanticRequestAsTypedQuestion() {
    Map<String, Object> typed = structured(tool("spring-pipeline-sample")
            .execute(Map.of("family", "impact", "component", "OrderRepository")));
    Map<String, Object> natural = structured(tool("spring-pipeline-sample")
            .execute(Map.of("question", "What may break if OrderRepository is replaced?")));

    assertThat(map(natural, "request")).isEqualTo(map(typed, "request"));
}
```

- [ ] **Step 7: Run the question tests**

Run: `mvn -Dtest=QuestionRequestNormalizerTest,AnswerArchitectureQuestionToolTest test`

Expected: PASS.

- [ ] **Step 8: Commit Task 1**

```bash
git add src/main/java/dev/dominikbreu/archlens/mcp/tools/question/QuestionRequestNormalizer.java \
  src/main/java/dev/dominikbreu/archlens/mcp/tools/question/Answer.java \
  src/main/java/dev/dominikbreu/archlens/mcp/tools/AnswerArchitectureQuestionTool.java \
  src/test/java/dev/dominikbreu/archlens/mcp/tools/question/QuestionRequestNormalizerTest.java \
  src/test/java/dev/dominikbreu/archlens/mcp/tools/AnswerArchitectureQuestionToolTest.java
git commit -m "feat: add canonical architecture question requests"
```

---

### Task 2: Validate Results and Derive Semantic Concept Identity

**Files:**

- Create: `src/main/java/dev/dominikbreu/archlens/okf/ArchitectureQuestionResult.java`
- Create: `src/main/java/dev/dominikbreu/archlens/okf/QuestionConceptIdentity.java`
- Create: `src/test/java/dev/dominikbreu/archlens/okf/ArchitectureQuestionResultTest.java`
- Create: `src/test/java/dev/dominikbreu/archlens/okf/QuestionConceptIdentityTest.java`

**Interfaces:**

- Produces: `ArchitectureQuestionResult.from(Map<String,Object>)` and typed accessors for all common fields.
- Produces: `QuestionConceptIdentity.derive(ArchitectureQuestionResult) -> ConceptIdentity`.
- Produces: `ConceptIdentity(String semanticKey, String familySlug, String subjectSlug, Path relativePath)`.

- [ ] **Step 1: Write failing result-validation tests**

```java
package dev.dominikbreu.archlens.okf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ArchitectureQuestionResultTest {
    @Test
    void parsesCompilableCommonEnvelope() {
        ArchitectureQuestionResult result = ArchitectureQuestionResult.from(result("partial"));
        assertThat(result.family()).isEqualTo("impact");
        assertThat(result.compilable()).isTrue();
        assertThat(result.unresolved()).containsExactly("security-not-modeled");
    }

    @Test
    void rejectsOnlyTerminalNonKnowledgeStatusesAsNonCompilable() {
        assertThat(ArchitectureQuestionResult.from(result("ambiguous")).compilable()).isTrue();
        assertThat(ArchitectureQuestionResult.from(result("unsupported")).compilable()).isFalse();
        assertThat(ArchitectureQuestionResult.from(result("needs-clarification")).compilable()).isFalse();
    }

    @Test
    void rejectsMissingRequestAndUnknownFamily() {
        Map<String, Object> missing = new java.util.LinkedHashMap<>(result("resolved"));
        missing.remove("request");
        assertThatThrownBy(() -> ArchitectureQuestionResult.from(missing))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("request");
        Map<String, Object> unknown = new java.util.LinkedHashMap<>(result("resolved"));
        unknown.put("family", "invented");
        assertThatThrownBy(() -> ArchitectureQuestionResult.from(unknown))
                .hasMessageContaining("family");
    }

    private static Map<String, Object> result(String status) {
        return Map.ofEntries(
                Map.entry("family", "impact"),
                Map.entry("status", status),
                Map.entry("request", Map.of("component", "com.example.OrderRepository", "maxDepth", 4)),
                Map.entry("interpretation", Map.of()),
                Map.entry("queryPlan", List.of()),
                Map.entry("subject", Map.of("id", "com.example.OrderRepository", "label", "Component")),
                Map.entry("answer", Map.of("components", List.of())),
                Map.entry("evidenceChain", List.of()),
                Map.entry("unresolved", List.of("security-not-modeled")),
                Map.entry("ambiguous", List.of()),
                Map.entry("clarifications", List.of()),
                Map.entry("suggestedQuestions", List.of()));
    }
}
```

- [ ] **Step 2: Run and verify RED**

Run: `mvn -Dtest=ArchitectureQuestionResultTest test`

Expected: FAIL during test compilation because `ArchitectureQuestionResult` does not exist.

- [ ] **Step 3: Implement the typed common envelope**

Use this record shape and validation rules:

```java
public record ArchitectureQuestionResult(
        String family,
        String status,
        Map<String, Object> request,
        Map<String, Object> interpretation,
        List<Map<String, Object>> queryPlan,
        Map<String, Object> subject,
        Map<String, Object> answer,
        List<Map<String, Object>> evidenceChain,
        List<String> unresolved,
        List<String> ambiguous,
        List<Map<String, Object>> clarifications,
        List<String> suggestedQuestions) {
    public static final Set<String> FAMILIES = Set.of(
            "persistence_destination", "consumer_context", "impact", "transaction_context",
            "endpoint_context", "messaging_flow", "state_lifecycle", "scheduled_workflow",
            "external_integration_context", "configuration_context", "relationship");

    public static ArchitectureQuestionResult from(Map<String, Object> input) {
        String family = requiredString(input, "family");
        String status = requiredString(input, "status");
        if (!FAMILIES.contains(family) && !Set.of("unsupported", "needs-clarification").contains(status)) {
            throw new IllegalArgumentException("Unknown question family: " + family);
        }
        Map<String, Object> request = requiredMap(input, "request");
        return new ArchitectureQuestionResult(
                family,
                status,
                request,
                requiredMap(input, "interpretation"),
                mapList(input, "queryPlan"),
                requiredMap(input, "subject"),
                requiredMap(input, "answer"),
                mapList(input, "evidenceChain"),
                stringList(input, "unresolved"),
                stringList(input, "ambiguous"),
                mapList(input, "clarifications"),
                stringList(input, "suggestedQuestions"));
    }

    public boolean compilable() {
        return !"unsupported".equals(status) && !"needs-clarification".equals(status);
    }
}
```

Implement `requiredString`, `requiredMap`, `mapList`, and `stringList` as private methods that reject missing keys and wrong element types with `IllegalArgumentException("Invalid question result field '<key>'")`. Copy all collections with `Map.copyOf` / `List.copyOf`.

- [ ] **Step 4: Run result-validation tests GREEN**

Run: `mvn -Dtest=ArchitectureQuestionResultTest test`

Expected: PASS, 3 tests.

- [ ] **Step 5: Write failing semantic-identity tests**

```java
@Test
void identityIgnoresMapOrderAndRawQuestionWording() {
    ArchitectureQuestionResult first = resultWithRequest(new java.util.LinkedHashMap<>(Map.of(
            "component", "com.example.OrderRepository", "maxDepth", 4)));
    java.util.LinkedHashMap<String, Object> reversed = new java.util.LinkedHashMap<>();
    reversed.put("maxDepth", 4);
    reversed.put("component", "com.example.OrderRepository");
    ArchitectureQuestionResult second = resultWithRequest(reversed);

    assertThat(new QuestionConceptIdentity().derive(first))
            .isEqualTo(new QuestionConceptIdentity().derive(second));
}

@Test
void identityChangesWhenSemanticScopeChanges() {
    QuestionConceptIdentity identity = new QuestionConceptIdentity();
    assertThat(identity.derive(resultWithRequest(Map.of(
                    "component", "com.example.OrderRepository", "maxDepth", 4))).semanticKey())
            .isNotEqualTo(identity.derive(resultWithRequest(Map.of(
                    "component", "com.example.OrderRepository", "maxDepth", 5))).semanticKey());
}

@Test
void createsReadablePathWithTwelveHexCharacters() {
    QuestionConceptIdentity.ConceptIdentity identity = new QuestionConceptIdentity()
            .derive(resultWithRequest(Map.of("component", "com.example.OrderRepository", "maxDepth", 4)));
    assertThat(identity.relativePath().toString().replace('\\', '/'))
            .matches("investigations/impact/order-repository-[0-9a-f]{12}\\.md");
}
```

- [ ] **Step 6: Run identity tests RED**

Run: `mvn -Dtest=QuestionConceptIdentityTest test`

Expected: FAIL during test compilation because `QuestionConceptIdentity` does not exist.

- [ ] **Step 7: Implement canonical identity**

Implement these signatures:

```java
public final class QuestionConceptIdentity {
    public ConceptIdentity derive(ArchitectureQuestionResult result) {
        String canonical = canonicalValue(Map.of("family", result.family(), "request", result.request()));
        String key = sha256(canonical);
        String familySlug = slug(result.family());
        String subjectSlug = subjectSlug(result.request());
        Path relative = Path.of("investigations", familySlug, subjectSlug + "-" + key.substring(0, 12) + ".md");
        return new ConceptIdentity(key, familySlug, subjectSlug, relative);
    }

    public record ConceptIdentity(
            String semanticKey, String familySlug, String subjectSlug, Path relativePath) {}
}
```

`canonicalValue` must recursively serialize maps through a `TreeMap<String,Object>`, preserve list order, JSON-quote strings through `McpJsonDefaults.getMapper().writeValueAsString`, and render numbers/booleans directly. `sha256` uses UTF-8 and `HexFormat`. `subjectSlug` selects the first of `entrypoint`, `component`, `field`, `query`, or `subject`, removes a package prefix and method signature punctuation, converts non-alphanumerics to `-`, lowercases, limits to 64 characters, and falls back to `unresolved-subject`.

- [ ] **Step 8: Run Task 2 tests GREEN**

Run: `mvn -Dtest=ArchitectureQuestionResultTest,QuestionConceptIdentityTest test`

Expected: PASS.

- [ ] **Step 9: Commit Task 2**

```bash
git add src/main/java/dev/dominikbreu/archlens/okf/ArchitectureQuestionResult.java \
  src/main/java/dev/dominikbreu/archlens/okf/QuestionConceptIdentity.java \
  src/test/java/dev/dominikbreu/archlens/okf/ArchitectureQuestionResultTest.java \
  src/test/java/dev/dominikbreu/archlens/okf/QuestionConceptIdentityTest.java
git commit -m "feat: validate and identify OKF investigations"
```

---

### Task 3: Confine Project, Bundle, and Template Paths

**Files:**

- Create: `src/main/java/dev/dominikbreu/archlens/okf/ProjectPathResolver.java`
- Create: `src/test/java/dev/dominikbreu/archlens/okf/ProjectPathResolverTest.java`

**Interfaces:**

- Produces: `ProjectPathResolver.resolve(Collection<String>, String, String, String) -> ResolvedPaths`.
- Produces: `ResolvedPaths(Path projectPath, Path bundlePath, Path templatePath)`; `templatePath` is nullable.

- [ ] **Step 1: Write failing containment tests**

```java
@TempDir Path tempDir;

@Test
void infersSingleRootAndDefaultsBundle() throws Exception {
    Path project = Files.createDirectory(tempDir.resolve("project")).toRealPath();
    ProjectPathResolver.ResolvedPaths paths = new ProjectPathResolver()
            .resolve(List.of(project.toString()), null, null, null);
    assertThat(paths.projectPath()).isEqualTo(project);
    assertThat(paths.bundlePath()).isEqualTo(project.resolve("docs/agent-wiki"));
    assertThat(paths.templatePath()).isNull();
}

@Test
void requiresProjectForMultipleRoots() throws Exception {
    Path one = Files.createDirectory(tempDir.resolve("one"));
    Path two = Files.createDirectory(tempDir.resolve("two"));
    assertThatThrownBy(() -> new ProjectPathResolver()
                    .resolve(List.of(one.toString(), two.toString()), null, null, null))
            .hasMessageContaining("projectPath is required");
}

@Test
void rejectsAbsoluteAndTraversalPaths() throws Exception {
    Path project = Files.createDirectory(tempDir.resolve("project"));
    ProjectPathResolver resolver = new ProjectPathResolver();
    assertThatThrownBy(() -> resolver.resolve(
                    List.of(project.toString()), project.toString(), tempDir.resolve("outside").toString(), null))
            .hasMessageContaining("bundlePath must be project-relative");
    assertThatThrownBy(() -> resolver.resolve(
                    List.of(project.toString()), project.toString(), "../outside", null))
            .hasMessageContaining("outside indexed project");
}

@Test
void rejectsExistingSymlinkEscape() throws Exception {
    Path project = Files.createDirectory(tempDir.resolve("project"));
    Path outside = Files.createDirectory(tempDir.resolve("outside"));
    Files.createSymbolicLink(project.resolve("linked"), outside);
    assertThatThrownBy(() -> new ProjectPathResolver().resolve(
                    List.of(project.toString()), project.toString(), "linked/wiki", null))
            .hasMessageContaining("symlink");
}
```

- [ ] **Step 2: Run and verify RED**

Run: `mvn -Dtest=ProjectPathResolverTest test`

Expected: FAIL during test compilation because `ProjectPathResolver` does not exist.

- [ ] **Step 3: Implement path resolution**

Use this public shape:

```java
public final class ProjectPathResolver {
    public ResolvedPaths resolve(
            Collection<String> indexedRootValues,
            String requestedProject,
            String bundleValue,
            String templateValue) throws IOException {
        List<Path> roots = indexedRootValues.stream()
                .map(Path::of)
                .map(ProjectPathResolver::realDirectory)
                .distinct()
                .toList();
        if (roots.isEmpty()) throw new IllegalArgumentException("Indexed graph has no project roots");
        Path project = selectProject(roots, requestedProject);
        Path bundle = contained(project, bundleValue == null ? Path.of("docs", "agent-wiki") : relative(bundleValue), false);
        Path template = templateValue == null ? null : contained(project, relative(templateValue), true);
        return new ResolvedPaths(project, bundle, template);
    }

    public record ResolvedPaths(Path projectPath, Path bundlePath, Path templatePath) {}
}
```

Implement helpers with these exact rules:

- `realDirectory` calls `toAbsolutePath().normalize().toRealPath()` and requires a directory.
- `selectProject` infers only one root; otherwise requires a requested value whose real path equals a root.
- `relative` rejects `Path.isAbsolute()`.
- `contained` normalizes `project.resolve(relative)`, requires `startsWith(project)`, then walks upward to the nearest existing parent and requires that parent's `toRealPath()` starts with `project`.
- When `mustExist` is true, require a regular file and verify its own `toRealPath()` starts with `project`.
- If an existing destination or parent is a symlink, reject it when its real target leaves `project`; include `symlink` in the error text.

- [ ] **Step 4: Run containment tests GREEN**

Run: `mvn -Dtest=ProjectPathResolverTest test`

Expected: PASS.

- [ ] **Step 5: Commit Task 3**

```bash
git add src/main/java/dev/dominikbreu/archlens/okf/ProjectPathResolver.java \
  src/test/java/dev/dominikbreu/archlens/okf/ProjectPathResolverTest.java
git commit -m "feat: confine OKF output paths to indexed projects"
```

---

### Task 4: Render and Validate OKF Investigation Entries

**Files:**

- Create: `src/main/java/dev/dominikbreu/archlens/okf/QuestionOkfRenderer.java`
- Create: `src/main/java/dev/dominikbreu/archlens/okf/OkfEntryValidator.java`
- Create: `src/test/java/dev/dominikbreu/archlens/okf/QuestionOkfRendererTest.java`
- Create: `src/test/java/dev/dominikbreu/archlens/okf/OkfEntryValidatorTest.java`

**Interfaces:**

- Consumes: `ArchitectureQuestionResult` and `QuestionConceptIdentity.ConceptIdentity` from Task 2.
- Produces: `QuestionOkfRenderer.render(...)->RenderedConcept`.
- Produces: `RenderedConcept(String title, String description, String markdown)`.
- Produces: `OkfEntryValidator.validateConcept(String, String)` plus `validateIndexEntry(String)` and `validateLogEntry(String)`.

- [ ] **Step 1: Write failing renderer tests**

```java
@Test
void rendersConformantFrontmatterEvidenceAndUncertainty() {
    ArchitectureQuestionResult result = Fixtures.partialImpactResult();
    QuestionConceptIdentity.ConceptIdentity identity = new QuestionConceptIdentity().derive(result);

    QuestionOkfRenderer.RenderedConcept rendered = new QuestionOkfRenderer().render(
            result, identity, Path.of("/project"), null, Instant.parse("2026-07-19T12:00:00Z"));

    assertThat(rendered.markdown())
            .startsWith("---\n")
            .contains("type: Architecture Investigation")
            .contains("archlens_status: partial")
            .contains("archlens_semantic_key: " + identity.semanticKey())
            .contains("archlens_project_path: /project")
            .contains("# Uncertainty")
            .contains("security-not-modeled")
            .contains("# Evidence");
}

@Test
void rendersAllSupportedFamilyAnswerKeys() {
    QuestionOkfRenderer renderer = new QuestionOkfRenderer();
    for (String family : ArchitectureQuestionResult.FAMILIES) {
        ArchitectureQuestionResult result = Fixtures.resultForFamily(family);
        String markdown = renderer.render(
                        result,
                        new QuestionConceptIdentity().derive(result),
                        Path.of("/project"),
                        null,
                        Instant.EPOCH)
                .markdown();
        assertThat(markdown).contains("# Findings").contains("archlens_family: " + family);
    }
}

@Test
void customTemplateRequiresEveryKnownBlockExactlyOnce(@TempDir Path tempDir) throws Exception {
    Path valid = tempDir.resolve("valid.md");
    Files.writeString(valid, String.join("\n",
            "{{frontmatter}}", "{{question}}", "{{subject}}", "{{answer}}",
            "{{evidence}}", "{{uncertainty}}", "{{query_plan}}", "{{suggested_questions}}"));
    ArchitectureQuestionResult result = Fixtures.partialImpactResult();
    assertThat(new QuestionOkfRenderer().render(
                    result,
                    new QuestionConceptIdentity().derive(result),
                    tempDir,
                    valid,
                    Instant.EPOCH).markdown())
            .doesNotContain("{{");

    Files.writeString(valid, "{{frontmatter}}\n{{question}}\n{{subject}}\n{{answer}}\n{{evidence}}\n{{uncertainty}}\n{{query_plan}}");
    assertThatThrownBy(() -> new QuestionOkfRenderer().render(
                    result,
                    new QuestionConceptIdentity().derive(result),
                    tempDir,
                    valid,
                    Instant.EPOCH))
            .hasMessageContaining("suggested_questions");
}
```

Create a package-private `Fixtures` helper in `QuestionOkfRendererTest` that returns complete common envelopes. Its `resultForFamily` must supply these exact answer keys:

```java
Map.ofEntries(
    Map.entry("persistence_destination", List.of("origins", "transformations", "operations", "destinations")),
    Map.entry("consumer_context", List.of("inboundBinding", "upstream", "downstream")),
    Map.entry("impact", List.of("entrypoints", "workflows", "persistence", "externalIntegrations", "components", "evidenceChains")),
    Map.entry("transaction_context", List.of("policies", "scopeTransitions", "governedCalls", "caveats")),
    Map.entry("endpoint_context", List.of("mode", "inbound", "owningComponent", "runtimeCalls", "dataFlowSinks", "transactionTransitions", "outboundCalls")),
    Map.entry("messaging_flow", List.of("channel", "broker", "topic", "producers", "producerSinks", "consumers", "downstreamSinks")),
    Map.entry("state_lifecycle", List.of("writers", "readers", "handoffs")),
    Map.entry("scheduled_workflow", List.of("triggerEvidence", "runtimeCalls", "stateReads", "stateWrites", "messagingAndExternalSinks")),
    Map.entry("external_integration_context", List.of("configuredDestination", "dataSentReceived", "callers", "replacementImpact")),
    Map.entry("configuration_context", List.of("declarations", "usages")),
    Map.entry("relationship", List.of("neighborhood", "paths")))
```

- [ ] **Step 2: Run renderer tests RED**

Run: `mvn -Dtest=QuestionOkfRendererTest test`

Expected: FAIL during test compilation because `QuestionOkfRenderer` does not exist.

- [ ] **Step 3: Implement deterministic rendering**

Define the eight placeholders as an insertion-ordered list and the family answer-key map shown in Step 1. Render answer keys in that order, then render any additive unknown answer keys alphabetically so future additive fields are preserved.

Use these public records/methods:

```java
public final class QuestionOkfRenderer {
    public RenderedConcept render(
            ArchitectureQuestionResult result,
            QuestionConceptIdentity.ConceptIdentity identity,
            Path projectPath,
            Path templatePath,
            Instant timestamp) throws IOException {
        String title = title(result);
        String description = humanize(result.family()) + " investigation compiled from ArchLens evidence.";
        Map<String, String> blocks = blocks(result, identity, projectPath, timestamp, title, description);
        String template = templatePath == null ? defaultTemplate() : Files.readString(templatePath);
        validateTemplate(template);
        for (Map.Entry<String, String> block : blocks.entrySet()) {
            template = template.replace("{{" + block.getKey() + "}}", block.getValue());
        }
        return new RenderedConcept(title, description, template.strip() + "\n");
    }

    public record RenderedConcept(String title, String description, String markdown) {}
}
```

Build frontmatter in a `LinkedHashMap` with exactly: `type`, `title`, `description`, `resource`, `tags`, `timestamp`, `archlens_family`, `archlens_status`, `archlens_semantic_key`, `archlens_project_path`, `archlens_generated`. Dump it with SnakeYAML block style between `---` delimiters. `resource` is `archlens://investigation/<full-key>`; tags are `architecture`, the hyphenated family, and status.

Render maps as sorted Markdown bullet keys, lists as ordered bullets, graph node maps as ``- `<id>` — <name> (<label>)`` with nested evidence/source properties, and scalar values as inline code where safe. The `question` block uses `interpretation.rawQuestion` when present; otherwise it renders a deterministic sentence from family and request. Always emit uncertainty with `None recorded.` when both lists are empty.

The default template is exactly:

```text
{{frontmatter}}

{{question}}

{{subject}}

{{answer}}

{{evidence}}

{{uncertainty}}

{{query_plan}}

{{suggested_questions}}
```

`validateTemplate` scans `\\{\\{([a-z_]+)}}`, rejects unknown names, and requires every known name exactly once.

- [ ] **Step 4: Write failing entry-validator tests**

```java
@Test
void acceptsGeneratedConceptAndCurrentSnippets() {
    String concept = """
            ---
            type: Architecture Investigation
            archlens_generated: true
            archlens_semantic_key: abc123
            ---
            # Question
            What changes?
            """;
    OkfEntryValidator validator = new OkfEntryValidator();
    validator.validateConcept(concept, "abc123");
    validator.validateIndexEntry("<!-- archlens:abc123 -->\n- [Title](investigations/impact/title-abc123.md) - Description");
    validator.validateLogEntry("- **Creation**: Added [Title](investigations/impact/title-abc123.md).");
}

@Test
void rejectsWrongKeyAndMalformedInsertedEntries() {
    OkfEntryValidator validator = new OkfEntryValidator();
    assertThatThrownBy(() -> validator.validateConcept("---\ntype: Architecture Investigation\n---\n", "abc"))
            .hasMessageContaining("archlens_generated");
    assertThatThrownBy(() -> validator.validateIndexEntry("Title without link"))
            .hasMessageContaining("index entry");
}
```

- [ ] **Step 5: Implement entry-only validation**

`validateConcept` must split the first YAML frontmatter block, parse it with `new Yaml().load`, require nonblank `type`, `archlens_generated == true`, and exact full semantic key. It validates only the rendered string passed to it. `validateIndexEntry` requires a semantic marker line followed by one Markdown link bullet. `validateLogEntry` requires a bullet beginning `- **Creation**:` or `- **Refresh**:` and containing a Markdown link. Do not read or validate existing bundle content here.

- [ ] **Step 6: Run Task 4 tests GREEN**

Run: `mvn -Dtest=QuestionOkfRendererTest,OkfEntryValidatorTest test`

Expected: PASS.

- [ ] **Step 7: Commit Task 4**

```bash
git add src/main/java/dev/dominikbreu/archlens/okf/QuestionOkfRenderer.java \
  src/main/java/dev/dominikbreu/archlens/okf/OkfEntryValidator.java \
  src/test/java/dev/dominikbreu/archlens/okf/QuestionOkfRendererTest.java \
  src/test/java/dev/dominikbreu/archlens/okf/OkfEntryValidatorTest.java
git commit -m "feat: render and validate question OKF concepts"
```

---

### Task 5: Safely Create and Refresh OKF Bundles

**Files:**

- Create: `src/main/java/dev/dominikbreu/archlens/okf/OkfBundleWriter.java`
- Create: `src/test/java/dev/dominikbreu/archlens/okf/OkfBundleWriterTest.java`

**Interfaces:**

- Consumes: validated rendered concept, semantic identity, resolved bundle path, and overwrite flag.
- Produces: `WriteOutcome(String status, Path conceptPath, Path indexPath, Path logPath, List<String> warnings)`.
- Produces package-private injectable `FilePromoter` used only to force promotion failure in tests.

- [ ] **Step 1: Write failing create/overwrite tests**

```java
@TempDir Path tempDir;

@Test
void createsConceptIndexAndLog() throws Exception {
    OkfBundleWriter.WriteOutcome outcome = writer().write(request(false));
    assertThat(outcome.status()).isEqualTo("created");
    assertThat(outcome.conceptPath()).hasContent(CONCEPT);
    assertThat(outcome.indexPath()).content().contains("<!-- archlens:" + KEY + " -->");
    assertThat(outcome.logPath()).content().contains("**Creation**");
}

@Test
void existingGeneratedConceptRequiresExplicitOverwrite() throws Exception {
    writer().write(request(false));
    OkfBundleWriter.WriteOutcome preview = writer().write(request(false));
    assertThat(preview.status()).isEqualTo("overwrite-required");
    assertThat(preview.warnings()).singleElement().asString().contains("allowOverwrite");
    assertThat(preview.logPath()).content().doesNotContain("**Refresh**");

    OkfBundleWriter.WriteOutcome updated = writer().write(request(true));
    assertThat(updated.status()).isEqualTo("updated");
    assertThat(updated.logPath()).content().contains("**Refresh**");
}

@Test
void refusesNonGeneratedAndDifferentlyKeyedTargets() throws Exception {
    Files.createDirectories(conceptPath().getParent());
    Files.writeString(conceptPath(), "human authored");
    assertThatThrownBy(() -> writer().write(request(true))).hasMessageContaining("not ArchLens-generated");
}
```

- [ ] **Step 2: Run writer tests RED**

Run: `mvn -Dtest=OkfBundleWriterTest test`

Expected: FAIL during test compilation because `OkfBundleWriter` does not exist.

- [ ] **Step 3: Implement index/log transformations and overwrite gate**

Use this request/outcome contract:

```java
public record WriteRequest(
        Path bundlePath,
        Path relativeConceptPath,
        String semanticKey,
        String familySlug,
        String title,
        String description,
        String conceptMarkdown,
        LocalDate logDate,
        boolean allowOverwrite) {}

public record WriteOutcome(
        String status,
        Path conceptPath,
        Path indexPath,
        Path logPath,
        List<String> warnings) {}
```

Before creating directories or temp files:

1. Resolve `conceptPath = bundlePath.resolve(relativeConceptPath).normalize()` and require it starts with `bundlePath`.
2. If the concept exists, parse only its frontmatter through `OkfEntryValidator` helpers.
3. Refuse when `archlens_generated` is not true or the full key differs.
4. Return `overwrite-required` without writes when the key matches and `allowOverwrite` is false.

Represent index entries exactly as:

```text
<!-- archlens:<full-key> -->
- [<title>](<bundle-relative-concept-path>) - <description>
```

When the marker exists, replace only the marker's immediately following bullet. Otherwise append under `## <humanized family>`; create `# Architecture Investigations` and the family heading when absent. Preserve every unrelated byte except a final newline normalization.

Represent log entries as:

```text
- **Creation**: Added [<title>](<bundle-relative-concept-path>).
- **Refresh**: Refreshed [<title>](<bundle-relative-concept-path>).
```

Insert beneath an existing `## YYYY-MM-DD` heading or create the date heading immediately after `# Architecture Investigation Log`. Preserve older sections.

- [ ] **Step 4: Add rollback failure test**

```java
@Test
void restoresOriginalsWhenPromotionFailsMidUpdate() throws Exception {
    OkfBundleWriter initial = writer();
    initial.write(request(false));
    String originalConcept = Files.readString(conceptPath());
    String originalIndex = Files.readString(tempDir.resolve("index.md"));

    java.util.concurrent.atomic.AtomicInteger moves = new java.util.concurrent.atomic.AtomicInteger();
    OkfBundleWriter failing = new OkfBundleWriter((source, target) -> {
        if (moves.incrementAndGet() == 2) throw new IOException("injected promotion failure");
        Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    });

    assertThatThrownBy(() -> failing.write(request(true))).hasMessageContaining("injected promotion failure");
    assertThat(conceptPath()).hasContent(originalConcept);
    assertThat(tempDir.resolve("index.md")).hasContent(originalIndex);
}
```

- [ ] **Step 5: Implement staged promotion and restoration**

Define package-private:

```java
@FunctionalInterface
interface FilePromoter {
    void move(Path source, Path target) throws IOException;
}
```

The default promoter first tries `ATOMIC_MOVE` with `REPLACE_EXISTING`, falling back to same-filesystem `REPLACE_EXISTING` only when atomic move is unsupported. `write` must:

1. Build final concept/index/log strings in memory.
2. Validate the concept and only the generated index/log entries.
3. Snapshot each destination as `Optional<byte[]>`.
4. Create parent directories and one destination-adjacent temp file per destination.
5. Write every staged file before promoting any.
6. Promote concept, index, and log in that order.
7. On failure, restore previously promoted paths from snapshots through new adjacent temp files, or delete paths that did not previously exist.
8. Delete all surviving temp files in `finally`.
9. If restoration fails, throw an `IOException` naming both the original promotion failure and every unrestored path.

- [ ] **Step 6: Run writer tests GREEN**

Run: `mvn -Dtest=OkfBundleWriterTest test`

Expected: PASS.

- [ ] **Step 7: Commit Task 5**

```bash
git add src/main/java/dev/dominikbreu/archlens/okf/OkfBundleWriter.java \
  src/test/java/dev/dominikbreu/archlens/okf/OkfBundleWriterTest.java
git commit -m "feat: safely create and refresh OKF bundles"
```

---

### Task 6: Coordinate Compilation and Expose the MCP Tool

**Files:**

- Create: `src/main/java/dev/dominikbreu/archlens/okf/QuestionOkfCompiler.java`
- Create: `src/main/java/dev/dominikbreu/archlens/mcp/tools/CompileArchitectureQuestionToOkfTool.java`
- Create: `src/test/java/dev/dominikbreu/archlens/mcp/tools/CompileArchitectureQuestionToOkfToolTest.java`
- Modify: `src/main/java/dev/dominikbreu/archlens/mcp/McpServer.java`
- Modify: `src/test/java/dev/dominikbreu/archlens/mcp/McpServerTest.java`

**Interfaces:**

- Produces: `QuestionOkfCompiler.compile(CompileRequest) -> CompileOutcome`.
- Produces: `CompileArchitectureQuestionToOkfTool.execute(Map<String,Object>) -> ToolResult`.
- Consumes: every OKF unit from Tasks 2–5 and `GraphQuery.allApplicationNodes()`.

- [ ] **Step 1: Write failing end-to-end tool tests**

```java
@TempDir Path tempDir;

@Test
void compilesReviewedQuestionResultIntoDefaultBundle() throws Exception {
    Path project = copyFixture("spring-pipeline-sample", tempDir.resolve("project"));
    ArchitectureModel model = new ArchitectureExtractor().extract(List.of(project.toString()));
    ModelCache cache = new ModelCache(null);
    cache.indexInMemory(model);
    ToolResult answer = new AnswerArchitectureQuestionTool(cache)
            .execute(Map.of("family", "impact", "component", "OrderRepository"));

    ToolResult compiled = new CompileArchitectureQuestionToOkfTool(cache)
            .execute(Map.of("result", answer.structured(), "projectPath", project.toString()));

    assertThat(compiled.error()).isFalse();
    assertThat((Map<String, Object>) compiled.structured()).containsEntry("status", "created");
    assertThat(project.resolve("docs/agent-wiki/index.md")).exists();
    assertThat(project.resolve("docs/agent-wiki/log.md")).exists();
}

@Test
void returnsOverwriteRequiredWithoutWriting() throws Exception {
    TestContext context = compiledContext();
    context.tool().execute(context.args(false));
    ToolResult preview = context.tool().execute(context.args(false));
    assertThat((Map<String, Object>) preview.structured())
            .containsEntry("status", "overwrite-required");
}

@Test
void rejectsUnsupportedResultAndUnknownProject() throws Exception {
    TestContext context = compiledContext();
    Map<String, Object> unsupported = new java.util.LinkedHashMap<>(context.result());
    unsupported.put("family", "unsupported");
    unsupported.put("status", "unsupported");
    assertThat(context.tool().execute(Map.of("result", unsupported, "projectPath", context.project().toString())).error())
            .isTrue();
    assertThat(context.tool().execute(Map.of("result", context.result(), "projectPath", tempDir.toString())).error())
            .isTrue();
}
```

`copyFixture` must use `Files.walk(source)` to recreate directories and copy regular files into `@TempDir`, so the test never writes under `src/test/resources`.

- [ ] **Step 2: Run tool tests RED**

Run: `mvn -Dtest=CompileArchitectureQuestionToOkfToolTest test`

Expected: FAIL during test compilation because the compiler and tool do not exist.

- [ ] **Step 3: Implement the compiler coordinator**

Use these records and orchestration order:

```java
public record CompileRequest(
        Map<String, Object> result,
        Collection<String> indexedRoots,
        String projectPath,
        String bundlePath,
        String templatePath,
        boolean allowOverwrite) {}

public record CompileOutcome(
        String status,
        Path conceptPath,
        Path indexPath,
        Path logPath,
        String semanticKey,
        String family,
        String answerStatus,
        List<String> warnings) {
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("status", status);
        map.put("conceptPath", conceptPath.toAbsolutePath().toString());
        map.put("indexPath", indexPath.toAbsolutePath().toString());
        map.put("logPath", logPath.toAbsolutePath().toString());
        map.put("semanticKey", semanticKey);
        map.put("family", family);
        map.put("answerStatus", answerStatus);
        map.put("warnings", warnings);
        return map;
    }
}
```

`QuestionOkfCompiler` takes `Clock` in a package-private test constructor and uses `Clock.systemUTC()` publicly. Its `compile` method performs exactly:

1. `ArchitectureQuestionResult.from(request.result())`.
2. Reject non-compilable status with `IllegalArgumentException("Question status cannot be compiled: <status>")`.
3. `ProjectPathResolver.resolve(...)`.
4. `QuestionConceptIdentity.derive(...)`.
5. `QuestionOkfRenderer.render(..., Instant.now(clock))`.
6. `OkfEntryValidator.validateConcept(...)` before invoking the writer.
7. `OkfBundleWriter.write(...)` with `LocalDate.now(clock)`.
8. Convert `WriteOutcome` to `CompileOutcome` without changing its status/warnings.

- [ ] **Step 4: Implement the MCP adapter**

```java
public class CompileArchitectureQuestionToOkfTool {
    private final ModelCache cache;
    private final QuestionOkfCompiler compiler;

    public CompileArchitectureQuestionToOkfTool(ModelCache cache) {
        this(cache, new QuestionOkfCompiler());
    }

    CompileArchitectureQuestionToOkfTool(ModelCache cache, QuestionOkfCompiler compiler) {
        this.cache = cache;
        this.compiler = compiler;
    }

    public ToolResult execute(Map<String, Object> args) {
        try {
            GraphQuery graph = cache.graph();
            if (!graph.isIndexed()) return ToolResult.error("No workspace indexed yet. Call index_workspace first.");
            Map<String, Object> result = ToolArgs.getMap(args, "result");
            if (result == null) return ToolResult.error("Error: 'result' object is required.");
            List<String> roots = graph.allApplicationNodes().stream()
                    .map(GraphQuery.ApplicationNode::rootPath)
                    .filter(java.util.Objects::nonNull)
                    .distinct()
                    .toList();
            QuestionOkfCompiler.CompileOutcome outcome = compiler.compile(new QuestionOkfCompiler.CompileRequest(
                    result,
                    roots,
                    ToolArgs.getString(args, "projectPath"),
                    ToolArgs.getString(args, "bundlePath"),
                    ToolArgs.getString(args, "templatePath"),
                    ToolArgs.getBool(args, "allowOverwrite", false)));
            return new ToolResult(
                    "OKF investigation " + outcome.status() + ": " + outcome.conceptPath(), outcome.toMap());
        } catch (Exception error) {
            return ToolResult.error("Error compiling architecture question to OKF: " + error.getMessage());
        }
    }
}
```

- [ ] **Step 5: Register the tool schema and constructor dependency**

In `McpServer`:

1. Add `private final CompileArchitectureQuestionToOkfTool compileQuestionToOkfTool;`.
2. Construct it immediately after `architectureQuestionTool`.
3. Add `private static final String TYPE_BOOLEAN = "boolean";`.
4. Add `SchemaBuilder.req(String name, String type, String description)`:

```java
SchemaBuilder req(String name, String type, String description) {
    props.put(name, Map.of("type", type, "description", description));
    required.add(name);
    return this;
}
```

5. Register immediately after `answer_architecture_question`:

```java
specs.add(toolSpec(
        "compile_architecture_question_to_okf",
        "Compile Architecture Question to OKF",
        "Explicitly compile a reviewed answer_architecture_question structured result into one project-local OKF investigation concept.",
        schema().req("result", "object", "Exact structuredContent returned by answer_architecture_question")
                .opt("projectPath", TYPE_STRING, "Indexed project root; required when multiple roots are indexed")
                .opt("bundlePath", TYPE_STRING, "Project-relative OKF bundle path (default docs/agent-wiki)")
                .opt("templatePath", TYPE_STRING, "Optional project-relative custom Markdown template")
                .opt("allowOverwrite", TYPE_BOOLEAN, "Replace an existing ArchLens-generated concept (default false)"),
        schema().opt("status", TYPE_STRING, "created | updated | overwrite-required")
                .opt("conceptPath", TYPE_STRING, "Written or resolved concept path")
                .opt("indexPath", TYPE_STRING, "Bundle index path")
                .opt("logPath", TYPE_STRING, "Bundle log path")
                .opt("semanticKey", TYPE_STRING, "Full SHA-256 semantic key")
                .opt("family", TYPE_STRING, "Question family")
                .opt("answerStatus", TYPE_STRING, "resolved | partial | ambiguous")
                .opt("warnings", "array", "Overwrite and uncertainty warnings"),
        compileQuestionToOkfTool::execute));
```

Also add `.opt("request", "object", "Canonical semantic selectors used for durable concept identity")` to the `answer_architecture_question` output schema.

- [ ] **Step 6: Add the explicit compilation prompt**

Append a prompt named `compile_architecture_question_knowledge` with required `question` and `projectPath`, optional `bundlePath`, and this body:

```text
Build durable architecture knowledge for `{question}` in `{projectPath}`.

1. Call `answer_architecture_question` with `question: "{question}"`.
2. Review its structuredContent. Do not compile status `unsupported` or `needs-clarification`.
3. Treat `partial` and `ambiguous` as compilable knowledge and preserve their warnings.
4. Only after review, call `compile_architecture_question_to_okf` with the exact structuredContent as `result`, `projectPath: "{projectPath}"`, and `bundlePath: "{bundlePath}"` (default `docs/agent-wiki`).
5. If the compiler returns `overwrite-required`, report the target and ask for explicit authorization before retrying with `allowOverwrite: true`.
```

- [ ] **Step 7: Extend schema/prompt tests**

Add to `McpServerTest`:

```java
@Test
void compileQuestionToOkfSchema_requiresReviewedResultAndExposesWriteOutcome() {
    McpSchema.Tool tool = tool(new McpServer(), "compile_architecture_question_to_okf");
    assertThat(tool.inputSchema()).extractingByKey("required").isEqualTo(List.of("result"));
    assertThat((Map<?, ?>) tool.inputSchema().get("properties"))
            .containsKeys("result", "projectPath", "bundlePath", "templatePath", "allowOverwrite");
    assertThat(properties(tool))
            .containsKeys("status", "conceptPath", "indexPath", "logPath", "semanticKey", "warnings");
}
```

Extend the existing answer schema assertion with `request`, the tool-name list with the compiler, and the prompt-name assertion with `compile_architecture_question_knowledge`.

- [ ] **Step 8: Run Task 6 tests GREEN**

Run: `mvn -Dtest=CompileArchitectureQuestionToOkfToolTest,McpServerTest test`

Expected: PASS.

- [ ] **Step 9: Commit Task 6**

```bash
git add src/main/java/dev/dominikbreu/archlens/okf/QuestionOkfCompiler.java \
  src/main/java/dev/dominikbreu/archlens/mcp/tools/CompileArchitectureQuestionToOkfTool.java \
  src/main/java/dev/dominikbreu/archlens/mcp/McpServer.java \
  src/test/java/dev/dominikbreu/archlens/mcp/tools/CompileArchitectureQuestionToOkfToolTest.java \
  src/test/java/dev/dominikbreu/archlens/mcp/McpServerTest.java
git commit -m "feat: expose architecture question OKF compiler"
```

---

### Task 7: Document, Resync Agent Guidance, and Verify the Feature

**Files:**

- Modify: `docs/TOOLS.md`
- Modify: `docs/ARCHITECTURE.md`
- Modify: `llms.txt`
- Modify: `skills/spoon-understand/SKILL.md`
- Modify: `skills/spoon-understand/references/mcp-tool-map.md`
- Modify: `.agents/skills/spoon-understand/SKILL.md`
- Modify: `.agents/skills/spoon-understand/references/mcp-tool-map.md`

**Interfaces:**

- Documents the final MCP contract implemented in Task 6.
- Produces no new runtime API.

- [ ] **Step 1: Update the tool reference**

Add a `compile_architecture_question_to_okf` section immediately after `answer_architecture_question` in `docs/TOOLS.md`. Include:

- the mandatory explicit two-call sequence;
- the exact input fields/defaults;
- accepted and rejected answer statuses;
- `projectPath` single/multi-root behavior;
- project containment and custom-template rules;
- semantic identity and 12-hex filename suffix;
- `created`, `updated`, and `overwrite-required` outputs;
- generated/non-generated overwrite policy; and
- one JSON example passing the prior tool's `structuredContent` as `result`.

Update the common question envelope example to include `"request": {}`.

- [ ] **Step 2: Update architecture and agent notes**

In `docs/ARCHITECTURE.md`, add `dev.dominikbreu.archlens.okf` as a graph-independent compilation layer that consumes caller-supplied question results and accesses graph data only through the MCP adapter's indexed-root lookup.

In `llms.txt`, state:

```text
- Durable knowledge is always explicit: first call `answer_architecture_question`, review its structured result, then pass that exact result to `compile_architecture_question_to_okf`.
- Never compile `unsupported` or `needs-clarification`; preserve `partial` and `ambiguous` warnings.
- An existing concept requires a separate retry with `allowOverwrite=true`; never assume overwrite authorization.
```

- [ ] **Step 3: Update and resync the `spoon-understand` skill**

Add the compiler to the canonical `skills/spoon-understand/SKILL.md` Exports section and to `skills/spoon-understand/references/mcp-tool-map.md` next to `answer_architecture_question`. Explicitly instruct agents not to compile automatically and not to retry `overwrite-required` without permission.

Resync only the changed files:

```bash
cp skills/spoon-understand/SKILL.md .agents/skills/spoon-understand/SKILL.md
cp skills/spoon-understand/references/mcp-tool-map.md .agents/skills/spoon-understand/references/mcp-tool-map.md
```

Verify exact mirrors:

```bash
cmp skills/spoon-understand/SKILL.md .agents/skills/spoon-understand/SKILL.md
cmp skills/spoon-understand/references/mcp-tool-map.md .agents/skills/spoon-understand/references/mcp-tool-map.md
```

Expected: both `cmp` commands exit 0 with no output.

- [ ] **Step 4: Apply formatting and run focused tests**

Run:

```bash
mvn spotless:apply
mvn -Dtest=QuestionRequestNormalizerTest,AnswerArchitectureQuestionToolTest,ArchitectureQuestionResultTest,QuestionConceptIdentityTest,ProjectPathResolverTest,QuestionOkfRendererTest,OkfEntryValidatorTest,OkfBundleWriterTest,CompileArchitectureQuestionToOkfToolTest,McpServerTest test
```

Expected: BUILD SUCCESS and all named tests pass.

- [ ] **Step 5: Run the complete test and verification gates**

Run:

```bash
mvn test
mvn verify
```

Expected: both commands end with BUILD SUCCESS. Do not skip Spotless or SpotBugs. If `verify` reports formatting, run `mvn spotless:apply`, review the resulting diff, and rerun both commands.

- [ ] **Step 6: Inspect the final diff and generated-file exclusions**

Run:

```bash
git status --short
git diff --check
git diff --stat
git status --short | rg '(^|/)(target|\.archlens-cache)/|dependency-reduced-pom\.xml' && exit 1 || true
```

Expected: no whitespace errors and no generated output listed. Review every changed public type for Javadocs.

- [ ] **Step 7: Commit documentation and final formatting**

```bash
git add docs/TOOLS.md docs/ARCHITECTURE.md llms.txt \
  skills/spoon-understand/SKILL.md skills/spoon-understand/references/mcp-tool-map.md \
  .agents/skills/spoon-understand/SKILL.md .agents/skills/spoon-understand/references/mcp-tool-map.md \
  src/main/java src/test/java
git commit -m "docs: document architecture question OKF compilation"
```

- [ ] **Step 8: Record final verification evidence**

Run:

```bash
git status --short
git log -7 --oneline
```

Expected: clean worktree and one focused commit for each task. Report the exact `mvn test` and `mvn verify` results in the implementation handoff.
