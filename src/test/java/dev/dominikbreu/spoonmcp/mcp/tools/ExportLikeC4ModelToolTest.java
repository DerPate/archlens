package dev.dominikbreu.spoonmcp.mcp.tools;

import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.dominikbreu.spoonmcp.cache.ModelCache;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ExportLikeC4ModelToolTest {

    @Test
    void exportsLikeC4TextFromIndexedGraph() throws Exception {
        ModelCache cache = ToolTestFixtures.indexFixtureProject("state-handoff");
        ExportLikeC4ModelTool tool = new ExportLikeC4ModelTool(cache);

        String result = tool.call(Map.of(
                "app", "state-handoff",
                "view", "component",
                "maxNodes", 12));

        assertTrue(result.contains("specification"), "expected specification in:\n" + result);
        assertTrue(result.contains("model"), "expected model in:\n" + result);
        assertTrue(result.contains("views"), "expected views in:\n" + result);
        assertTrue(result.contains("component"), "expected component in:\n" + result);
    }
}
