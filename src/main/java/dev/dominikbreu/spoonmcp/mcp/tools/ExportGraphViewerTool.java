package dev.dominikbreu.spoonmcp.mcp.tools;

import dev.dominikbreu.spoonmcp.cache.ArchitectureGraph;
import dev.dominikbreu.spoonmcp.cache.ModelCache;
import dev.dominikbreu.spoonmcp.renderer.GraphViewerHtmlRenderer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
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
     * @return export status message with raw counts
     */
    public String execute(Map<String, Object> args) {
        try {
            ArchitectureGraph graph = cache.graph();
            if (graph.isEmpty()) return "No workspace indexed yet. Call index_workspace first.";

            Path output = Path.of(ToolArgs.getString(args, "outputPath", DEFAULT_OUTPUT.toString()));
            int limit = ToolArgs.getInt(args, "limit", DEFAULT_LIMIT);
            ArchitectureGraph.GraphSnapshot snapshot = graph.snapshot(limit);
            String html = renderer.render(GraphExportJson.write(snapshot, Instant.now()));

            Path parent = output.getParent();
            if (parent != null) Files.createDirectories(parent);
            Files.writeString(output, html);

            return "Exported graph viewer to " + output.toAbsolutePath()
                    + "\nNodes: " + snapshot.metadata().includedNodeCount()
                    + "\nEdges: " + snapshot.metadata().includedEdgeCount();
        } catch (Exception e) {
            return "Error exporting graph viewer: " + e.getMessage();
        }
    }
}
