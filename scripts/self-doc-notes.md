## Architecture Question OKF Compilation

`dev.dominikbreu.archlens.okf` is a graph-independent compilation layer for turning a
caller-reviewed `answer_architecture_question` structured result into a project-local OKF
investigation. It does not retain or query the extraction model. The MCP adapter obtains indexed
project roots through its graph lookup, then supplies those roots and the caller-provided result
to the compiler for contained-path resolution, deterministic semantic identity, rendering, and
safe bundle writes.

## Diagram Templates

`renderer/` prepares presentation data for Mermaid (including its C4 dialect) and LikeC4.
Graph queries, filtering, traversal, stable ordering, identifier allocation, semantic labels,
and target-language escaping stay in Java. Typed presentation records under `renderer/template/`
carry that data into Mustache templates under `src/main/resources/templates/mermaid/` and
`src/main/resources/templates/likec4/`, which own diagram syntax and document layout.

Maven explicitly runs the JStachio annotation processor to validate template bindings and generate
Java renderers in `target/generated-sources/annotations/`. Templates use `JStacheType.STACHE` and
the application calls generated renderers directly. The annotation dependency has provided scope;
there is no runtime template interpreter, reflection fallback, or runtime template compilation.
Template changes require a rebuild (use `mvn clean verify` when changing only template resources).
STACHE passes values through without HTML escaping: Java must escape each value for its Mermaid
or LikeC4 position before rendering. Values containing Mustache syntax remain literal data.

HTML graph-viewer and user-editable OKF templates have separate rendering paths.

`scripts/self-doc.py` runs a clean package build before indexing this repository. It shortens
internal Mermaid identifiers and splits oversized flowcharts across fenced blocks, preserving
all dependencies within Mermaid's default text and edge limits. Labels and node styles remain
intact. The prose in this section lives in `scripts/self-doc-notes.md` so regeneration preserves it.
