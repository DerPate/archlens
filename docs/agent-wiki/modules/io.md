---
type: Module
title: IO
description: Safe, atomic file-write primitives shared by every tool that writes to disk.
tags: [archlens, module]
resource: src/main/java/dev/dominikbreu/archlens/io/
status: stable
generated: { by: claude-code/claude-sonnet-5, at: 2026-09-17T00:00:00Z }
verified: { by: human:dominik, at: 2026-09-17T00:00:00Z }
---

# IO

**Package**: `dev.dominikbreu.archlens.io`

`AtomicFileWriter` writes to a temp file and `FilePromoter` promotes it into place, so exports and generated docs never leave a half-written file behind. Used by the [okf](okf.md) module's `OkfBundleWriter` and by every `export_*`/`render_*` tool that takes an `outputPath`.
