---
type: Module
title: Merger
description: Merges deployment descriptors into the architecture model during indexing.
tags: [archlens, module]
resource: src/main/java/dev/dominikbreu/archlens/merger/
status: stable
generated: { by: claude-code/claude-sonnet-5, at: 2026-09-17T00:00:00Z }
verified: { by: human:dominik, at: 2026-09-17T00:00:00Z }
---

# Merger

**Package**: `dev.dominikbreu.archlens.merger`

`DockerComposeMerger` and `AnsibleMerger` parse `docker-compose`/Ansible deployment descriptors; `DeploymentMerger` combines their output into the model's `DeploymentEntry`/`Deployment` data, giving `query_architecture_graph`'s `DEPLOYS` edges their evidence.
