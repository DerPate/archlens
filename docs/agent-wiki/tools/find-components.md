---
type: MCP Tool
title: find_components
description: Return architecture-relevant components (services, repositories, EJBs, entities, etc.).
tags: [archlens, mcp-tool]
resource: src/main/java/dev/dominikbreu/archlens/mcp/tools/FindComponentsTool.java
status: stable
generated: { by: claude-code/claude-sonnet-5, at: 2026-09-17T00:00:00Z }
verified: { by: human:dominik, at: 2026-09-17T00:00:00Z }
sources:
  - id: tools-md
    resource: docs/TOOLS.md
    title: MCP Tool And Prompt Reference
---

# `find_components`

Return architecture-relevant components (services, repositories, EJBs, entities, etc.).

**Arguments**: `appId`, `type` (`REST_RESOURCE`, `SERVICE`, `REPOSITORY`, `ENTITY`, `EJB_STATELESS`, `EJB_STATEFUL`, `EJB_SINGLETON`, `MESSAGE_DRIVEN_BEAN`, `SCHEDULER`, `HTTP_CLIENT`, `CDI_EVENT_CONSUMER`, `CDI_EVENT_PRODUCER`, `REMOTE_SERVICE`, `UTILITY`, `UNKNOWN`), `technology` (`spring`, `quarkus`, `javaee`, `jpa`) - all optional.

This is the tool used to derive this bundle's own [module](../modules/index.md) breakdown: 107 `SERVICE`, 78 `UNKNOWN`, and 47 `ENTITY` components across ArchLens's own 232 indexed components.

Implemented by [`FindComponentsTool`](../modules/mcp-tools.md), backed by the [cache](../modules/cache.md) module. See the full reference in [MCP Tool And Prompt Reference](../../TOOLS.md).[^tools-md]

[^tools-md]: MCP Tool And Prompt Reference
