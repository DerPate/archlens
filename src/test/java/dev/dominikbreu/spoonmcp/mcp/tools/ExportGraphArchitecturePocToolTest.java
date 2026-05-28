package dev.dominikbreu.spoonmcp.mcp.tools;

import static org.assertj.core.api.Assertions.assertThat;

import dev.dominikbreu.spoonmcp.cache.ModelCache;
import dev.dominikbreu.spoonmcp.model.AppEntry;
import dev.dominikbreu.spoonmcp.model.ArchitectureModel;
import dev.dominikbreu.spoonmcp.model.Component;
import dev.dominikbreu.spoonmcp.model.ComponentType;
import dev.dominikbreu.spoonmcp.model.Dependency;
import dev.dominikbreu.spoonmcp.model.FieldAccess;
import dev.dominikbreu.spoonmcp.model.SourceInfo;
import dev.dominikbreu.spoonmcp.model.ids.ComponentId;
import dev.dominikbreu.spoonmcp.model.ids.DependencyId;
import dev.dominikbreu.spoonmcp.model.ids.FieldBinding;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExportGraphArchitecturePocToolTest {

    @Test
    void exportsGraphCentricPocDocument(@TempDir Path tempDir) throws Exception {
        Path output = tempDir.resolve("SOURCE_ARCHITECTURE_POC.md");
        ModelCache cache = new ModelCache(tempDir.toString(), ModelCache.CacheBackend.GRAPH);
        cache.store(model());
        ExportGraphArchitecturePocTool tool = new ExportGraphArchitecturePocTool(cache);

        String result = tool.execute(Map.of("outputPath", output.toString(), "focusComponent", "OrderService"));

        assertThat(result).contains("Exported graph POC docs");
        assertThat(Files.readString(output)).contains("Generated Architecture Graph POC");
        assertThat(Files.readString(output)).contains("Graph Metadata POC");
        assertThat(Files.readString(output)).contains("Cross-Module Dependencies");
        assertThat(Files.readString(output)).contains("componentType");
        assertThat(Files.readString(output)).contains("query_architecture_graph");
    }

    @Test
    void highSignalComponentsPreferWorkflowRelevantNodesOverInternalStateNoise(@TempDir Path tempDir) throws Exception {
        Path output = tempDir.resolve("SOURCE_ARCHITECTURE_POC.md");
        ModelCache cache = new ModelCache(tempDir.toString(), ModelCache.CacheBackend.GRAPH);
        ArchitectureModel model = model();
        addNoisyInternalGraphComponent(model);
        cache.store(model);
        ExportGraphArchitecturePocTool tool = new ExportGraphArchitecturePocTool(cache);

        tool.execute(Map.of("outputPath", output.toString(), "focusComponent", "OrderService"));

        String markdown = Files.readString(output);
        int serviceIndex = markdown.indexOf("`comp:OrderService`");
        int graphIndex = markdown.indexOf("`comp:ArchitectureGraph`");
        assertThat(serviceIndex).isPositive();
        assertThat(graphIndex).isPositive();
        assertThat(serviceIndex).isLessThan(graphIndex);
    }

    private ArchitectureModel model() {
        ArchitectureModel model = new ArchitectureModel("test");

        AppEntry app = new AppEntry();
        app.id = "app:orders";
        app.name = "orders";
        app.componentIds.add(ComponentId.of("comp:OrderService"));
        app.componentIds.add(ComponentId.of("comp:OrderRepository"));
        model.applications.add(app);

        Component service = new Component();
        service.id = ComponentId.of("comp:OrderService");
        service.name = "OrderService";
        service.qualifiedName = "com.example.orders.OrderService";
        service.type = ComponentType.SERVICE;
        service.module = "orders-service";
        service.source = new SourceInfo("src/OrderService.java", 21, "annotation", 0.92);
        model.components.add(service);

        Component repository = new Component();
        repository.id = ComponentId.of("comp:OrderRepository");
        repository.name = "OrderRepository";
        repository.qualifiedName = "com.example.shared.OrderRepository";
        repository.type = ComponentType.REPOSITORY;
        repository.module = "shared-domain";
        model.components.add(repository);

        Dependency dependency = new Dependency();
        dependency.fromId = ComponentId.of("comp:OrderService");
        dependency.toId = ComponentId.of("comp:OrderRepository");
        dependency.id = DependencyId.of(dependency.fromId, dependency.toId);
        dependency.kind = "field-reference";
        dependency.derivedFrom = "field";
        dependency.confidence = 0.85;
        model.dependencies.add(dependency);

        return model;
    }

    private void addNoisyInternalGraphComponent(ArchitectureModel model) {
        Component graph = new Component();
        graph.id = ComponentId.of("comp:ArchitectureGraph");
        graph.name = "ArchitectureGraph";
        graph.qualifiedName = "com.example.internal.ArchitectureGraph";
        graph.type = ComponentType.UNKNOWN;
        graph.module = "orders-service";
        model.components.add(graph);

        for (int i = 0; i < 30; i++) {
            FieldAccess read = new FieldAccess();
            read.id = "field:comp:ArchitectureGraph#method" + i + "@verticesById:read";
            read.kind = FieldAccess.Kind.READ;
            read.componentId = ComponentId.of("comp:ArchitectureGraph");
            read.method = "method" + i;
            read.fieldBinding = new FieldBinding.Own("verticesById");
            model.fieldAccesses.add(read);
        }
    }
}
