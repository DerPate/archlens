# Dependency Upgrade Consolidation

## Problem

Six open Dependabot pull requests independently modify `pom.xml`. Two compete
to update the same Jackson version property, while the MCP SDK and JLine pull
requests are major-version upgrades. The MCP SDK pull request changes only its
version and currently fails compilation because SDK 2.0 changed tool input
schemas from `McpSchema.JsonSchema` to `Map<String, Object>`.

## Pull Request Structure

Create two human-owned draft pull requests based on current `main`.

### MCP SDK 2.0 Migration

This pull request updates `io.modelcontextprotocol.sdk:mcp` from 1.1.2 to 2.0.0
and contains every source and test adaptation required by the official SDK 2.0
migration guide. In particular, dashboard help rendering must consume tool input
schemas as maps. Schema construction should use the SDK 2.0-preferred map-based
API where practical, while preserving ArchLens's existing MCP wire behavior.

The migration remains isolated because it changes a runtime protocol library,
enables stricter schema validation, and may require behavioral tests beyond
compilation.

### Consolidated Maintenance Upgrades

This pull request contains the remaining current Dependabot updates:

- Jackson 3.0.3 to 3.2.0. The competing 3.1.1 update is superseded.
- JLine 3.27.1 to 4.2.1.
- Maven Source Plugin 3.3.1 to 3.4.0.
- JReleaser 1.23.0 to 1.24.0.

It also updates `.github/dependabot.yml` to group future non-major Maven
dependency updates and Maven plugin updates. Major updates remain separate so
breaking migrations do not hide inside routine maintenance bundles.

## Verification

For each branch, run focused tests for changed integration points followed by
`mvn test`, `mvn package`, and `mvn dependency:analyze`. Inspect the resolved
dependency tree to ensure the selected Jackson version is coherent.

For the MCP branch, test dashboard help rendering with map-based schemas and
exercise the MCP server handshake/tool surface. Validate generated tool schemas
under SDK 2.0's default schema validation.

For the maintenance branch, exercise dashboard/JLine tests and package/release
plugin goals that can run without publishing credentials. Review release notes
for each selected version before accepting the update.

Both pull requests remain drafts until their GitHub CI, CodeQL, and dependency
review checks are green.

## Superseded Pull Requests

After the replacement drafts exist and pass local verification, close bot pull
requests #19 through #24 as superseded, linking each closure to the appropriate
replacement draft. Do not close a bot pull request before its replacement is
available for review.
