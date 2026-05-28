package dev.dominikbreu.spoonmcp.renderer;

import static org.assertj.core.api.Assertions.assertThat;

import dev.dominikbreu.spoonmcp.model.ArchitectureModel;
import dev.dominikbreu.spoonmcp.model.Component;
import dev.dominikbreu.spoonmcp.model.ComponentType;
import dev.dominikbreu.spoonmcp.model.Dependency;
import dev.dominikbreu.spoonmcp.model.ids.ComponentId;
import java.util.List;
import org.junit.jupiter.api.Test;

class MermaidDependencyMapRendererTest {

    private final MermaidDependencyMapRenderer renderer = new MermaidDependencyMapRenderer();

    @Test
    void aggregatesDependenciesBySourceResponsibility() {
        ArchitectureModel model = new ArchitectureModel("test");
        Component server = component("comp:server", "McpServer", "dev.dominikbreu.spoonmcp.mcp.McpServer");
        Component tool =
                component("comp:tool", "IndexWorkspaceTool", "dev.dominikbreu.spoonmcp.mcp.tools.IndexWorkspaceTool");
        Component extractor = component(
                "comp:extractor", "ArchitectureExtractor", "dev.dominikbreu.spoonmcp.extractor.ArchitectureExtractor");
        Component scanner = component("comp:scanner", "SpoonScanner", "dev.dominikbreu.spoonmcp.scanner.SpoonScanner");
        model.components.addAll(List.of(server, tool, extractor, scanner));
        model.dependencies.add(dependency(server.id, tool.id, "field-reference"));
        model.dependencies.add(dependency(tool.id, extractor.id, "field-reference"));
        model.dependencies.add(dependency(extractor.id, scanner.id, "field-reference"));

        String out = renderer.render(model);

        assertThat(out).startsWith("flowchart LR");
        assertThat(out).contains("mcp.tools\\n1 components");
        assertThat(out).contains("extractor\\n1 components");
        assertThat(out).contains("dep_mcp -->|1 dep / field-reference=1| dep_mcp_tools");
        assertThat(out).contains("dep_mcp_tools -->|1 dep / field-reference=1| dep_extractor");
    }

    @Test
    void countsInternalDependenciesWithoutDrawingSelfEdges() {
        ArchitectureModel model = new ArchitectureModel("test");
        Component extractor = component(
                "comp:extractor", "ArchitectureExtractor", "dev.dominikbreu.spoonmcp.extractor.ArchitectureExtractor");
        Component condenser = component(
                "comp:condenser", "DependencyCondenser", "dev.dominikbreu.spoonmcp.extractor.DependencyCondenser");
        model.components.addAll(List.of(extractor, condenser));
        model.dependencies.add(dependency(extractor.id, condenser.id, "field-reference"));

        String out = renderer.render(model);

        assertThat(out).contains("extractor\\n2 components\\n1 internal deps");
        assertThat(out).doesNotContain("dep_extractor -->");
    }

    private Component component(String id, String name, String qualifiedName) {
        Component component = new Component();
        component.id = ComponentId.of(id);
        component.name = name;
        component.qualifiedName = qualifiedName;
        component.type = ComponentType.SERVICE;
        component.technology = "java";
        return component;
    }

    private Dependency dependency(ComponentId from, ComponentId to, String kind) {
        Dependency dependency = new Dependency();
        dependency.id = "dep:" + from.serialize() + "->" + to.serialize();
        dependency.fromId = from;
        dependency.toId = to;
        dependency.kind = kind;
        dependency.derivedFrom = "type-relation";
        dependency.confidence = 0.65;
        return dependency;
    }
}
