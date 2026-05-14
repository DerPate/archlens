package dev.dominikbreu.spoonmcp.renderer;

import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.dominikbreu.spoonmcp.view.ArchitectureViewKind;
import dev.dominikbreu.spoonmcp.view.ArchitectureViewProjection;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LikeC4ModelRendererTest {

    @Test
    void rendersProjectionAsLikeC4TextWithMetadata() {
        ArchitectureViewProjection projection = new ArchitectureViewProjection(
                ArchitectureViewKind.COMPONENT,
                "Demo Component View",
                "app:demo",
                List.of(new ArchitectureViewProjection.Node(
                        "comp:SchedulerJob",
                        "SchedulerJob",
                        "component",
                        Map.of("workflowRelevant", true, "noiseScore", 0))),
                List.of(),
                List.of());

        String likec4 = new LikeC4ModelRenderer().render(projection);

        assertTrue(likec4.contains("specification"));
        assertTrue(likec4.contains("model"));
        assertTrue(likec4.contains("views"));
        assertTrue(likec4.contains("schedulerjob = component 'SchedulerJob'"));
        assertTrue(likec4.contains("workflowrelevant 'true'"), "expected workflowrelevant 'true' in:\n" + likec4);
    }
}
