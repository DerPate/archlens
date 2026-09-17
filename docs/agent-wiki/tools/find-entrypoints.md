---
type: MCP Tool
title: find_entrypoints
description: Return architecturally relevant entry points: REST, messaging, schedulers, EJB, CDI, EventBus, WebSocket/SSE/gRPC, and more.
tags: [archlens, mcp-tool]
resource: src/main/java/dev/dominikbreu/archlens/mcp/tools/FindEntrypointsTool.java
status: stable
generated: { by: claude-code/claude-sonnet-5, at: 2026-09-17T00:00:00Z }
verified: { by: human:dominik, at: 2026-09-17T00:00:00Z }
sources:
  - id: tools-md
    resource: docs/TOOLS.md
    title: MCP Tool And Prompt Reference
---

# `find_entrypoints`

Return architecturally relevant entry points: REST, messaging, schedulers, EJB, CDI, EventBus, WebSocket/SSE/gRPC, and more.

**Arguments**: `appId`, `type` (`REST_ENDPOINT`, `JMS_CONSUMER`, `MESSAGING_CONSUMER`, `MESSAGING_PRODUCER`, `CDI_EVENT_OBSERVER`, `SCHEDULER`, `EJB_BUSINESS_METHOD`, `RMI_ENDPOINT`, `MAIN_METHOD`, `EVENT_BUS_CONSUMER`, `WEBSOCKET_ENDPOINT`, `SSE_ENDPOINT`, `GRPC_METHOD`, `UNKNOWN`), `httpMethod`, `path` (prefix match) - all optional and combinable.

Messaging entrypoints carry `channelName`, `broker` (`KAFKA`, `MQTT`, `AMQP`, `RABBITMQ`, `PULSAR`, `IN_MEMORY`, `UNKNOWN`), and `topic`. The broker is resolved from `mp.messaging.*.connector` config; channels with no connector referenced by both `@Incoming` and `@Outgoing` in the same module are tagged `IN_MEMORY`. Each entrypoint's `parameters` list powers [trace_data_flow](trace-data-flow.md).

Implemented by [`FindEntrypointsTool`](../modules/mcp-tools.md), backed by the [extractor](../modules/extractor.md) module. See the full reference in [MCP Tool And Prompt Reference](../../TOOLS.md).[^tools-md]

[^tools-md]: MCP Tool And Prompt Reference
