package dev.dominikbreu.spoonmcp.mcp.tools;

import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.dominikbreu.spoonmcp.cache.ModelCache;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RenderArchitectureViewToolTest {

    @Test
    void returnsMermaidForIndexedApplication() throws Exception {
        ModelCache cache = ToolTestFixtures.indexFixtureProject("state-handoff");
        RenderArchitectureViewTool tool = new RenderArchitectureViewTool(cache);

        String result = tool.call(Map.of(
                "app", "state-handoff",
                "view", "component",
                "maxNodes", 12));

        assertTrue(result.contains("flowchart LR"), "expected flowchart LR in:\n" + result);
        assertTrue(
                result.contains("state handoff") || result.contains("STATE_HANDOFF"),
                "expected state handoff edge in:\n" + result);
        assertTrue(result.contains("Scheduler"), "expected Scheduler in:\n" + result);
    }
}
