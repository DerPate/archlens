package dev.dominikbreu.archlens.mcp.tools;

import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.dominikbreu.archlens.cache.ModelCache;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ExportLikeC4ModelToolTest {

    @Test
    void defaultsToWorkspaceViewWithStandardLikeC4Views() throws Exception {
        ModelCache cache = ToolTestFixtures.indexFixtureProject("state-handoff");
        ExportLikeC4ModelTool tool = new ExportLikeC4ModelTool(cache);

        String result = tool.call(Map.of("app", "state-handoff", "maxNodes", 12));

        assertTrue(result.contains("specification"), "expected specification in:\n" + result);
        assertTrue(result.contains("model"), "expected model in:\n" + result);
        assertTrue(result.contains("views"), "expected views in:\n" + result);
        assertTrue(result.contains("view context"), "expected context view in:\n" + result);
        assertTrue(result.contains("view container"), "expected container view in:\n" + result);
        assertTrue(result.contains("view component"), "expected component view in:\n" + result);
    }

    @Test
    void componentViewExportsFocusedComponentOutput() throws Exception {
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
        assertTrue(result.contains("view index"), "expected focused index view in:\n" + result);
    }

    @Test
    void unsupportedViewNamesSupportedViews() throws Exception {
        ModelCache cache = ToolTestFixtures.indexFixtureProject("state-handoff");
        ExportLikeC4ModelTool tool = new ExportLikeC4ModelTool(cache);

        String result = tool.call(Map.of("view", "deployment"));

        assertTrue(result.contains("workspace"), "expected workspace in:\n" + result);
        assertTrue(result.contains("component"), "expected component in:\n" + result);
        assertTrue(result.contains("deployment"), "expected received view in:\n" + result);
    }
}
