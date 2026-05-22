# OTel Tracing Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace `System.err.printf` timing instrumentation with proper OpenTelemetry spans across all heavy extractors, exportable to stdout or an OTLP endpoint via `-Dspoon.traces=console|otlp|none`.

**Architecture:** Two new classes in `dev.dominikbreu.spoonmcp.tracing` — `TracingConfig` (builds the `TracerProvider` from the system property, returns an `OpenTelemetry` instance) and `StdoutSpanExporter` (writes completed spans to stdout). `Main.main()` calls `TracingConfig.configure()` then `GlobalOpenTelemetry.set()` before starting the server. Extractors call `GlobalOpenTelemetry.getTracer()` via a static `tracer()` helper at use-time — not at class-load time — to avoid noop-snapshot issues.

**Tech Stack:** Java 21, Maven, OpenTelemetry Java SDK 1.40.0 (`opentelemetry-sdk`, `opentelemetry-exporter-otlp`), JUnit 5, AssertJ.

---

## File Structure

**New files:**
- `src/main/java/dev/dominikbreu/spoonmcp/tracing/TracingConfig.java` — reads `-Dspoon.traces`, builds `SdkTracerProvider`, returns `OpenTelemetry`
- `src/main/java/dev/dominikbreu/spoonmcp/tracing/StdoutSpanExporter.java` — formats completed spans to `System.out`
- `src/test/java/dev/dominikbreu/spoonmcp/tracing/StdoutSpanExporterTest.java`
- `src/test/java/dev/dominikbreu/spoonmcp/tracing/TracingConfigTest.java`

**Modified files:**
- `pom.xml` — add `otel.version` property, two OTel dependencies
- `src/main/java/dev/dominikbreu/spoonmcp/Main.java` — call `TracingConfig.configure()` + `GlobalOpenTelemetry.set()` before `McpServer`
- `src/main/java/dev/dominikbreu/spoonmcp/extractor/ArchitectureExtractor.java` — replace `System.err.printf` + `log()` with OTel spans
- `src/main/java/dev/dominikbreu/spoonmcp/extractor/PipelineGraphBuilder.java` — replace `System.err.printf` with OTel spans
- `src/main/java/dev/dominikbreu/spoonmcp/extractor/CallGraphExtractor.java` — add single `callgraph.extract` span
- `src/main/java/dev/dominikbreu/spoonmcp/extractor/DataFlowTracer.java` — add single `dataflow.trace` span with `paths-found` attribute
- `src/main/java/dev/dominikbreu/spoonmcp/extractor/SpringExtractor.java` — add single `spring.extract` span
- `src/main/java/dev/dominikbreu/spoonmcp/extractor/objectflow/ObjectFlowIndexBuilder.java` — add single `objectflow.build` span

---

## Task 1: Add OTel Dependencies

**Files:**
- Modify: `pom.xml`

- [ ] **Step 1: Add version property and two dependencies**

In `pom.xml`, add `<otel.version>1.40.0</otel.version>` inside `<properties>` (after `<slf4j.version>`):

```xml
<slf4j.version>2.0.17</slf4j.version>
<otel.version>1.40.0</otel.version>
```

Then add two dependencies inside `<dependencies>` (after the `slf4j-simple` block):

```xml
<dependency>
    <groupId>io.opentelemetry</groupId>
    <artifactId>opentelemetry-sdk</artifactId>
    <version>${otel.version}</version>
</dependency>
<dependency>
    <groupId>io.opentelemetry</groupId>
    <artifactId>opentelemetry-exporter-otlp</artifactId>
    <version>${otel.version}</version>
</dependency>
```

- [ ] **Step 2: Verify dependencies resolve**

Run from `/home/dominik/spoon-mcp-server`:
```bash
mvn compile -q
```

Expected: `BUILD SUCCESS`. If resolution fails, check the version — the latest stable OTel 1.x release may differ; try `1.39.0` as a fallback.

- [ ] **Step 3: Commit**

```bash
git add pom.xml
git commit -m "build: add opentelemetry-sdk and otlp exporter dependencies"
```

---

## Task 2: Implement StdoutSpanExporter With Tests

**Files:**
- Create: `src/main/java/dev/dominikbreu/spoonmcp/tracing/StdoutSpanExporter.java`
- Create: `src/test/java/dev/dominikbreu/spoonmcp/tracing/StdoutSpanExporterTest.java`

- [ ] **Step 1: Write failing tests**

Create `src/test/java/dev/dominikbreu/spoonmcp/tracing/StdoutSpanExporterTest.java`:

```java
package dev.dominikbreu.spoonmcp.tracing;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.assertj.core.api.Assertions.assertThat;

class StdoutSpanExporterTest {

    private PrintStream originalOut;
    private ByteArrayOutputStream captured;
    private Tracer tracer;

    @BeforeEach
    void setUp() {
        originalOut = System.out;
        captured = new ByteArrayOutputStream();
        System.setOut(new PrintStream(captured));

        SdkTracerProvider provider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(new StdoutSpanExporter()))
                .build();
        tracer = OpenTelemetrySdk.builder()
                .setTracerProvider(provider)
                .build()
                .getTracer("test");
    }

    @AfterEach
    void tearDown() {
        System.setOut(originalOut);
    }

    @Test
    void rootSpanIncludesTraceId() {
        Span span = tracer.spanBuilder("extract").startSpan();
        span.end();

        String out = captured.toString();
        assertThat(out).contains("[trace]");
        assertThat(out).contains("extract");
        assertThat(out).contains("ms");
        assertThat(out).contains("traceId=");
    }

    @Test
    void childSpanOmitsTraceId() {
        Span parent = tracer.spanBuilder("extract").startSpan();
        try (var ignored = parent.makeCurrent()) {
            Span child = tracer.spanBuilder("pass1-scan").startSpan();
            child.end();
        } finally {
            parent.end();
        }

        // child line should not contain traceId, parent line should
        String[] lines = captured.toString().split(System.lineSeparator());
        String childLine = lines[0]; // child ends first
        String parentLine = lines[1];
        assertThat(childLine).contains("pass1-scan").doesNotContain("traceId=");
        assertThat(parentLine).contains("extract").contains("traceId=");
    }

    @Test
    void spanAttributesAppearAsKeyValuePairs() {
        Span span = tracer.spanBuilder("pass1-scan").startSpan();
        span.setAttribute("modules", 3L);
        span.end();

        assertThat(captured.toString()).contains("modules=3");
    }

    @Test
    void errorSpanIncludesErrorField() {
        Span span = tracer.spanBuilder("extract").startSpan();
        span.setStatus(StatusCode.ERROR, "something went wrong");
        span.end();

        assertThat(captured.toString()).contains("error=something went wrong");
    }
}
```

- [ ] **Step 2: Run tests to confirm they fail**

```bash
mvn -Dtest='dev.dominikbreu.spoonmcp.tracing.StdoutSpanExporterTest' test
```

Expected: compilation failure — `StdoutSpanExporter` does not exist yet.

- [ ] **Step 3: Implement StdoutSpanExporter**

Create `src/main/java/dev/dominikbreu/spoonmcp/tracing/StdoutSpanExporter.java`:

```java
package dev.dominikbreu.spoonmcp.tracing;

import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.data.StatusData;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.sdk.trace.export.SpanExporter;

import java.util.Collection;
import java.util.concurrent.TimeUnit;

public class StdoutSpanExporter implements SpanExporter {

    @Override
    public CompletableResultCode export(Collection<? extends SpanData> spans) {
        for (SpanData span : spans) {
            System.out.println(format(span));
        }
        return CompletableResultCode.ofSuccess();
    }

    @Override
    public CompletableResultCode flush() {
        return CompletableResultCode.ofSuccess();
    }

    @Override
    public CompletableResultCode shutdown() {
        return CompletableResultCode.ofSuccess();
    }

    static String format(SpanData span) {
        long durationMs = TimeUnit.NANOSECONDS.toMillis(
                span.getEndEpochNanos() - span.getStartEpochNanos());

        boolean isRoot = !span.getParentSpanContext().isValid();

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("[trace] %-36s %dms", span.getName(), durationMs));

        if (isRoot) {
            sb.append("  traceId=").append(span.getTraceId());
        }

        span.getAttributes().forEach((key, value) ->
                sb.append("  ").append(key.getKey()).append("=").append(value));

        StatusData status = span.getStatus();
        if (status.getStatusCode() == StatusCode.ERROR) {
            String desc = status.getDescription();
            if (desc != null && !desc.isBlank()) {
                sb.append("  error=").append(desc);
            }
        }

        return sb.toString();
    }
}
```

- [ ] **Step 4: Run tests to confirm they pass**

```bash
mvn -Dtest='dev.dominikbreu.spoonmcp.tracing.StdoutSpanExporterTest' test
```

Expected: `Tests run: 4, Failures: 0, Errors: 0`.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/dev/dominikbreu/spoonmcp/tracing/StdoutSpanExporter.java \
        src/test/java/dev/dominikbreu/spoonmcp/tracing/StdoutSpanExporterTest.java
git commit -m "feat: add StdoutSpanExporter for console tracing output"
```

---

## Task 3: Implement TracingConfig And Wire Main

**Files:**
- Create: `src/main/java/dev/dominikbreu/spoonmcp/tracing/TracingConfig.java`
- Create: `src/test/java/dev/dominikbreu/spoonmcp/tracing/TracingConfigTest.java`
- Modify: `src/main/java/dev/dominikbreu/spoonmcp/Main.java`

- [ ] **Step 1: Write failing tests**

Create `src/test/java/dev/dominikbreu/spoonmcp/tracing/TracingConfigTest.java`:

```java
package dev.dominikbreu.spoonmcp.tracing;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.assertj.core.api.Assertions.assertThat;

class TracingConfigTest {

    @AfterEach
    void clearProperty() {
        System.clearProperty("spoon.traces");
        System.clearProperty("spoon.otlp.endpoint");
    }

    @Test
    void noneModeReturnsNoop() {
        System.setProperty("spoon.traces", "none");
        OpenTelemetry otel = TracingConfig.configure("test-service");

        // Noop spans are not recorded
        Span span = otel.getTracer("test").spanBuilder("my-span").startSpan();
        assertThat(span.getSpanContext().isValid()).isFalse();
        span.end();
    }

    @Test
    void defaultModeIsNoop() {
        // no system property set
        OpenTelemetry otel = TracingConfig.configure("test-service");

        Span span = otel.getTracer("test").spanBuilder("my-span").startSpan();
        assertThat(span.getSpanContext().isValid()).isFalse();
        span.end();
    }

    @Test
    void consoleModeProducesValidSpans() {
        System.setProperty("spoon.traces", "console");

        PrintStream originalOut = System.out;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        System.setOut(new PrintStream(captured));
        try {
            OpenTelemetry otel = TracingConfig.configure("test-service");

            Span span = otel.getTracer("test").spanBuilder("my-span").startSpan();
            assertThat(span.getSpanContext().isValid()).isTrue();
            span.end();

            // flush the simple span processor
            if (otel instanceof OpenTelemetrySdk sdk) {
                ((SdkTracerProvider) sdk.getTracerProvider()).forceFlush();
            }

            assertThat(captured.toString()).contains("[trace]").contains("my-span");
        } finally {
            System.setOut(originalOut);
        }
    }

    @Test
    void otlpModeProducesValidSpans() {
        System.setProperty("spoon.traces", "otlp");
        System.setProperty("spoon.otlp.endpoint", "http://localhost:4317");

        OpenTelemetry otel = TracingConfig.configure("test-service");

        // Should succeed even without a real collector — spans are buffered
        Span span = otel.getTracer("test").spanBuilder("my-span").startSpan();
        assertThat(span.getSpanContext().isValid()).isTrue();
        span.end();
    }

    @Test
    void unknownModeDefaultsToNoop() {
        System.setProperty("spoon.traces", "garbage");
        OpenTelemetry otel = TracingConfig.configure("test-service");

        Span span = otel.getTracer("test").spanBuilder("my-span").startSpan();
        assertThat(span.getSpanContext().isValid()).isFalse();
        span.end();
    }
}
```

- [ ] **Step 2: Run tests to confirm they fail**

```bash
mvn -Dtest='dev.dominikbreu.spoonmcp.tracing.TracingConfigTest' test
```

Expected: compilation failure — `TracingConfig` does not exist yet.

- [ ] **Step 3: Implement TracingConfig**

Create `src/main/java/dev/dominikbreu/spoonmcp/tracing/TracingConfig.java`:

```java
package dev.dominikbreu.spoonmcp.tracing;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TracingConfig {

    private static final Logger log = LoggerFactory.getLogger(TracingConfig.class);

    private TracingConfig() {}

    /**
     * Reads {@code -Dspoon.traces=none|console|otlp} and builds an OpenTelemetry instance.
     * The caller is responsible for registering it via {@code GlobalOpenTelemetry.set()}.
     * Falls back to noop if configuration fails.
     */
    public static OpenTelemetry configure(String serviceName) {
        String mode = System.getProperty("spoon.traces", "none");
        String endpoint = System.getProperty("spoon.otlp.endpoint", "http://localhost:4317");
        try {
            Resource resource = Resource.create(Attributes.of(
                    AttributeKey.stringKey("service.name"), serviceName));

            SdkTracerProvider tracerProvider = switch (mode) {
                case "console" -> SdkTracerProvider.builder()
                        .setResource(resource)
                        .addSpanProcessor(SimpleSpanProcessor.create(new StdoutSpanExporter()))
                        .build();
                case "otlp" -> SdkTracerProvider.builder()
                        .setResource(resource)
                        .addSpanProcessor(BatchSpanProcessor.builder(
                                OtlpGrpcSpanExporter.builder()
                                        .setEndpoint(endpoint)
                                        .build())
                                .build())
                        .build();
                default -> SdkTracerProvider.builder()
                        .setResource(resource)
                        .build();
            };

            return OpenTelemetrySdk.builder()
                    .setTracerProvider(tracerProvider)
                    .build();
        } catch (Exception e) {
            log.warn("Failed to configure tracing (mode={}) — using noop: {}", mode, e.getMessage());
            return OpenTelemetry.noop();
        }
    }
}
```

- [ ] **Step 4: Run TracingConfig tests**

```bash
mvn -Dtest='dev.dominikbreu.spoonmcp.tracing.TracingConfigTest' test
```

Expected: `Tests run: 5, Failures: 0, Errors: 0`.

- [ ] **Step 5: Wire TracingConfig into Main.java**

Replace the body of `Main.java` with:

```java
package dev.dominikbreu.spoonmcp;

import dev.dominikbreu.spoonmcp.mcp.McpServer;
import dev.dominikbreu.spoonmcp.tracing.TracingConfig;
import io.opentelemetry.api.GlobalOpenTelemetry;

public class Main {
    private Main() {}

    public static void main(String[] args) {
        GlobalOpenTelemetry.set(TracingConfig.configure("spoon-mcp-server"));
        new McpServer().run();
    }
}
```

- [ ] **Step 6: Run full test suite**

```bash
mvn test
```

Expected: all tests pass. (`GlobalOpenTelemetry.set()` is only called once in production; tests do not go through `Main.main()` so there is no conflict.)

- [ ] **Step 7: Commit**

```bash
git add src/main/java/dev/dominikbreu/spoonmcp/tracing/TracingConfig.java \
        src/test/java/dev/dominikbreu/spoonmcp/tracing/TracingConfigTest.java \
        src/main/java/dev/dominikbreu/spoonmcp/Main.java
git commit -m "feat: add TracingConfig and wire OTel into server startup"
```

---

## Task 4: Instrument ArchitectureExtractor

**Files:**
- Modify: `src/main/java/dev/dominikbreu/spoonmcp/extractor/ArchitectureExtractor.java`

- [ ] **Step 1: Replace System.err.printf and the log() helper with OTel spans**

The current `extract()` method (lines ~45–100) uses `long tN` variables and a `log(phase, startMs)` helper that calls `System.err.printf`. Replace the entire `extract()` method and delete the `log()` helper. Add these imports:

```java
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
```

Replace the `extract()` method body and remove the `log()` method. The new `extract()` method:

```java
public ArchitectureModel extract(List<String> projectPaths) {
    ArchitectureModel model = new ArchitectureModel(String.join(",", projectPaths));
    Span extractSpan = tracer().spanBuilder("extract")
            .setAttribute("workspace-path", model.workspacePath)
            .startSpan();
    try (Scope extractScope = extractSpan.makeCurrent()) {

        // Pass 1: components + entrypoints per project/module, with WAR role assignment
        Map<String, CtModel> ctModels = new LinkedHashMap<>();
        Span pass1 = tracer().spanBuilder("pass1-scan").startSpan();
        try (Scope s = pass1.makeCurrent()) {
            for (String path : projectPaths) {
                BuildProject project = buildMetadataService.detect(new File(path));
                registerBuildProject(project, model, ctModels);
            }
            pass1.setAttribute("modules", (long) ctModels.size());
        } finally {
            pass1.end();
        }

        // Pass 2: injection dependencies (needs all components to be known)
        Span pass2 = tracer().spanBuilder("pass2-deps").startSpan();
        try (Scope s = pass2.makeCurrent()) {
            for (CtModel ctModel : ctModels.values()) {
                dependencyExtractor.extract(ctModel, model);
            }
            eventBusExtractor.linkCrossModuleEvents(model);
        } finally {
            pass2.end();
        }

        // Pass 2b: call graph — actual method invocations between components
        Span pass2b = tracer().spanBuilder("pass2b-callgraph").startSpan();
        try (Scope s = pass2b.makeCurrent()) {
            pass2b.setAttribute("modules", (long) ctModels.size());
            for (CtModel ctModel : ctModels.values()) {
                ObjectFlowIndex objectFlowIndex = new ObjectFlowIndexBuilder().build(ctModel, model);
                new CallGraphExtractor(objectFlowIndex).extract(ctModel, model);
            }
        } finally {
            pass2b.end();
        }

        // Pass 2c: data-flow tracing — parameter propagation to sinks
        Span pass2c = tracer().spanBuilder("pass2c-dataflow").startSpan();
        try (Scope s = pass2c.makeCurrent()) {
            List<DataFlowPath> paths = dataFlowTracer.trace(model);
            model.dataFlowPaths.addAll(paths);
            pass2c.setAttribute("paths-found", (long) paths.size());
        } finally {
            pass2c.end();
        }

        // Pass 3: container inference
        // Pass 4: messaging broker resolution + external system inference
        Span pass34 = tracer().spanBuilder("pass3-4-runtime").startSpan();
        try (Scope s = pass34.makeCurrent()) {
            model.containers.addAll(containerInferrer.infer(model.components));
            externalSystemInferrer.infer(model);
            for (Entrypoint entrypoint : model.entrypoints) {
                RuntimeFlow flow = runtimeFlowInferrer.infer(entrypoint.id, 5, model);
                if (flow != null) {
                    model.runtimeFlows.add(flow);
                }
            }
        } finally {
            pass34.end();
        }

    } catch (RuntimeException e) {
        extractSpan.recordException(e);
        extractSpan.setStatus(StatusCode.ERROR, e.getMessage());
        throw e;
    } finally {
        extractSpan.end();
    }
    return model;
}

private static Tracer tracer() {
    return GlobalOpenTelemetry.getTracer("dev.dominikbreu.spoonmcp");
}
```

Also delete the old `log()` method:
```java
// DELETE this method:
private static void log(String phase, long startMs) {
    System.err.printf("[extractor] %s: %dms%n", phase, System.currentTimeMillis() - startMs);
}
```

- [ ] **Step 2: Run extractor tests**

```bash
mvn -Dtest='dev.dominikbreu.spoonmcp.extractor.ArchitectureExtractorTest' test
```

Expected: all pass.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/dev/dominikbreu/spoonmcp/extractor/ArchitectureExtractor.java
git commit -m "feat: instrument ArchitectureExtractor with OTel spans"
```

---

## Task 5: Instrument PipelineGraphBuilder

**Files:**
- Modify: `src/main/java/dev/dominikbreu/spoonmcp/extractor/PipelineGraphBuilder.java`

- [ ] **Step 1: Replace System.err.printf with OTel spans**

Add these imports to `PipelineGraphBuilder.java`:

```java
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
```

Replace the `build()` method (currently lines ~82–107). The new version:

```java
public List<Chain> build(ArchitectureModel model, int maxDepth) {
    if (model == null || model.dataFlowPaths == null || model.dataFlowPaths.isEmpty()) {
        return List.of();
    }
    Span buildSpan = tracer().spanBuilder("pipeline.build").startSpan();
    try (Scope buildScope = buildSpan.makeCurrent()) {

        Span wfSpan = tracer().spanBuilder("pipeline.workflow-graph").startSpan();
        WorkflowGraph workflowGraph;
        try (Scope s = wfSpan.makeCurrent()) {
            workflowGraph = new WorkflowGraphBuilder().build(model);
            wfSpan.setAttribute("roots", (long) workflowGraph.rootPaths().size());
            wfSpan.setAttribute("links", (long) workflowGraph.totalLinks());
        } finally {
            wfSpan.end();
        }

        List<Chain> chains = new ArrayList<>();
        Span dfsSpan = tracer().spanBuilder("pipeline.dfs").startSpan();
        try (Scope s = dfsSpan.makeCurrent()) {
            for (DataFlowPath p : workflowGraph.rootPaths()) {
                extend(new ArrayList<>(), p, null, null, workflowGraph, chains, maxDepth,
                        new LinkedHashSet<>(), new LinkedHashSet<>());
            }
            dfsSpan.setAttribute("raw-chains", (long) chains.size());
        } finally {
            dfsSpan.end();
        }

        List<Chain> result;
        Span dedupSpan = tracer().spanBuilder("pipeline.dedup").startSpan();
        try (Scope s = dedupSpan.makeCurrent()) {
            result = removeDuplicateChains(removePrefixChains(chains));
            dedupSpan.setAttribute("final-chains", (long) result.size());
        } finally {
            dedupSpan.end();
        }

        return result;
    } finally {
        buildSpan.end();
    }
}

private static Tracer tracer() {
    return GlobalOpenTelemetry.getTracer("dev.dominikbreu.spoonmcp");
}
```

- [ ] **Step 2: Run pipeline tests**

```bash
mvn -Dtest='dev.dominikbreu.spoonmcp.extractor.PipelineGraphBuilderTest,dev.dominikbreu.spoonmcp.renderer.PipelineRendererIntegrationTest,dev.dominikbreu.spoonmcp.mcp.tools.RenderPipelineToolTest' test
```

Expected: all pass.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/dev/dominikbreu/spoonmcp/extractor/PipelineGraphBuilder.java
git commit -m "feat: instrument PipelineGraphBuilder with OTel spans"
```

---

## Task 6: Add Single Spans To Remaining Extractors

**Files:**
- Modify: `src/main/java/dev/dominikbreu/spoonmcp/extractor/CallGraphExtractor.java`
- Modify: `src/main/java/dev/dominikbreu/spoonmcp/extractor/DataFlowTracer.java`
- Modify: `src/main/java/dev/dominikbreu/spoonmcp/extractor/SpringExtractor.java`
- Modify: `src/main/java/dev/dominikbreu/spoonmcp/extractor/objectflow/ObjectFlowIndexBuilder.java`

Each extractor gets the same three OTel imports and a `tracer()` helper. The span wraps only the public entry method; no children are added yet.

- [ ] **Step 1: Add OTel imports and tracer() helper to all four files**

For each of the four files, add these imports (after existing imports):

```java
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
```

And add this private method to each class:

```java
private static Tracer tracer() {
    return GlobalOpenTelemetry.getTracer("dev.dominikbreu.spoonmcp");
}
```

- [ ] **Step 2: Wrap CallGraphExtractor.extract() with a span**

`CallGraphExtractor.extract()` starts at line ~159. Add the span open as the first statement and a try/finally around the entire existing body. The transformation pattern is:

**Before (existing first line of method body):**
```java
public void extract(CtModel ctModel, ArchitectureModel model) {
    Map<String, Component> byId = new HashMap<>();
    // ... rest of method ...
}
```

**After:**
```java
public void extract(CtModel ctModel, ArchitectureModel model) {
    Span span = tracer().spanBuilder("callgraph.extract").startSpan();
    try (Scope scope = span.makeCurrent()) {
        Map<String, Component> byId = new HashMap<>();
        // ... ALL existing statements remain exactly as-is ...
    } finally {
        span.end();
    }
}
```

Do not change any logic inside — only add the span open at the top and the try/finally wrapping the entire existing body.

- [ ] **Step 3: Wrap DataFlowTracer.trace() with a span**

`DataFlowTracer.trace()` starts at line ~51 and ends with `return result;`. Two changes:
1. Add span open as first statement + try/finally wrapping
2. Add `span.setAttribute(...)` just before the existing `return result;`

**Before (existing last two lines of method body):**
```java
        // (existing code above unchanged)
        return result;
    }
```

**After:**
```java
public List<DataFlowPath> trace(ArchitectureModel model) {
    Span span = tracer().spanBuilder("dataflow.trace").startSpan();
    try (Scope scope = span.makeCurrent()) {
        // ... ALL existing statements remain exactly as-is ...
        span.setAttribute("paths-found", (long) result.size());
        return result;
    } finally {
        span.end();
    }
}
```

- [ ] **Step 4: Wrap SpringExtractor.extract() with a span**

`SpringExtractor.extract()` starts at line ~58, returns void, and ends with `}`. Add span open as first statement + try/finally around the entire body. Same transformation pattern as Step 2:

```java
public void extract(Collection<CtType<?>> types, ArchitectureModel model, String appId) {
    Span span = tracer().spanBuilder("spring.extract").startSpan();
    try (Scope scope = span.makeCurrent()) {
        // ALL existing statements remain exactly as-is
    } finally {
        span.end();
    }
}
```

- [ ] **Step 5: Wrap ObjectFlowIndexBuilder.build() with a span**

`ObjectFlowIndexBuilder.build()` starts at line ~32 and ends with `return index;`. Same transformation pattern as Step 2:

```java
public ObjectFlowIndex build(CtModel ctModel, ArchitectureModel architecture) {
    Span span = tracer().spanBuilder("objectflow.build").startSpan();
    try (Scope scope = span.makeCurrent()) {
        // ALL existing statements remain exactly as-is
        // The final two lines are:
        //   ObjectFlowIndex index = new ObjectFlowIndex(types, implementations, receiverTargets(...));
        //   return index;
    } finally {
        span.end();
    }
}
```

- [ ] **Step 6: Run full test suite**

```bash
mvn test
```

Expected: all existing tests pass — span wrappers are noop in tests because `GlobalOpenTelemetry` is not set by the tests.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/dev/dominikbreu/spoonmcp/extractor/CallGraphExtractor.java \
        src/main/java/dev/dominikbreu/spoonmcp/extractor/DataFlowTracer.java \
        src/main/java/dev/dominikbreu/spoonmcp/extractor/SpringExtractor.java \
        src/main/java/dev/dominikbreu/spoonmcp/extractor/objectflow/ObjectFlowIndexBuilder.java
git commit -m "feat: add OTel spans to callgraph dataflow spring and objectflow extractors"
```
