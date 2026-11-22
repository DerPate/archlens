package dev.dominikbreu.archlens.mcp.tools;

import dev.dominikbreu.archlens.cache.GraphQuery;
import dev.dominikbreu.archlens.io.OutputBoundary;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Shared {@code outputPath} handling of the export tools. */
final class ExportTarget {
    private ExportTarget() {}

    /** Resolves the requested or default output path, confined to the project it names. */
    static Path resolve(Map<String, Object> args, Path defaultOutput, GraphQuery graph) throws IOException {
        Path requested = Path.of(ToolArgs.getString(args, "outputPath", defaultOutput.toString()));
        List<String> roots = graph.allApplicationNodes().stream()
                .map(GraphQuery.ApplicationNode::rootPath)
                .filter(Objects::nonNull)
                .toList();
        return OutputBoundary.resolve(requested, roots);
    }
}
