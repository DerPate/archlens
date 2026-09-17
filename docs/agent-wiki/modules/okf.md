---
type: Module
title: OKF Compiler
description: Graph-independent layer that compiles a reviewed answer_architecture_question result into an OKF concept.
tags: [archlens, module]
resource: src/main/java/dev/dominikbreu/archlens/okf/
status: stable
generated: { by: claude-code/claude-sonnet-5, at: 2026-09-17T00:00:00Z }
verified: { by: human:dominik, at: 2026-09-17T00:00:00Z }
sources:
  - id: generated-architecture-md
    resource: docs/GENERATED_ARCHITECTURE.md
    title: Generated Architecture (self-doc)
---

# OKF Compiler

**Package**: `dev.dominikbreu.archlens.okf`

As `docs/GENERATED_ARCHITECTURE.md` puts it: this package "does not retain or query the extraction model." `QuestionOkfCompiler` derives a stable semantic key from the normalized family/request, `QuestionConceptIdentity` and `AnswerValueRenderer` support that; `QuestionOkfRenderer` renders the concept body; `OkfBundleWriter` (via [io](io.md)'s `AtomicFileWriter`/`FilePromoter`) writes it safely; `OkfEntryValidator` checks conformance; `ProjectPathResolver` resolves contained paths within an indexed project root. This module is what makes ArchLens itself an OKF *producer*, not just a subject — see [compile_architecture_question_to_okf](../tools/compile-architecture-question-to-okf.md).[^generated-architecture-md]

[^generated-architecture-md]: Generated Architecture (self-doc)
