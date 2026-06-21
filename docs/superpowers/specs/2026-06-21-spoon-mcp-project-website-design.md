# ArchLens Project Website Design

## Goal

Create a polished single-page project website for ArchLens that can be
hosted on a subdomain such as `archlens.dominikbreu.dev`. The page should showcase
the project as a strong personal engineering artifact while still giving
technical visitors a fast path to installation, documentation, and examples.

The primary audience is potential clients, employers, and technical peers who
should quickly understand the sophistication of the project. The secondary
audience is developers and AI-agent users who want practical setup information.

## Positioning

ArchLens should be presented as an independent architecture-understanding tool
for real Java systems. It is MCP architecture analysis built on Spoon, not an
official Spoon product and not a generic code graph viewer. The page should make
clear that ArchLens indexes Java workspaces with Spoon, projects the result into
a TinkerGraph-backed architecture graph, and exposes MCP tools for answering
questions about entrypoints, components, runtime paths, data movement, workflow
handoffs, impact, and visual exports.

Recommended naming and headline direction:

> ArchLens
>
> Architecture understanding for real Java systems.
>
> MCP architecture analysis built on Spoon.

Supporting copy should emphasize source-derived facts, agent-friendly MCP
workflows, and production-style Java architectures such as Spring, Quarkus,
Java EE, messaging, schedulers, repositories, outbound clients, and deployment
hints.

## Site Shape

Use a single-page showcase, not a multi-page documentation site. The page should
be easy to host as a standalone static artifact and easy to share as a portfolio
piece. Deep documentation remains in the repository docs.

The page should include:

- Hero section with the project name as the first-viewport signal.
- Proof panel showing a realistic MCP workflow.
- Capability sections for discovery, tracing, querying, and export.
- A concise "how it works" architecture explanation.
- Example questions the tool answers.
- Install and first-tour snippets.
- Links to GitHub, `docs/INSTALL.md`, `docs/TOOLS.md`, and the personal site.

## Visual Direction

The project website should feel like a sibling of `dominikbreu.dev`, not a
separate brand. Reuse the personal site's design language:

- Dark Catppuccin-style palette.
- Subtle grid texture and restrained gradients.
- Fixed glassy navigation.
- 8px border radius for panels and controls.
- Inter/system sans typography.
- Technical, calm, production-minded tone.
- Dense but readable information hierarchy.

The site should avoid a generic SaaS landing-page feel. No oversized marketing
illustrations, decorative blobs, or vague product claims. Visual interest should
come from architecture-tool motifs: terminal panels, graph nodes and edges,
code snippets, workflow cards, and renderer/export previews.

## Page Sections

### Navigation

Navigation should be compact and fixed, matching the personal site's nav style.
Suggested links:

- Overview
- Workflow
- Capabilities
- Install
- GitHub

The logo can reuse the existing node/arc visual language from the personal
site, adapted only if needed for the project subdomain. The subdomain should
prefer the independent project name, for example `archlens.dominikbreu.dev`.

### Hero

The hero should immediately name the project:

- Eyebrow: `ArchLens`
- Headline: `Architecture understanding for real Java systems.`
- Supporting text: explain that it indexes Java workspaces with Spoon and
  exposes MCP tools for architecture exploration. The text should describe
  Spoon as the analysis foundation, not as the owner or brand of the project.
- Primary action: `View on GitHub`
- Secondary action: `Read install guide`

The hero visual should be a technical proof panel, not a stock image. It should
combine a terminal-style workflow with a small graph or architecture summary.
Example workflow:

```text
index_workspace
list_apps
find_entrypoints
trace_data_flow
query_architecture_graph
export_graph_viewer
```

### Proof Strip

Add a compact strip after the hero that names the project's concrete domains:

- Java 21
- Spoon
- MCP stdio
- TinkerGraph
- Mermaid
- LikeC4

### Capabilities

Present the tool surface as four to six capability cards:

- Discover entrypoints and components.
- Trace runtime flow from endpoints, consumers, schedulers, and main methods.
- Follow parameter and message data to persistence, messaging, HTTP, event bus,
  file/object storage, and shared-state stores.
- Stitch workflows across messaging, event bus, persistence, and shared-state
  handoffs.
- Query the architecture graph for neighborhoods, paths, and impact slices.
- Export Mermaid, LikeC4, Markdown architecture docs, graph JSON, and a
  self-contained HTML graph viewer.

Cards should use real project language from `README.md` and `docs/TOOLS.md`.

### How It Works

Use a horizontal or stepped diagram:

```text
Java workspace -> Spoon extraction -> Architecture graph -> MCP tools -> Visual exports
```

Each step should include one short sentence:

- Source scan detects build modules, frameworks, annotations, calls, and source facts.
- Extraction builds components, entrypoints, call edges, data-flow paths, and workflow links.
- Graph projection stores the runtime source of truth in TinkerGraph.
- MCP tools expose typed architecture queries and renderers.
- Exports produce Mermaid, LikeC4, Markdown docs, JSON, and an HTML graph viewer.

The design must respect the repository's architectural rule: after indexing,
the graph is the runtime source of truth and tools access it through
`GraphQuery`.

### Questions It Answers

Use a section aimed at technically minded evaluators:

- Which endpoints, consumers, and schedulers are the real ways into this system?
- What services, repositories, clients, and external systems does a use case touch?
- Where does a request parameter or message payload end up?
- Which async chains continue through a broker, event bus, shared field, or repository?
- What components are impacted if this repository, service, or integration changes?

This section should make the project value concrete without over-claiming.

### Install And First Tour

Include concise commands:

```sh
mvn test
mvn package
java -jar target/spoon-mcp-server.jar
```

Include the recommended first workflow:

```text
index_workspace -> list_apps -> find_entrypoints -> find_components -> render_architecture_view
```

Then show drill-down tools:

```text
call_flow
trace_data_flow
render_pipeline
query_architecture_graph
export_graph_viewer
```

This section should link to the full install guide instead of trying to become
complete documentation.

### Footer

Footer should link back to `dominikbreu.dev`, GitHub, install docs, tool docs,
and the repository license.

## Implementation Recommendation

Create a small static Astro site in this repository, likely under `site/` or
`website/`, so it can be deployed independently from the Java Maven package.
Borrow the visual system from `dominikbreu.dev` but keep the code self-contained
in this repository.

Recommended stack:

- Astro for static generation.
- Plain CSS with the existing personal-site design tokens adapted locally.
- No backend, analytics, contact form, or cookie consent for the project page.
- No new Java runtime integration.

The resulting site should be buildable and previewable with local npm scripts,
and deployable as static files to the chosen subdomain.

## Verification

Implementation should be verified by:

- Building the Astro site successfully.
- Running a local preview or dev server.
- Checking desktop and mobile layouts in a browser.
- Confirming text does not overflow buttons, cards, nav, or code panels.
- Confirming links point to real repository docs and external destinations.

## Out Of Scope

- Replacing repository documentation.
- Building an interactive live MCP demo.
- Adding analytics, forms, cookies, or server-side functionality.
- Creating release automation or deployment pipelines.
- Modifying the Java MCP server runtime.
