---
type: Module
title: Build
description: Detects the build system (Maven/Gradle) and module layout of an indexed project.
tags: [archlens, module]
resource: src/main/java/dev/dominikbreu/archlens/build/
status: stable
generated: { by: claude-code/claude-sonnet-5, at: 2026-09-17T00:00:00Z }
verified: { by: human:dominik, at: 2026-09-17T00:00:00Z }
---

# Build

**Package**: `dev.dominikbreu.archlens.build`

`BuildMetadataService` delegates to `MavenBuildProjectDetector`, `GradleBuildProjectDetector`, or `UnknownBuildProjectDetector` (via the `BuildProjectDetector` interface) to populate `BuildProject`/`BuildModule` records, used during `index_workspace` and by [extractor](extractor.md)'s persistence-topology and transaction-policy extraction to locate module roots.
