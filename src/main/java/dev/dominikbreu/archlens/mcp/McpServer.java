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
                "Analyze one or more Java project roots and build the internal architecture model.",
                schema().reqArray("paths", TYPE_STRING, "Project root directory paths to analyze"),
                indexTool::execute));

        specs.add(toolSpec(
                "list_apps",
                "List recognized applications, modules, and their packaging types.",
                schema(),
                listAppsTool::execute));

        specs.add(toolSpec(
                "find_entrypoints",
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
                entrypointsTool::execute));

        specs.add(toolSpec(
                "find_components",
                "Return architecture-relevant components (services, repositories, EJBs, entities, etc.).",
                schema().opt(APP_ID, TYPE_STRING, APP_ID_DESCRIPTION)
                        .opt(
                                "type",
                                TYPE_STRING,
                                "REST_RESOURCE | SERVICE | REPOSITORY | ENTITY | EJB_STATELESS | EJB_STATEFUL | EJB_SINGLETON | MESSAGE_DRIVEN_BEAN | SCHEDULER | HTTP_CLIENT | CDI_EVENT_CONSUMER | CDI_EVENT_PRODUCER | REMOTE_SERVICE | UTILITY | UNKNOWN")
                        .opt("technology", TYPE_STRING, "quarkus | javaee | jpa"),
                componentsTool::execute));

        specs.add(toolSpec(
                "get_component_dependencies",
                "Return relevant dependencies for a component, with optional depth limit and condensation of non-architectural intermediaries.",
                schema().opt(
                                "componentId",
                                TYPE_STRING,
                                "Component ID — the fully-qualified class name, e.g. com.example.UserService")
                        .opt("name", TYPE_STRING, "Component simple name (partial match)")
                        .opt("depth", TYPE_INTEGER, "Traversal depth (default 1, max 5)")
                        .opt("condensed", "boolean", "Remove UTILITY/UNKNOWN intermediaries (default true)"),
                dependenciesTool::execute));

        specs.add(toolSpec(
                "infer_containers",
                "Group components into logical containers (api / service / repository / domain / messaging / scheduling).",
                schema().opt(APP_ID, TYPE_STRING, APP_ID_DESCRIPTION),
                containersTool::execute));

        specs.add(toolSpec(
                "render_mermaid_flowchart",
                "Render a Mermaid flowchart for static architecture views (system / container / component level).",
                schema().opt(APP_ID, TYPE_STRING, APP_ID_DESCRIPTION)
                        .opt(
                                "level",
                                TYPE_STRING,
                                "system | container | module | component (default: component) — module shows WAR deployment-unit with embedded JAR internal_modules"),
                flowchartTool::execute));

        specs.add(toolSpec(
                "call_flow",
                "Return the runtime call flow for an entry point: ordered steps and a Mermaid flowchart. Component shapes reflect architectural role (cylinder=repository, parallelogram=http-client, etc.). Edge labels show the actual called method name.",
                schema().opt(ENTRYPOINT_ID, TYPE_STRING, "Entrypoint ID (from find_entrypoints)")
                        .opt(
                                ENTRYPOINT_NAME,
                                TYPE_STRING,
                                "Entrypoint path, name, or 'METHOD /path' (e.g. 'GET /account') for HTTP-method disambiguation"),
                callFlowTool::execute));

        specs.add(toolSpec(
                "render_source_overview",
                "Render a package-aware Mermaid source overview with components and dependency edges.",
                schema().opt(
                                "maxComponentsPerPackage",
                                TYPE_INTEGER,
                                "Maximum rendered component nodes per package (default 25)"),
                sourceOverviewTool::execute));

        specs.add(toolSpec(
                "render_dependency_map",
                "Render an aggregated Mermaid dependency map grouped by source responsibility.",
                schema(),
                dependencyMapTool::execute));

        specs.add(toolSpec(
                "render_component_dependency_diagram",
                "Render a focused Mermaid dependency diagram for one component.",
                schema().opt("componentId", TYPE_STRING, "Component ID")
                        .opt("name", TYPE_STRING, "Component simple name or partial qualified name")
                        .opt("depth", TYPE_INTEGER, "Traversal depth (default 2)"),
                dependencyDiagramTool::execute));

        specs.add(toolSpec(
                "export_architecture_docs",
                "Write Markdown architecture documentation with MCP-generated Mermaid diagrams.",
                schema().opt("outputPath", TYPE_STRING, "Output Markdown path (default docs/GENERATED_ARCHITECTURE.md)")
                        .opt(
                                FOCUS_COMPONENT,
                                TYPE_STRING,
                                "Component used for the dependency slice (default McpServer)"),
                exportDocsTool::execute));

        specs.add(toolSpec(
                "export_graph_architecture_poc",
                "Write a graph-centric architecture POC document with graph metadata, property examples, and MCP query samples.",
                schema().opt(
                                "outputPath",
                                TYPE_STRING,
                                "Output Markdown path (default docs/SOURCE_ARCHITECTURE_POC.md)")
                        .opt(
                                FOCUS_COMPONENT,
                                TYPE_STRING,
                                "Component used for the graph focus slice (default McpServer)"),
                exportGraphPocTool::execute));

        specs.add(toolSpec(
                "export_graph_data",
                "Write architecture graph JSON for standalone visual graph viewers, including raw snapshot data and viewer-ready projections.",
                schema().opt("outputPath", TYPE_STRING, "Output JSON path (default docs/GRAPH_DATA.json)")
                        .opt("limit", TYPE_INTEGER, "Maximum graph nodes to export (default 5000)"),
                exportGraphDataTool::execute));

        specs.add(toolSpec(
                "export_graph_viewer",
                "Write a self-contained HTML viewer using the same graph export payload as export_graph_data.",
                schema().opt("outputPath", TYPE_STRING, "Output HTML path (default docs/GRAPH_VIEWER.html)")
                        .opt("limit", TYPE_INTEGER, "Maximum graph nodes to export (default 5000)"),
                exportGraphViewerTool::execute));

        specs.add(toolSpec(
                "query_architecture_graph",
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
                        .opt("isCondensable", TYPE_STRING, "Shorthand filter: true | false — only condensable edges"),
                graphTool::execute));

        specs.add(toolSpec(
                "trace_data_flow",
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
                traceDataFlowTool::execute));

        specs.add(toolSpec(
                "render_use_case_timeline",
                "Render a Mermaid gantt chart showing sequential execution steps across use cases. Each use case is a section; each component hop is a task bar positioned by call depth. Useful for comparing execution depth and component involvement across entry points.",
                schema().opt(ENTRYPOINT_ID, TYPE_STRING, "Filter to a single use case by entrypoint ID")
                        .opt(
                                ENTRYPOINT_NAME,
                                TYPE_STRING,
                                "Filter by path, name, or 'METHOD /path' (e.g. 'GET /account') for HTTP-method disambiguation")
                        .opt("maxUseCases", TYPE_INTEGER, "Maximum sections to render (default 10)")
                        .opt(MAX_DEPTH, TYPE_INTEGER, "Maximum steps per section (default 5)"),
                useCaseTimelineTool::execute));

        specs.add(toolSpec(
                "render_pipeline",
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
                pipelineTool::execute));

        specs.add(toolSpec(
                "detect_use_cases",
                "Detect business use cases from indexed entrypoints and their call chains. Uses call-graph data when available; falls back to injection-dependency traversal.",
                schema().opt(
                                "configFile",
                                TYPE_STRING,
                                "Path to a JSON naming config file ({ \"names\": { \"<entrypointId>\": \"Display Name\" } })")
                        .opt("module", TYPE_STRING, "Filter results by app/module ID (partial match)")
                        .opt(MAX_DEPTH, TYPE_INTEGER, "Max call-chain depth shown per use case (default 5)"),
                detectUseCasesTool::execute));

        specs.add(toolSpec(
                "render_architecture_view",
                "Render a projection-first architecture view from the indexed graph. Start with view=component for C4-style component views. Prioritizes workflow-relevant components over utility fan-in noise.",
                schema().opt("app", TYPE_STRING, APP_NAME_OR_ID)
                        .opt("view", TYPE_STRING, "View kind: component")
                        .opt(MAX_NODES, TYPE_INTEGER, "Maximum component nodes to include (default 18)"),
                renderArchitectureViewTool::call));

        specs.add(toolSpec(
                "export_likec4_model",
                "Export the indexed architecture graph as LikeC4-style model text. Supports workspace (default) or component views for loading into LikeC4 tooling or providing structured LLM context.",
                schema().opt("app", TYPE_STRING, APP_NAME_OR_ID)
                        .opt("view", TYPE_STRING, "View kind: workspace or component (default workspace)")
                        .opt(MAX_NODES, TYPE_INTEGER, "Maximum component nodes to include (default 18)"),
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

    private McpServerFeatures.SyncToolSpecification toolSpec(
            String name, String description, SchemaBuilder schema, Function<Map<String, Object>, String> handler) {

        McpSchema.Tool tool = McpSchema.Tool.builder(name, schema.build())
                .description(description)
                .build();

        return new McpServerFeatures.SyncToolSpecification(tool, (exchange, request) -> {
            Map<String, Object> args;
            if (request.arguments() != null) {
                args = request.arguments();
            } else {
                args = Map.of();
            }
            String result = handler.apply(args);
            return McpSchema.CallToolResult.builder().addTextContent(result).build();
        });
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

    private static final class SchemaBuilder {
        private final Map<String, Object> props = new LinkedHashMap<>();
        private final List<String> required = new ArrayList<>();

        SchemaBuilder opt(String name, String type, String description) {
            props.put(name, Map.of("type", type, "description", description));
            return this;
        }

        SchemaBuilder reqArray(String name, String itemType, String description) {
            props.put(name, Map.of("type", "array", "items", Map.of("type", itemType), "description", description));
            required.add(name);
            return this;
        }

        Map<String, Object> build() {
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
