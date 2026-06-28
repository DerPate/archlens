package dev.dominikbreu.archlens.mcp;

import dev.dominikbreu.archlens.cache.ModelCache;
import dev.dominikbreu.archlens.extractor.ArchitectureExtractor;
import dev.dominikbreu.archlens.mcp.tools.*;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.function.Function;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;

/** Stdio MCP server exposing architecture-analysis tools via the official MCP Java SDK. */
public class McpServer {
    public static final String SERVER_VERSION = loadVersion();

    private static String loadVersion() {
        // Packaged JAR: pom.properties written by Maven at package time
        try (InputStream in = McpServer.class.getResourceAsStream(
                "/META-INF/maven/dev.dominikbreu.archlens/archlens/pom.properties")) {
            if (in != null) {
                Properties props = new Properties();
                props.load(in);
                String v = props.getProperty("version");
                if (v != null && !v.isBlank()) return v;
            }
        } catch (IOException ignored) {
        }
        // IDE / test classpath: parse pom.xml from working directory
        try (InputStream in = McpServer.class.getResourceAsStream("/pom.xml")) {
            if (in != null) return new MavenXpp3Reader().read(in).getVersion();
        } catch (IOException | org.codehaus.plexus.util.xml.pull.XmlPullParserException ignored) {
        }
        try (InputStream in = new java.io.FileInputStream("pom.xml")) {
            return new MavenXpp3Reader().read(in).getVersion();
        } catch (IOException | org.codehaus.plexus.util.xml.pull.XmlPullParserException ignored) {
        }
        return "unknown";
    }

    private final IndexWorkspaceTool indexTool;
    private final ListAppsTool listAppsTool;
    private final FindEntrypointsTool entrypointsTool;
    private final FindComponentsTool componentsTool;
    private final GetComponentDependenciesTool dependenciesTool;
    private final InferContainersTool containersTool;
    private final RenderMermaidFlowchartTool flowchartTool;
    private final CallFlowTool callFlowTool;
    private final RenderSourceOverviewTool sourceOverviewTool;
    private final RenderDependencyMapTool dependencyMapTool;
    private final RenderComponentDependencyDiagramTool dependencyDiagramTool;
    private final ExportArchitectureDocsTool exportDocsTool;
    private final ExportGraphArchitecturePocTool exportGraphPocTool;
    private final ExportGraphDataTool exportGraphDataTool;
    private final ExportGraphViewerTool exportGraphViewerTool;
    private final QueryArchitectureGraphTool graphTool;
    private final DetectUseCasesTool detectUseCasesTool;
    private final TraceDataFlowTool traceDataFlowTool;
    private final RenderUseCaseTimelineTool useCaseTimelineTool;
    private final RenderPipelineTool pipelineTool;
    private final RenderArchitectureViewTool renderArchitectureViewTool;
    private final ExportLikeC4ModelTool exportLikeC4ModelTool;
    private final StructuredOutputMode structuredOutputMode;

    private static final String TYPE_STRING = "string";
    private static final String TYPE_INTEGER = "integer";
    private static final String APP_ID = "appId";
    private static final String APP_ID_DESCRIPTION = "Filter by app ID (partial match)";
    private static final String ENTRYPOINT_ID = "entrypointId";
    private static final String ENTRYPOINT_NAME = "entrypointName";
    private static final String MAX_DEPTH = "maxDepth";
    private static final String FOCUS_COMPONENT = "focusComponent";
    private static final String MAX_NODES = "maxNodes";
    private static final String APP_NAME_OR_ID = "Application name or id";

    /** Creates the server with the default extractor, cache, and tool registry. */
    public McpServer() {
        this(
                Boolean.parseBoolean(System.getenv("ARCHLENS_MCP_EXPERIMENTAL_DRAFT"))
                        ? StructuredOutputMode.DRAFT
                        : StructuredOutputMode.STABLE);
    }

    McpServer(StructuredOutputMode structuredOutputMode) {
        this.structuredOutputMode = structuredOutputMode;
        ModelCache cache = new ModelCache();
        ArchitectureExtractor extractor = new ArchitectureExtractor();

        this.indexTool = new IndexWorkspaceTool(extractor, cache);
        this.listAppsTool = new ListAppsTool(cache);
        this.entrypointsTool = new FindEntrypointsTool(cache);
        this.componentsTool = new FindComponentsTool(cache);
        this.dependenciesTool = new GetComponentDependenciesTool(cache);
        this.containersTool = new InferContainersTool(cache);
        this.flowchartTool = new RenderMermaidFlowchartTool(cache);
        this.callFlowTool = new CallFlowTool(cache);
        this.sourceOverviewTool = new RenderSourceOverviewTool(cache);
        this.dependencyMapTool = new RenderDependencyMapTool(cache);
        this.dependencyDiagramTool = new RenderComponentDependencyDiagramTool(cache);
        this.exportDocsTool = new ExportArchitectureDocsTool(cache);
        this.exportGraphPocTool = new ExportGraphArchitecturePocTool(cache);
        this.exportGraphDataTool = new ExportGraphDataTool(cache);
        this.exportGraphViewerTool = new ExportGraphViewerTool(cache);
        this.graphTool = new QueryArchitectureGraphTool(cache);
        this.detectUseCasesTool = new DetectUseCasesTool(cache);
        this.traceDataFlowTool = new TraceDataFlowTool(cache);
        this.useCaseTimelineTool = new RenderUseCaseTimelineTool(cache);
        this.pipelineTool = new RenderPipelineTool(cache);
        this.renderArchitectureViewTool = new RenderArchitectureViewTool(cache);
        this.exportLikeC4ModelTool = new ExportLikeC4ModelTool(cache);
    }

    /**
     * Starts the MCP server on stdio.
     */
    public void run() {
        StdioServerTransportProvider transport = new StdioServerTransportProvider(McpJsonDefaults.getMapper());

        io.modelcontextprotocol.server.McpServer.sync(transport)
                .serverInfo("archlens", SERVER_VERSION)
                .capabilities(McpSchema.ServerCapabilities.builder()
                        .prompts(false)
                        .tools(false)
                        .build())
                .tools(buildToolSpecifications())
                .prompts(buildPromptSpecifications())
                .build();
    }

    // ── tool registration ─────────────────────────────────────────────────────

    public List<McpServerFeatures.SyncToolSpecification> buildToolSpecifications() {
        List<McpServerFeatures.SyncToolSpecification> specs = new ArrayList<>();

        specs.add(toolSpec(
                "index_workspace",
                "Index Workspace",
                "Analyze one or more Java project roots and build the internal architecture model.",
                schema().reqArray("paths", TYPE_STRING, "Project root directory paths to analyze"),
                schema().opt("appCount", TYPE_INTEGER, "Indexed application count")
                        .opt("componentCount", TYPE_INTEGER, "Indexed component count")
                        .opt("entrypointCount", TYPE_INTEGER, "Indexed entrypoint count"),
                indexTool::execute));

        specs.add(toolSpec(
                "list_apps",
                "List Applications",
                "List recognized applications, modules, and their packaging types.",
                schema(),
                schema().opt("apps", "array", "Indexed application nodes")
                        .opt("componentCount", TYPE_INTEGER, "Total component count")
                        .opt("entrypointCount", TYPE_INTEGER, "Total entrypoint count")
                        .opt("interfaceCount", TYPE_INTEGER, "Total interface count")
                        .opt("runtimeFlowCount", TYPE_INTEGER, "Total runtime flow count"),
                listAppsTool::execute));

        specs.add(collectionToolSpec(
                "find_entrypoints",
                "Find Entrypoints",
                "Return architecturally relevant entry points: REST endpoints, JMS/messaging consumers, schedulers, EJB methods, CDI event observers, Vert.x EventBus consumers, WebSocket/SSE/gRPC endpoints, and more. All filters are combinable.",
                schema().opt(APP_ID, TYPE_STRING, APP_ID_DESCRIPTION)
                        .opt(
                                "type",
                                TYPE_STRING,
                                "REST_ENDPOINT | JMS_CONSUMER | MESSAGING_CONSUMER | MESSAGING_PRODUCER | CDI_EVENT_OBSERVER | SCHEDULER | EJB_BUSINESS_METHOD | RMI_ENDPOINT | MAIN_METHOD | EVENT_BUS_CONSUMER | WEBSOCKET_ENDPOINT | SSE_ENDPOINT | GRPC_METHOD | UNKNOWN")
                        .opt(
                                "httpMethod",
                                TYPE_STRING,
                                "Filter REST endpoints by HTTP verb: GET | POST | PUT | DELETE | PATCH | HEAD | OPTIONS")
                        .opt(
                                "path",
                                TYPE_STRING,
                                "Filter by path prefix — returns all endpoints at or below this path (e.g. '/customer' returns /customer, /customer/{id}, /customer/{id}/address/{aid}, ...)"),
                "entrypoints",
                nodeItemSchema(),
                entrypointsTool::execute));

        specs.add(collectionToolSpec(
                "find_components",
                "Find Components",
                "Return architecture-relevant components (services, repositories, EJBs, entities, etc.).",
                schema().opt(APP_ID, TYPE_STRING, APP_ID_DESCRIPTION)
                        .opt(
                                "type",
                                TYPE_STRING,
                                "REST_RESOURCE | SERVICE | REPOSITORY | ENTITY | EJB_STATELESS | EJB_STATEFUL | EJB_SINGLETON | MESSAGE_DRIVEN_BEAN | SCHEDULER | HTTP_CLIENT | CDI_EVENT_CONSUMER | CDI_EVENT_PRODUCER | REMOTE_SERVICE | UTILITY | UNKNOWN")
                        .opt("technology", TYPE_STRING, "quarkus | javaee | jpa"),
                "components",
                nodeItemSchema(),
                componentsTool::execute));

        specs.add(collectionToolSpec(
                "get_component_dependencies",
                "Get Component Dependencies",
                "Return relevant dependencies for a component, with optional depth limit and condensation of non-architectural intermediaries.",
                schema().opt(
                                "componentId",
                                TYPE_STRING,
                                "Component ID — the fully-qualified class name, e.g. com.example.UserService")
                        .opt("name", TYPE_STRING, "Component simple name (partial match)")
                        .opt("depth", TYPE_INTEGER, "Traversal depth (default 1, max 5)")
                        .opt("condensed", "boolean", "Remove UTILITY/UNKNOWN intermediaries (default true)"),
                "dependencies",
                edgeItemSchema(),
                dependenciesTool::execute));

        specs.add(collectionToolSpec(
                "infer_containers",
                "Infer Containers",
                "Group components into logical containers (api / service / repository / domain / messaging / scheduling).",
                schema().opt(APP_ID, TYPE_STRING, APP_ID_DESCRIPTION),
                "containers",
                nodeItemSchema(),
                containersTool::execute));

        specs.add(toolSpec(
                "render_mermaid_flowchart",
                "Render Mermaid Flowchart",
                "Render a Mermaid flowchart for static architecture views (system / container / component level).",
                schema().opt(APP_ID, TYPE_STRING, APP_ID_DESCRIPTION)
                        .opt(
                                "level",
                                TYPE_STRING,
                                "system | container | module | component (default: component) — module shows WAR deployment-unit with embedded JAR internal_modules"),
                diagramOutput(),
                flowchartTool::execute));

        specs.add(toolSpec(
                "call_flow",
                "Call Flow",
                "Return the runtime call flow for an entry point: ordered steps and a Mermaid flowchart. Component shapes reflect architectural role (cylinder=repository, parallelogram=http-client, etc.). Edge labels show the actual called method name.",
                schema().opt(ENTRYPOINT_ID, TYPE_STRING, "Entrypoint ID (from find_entrypoints)")
                        .opt(
                                ENTRYPOINT_NAME,
                                TYPE_STRING,
                                "Entrypoint path, name, or 'METHOD /path' (e.g. 'GET /account') for HTTP-method disambiguation"),
                schema().opt("steps", "array", "Ordered call-flow steps")
                        .opt("diagram", TYPE_STRING, "Rendered Mermaid flowchart"),
                callFlowTool::execute));

        specs.add(toolSpec(
                "render_source_overview",
                "Render Source Overview",
                "Render a package-aware Mermaid source overview with components and dependency edges.",
                schema().opt(
                                "maxComponentsPerPackage",
                                TYPE_INTEGER,
                                "Maximum rendered component nodes per package (default 25)"),
                diagramOutput(),
                sourceOverviewTool::execute));

        specs.add(toolSpec(
                "render_dependency_map",
                "Render Dependency Map",
                "Render an aggregated Mermaid dependency map grouped by source responsibility.",
                schema(),
                diagramOutput(),
                dependencyMapTool::execute));

        specs.add(toolSpec(
                "render_component_dependency_diagram",
                "Render Component Dependency Diagram",
                "Render a focused Mermaid dependency diagram for one component.",
                schema().opt("componentId", TYPE_STRING, "Component ID")
                        .opt("name", TYPE_STRING, "Component simple name or partial qualified name")
                        .opt("depth", TYPE_INTEGER, "Traversal depth (default 2)"),
                diagramOutput(),
                dependencyDiagramTool::execute));

        specs.add(toolSpec(
                "export_architecture_docs",
                "Export Architecture Docs",
                "Write Markdown architecture documentation with MCP-generated Mermaid diagrams.",
                schema().opt("outputPath", TYPE_STRING, "Output Markdown path (default docs/GENERATED_ARCHITECTURE.md)")
                        .opt(
                                FOCUS_COMPONENT,
                                TYPE_STRING,
                                "Component used for the dependency slice (default McpServer)"),
                schema().opt("outputPath", TYPE_STRING, "Written file path"),
                exportDocsTool::execute));

        specs.add(toolSpec(
                "export_graph_architecture_poc",
                "Export Graph Architecture POC",
                "Write a graph-centric architecture POC document with graph metadata, property examples, and MCP query samples.",
                schema().opt(
                                "outputPath",
                                TYPE_STRING,
                                "Output Markdown path (default docs/SOURCE_ARCHITECTURE_POC.md)")
                        .opt(
                                FOCUS_COMPONENT,
                                TYPE_STRING,
                                "Component used for the graph focus slice (default McpServer)"),
                schema().opt("outputPath", TYPE_STRING, "Written file path"),
                exportGraphPocTool::execute));

        specs.add(toolSpec(
                "export_graph_data",
                "Export Graph Data",
                "Write architecture graph JSON for standalone visual graph viewers, including raw snapshot data and viewer-ready projections.",
                schema().opt("outputPath", TYPE_STRING, "Output JSON path (default docs/GRAPH_DATA.json)")
                        .opt("limit", TYPE_INTEGER, "Maximum graph nodes to export (default 5000)"),
                schema().opt("outputPath", TYPE_STRING, "Written file path")
                        .opt("nodeCount", TYPE_INTEGER, "Exported node count")
                        .opt("edgeCount", TYPE_INTEGER, "Exported edge count"),
                exportGraphDataTool::execute));

        specs.add(toolSpec(
                "export_graph_viewer",
                "Export Graph Viewer",
                "Write a self-contained HTML viewer using the same graph export payload as export_graph_data.",
                schema().opt("outputPath", TYPE_STRING, "Output HTML path (default docs/GRAPH_VIEWER.html)")
                        .opt("limit", TYPE_INTEGER, "Maximum graph nodes to export (default 5000)"),
                schema().opt("outputPath", TYPE_STRING, "Written file path")
                        .opt("nodeCount", TYPE_INTEGER, "Exported node count")
                        .opt("edgeCount", TYPE_INTEGER, "Exported edge count"),
                exportGraphViewerTool::execute));

        specs.add(graphToolSpec(
                "query_architecture_graph",
                "Query Architecture Graph",
                "Query the architecture as a graph: summary, node search, neighborhoods, paths, or impact slices.",
                schema().opt(
                                "action",
                                TYPE_STRING,
                                "summary | find_nodes | find_edges | neighborhood | paths | impacted_by")
                        .opt(
                                "label",
                                TYPE_STRING,
                                "Node label for find_nodes: Application | Component | Entrypoint | Interface | Container | Deployment | RuntimeFlow | DataFlowPath | DataFlowSink | PipelineChain")
                        .opt("query", TYPE_STRING, "Free-text node search")
                        .opt(
                                "filters",
                                "object",
                                "Property filters, with numeric comparisons such as {\"confidence\":\"<=0.6\"}")
                        .opt("nodeId", TYPE_STRING, "Node ID for neighborhood or impacted_by")
                        .opt("fromId", TYPE_STRING, "Source node ID for paths")
                        .opt("toId", TYPE_STRING, "Target node ID for paths")
                        .opt("direction", TYPE_STRING, "in | out | both for neighborhood")
                        .opt(MAX_DEPTH, TYPE_INTEGER, "Traversal depth for paths or impacted_by")
                        .opt(
                                "limit",
                                TYPE_INTEGER,
                                "Maximum returned rows. find_nodes is unlimited by default; other actions default to 256")
                        .opt("type", TYPE_STRING, "Shorthand filter: node or edge type property")
                        .opt("technology", TYPE_STRING, "Shorthand filter: technology property (e.g. quarkus, jpa)")
                        .opt("module", TYPE_STRING, "Shorthand filter: module/app ID property")
                        .opt("packageName", TYPE_STRING, "Shorthand filter: packageName property (partial match)")
                        .opt(
                                "entrypointReachable",
                                TYPE_STRING,
                                "Shorthand filter: true | false — only nodes reachable from an entrypoint")
                        .opt(
                                "workflowRelevant",
                                TYPE_STRING,
                                "Shorthand filter: true | false — only workflow-relevant components")
                        .opt(
                                "businessRelevant",
                                TYPE_STRING,
                                "Shorthand filter: true | false — only business-relevant components")
                        .opt(
                                "infrastructureRole",
                                TYPE_STRING,
                                "Shorthand filter: component role such as scheduler, repository, utility")
                        .opt(
                                "primaryRole",
                                TYPE_STRING,
                                "Shorthand filter: entrypoint | business-service | data-access | domain-model | integration | support")
                        .opt(
                                "supportRole",
                                TYPE_STRING,
                                "Shorthand filter: configuration, mapper, converter, redis-lock, migration-initializer, security-configuration, utility, etc.")
                        .opt(
                                "agentCategory",
                                TYPE_STRING,
                                "Shorthand filter: core-workflow | boundary | data | integration | supporting-infrastructure | low-signal")
                        .opt(
                                "classificationEvidence",
                                TYPE_STRING,
                                "Shorthand filter: source evidence fragment such as package:redis or stereotype:configuration")
                        .opt("isCrossModule", TYPE_STRING, "Shorthand filter: true | false — only cross-module edges")
                        .opt(
                                "isRuntimeRelevant",
                                TYPE_STRING,
                                "Shorthand filter: true | false — only runtime-relevant edges")
                        .opt("isCondensable", TYPE_STRING, "Shorthand filter: true | false — only condensable edges")));

        specs.add(collectionToolSpec(
                "trace_data_flow",
                "Trace Data Flow",
                "Trace how entrypoint parameters flow through the call graph to sinks (persistence, messaging, http-outbound, event-bus, store, file-outbound, object-storage). Requires call-graph data from index_workspace.",
                schema().opt(ENTRYPOINT_ID, TYPE_STRING, "Filter by entrypoint ID (partial match)")
                        .opt(
                                ENTRYPOINT_NAME,
                                TYPE_STRING,
                                "Filter by path, name, or 'METHOD /path' (e.g. 'GET /account') for HTTP-method disambiguation")
                        .opt("param", TYPE_STRING, "Filter by tracked parameter name")
                        .opt(
                                "sinkKind",
                                TYPE_STRING,
                                "Filter by sink kind: persistence | messaging | http-outbound | event-bus | store | file-outbound | object-storage | unknown"),
                "paths",
                Map.of(
                        "type",
                        "object",
                        "properties",
                        Map.of(
                                "id", Map.of("type", "string"),
                                "entrypointId", Map.of("type", "string"),
                                "entrypoint", Map.of("type", "string"),
                                "trackedParam", Map.of("type", "string"),
                                "sinks", Map.of("type", "array"))),
                traceDataFlowTool::execute));

        specs.add(toolSpec(
                "render_use_case_timeline",
                "Render Use Case Timeline",
                "Render a Mermaid gantt chart showing sequential execution steps across use cases. Each use case is a section; each component hop is a task bar positioned by call depth. Useful for comparing execution depth and component involvement across entry points.",
                schema().opt(ENTRYPOINT_ID, TYPE_STRING, "Filter to a single use case by entrypoint ID")
                        .opt(
                                ENTRYPOINT_NAME,
                                TYPE_STRING,
                                "Filter by path, name, or 'METHOD /path' (e.g. 'GET /account') for HTTP-method disambiguation")
                        .opt("maxUseCases", TYPE_INTEGER, "Maximum sections to render (default 10)")
                        .opt(MAX_DEPTH, TYPE_INTEGER, "Maximum steps per section (default 5)"),
                diagramOutput(),
                useCaseTimelineTool::execute));

        specs.add(toolSpec(
                "render_pipeline",
                "Render Pipeline",
                "Render an end-to-end pipeline diagram by stitching data-flow paths across entrypoints via typed workflow links (state handoffs, messaging consumers, event-bus consumers). Produces a single connected Mermaid flowchart per chain rather than separate per-entrypoint diagrams.",
                schema().opt(
                                ENTRYPOINT_NAME,
                                TYPE_STRING,
                                "Filter chains by root entrypoint path, name, or 'METHOD /path' (e.g. 'POST /account') for HTTP-method disambiguation")
                        .opt(
                                "channel",
                                TYPE_STRING,
                                "Filter chains that pass through a messaging link whose channel name contains this substring")
                        .opt(MAX_DEPTH, TYPE_INTEGER, "Maximum number of pipeline segments per chain (default 8)")
                        .opt("maxChains", TYPE_INTEGER, "Maximum number of chains to render (default 5)")
                        .opt(
                                "includeLifecycle",
                                "boolean",
                                "Include CDI lifecycle observer, main-method, and RMI chains (default false)"),
                diagramOutput(),
                pipelineTool::execute));

        specs.add(collectionToolSpec(
                "detect_use_cases",
                "Detect Use Cases",
                "Detect business use cases from indexed entrypoints and their call chains. Uses call-graph data when available; falls back to injection-dependency traversal.",
                schema().opt(
                                "configFile",
                                TYPE_STRING,
                                "Path to a JSON naming config file ({ \"names\": { \"<entrypointId>\": \"Display Name\" } })")
                        .opt("module", TYPE_STRING, "Filter results by app/module ID (partial match)")
                        .opt(MAX_DEPTH, TYPE_INTEGER, "Max call-chain depth shown per use case (default 5)"),
                "useCases",
                Map.of(
                        "type",
                        "object",
                        "properties",
                        Map.of(
                                "id", Map.of("type", "string"),
                                "name", Map.of("type", "string"),
                                "type", Map.of("type", "string"),
                                "channelOrPath", Map.of("type", "string"),
                                "components", Map.of("type", "string"),
                                "methodChain", Map.of("type", "array"))),
                detectUseCasesTool::execute));

        specs.add(toolSpec(
                "render_architecture_view",
                "Render Architecture View",
                "Render a projection-first architecture view from the indexed graph. Start with view=component for C4-style component views. Prioritizes workflow-relevant components over utility fan-in noise.",
                schema().opt("app", TYPE_STRING, APP_NAME_OR_ID)
                        .opt("view", TYPE_STRING, "View kind: component")
                        .opt(MAX_NODES, TYPE_INTEGER, "Maximum component nodes to include (default 18)"),
                diagramOutput(),
                renderArchitectureViewTool::call));

        specs.add(toolSpec(
                "export_likec4_model",
                "Export LikeC4 Model",
                "Export the indexed architecture graph as LikeC4-style model text. Supports workspace (default) or component views for loading into LikeC4 tooling or providing structured LLM context.",
                schema().opt("app", TYPE_STRING, APP_NAME_OR_ID)
                        .opt("view", TYPE_STRING, "View kind: workspace or component (default workspace)")
                        .opt(MAX_NODES, TYPE_INTEGER, "Maximum component nodes to include (default 18)"),
                diagramOutput(),
                exportLikeC4ModelTool::call));

        return specs;
    }

    // ── prompt registration ───────────────────────────────────────────────────

    List<McpServerFeatures.SyncPromptSpecification> buildPromptSpecifications() {
        return List.of(
                promptSpec(
                        "analyze_workspace",
                        "Analyze a Java workspace and summarize the discovered architecture.",
                        List.of(arg("path", "Absolute project or workspace root to index.", true)),
                        """
                        Analyze the Java workspace at `{path}`.

                        Use this workflow:
                        1. Call `index_workspace` with `paths: ["{path}"]`.
                        2. Call `list_apps` to identify modules and packaging.
                        3. Call `find_entrypoints` and `find_components` to map the runtime surface.
                        4. Call `query_architecture_graph` with `action: "summary"` for a concise graph overview.
                        5. Mention uncertainty where component types or technologies are inferred with low confidence.
                        """),
                promptSpec(
                        "generate_architecture_docs",
                        "Generate checked-in architecture documentation for a Java workspace.",
                        List.of(
                                arg("path", "Absolute project or workspace root to index.", true),
                                arg(FOCUS_COMPONENT, "Component name used for focused dependency slices.", false)),
                        """
                        Generate architecture documentation for `{path}`.

                        Use this workflow:
                        1. Call `index_workspace` with `paths: ["{path}"]`.
                        2. Call `export_architecture_docs` with `outputPath: "docs/ARCHITECTURE.md"` and `focusComponent: "{focusComponent}"`.
                        3. Call `export_graph_architecture_poc` with `outputPath: "docs/SOURCE_ARCHITECTURE_POC.md"` and `focusComponent: "{focusComponent}"`.
                        4. Review the tool summaries and report component, dependency, node, and edge counts.
                        """),
                promptSpec(
                        "investigate_component",
                        "Investigate a component's dependencies, graph neighborhood, and impact surface.",
                        List.of(arg("component", "Component simple name, qualified name, or component ID.", true)),
                        """
                        Investigate component `{component}`.

                        Use this workflow:
                        1. Call `find_components` with `query` if available to locate likely component IDs, or use `query_architecture_graph` with `action: "find_nodes"` and `query: "{component}"`.
                        2. Call `get_component_dependencies` with `name: "{component}"`, `depth: 2`, and `condensed: true`.
                        3. Call `render_component_dependency_diagram` with `name: "{component}"` and `depth: 2`.
                        4. Call `query_architecture_graph` with `action: "neighborhood"` for the selected node ID.
                        5. Summarize inbound dependencies, outbound dependencies, runtime-relevant edges, and likely change impact.
                        """),
                promptSpec(
                        "trace_use_case",
                        "Trace an entrypoint or use case through runtime flow, call flow, and data flow.",
                        List.of(arg("entrypoint", "Entrypoint name, path, or entrypoint ID.", true)),
                        """
                        Trace use case `{entrypoint}`.

                        Use this workflow:
                        1. Call `find_entrypoints` to confirm the matching entrypoint.
                        2. Call `call_flow` with `entrypointName: "{entrypoint}"`.
                        3. Call `trace_data_flow` with `entrypointName: "{entrypoint}"`.
                        4. Call `render_use_case_timeline` with `entrypointName: "{entrypoint}"`.
                        5. Explain the request path, component hops, data sinks, and any missing call-graph evidence.
                        """),
                promptSpec(
                        "architecture_view",
                        "Generate a projection-first architecture view from the indexed graph.",
                        List.of(
                                arg("app", APP_NAME_OR_ID, true),
                                arg("view", "View kind: component", false),
                                arg(MAX_NODES, "Maximum nodes", false)),
                        """
                        1. If no workspace has been indexed yet, call `index_workspace` first.
                        2. Call `render_architecture_view` with `app: "{app}"`, `view: "{view}"`, and `maxNodes: {maxNodes}` (default 18).
                        3. If the output contains a %% Warnings section, report it to the user and suggest increasing maxNodes or re-indexing.
                        4. If the user asks for LikeC4, call `export_likec4_model` with the same arguments.
                        5. Explain that the output is a projection of source-derived graph facts — do not invent actors, components, or edges.
                        """),
                promptSpec(
                        "find_pipeline",
                        "Find cross-entrypoint or messaging/store-linked pipeline chains.",
                        List.of(arg(
                                "filter", "Optional entrypoint name, HTTP path, or messaging channel filter.", false)),
                        """
                        Find pipeline chains matching `{filter}`.

                        Use this workflow:
                        1. Call `trace_data_flow` to inspect available data-flow paths and sinks.
                        2. Call `render_pipeline` with `entrypointName: "{filter}"`, `channel: "{filter}"`, `maxDepth: 8`, and `maxChains: 5`.
                        3. If no chains match, call `query_architecture_graph` with `action: "find_edges"`, `label: "WORKFLOW_LINK"`, and filters such as `kind: "STATE_HANDOFF"` or `kind: "MESSAGING"` where useful.
                        4. Summarize each pipeline segment, bridge kind, channel/store name, and downstream consumer.
                        """));
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    /**
     * Wires a tool handler into the SDK's spec type. Every response carries the handler's text
     * unchanged in {@code content[0].text}, plus (when non-null) the handler's structured payload
     * in {@code structuredContent}, matching the declared {@code outputSchema}.
     */
    private McpServerFeatures.SyncToolSpecification toolSpec(
            String name,
            String title,
            String description,
            SchemaBuilder schema,
            SchemaBuilder outputSchema,
            Function<Map<String, Object>, ToolResult> handler) {

        McpSchema.Tool tool = McpSchema.Tool.builder(name, schema.build())
                .title(title)
                .description(description)
                .outputSchema(outputSchema.build())
                .build();

        return new McpServerFeatures.SyncToolSpecification(tool, (exchange, request) -> {
            Map<String, Object> args;
            if (request.arguments() != null) {
                args = request.arguments();
            } else {
                args = Map.of();
            }
            ToolResult result = handler.apply(args);
            McpSchema.CallToolResult.Builder resultBuilder = McpSchema.CallToolResult.builder()
                    .addTextContent(result.text())
                    .structuredContent(result.structured() == null ? Map.of() : result.structured())
                    .isError(result.error());
            return resultBuilder.build();
        });
    }

    private McpServerFeatures.SyncToolSpecification collectionToolSpec(
            String name,
            String title,
            String description,
            SchemaBuilder inputSchema,
            String collectionKey,
            Map<String, Object> itemSchema,
            Function<Map<String, Object>, ToolResult> handler) {
        SchemaBuilder outputSchema = structuredOutputMode == StructuredOutputMode.DRAFT
                ? arrayOutput(itemSchema)
                : schema().reqArraySchema(collectionKey, itemSchema);
        return toolSpec(name, title, description, inputSchema, outputSchema, args -> {
            ToolResult result = handler.apply(args);
            Object structured = result.structured() == null ? List.of() : result.structured();
            if (structuredOutputMode == StructuredOutputMode.STABLE) {
                structured = Map.of(collectionKey, structured);
            }
            return new ToolResult(result.text(), structured, result.error());
        });
    }

    private McpServerFeatures.SyncToolSpecification graphToolSpec(
            String name, String title, String description, SchemaBuilder inputSchema) {
        return toolSpec(
                name,
                title,
                description,
                inputSchema,
                graphOutputSchema(),
                args -> normalizeGraphResult(args, graphTool.execute(args)));
    }

    private ToolResult normalizeGraphResult(Map<String, Object> args, ToolResult result) {
        String action = String.valueOf(args.getOrDefault("action", "summary"));
        if ("summary".equals(action)) {
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("action", action);
            if (result.structured() instanceof Map<?, ?> values) {
                for (Map.Entry<?, ?> entry : values.entrySet()) {
                    summary.put(String.valueOf(entry.getKey()), entry.getValue());
                }
            }
            return new ToolResult(result.text(), summary, result.error());
        }

        String collectionKey = graphCollectionKey(action);
        if (collectionKey == null) {
            return new ToolResult(result.text(), Map.of("action", action), result.error());
        }
        List<?> values = result.structured() instanceof List<?> list ? list : List.of();
        if (structuredOutputMode == StructuredOutputMode.DRAFT) {
            return new ToolResult(result.text(), values, result.error());
        }
        return new ToolResult(result.text(), Map.of("action", action, collectionKey, values), result.error());
    }

    private static String graphCollectionKey(String action) {
        return switch (action) {
            case "find_nodes", "impacted_by" -> "nodes";
            case "find_edges", "neighborhood" -> "edges";
            case "paths" -> "paths";
            default -> null;
        };
    }

    private SchemaBuilder graphOutputSchema() {
        Map<String, Object> summarySchema = graphSummarySchema();
        if (structuredOutputMode == StructuredOutputMode.DRAFT) {
            Map<String, Object> collectionItemSchema =
                    Map.of("anyOf", List.of(nodeItemSchema(), edgeItemSchema(), pathItemSchema()));
            return schema().raw(Map.of(
                    "oneOf", List.of(summarySchema, Map.of("type", "array", "items", collectionItemSchema))));
        }

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("action", Map.of("type", "string"));
        properties.put("nodeCount", Map.of("type", "integer"));
        properties.put("edgeCount", Map.of("type", "integer"));
        properties.put("labels", countMapSchema());
        properties.put("nodes", Map.of("type", "array", "items", nodeItemSchema()));
        properties.put(
                "edges",
                Map.of("oneOf", List.of(countMapSchema(), Map.of("type", "array", "items", edgeItemSchema()))));
        properties.put("paths", Map.of("type", "array", "items", pathItemSchema()));
        return schema().raw(Map.of("type", "object", "properties", properties));
    }

    private static Map<String, Object> graphSummarySchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("action", Map.of("type", "string"));
        properties.put("nodeCount", Map.of("type", "integer"));
        properties.put("edgeCount", Map.of("type", "integer"));
        properties.put("labels", countMapSchema());
        properties.put("edges", countMapSchema());
        return Map.of("type", "object", "properties", properties);
    }

    private static Map<String, Object> countMapSchema() {
        return Map.of("type", "object", "additionalProperties", Map.of("type", "integer"));
    }

    private static Map<String, Object> pathItemSchema() {
        return Map.of(
                "type",
                "object",
                "properties",
                Map.of(
                        "nodeIds", Map.of("type", "array", "items", Map.of("type", "string")),
                        "edgeLabels", Map.of("type", "array", "items", Map.of("type", "string"))));
    }

    private McpServerFeatures.SyncPromptSpecification promptSpec(
            String name, String description, List<McpSchema.PromptArgument> args, String template) {
        McpSchema.Prompt prompt = new McpSchema.Prompt(name, description, args);
        return new McpServerFeatures.SyncPromptSpecification(prompt, (exchange, request) -> {
            Map<String, Object> promptArgs;
            if (request.arguments() != null) {
                promptArgs = request.arguments();
            } else {
                promptArgs = Map.of();
            }
            String text = fillTemplate(template, promptArgs);
            return new McpSchema.GetPromptResult(
                    description,
                    List.of(new McpSchema.PromptMessage(McpSchema.Role.USER, new McpSchema.TextContent(text))));
        });
    }

    private static McpSchema.PromptArgument arg(String name, String description, boolean required) {
        return new McpSchema.PromptArgument(name, description, required);
    }

    static String fillTemplate(String template, Map<String, Object> args) {
        String result = template.strip();
        for (Map.Entry<String, Object> entry : args.entrySet()) {
            String value;
            if (entry.getValue() == null) {
                value = "";
            } else {
                value = entry.getValue().toString();
            }
            result = result.replace("{" + entry.getKey() + "}", value);
        }
        return result.replaceAll("\\{[A-Za-z0-9_]+}", "");
    }

    private static SchemaBuilder schema() {
        return new SchemaBuilder();
    }

    /** Output schema for a top-level array whose items match the given property map. */
    private static SchemaBuilder arrayOutput(Map<String, Object> itemProperties) {
        return new SchemaBuilder().asArray(itemProperties);
    }

    /** Minimal output schema for diagram-rendering tools whose real payload is the text content, not structured data. */
    private static SchemaBuilder diagramOutput() {
        return new SchemaBuilder().opt("diagramType", TYPE_STRING, "Rendered diagram format, e.g. mermaid or likec4");
    }

    /** Reusable item shape for tools returning {@code GraphQuery.GraphNode}s: id, name, label, and a free-form properties bag. */
    private static Map<String, Object> nodeItemSchema() {
        return Map.of(
                "type",
                "object",
                "properties",
                Map.of(
                        "id", Map.of("type", "string"),
                        "name", Map.of("type", "string"),
                        "label", Map.of("type", "string"),
                        "properties", Map.of("type", "object")));
    }

    /** Reusable item shape for tools returning {@code GraphQuery.GraphEdge}s. */
    private static Map<String, Object> edgeItemSchema() {
        return Map.of(
                "type",
                "object",
                "properties",
                Map.of(
                        "fromId", Map.of("type", "string"),
                        "toId", Map.of("type", "string"),
                        "label", Map.of("type", "string"),
                        "properties", Map.of("type", "object")));
    }

    private static final class SchemaBuilder {
        private final Map<String, Object> props = new LinkedHashMap<>();
        private final List<String> required = new ArrayList<>();
        private Map<String, Object> arrayItems;
        private Map<String, Object> rawSchema;

        SchemaBuilder opt(String name, String type, String description) {
            props.put(name, Map.of("type", type, "description", description));
            return this;
        }

        SchemaBuilder reqArray(String name, String itemType, String description) {
            props.put(name, Map.of("type", "array", "items", Map.of("type", itemType), "description", description));
            required.add(name);
            return this;
        }

        SchemaBuilder reqArraySchema(String name, Map<String, Object> itemSchema) {
            props.put(name, Map.of("type", "array", "items", itemSchema));
            required.add(name);
            return this;
        }

        /** Marks this schema as a top-level array (used for output schemas) instead of an object. */
        SchemaBuilder asArray(Map<String, Object> itemSchema) {
            this.arrayItems = itemSchema;
            return this;
        }

        SchemaBuilder raw(Map<String, Object> value) {
            this.rawSchema = new LinkedHashMap<>(value);
            return this;
        }

        Map<String, Object> build() {
            if (rawSchema != null) {
                return rawSchema;
            }
            if (arrayItems != null) {
                Map<String, Object> schema = new LinkedHashMap<>();
                schema.put("type", "array");
                schema.put("items", arrayItems);
                return schema;
            }
            Map<String, Object> schema = new LinkedHashMap<>();
            schema.put("type", "object");
            if (!props.isEmpty()) {
                schema.put("properties", props);
            }
            if (!required.isEmpty()) {
                schema.put("required", required);
            }
            return schema;
        }
    }
}
