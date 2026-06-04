package dev.dominikbreu.spoonmcp.mcp.tools;

import dev.dominikbreu.spoonmcp.cache.ArchitectureGraph;
import dev.dominikbreu.spoonmcp.cache.GraphDataProjection;
import java.time.Instant;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

/** Shared JSON contract for graph export tools. */
final class GraphExportJson {

    private static final JsonMapper MAPPER = JsonMapper.builder()
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .build();

    private GraphExportJson() {}

    static String write(ArchitectureGraph.GraphSnapshot snapshot, Instant generatedAt) throws Exception {
        return MAPPER.writerWithDefaultPrettyPrinter()
                .writeValueAsString(new Payload(snapshot, GraphDataProjection.from(snapshot), generatedAt));
    }

    record Payload(
            ArchitectureGraph.GraphSnapshot snapshot,
            GraphDataProjection.ViewerProjections projections,
            Instant generatedAt) {}
}
