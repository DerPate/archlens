package dev.dominikbreu.archlens.mcp.tools;

import dev.dominikbreu.archlens.cache.GraphQuery;
import dev.dominikbreu.archlens.cache.ModelCache;
import dev.dominikbreu.archlens.renderer.GraphViewerHtmlRenderer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/** MCP tool that writes a self-contained visual architecture graph debugger. */
public class ExportGraphViewerTool {

    private static final Path DEFAULT_OUTPUT = Path.of("docs", "GRAPH_VIEWER.html");
    private static final int DEFAULT_LIMIT = 5000;

    private final ModelCache cache;
    private final GraphViewerHtmlRenderer renderer = new GraphViewerHtmlRenderer();

    /**
     * Creates the tool with the shared model cache.
     *
     * @param cache model cache used by prior indexing
     */
    public ExportGraphViewerTool(ModelCache cache) {
        this.cache = cache;
    }

    /**
     * Exports an HTML graph viewer to the requested path.
     *
     * @param args JSON arguments including outputPath and limit
     * @return export status text plus a structured {outputPath, nodeCount, edgeCount} summary
     */
    public ToolResult execute(Map<String, Object> args) {
        try {
            GraphQuery graph = cache.graph();
            if (graph.isEmpty()) return ToolResult.textOnly("No workspace indexed yet. Call index_workspace first.");

            Path output = Path.of(ToolArgs.getString(args, "outputPath", DEFAULT_OUTPUT.toString()));
            int limit = ToolArgs.getInt(args, "limit", DEFAULT_LIMIT);
            GraphQuery.GraphSnapshot snapshot = graph.snapshot(limit);
            String html = renderer.render(GraphExportJson.write(snapshot, Instant.now()));

            Path parent = output.getParent();
            if (parent != null) Files.createDirectories(parent);
            Files.writeString(output, html);

            Map<String, Object> structured = new LinkedHashMap<>();
            structured.put("outputPath", output.toAbsolutePath().toString());
            structured.put("nodeCount", snapshot.metadata().includedNodeCount());
            structured.put("edgeCount", snapshot.metadata().includedEdgeCount());
            return new ToolResult(
                    "Exported graph viewer to " + output.toAbsolutePath()
                            + "\nNodes: " + snapshot.metadata().includedNodeCount()
                            + "\nEdges: " + snapshot.metadata().includedEdgeCount(),
                    structured);
        } catch (Exception e) {
            return ToolResult.textOnly("Error exporting graph viewer: " + e.getMessage());
        }
    }
}
