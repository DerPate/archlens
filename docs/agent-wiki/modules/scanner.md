---
type: Module
title: Scanner
description: Spoon-based Java AST scanning that the extraction pipeline runs on.
tags: [archlens, module]
resource: src/main/java/dev/dominikbreu/archlens/scanner/
status: stable
generated: { by: claude-code/claude-sonnet-5, at: 2026-09-17T00:00:00Z }
verified: { by: human:dominik, at: 2026-09-17T00:00:00Z }
---

# Scanner

**Package**: `dev.dominikbreu.archlens.scanner`

`SpoonScanner` is the single component in this package and the structural foundation the [extractor](extractor.md) pipeline builds on: it parses project sources into an AST that the six extraction passes walk.
