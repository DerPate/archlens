# OTel Tracing Design

Date: 2026-05-22

## Goal

Replace ad-hoc `System.err.printf` timing instrumentation with proper OpenTelemetry spans across all heavy extractors. Spans are exported to stdout (console) or an OTLP endpoint, selected by a JVM system property. Default is noop — zero overhead when tracing is off.

## System Property

```
-Dspoon.traces=none|console|otlp   (default: none)
-Dspoon.otlp.endpoint=<url>        (default: http://localhost:4317)
```

## Dependencies

Add to `pom.xml`:

```xml
<otel.version>1.40.0</otel.version>

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

No additional dependency for the console exporter — it is implemented in-project.

## New Package: `dev.dominikbreu.spoonmcp.tracing`

### `TracingConfig`

Reads `-Dspoon.traces`, builds a `SdkTracerProvider` with the appropriate `SpanExporter`, then calls `GlobalOpenTelemetry.set(sdk)`. Called once at server startup in `McpServer.start()` before any extractor runs.

```
mode=none    → SdkTracerProvider with no exporter (noop, zero overhead)
mode=console → SdkTracerProvider with StdoutSpanExporter
mode=otlp    → SdkTracerProvider with OtlpGrpcSpanExporter pointing at spoon.otlp.endpoint
```

If `build()` itself throws (bad config, missing dep), it catches the exception, logs a warning via SLF4J, and leaves `GlobalOpenTelemetry` as its default noop — the server still starts normally.

### `StdoutSpanExporter`

Implements `SpanExporter`. On `export(spans)`, writes one line per span to `System.out`:

```
[trace] extract                 1234ms  traceId=abc123
[trace] extract/pass1-scan       342ms
[trace] extract/pass2b-callgraph 891ms  modules=3
[trace] pipeline.build           456ms  traceId=def456
[trace] pipeline.build/dfs        98ms  raw-chains=241  final-chains=12
```

Format rules:
- Name is the span name, prefixed with parent name if a child span (`parent/child`).
- Duration is wall-clock elapsed in milliseconds.
- `traceId` is printed only on root spans (no parent) for correlation.
- Span attributes are appended as `key=value` pairs.
- Error spans append `error=<message>`.

## Wiring Point

`Main.main()` calls `TracingConfig.build("spoon-mcp-server")` as the very first statement, before `new McpServer()` is constructed. This ensures `GlobalOpenTelemetry` is set before any extractor class is loaded.

No Tracer is passed through constructors. All instrumented classes obtain a tracer via a static helper method, not a static final field — `GlobalOpenTelemetry.getTracer()` returns a noop snapshot if called before `set()`, so caching it at class-load time is unsafe:

```java
private static Tracer tracer() {
    return GlobalOpenTelemetry.getTracer("dev.dominikbreu.spoonmcp");
}
```

## Span Structure

### `ArchitectureExtractor.extract()`

```
extract                          root span, attr: workspace-path
├── pass1-scan                   attr: modules
├── pass2-deps
├── pass2b-callgraph             attr: modules
│   └── objectflow.build         one child per module (from ObjectFlowIndexBuilder)
├── pass2c-dataflow              attr: paths-found
└── pass3-4-runtime
```

### `PipelineGraphBuilder.build()`

```
pipeline.build                   root span
├── pipeline.workflow-graph      attr: roots, links
├── pipeline.dfs                 attr: raw-chains
└── pipeline.dedup               attr: final-chains
```

### Single-span extractors

Each wraps its main entry method with one span. No children yet — can be decomposed once profiling reveals where time is spent.

| Span name         | Class                   | Entry method       | Attributes         |
|-------------------|-------------------------|--------------------|--------------------|
| `callgraph.extract` | `CallGraphExtractor`  | `extract()`        | —                  |
| `dataflow.trace`  | `DataFlowTracer`        | `trace()`          | `paths-found`      |
| `spring.extract`  | `SpringExtractor`       | `extract()`        | —                  |
| `objectflow.build`| `ObjectFlowIndexBuilder`| `build()`          | — (nests under `pass2b-callgraph` automatically via OTel thread-local context when called from `ArchitectureExtractor`) |

## Error Handling

If a span's body throws, the span is recorded as `ERROR` with the exception message as the `error.message` attribute, then the exception is rethrown. Spans never swallow exceptions.

## Files Changed

**New:**
- `src/main/java/dev/dominikbreu/spoonmcp/tracing/TracingConfig.java`
- `src/main/java/dev/dominikbreu/spoonmcp/tracing/StdoutSpanExporter.java`

**Modified:**
- `pom.xml` — OTel version property + two dependencies
- `src/main/java/dev/dominikbreu/spoonmcp/Main.java` — call `TracingConfig.build()` as first statement in `main()`
- `src/main/java/dev/dominikbreu/spoonmcp/extractor/ArchitectureExtractor.java` — replace `System.err.printf` with spans
- `src/main/java/dev/dominikbreu/spoonmcp/extractor/PipelineGraphBuilder.java` — replace `System.err.printf` with spans
- `src/main/java/dev/dominikbreu/spoonmcp/extractor/CallGraphExtractor.java` — add single span
- `src/main/java/dev/dominikbreu/spoonmcp/extractor/DataFlowTracer.java` — add single span
- `src/main/java/dev/dominikbreu/spoonmcp/extractor/SpringExtractor.java` — add single span
- `src/main/java/dev/dominikbreu/spoonmcp/extractor/objectflow/ObjectFlowIndexBuilder.java` — add single span

## Out of Scope

- Propagating trace context across MCP tool calls (no distributed tracing across client/server boundary)
- Metrics (counters, histograms) — tracing only
- Log correlation (injecting traceId into SLF4J MDC)
- Renderer or cache layer spans
