# Spoon MCP Server

Spoon MCP Server is a Java 21 Model Context Protocol server that analyzes Java workspaces with Spoon and exposes architecture-oriented tools over stdio.

It can index Java projects, identify applications and entry points, infer logical containers, trace component dependencies, and render Mermaid flowcharts or sequence diagrams for agent-friendly architecture exploration.

## Features

- Index one or more Java project roots.
- Detect REST endpoints, JMS consumers, schedulers, EJB methods, services, repositories, entities, and related components.
- Infer container-level groupings such as API, service, repository, domain, messaging, and scheduling.
- Merge deployment hints from Docker Compose and Ansible assets.
- Render Mermaid flowcharts and sequence diagrams from the extracted model.

## Requirements

- Java 21 or newer
- Maven 3.9 or newer

## Build

```sh
mvn test
mvn package
```

The packaged server jar is written to `target/spoon-mcp-server-1.0.0-SNAPSHOT.jar`.

## Run

```sh
java -jar target/spoon-mcp-server-1.0.0-SNAPSHOT.jar
```

The server reads JSON-RPC messages from stdin and writes responses to stdout. Configure your MCP client to launch the jar with the command above.

For step-by-step install instructions and example MCP client configurations (Claude Desktop, Claude Code, generic stdio clients), see [docs/INSTALL.md](docs/INSTALL.md).

## MCP Tools

- `index_workspace`
- `list_apps`
- `find_entrypoints`
- `find_components`
- `get_component_dependencies`
- `infer_containers`
- `render_mermaid_flowchart`
- `get_runtime_flow`
- `render_mermaid_sequence`
- `render_source_overview`
- `render_dependency_map`
- `render_component_dependency_diagram`
- `export_architecture_docs`
- `export_graph_architecture_poc`
- `query_architecture_graph`
- `explain_architecture`

See `docs/TOOLS.md` for arguments and example payloads.

## Cache Backend

The default cache stores the latest architecture model as JSON under `.spoon-mcp-cache/`.
Graph queries are available through a lazy embedded graph projection. To maintain that
projection eagerly during cache store/load, enable the graph backend:

```sh
SPOON_MCP_CACHE_BACKEND=graph java -jar target/spoon-mcp-server-1.0.0-SNAPSHOT.jar
```

The equivalent JVM property is `-Dspoonmcp.cache.backend=graph`.

## Documentation

- `docs/INSTALL.md`: install, MCP client wiring, and configuration.
- `docs/TOOLS.md`: MCP tool reference.
- `docs/ARCHITECTURE.md`: package responsibilities and data flow.
- `AGENTS.md`: repository guide for coding agents.
- `examples/jsonrpc/`: example JSON-RPC requests.
- `llms.txt`: compact index for LLM and agent ingestion.

## Development

Run the test suite before opening a pull request:

```sh
mvn test
```

Useful maintenance commands:

```sh
mvn dependency:analyze
mvn versions:display-dependency-updates
mvn versions:display-plugin-updates
mvn dependency-check:check
```

`dependency-check:check` runs the OWASP Dependency-Check and fails on CVEs with CVSS score ≥ 7.
Set `NVD_API_KEY` or pass `-DnvdApiKey=<key>` for faster NVD data downloads (free key at https://nvd.nist.gov/developers/request-an-api-key).
Add false positives to `dependency-check-suppressions.xml`.

Generated files such as `target/`, `.spoon-mcp-cache/`, and `dependency-reduced-pom.xml` are intentionally ignored.

## Local Releases

The Maven release plugin is configured for local Git releases by default. It tags the current repository, does not push to a remote, and runs `clean package jreleaser:full-release` during `release:perform`. JReleaser therefore always executes inside the release checkout where the version is already resolved to the release tag — not the post-release SNAPSHOT.

**All working-tree changes must be committed before running the release.**

Actual release (requires a GitHub token with repository release permissions):

```sh
JRELEASER_GITHUB_TOKEN=<token> mvn release:prepare release:perform
git push origin main --tags
```

Dry run — tests the full pipeline locally without publishing or pushing anything:

```sh
JRELEASER_GITHUB_TOKEN=dummy mvn release:prepare release:perform \
  -Darguments="-DskipTests -Drelease.perform=true -Djreleaser.dry.run=true"
```

After the dry run, remove the local commits and tag that `release:prepare` created:

```sh
git reset --hard HEAD~2
git tag -d $(git describe --tags --abbrev=0)
```

Non-mutating check of just the preparation phase:

```sh
mvn release:prepare -DdryRun=true
mvn release:clean
```

JReleaser is configured for GitHub releases under `DerPate/spoon-mcp-server`. It uses the shaded server jar as the runnable distribution artifact, attaches source and Javadoc jars as release files, and appends generated release notes to `CHANGELOG.md`.

## Publishing Notes

This repository includes GitHub community defaults and project hygiene files. It does not use pre-commit hooks.
