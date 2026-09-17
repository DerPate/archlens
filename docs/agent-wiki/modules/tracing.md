---
type: Module
title: Tracing
description: OpenTelemetry-style span configuration and a stdout exporter for local diagnostics.
tags: [archlens, module]
resource: src/main/java/dev/dominikbreu/archlens/tracing/
status: stable
generated: { by: claude-code/claude-sonnet-5, at: 2026-09-17T00:00:00Z }
verified: { by: human:dominik, at: 2026-09-17T00:00:00Z }
---

# Tracing

**Package**: `dev.dominikbreu.archlens.tracing`

`TracingConfig` configures instrumentation, `Spans` provides span helpers, and `StdoutSpanExporter` writes spans to stdout — a lightweight tracing setup for diagnosing the extraction/rendering pipeline locally, independent of an external collector.
