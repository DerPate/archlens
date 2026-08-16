package dev.dominikbreu.archlens.mcp.tools;

import static org.assertj.core.api.Assertions.assertThat;

import dev.dominikbreu.archlens.cache.ModelCache;
import dev.dominikbreu.archlens.model.ArchitectureModel;
import dev.dominikbreu.archlens.model.Component;
import dev.dominikbreu.archlens.model.ComponentType;
import dev.dominikbreu.archlens.model.Entrypoint;
import dev.dominikbreu.archlens.model.EntrypointType;
import dev.dominikbreu.archlens.model.RuntimeFlow;
import dev.dominikbreu.archlens.model.RuntimeFlowStep;
import dev.dominikbreu.archlens.model.ids.ComponentId;
import dev.dominikbreu.archlens.model.ids.EntrypointId;
import java.nio.file.Files;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RenderUseCaseTimelineToolTest {

    @Test
    void unfilteredRenderPicksDeepestUseCasesAndReportsTruncation() throws Exception {
        // 8 use cases of increasing depth; the unfiltered default shows only the deepest 5.
        RenderUseCaseTimelineTool tool = new RenderUseCaseTimelineTool(cacheWithFlows(8));

        ToolResult result = tool.execute(Map.of());

        assertThat(result.error()).isFalse();
        Map<String, Object> structured = asMap(result);
        assertThat(structured).containsEntry("useCasesMatched", 8).containsEntry("useCasesShown", 5);
        assertThat(String.valueOf(structured.get("truncationHint"))).contains("5 deepest of 8");
        // ep7 is the deepest and ep0 the shallowest, so ep0 must be the one dropped.
        assertThat(result.text()).contains("[\"ep7\"]").doesNotContain("[\"ep0\"]");
    }

    @Test
    void explicitFilterShowsEveryMatchWithoutTruncation() throws Exception {
        // A caller who narrowed to one family wants all of it, not the unfiltered sample size.
        RenderUseCaseTimelineTool tool = new RenderUseCaseTimelineTool(cacheWithFlows(8));

        ToolResult result = tool.execute(Map.of("entrypointName", "ep"));

        assertThat(asMap(result))
                .containsEntry("useCasesMatched", 8)
                .containsEntry("useCasesShown", 8)
                .doesNotContainKey("truncationHint");
    }

    @Test
    void sectionOrderIsDeterministicAcrossRenders() throws Exception {
        RenderUseCaseTimelineTool first = new RenderUseCaseTimelineTool(cacheWithFlows(8));
        RenderUseCaseTimelineTool second = new RenderUseCaseTimelineTool(cacheWithFlows(8));

        assertThat(first.execute(Map.of()).text())
                .isEqualTo(second.execute(Map.of()).text());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(ToolResult result) {
        return (Map<String, Object>) result.structured();
    }

    /** Builds a model with {@code count} entrypoints where epN has N+1 flow steps. */
    private ModelCache cacheWithFlows(int count) throws Exception {
        ArchitectureModel model = new ArchitectureModel("timeline-test");
        for (int i = 0; i <= count; i++) {
            Component c = new Component();
            c.id = ComponentId.of("Comp" + i);
            c.name = "Comp" + i;
            c.qualifiedName = "Comp" + i;
            c.type = i == 0 ? ComponentType.REST_RESOURCE : ComponentType.SERVICE;
            model.components.add(c);
        }
        for (int i = 0; i < count; i++) {
            Entrypoint ep = new Entrypoint();
            ep.id = EntrypointId.deserialize("Comp0#ep" + i);
            ep.name = "ep" + i;
            ep.componentId = ComponentId.of("Comp0");
            ep.type = EntrypointType.REST_ENDPOINT;
            model.entrypoints.add(ep);

            RuntimeFlow flow = new RuntimeFlow();
            flow.id = "flow:" + ep.id.serialize();
            flow.entrypointId = ep.id;
            for (int step = 0; step <= i; step++) {
                RuntimeFlowStep s = new RuntimeFlowStep();
                s.order = step;
                s.componentId = ComponentId.of("Comp" + step);
                s.componentName = "Comp" + step;
                s.componentType = step == 0 ? "REST_RESOURCE" : "SERVICE";
                s.via = "call";
                flow.steps.add(s);
            }
            model.runtimeFlows.add(flow);
        }
        ModelCache cache = new ModelCache(
                Files.createTempDirectory("archlens-timeline-test-").toString());
        cache.store(model);
        return cache;
    }
}
