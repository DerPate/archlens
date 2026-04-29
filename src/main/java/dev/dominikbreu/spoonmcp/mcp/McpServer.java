package dev.dominikbreu.spoonmcp.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.dominikbreu.spoonmcp.cache.ModelCache;
import dev.dominikbreu.spoonmcp.extractor.ArchitectureExtractor;
import dev.dominikbreu.spoonmcp.mcp.tools.*;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintStream;

/**
 * Minimal stdio JSON-RPC server exposing architecture-analysis MCP tools.
 */
public class McpServer {

    private final ObjectMapper mapper;
    private final IndexWorkspaceTool indexTool;
    private final ListAppsTool listAppsTool;
    private final FindEntrypointsTool entrypointsTool;
    private final FindComponentsTool componentsTool;
    private final GetComponentDependenciesTool dependenciesTool;
    private final InferContainersTool containersTool;
    private final RenderMermaidFlowchartTool flowchartTool;
    private final GetRuntimeFlowTool runtimeFlowTool;
    private final RenderCallFlowTool callFlowTool;
    private final ExplainArchitectureTool explainTool;
    private final RenderSourceOverviewTool sourceOverviewTool;
    private final RenderDependencyMapTool dependencyMapTool;
    private final RenderComponentDependencyDiagramTool dependencyDiagramTool;
    private final ExportArchitectureDocsTool exportDocsTool;
    private final ExportGraphArchitecturePocTool exportGraphPocTool;
    private final QueryArchitectureGraphTool graphTool;
    private final DetectUseCasesTool detectUseCasesTool;
    private final TraceDataFlowTool traceDataFlowTool;
    private final RenderUseCaseTimelineTool useCaseTimelineTool;
    private final PrintStream out;

    /** Creates a server with the default extractor, cache, and tool registry. */
    public McpServer() {
        this.mapper = new ObjectMapper();
        this.mapper.registerModule(new JavaTimeModule());

        ModelCache cache = new ModelCache();
        ArchitectureExtractor extractor = new ArchitectureExtractor();

        this.indexTool       = new IndexWorkspaceTool(extractor, cache);
        this.listAppsTool    = new ListAppsTool(cache);
        this.entrypointsTool = new FindEntrypointsTool(cache);
        this.componentsTool  = new FindComponentsTool(cache);
        this.dependenciesTool = new GetComponentDependenciesTool(cache);
        this.containersTool  = new InferContainersTool(cache);
        this.flowchartTool   = new RenderMermaidFlowchartTool(cache);
        this.runtimeFlowTool = new GetRuntimeFlowTool(cache);
        this.callFlowTool    = new RenderCallFlowTool(cache);
        this.explainTool     = new ExplainArchitectureTool(cache);
        this.sourceOverviewTool = new RenderSourceOverviewTool(cache);
        this.dependencyMapTool = new RenderDependencyMapTool(cache);
        this.dependencyDiagramTool = new RenderComponentDependencyDiagramTool(cache);
        this.exportDocsTool = new ExportArchitectureDocsTool(cache);
        this.exportGraphPocTool = new ExportGraphArchitecturePocTool(cache);
        this.graphTool = new QueryArchitectureGraphTool(cache);
        this.detectUseCasesTool = new DetectUseCasesTool(cache);
        this.traceDataFlowTool      = new TraceDataFlowTool(cache);
        this.useCaseTimelineTool    = new RenderUseCaseTimelineTool(cache);
        this.out = System.out;
    }

    /**
     * Runs the JSON-RPC read/evaluate/write loop until standard input closes.
     *
     * @throws Exception if input reading or response serialization fails
     */
    public void run() throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        String line;
        while ((line = reader.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty()) continue;
            try {
                handleMessage(mapper.readTree(line));
            } catch (Exception e) {
                System.err.println("[spoon-mcp] Error: " + e.getMessage());
            }
        }
    }

    private void handleMessage(JsonNode msg) throws Exception {
        if (!msg.has("id")) return; // notification

        String method = msg.has("method") ? msg.get("method").asText() : "";
        JsonNode id = msg.get("id");
        JsonNode params = msg.get("params");

        ObjectNode response = mapper.createObjectNode();
        response.put("jsonrpc", "2.0");
        response.set("id", id);

        switch (method) {
            case "initialize" -> response.set("result", buildInitializeResult());
            case "tools/list" -> {
                ObjectNode result = mapper.createObjectNode();
                result.set("tools", buildToolsList());
                response.set("result", result);
            }
            case "tools/call" -> {
                String toolName = params != null && params.has("name") ? params.get("name").asText() : "";
                JsonNode arguments = params != null ? params.get("arguments") : null;
                response.set("result", buildCallResult(callTool(toolName, arguments)));
            }
            default -> {
                ObjectNode error = mapper.createObjectNode();
                error.put("code", -32601);
                error.put("message", "Method not found: " + method);
                response.set("error", error);
            }
        }

        out.println(mapper.writeValueAsString(response));
        out.flush();
    }

    private String callTool(String name, JsonNode args) {
        return switch (name) {
            case "index_workspace"           -> indexTool.execute(args);
            case "list_apps"                 -> listAppsTool.execute(args);
            case "find_entrypoints"          -> entrypointsTool.execute(args);
            case "find_components"           -> componentsTool.execute(args);
            case "get_component_dependencies"-> dependenciesTool.execute(args);
            case "infer_containers"          -> containersTool.execute(args);
            case "render_mermaid_flowchart"  -> flowchartTool.execute(args);
            case "get_runtime_flow"          -> runtimeFlowTool.execute(args);
            case "render_call_flow"          -> callFlowTool.execute(args);
            case "explain_architecture"      -> explainTool.execute(args);
            case "render_source_overview"    -> sourceOverviewTool.execute(args);
            case "render_dependency_map"     -> dependencyMapTool.execute(args);
            case "render_component_dependency_diagram" -> dependencyDiagramTool.execute(args);
            case "export_architecture_docs"  -> exportDocsTool.execute(args);
            case "export_graph_architecture_poc" -> exportGraphPocTool.execute(args);
            case "query_architecture_graph"  -> graphTool.execute(args);
            case "detect_use_cases"          -> detectUseCasesTool.execute(args);
            case "trace_data_flow"           -> traceDataFlowTool.execute(args);
            case "render_use_case_timeline"  -> useCaseTimelineTool.execute(args);
            default -> "Unknown tool: " + name;
        };
    }

    // ── tool list ─────────────────────────────────────────────────────────────

    private ArrayNode buildToolsList() {
        ArrayNode tools = mapper.createArrayNode();

        tools.add(tool("index_workspace",
            "Analyze one or more Java project roots and build the internal architecture model.",
            schema().reqArray("paths", "string", "Project root directory paths to analyze")));

        tools.add(tool("list_apps",
            "List recognized applications, modules, and their packaging types.",
            schema()));

        tools.add(tool("find_entrypoints",
            "Return architecturally relevant entry points: REST endpoints, JMS consumers, schedulers, EJB methods.",
            schema()
                .opt("appId", "string", "Filter by app ID (partial match)")
                .opt("type", "string", "REST_ENDPOINT | JMS_CONSUMER | SCHEDULER | EJB_BUSINESS_METHOD")));

        tools.add(tool("find_components",
            "Return architecture-relevant components (services, repositories, EJBs, entities, etc.).",
            schema()
                .opt("appId", "string", "Filter by app ID (partial match)")
                .opt("type", "string", "REST_RESOURCE | SERVICE | REPOSITORY | ENTITY | EJB_STATELESS | EJB_STATEFUL | EJB_SINGLETON | MESSAGE_DRIVEN_BEAN | SCHEDULER | HTTP_CLIENT")
                .opt("technology", "string", "quarkus | javaee | jpa")));

        tools.add(tool("get_component_dependencies",
            "Return relevant dependencies for a component, with optional depth limit and condensation of non-architectural intermediaries.",
            schema()
                .opt("componentId", "string", "Component ID (e.g. comp:com.example.UserService)")
                .opt("name", "string", "Component simple name (partial match)")
                .opt("depth", "integer", "Traversal depth (default 1, max 5)")
                .opt("condensed", "boolean", "Remove UTILITY/UNKNOWN intermediaries (default true)")));

        tools.add(tool("infer_containers",
            "Group components into logical containers (api / service / repository / domain / messaging / scheduling).",
            schema()
                .opt("appId", "string", "Filter by app ID (partial match)")));

        tools.add(tool("render_mermaid_flowchart",
            "Render a Mermaid flowchart for static architecture views (system / container / component level).",
            schema()
                .opt("appId", "string", "Filter by app ID (partial match)")
                .opt("level", "string", "system | container | module | component (default: component) — module shows WAR deployment-unit with embedded JAR internal_modules")));

        tools.add(tool("get_runtime_flow",
            "Return a reduced runtime path for a use case or entry point by following injection dependencies.",
            schema()
                .opt("entrypointId", "string", "Entrypoint ID (from find_entrypoints)")
                .opt("entrypointName", "string", "Entrypoint name (partial match)")
                .opt("maxDepth", "integer", "Max traversal depth (default 5)")));

        tools.add(tool("render_call_flow",
            "Render a Mermaid flowchart showing the execution path from an entry point through its call chain. Component shapes reflect architectural role (cylinder=repository, parallelogram=http-client, etc.). Edge labels show the actual called method name.",
            schema()
                .opt("entrypointId", "string", "Entrypoint ID (from find_entrypoints)")
                .opt("entrypointName", "string", "Entrypoint name or path (partial match)")
                .opt("maxDepth", "integer", "Max traversal depth (default 5)")));

        tools.add(tool("explain_architecture",
            "Return an agent-friendly textual summary of the architecture model (apps, components, dependencies, deployments).",
            schema()
                .opt("appId", "string", "Filter by app ID (partial match)")));

        tools.add(tool("render_source_overview",
            "Render a package-aware Mermaid source overview with components and dependency edges.",
            schema()
                .opt("maxComponentsPerPackage", "integer", "Maximum rendered component nodes per package (default 25)")));

        tools.add(tool("render_dependency_map",
            "Render an aggregated Mermaid dependency map grouped by source responsibility.",
            schema()));

        tools.add(tool("render_component_dependency_diagram",
            "Render a focused Mermaid dependency diagram for one component.",
            schema()
                .opt("componentId", "string", "Component ID")
                .opt("name", "string", "Component simple name or partial qualified name")
                .opt("depth", "integer", "Traversal depth (default 2)")));

        tools.add(tool("export_architecture_docs",
            "Write Markdown architecture documentation with MCP-generated Mermaid diagrams.",
            schema()
                .opt("outputPath", "string", "Output Markdown path (default docs/GENERATED_ARCHITECTURE.md)")
                .opt("focusComponent", "string", "Component used for the dependency slice (default McpServer)")));

        tools.add(tool("export_graph_architecture_poc",
            "Write a graph-centric architecture POC document with graph metadata, property examples, and MCP query samples.",
            schema()
                .opt("outputPath", "string", "Output Markdown path (default docs/SOURCE_ARCHITECTURE_POC.md)")
                .opt("focusComponent", "string", "Component used for the graph focus slice (default McpServer)")));

        tools.add(tool("query_architecture_graph",
            "Query the architecture as a graph: summary, node search, neighborhoods, paths, or impact slices.",
            schema()
                .opt("action", "string", "summary | find_nodes | find_edges | neighborhood | paths | impacted_by")
                .opt("label", "string", "Node label for find_nodes: Application | Component | Entrypoint | Interface | Container | Deployment | RuntimeFlow")
                .opt("query", "string", "Free-text node search")
                .opt("filters", "object", "Property filters, with numeric comparisons such as {\"confidence\":\"<=0.6\"}")
                .opt("nodeId", "string", "Node ID for neighborhood or impacted_by")
                .opt("fromId", "string", "Source node ID for paths")
                .opt("toId", "string", "Target node ID for paths")
                .opt("direction", "string", "in | out | both for neighborhood")
                .opt("maxDepth", "integer", "Traversal depth for paths or impacted_by")
                .opt("limit", "integer", "Maximum returned rows")));

        tools.add(tool("trace_data_flow",
            "Trace how entrypoint parameters flow through the call graph to sinks (persistence, messaging, http-outbound, event-bus). Requires call-graph data from index_workspace.",
            schema()
                .opt("entrypointId", "string", "Filter by entrypoint ID (partial match)")
                .opt("entrypointName", "string", "Filter by entrypoint name or path (partial match)")
                .opt("param", "string", "Filter by tracked parameter name")
                .opt("sinkKind", "string", "Filter by sink kind: persistence | messaging | http-outbound | event-bus")));

        tools.add(tool("render_use_case_timeline",
            "Render a Mermaid gantt chart showing sequential execution steps across use cases. Each use case is a section; each component hop is a task bar positioned by call depth. Useful for comparing execution depth and component involvement across entry points.",
            schema()
                .opt("entrypointId", "string", "Filter to a single use case by entrypoint ID")
                .opt("entrypointName", "string", "Filter by entrypoint name or HTTP path (partial match)")
                .opt("maxUseCases", "integer", "Maximum sections to render (default 10)")
                .opt("maxDepth", "integer", "Maximum steps per section (default 5)")));

        tools.add(tool("detect_use_cases",
            "Detect business use cases from indexed entrypoints and their call chains. Uses call-graph data when available; falls back to injection-dependency traversal.",
            schema()
                .opt("configFile", "string", "Path to a JSON naming config file ({ \"names\": { \"<entrypointId>\": \"Display Name\" } })")
                .opt("module", "string", "Filter results by app/module ID (partial match)")
                .opt("maxDepth", "integer", "Max call-chain depth shown per use case (default 5)")));

        return tools;
    }

    // ── schema builder ────────────────────────────────────────────────────────

    private SchemaBuilder schema() { return new SchemaBuilder(mapper); }

    private ObjectNode tool(String name, String description, SchemaBuilder schema) {
        ObjectNode t = mapper.createObjectNode();
        t.put("name", name);
        t.put("description", description);
        t.set("inputSchema", schema.build());
        return t;
    }

    private static class SchemaBuilder {
        private final ObjectMapper mapper;
        private final ObjectNode props;
        private final ArrayNode required;

        SchemaBuilder(ObjectMapper mapper) {
            this.mapper = mapper;
            this.props = mapper.createObjectNode();
            this.required = mapper.createArrayNode();
        }

        SchemaBuilder req(String name, ObjectNode propNode) {
            props.set(name, propNode);
            required.add(name);
            return this;
        }

        SchemaBuilder opt(String name, String type, String description) {
            ObjectNode p = mapper.createObjectNode();
            p.put("type", type);
            p.put("description", description);
            props.set(name, p);
            return this;
        }

        SchemaBuilder reqArray(String name, String itemType, String description) {
            ObjectNode p = mapper.createObjectNode();
            p.put("type", "array");
            ObjectNode items = mapper.createObjectNode();
            items.put("type", itemType);
            p.set("items", items);
            p.put("description", description);
            props.set(name, p);
            required.add(name);
            return this;
        }

        ObjectNode build() {
            ObjectNode schema = mapper.createObjectNode();
            schema.put("type", "object");
            schema.set("properties", props);
            if (required.size() > 0) schema.set("required", required);
            return schema;
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private ObjectNode buildInitializeResult() {
        ObjectNode result = mapper.createObjectNode();
        result.put("protocolVersion", "2024-11-05");
        ObjectNode caps = mapper.createObjectNode();
        caps.set("tools", mapper.createObjectNode());
        result.set("capabilities", caps);
        ObjectNode info = mapper.createObjectNode();
        info.put("name", "spoon-mcp-server");
        info.put("version", "1.0.0");
        result.set("serverInfo", info);
        return result;
    }

    private ObjectNode buildCallResult(String text) {
        ObjectNode result = mapper.createObjectNode();
        ArrayNode content = mapper.createArrayNode();
        ObjectNode textNode = mapper.createObjectNode();
        textNode.put("type", "text");
        textNode.put("text", text);
        content.add(textNode);
        result.set("content", content);
        return result;
    }
}
