package dev.dominikbreu.archlens.renderer;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.dominikbreu.archlens.view.ArchitectureViewKind;
import dev.dominikbreu.archlens.view.ArchitectureViewProjection;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ArchitectureViewMermaidRendererTest {

    @Test
    void rendersC4StyleComponentProjectionWithoutInventingNodes() {
        ArchitectureViewProjection projection = new ArchitectureViewProjection(
                ArchitectureViewKind.COMPONENT,
                "Demo Component View",
                "app:demo",
                List.of(
                        new ArchitectureViewProjection.Node(
                                "Consumer", "Consumer", "component", Map.of("componentType", "SERVICE")),
                        new ArchitectureViewProjection.Node(
                                "Scheduler", "Scheduler", "component", Map.of("componentType", "SCHEDULER"))),
                List.of(new ArchitectureViewProjection.Edge(
                        "Consumer", "Scheduler", "STATE_HANDOFF", "shared state handoff")),
                List.of());

        String mermaid = new ArchitectureViewMermaidRenderer().render(projection);

        assertTrue(mermaid.contains("flowchart LR"));
        assertTrue(mermaid.contains("Demo Component View"));
        assertTrue(mermaid.contains("Consumer"));
        assertTrue(mermaid.contains("Scheduler"));
        assertTrue(mermaid.contains("shared state handoff"));
        assertFalse(mermaid.contains("External User"));
    }
}
