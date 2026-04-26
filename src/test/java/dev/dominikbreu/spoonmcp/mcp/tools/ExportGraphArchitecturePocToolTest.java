package dev.dominikbreu.spoonmcp.mcp.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dominikbreu.spoonmcp.cache.ModelCache;
import dev.dominikbreu.spoonmcp.model.AppEntry;
import dev.dominikbreu.spoonmcp.model.ArchitectureModel;
import dev.dominikbreu.spoonmcp.model.Component;
import dev.dominikbreu.spoonmcp.model.ComponentType;
import dev.dominikbreu.spoonmcp.model.Dependency;
import dev.dominikbreu.spoonmcp.model.SourceInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ExportGraphArchitecturePocToolTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void exportsGraphCentricPocDocument(@TempDir Path tempDir) throws Exception {
        Path output = tempDir.resolve("SOURCE_ARCHITECTURE_POC.md");
        ModelCache cache = new ModelCache(tempDir.toString(), ModelCache.CacheBackend.GRAPH);
        cache.store(model());
        ExportGraphArchitecturePocTool tool = new ExportGraphArchitecturePocTool(cache);

        String result = tool.execute(mapper.readTree("""
            {"outputPath":"%s","focusComponent":"OrderService"}
            """.formatted(output.toString().replace("\\", "\\\\"))));

        assertThat(result).contains("Exported graph POC docs");
        assertThat(Files.readString(output)).contains("Generated Architecture Graph POC");
        assertThat(Files.readString(output)).contains("Graph Metadata POC");
        assertThat(Files.readString(output)).contains("Cross-Module Dependencies");
        assertThat(Files.readString(output)).contains("componentType");
        assertThat(Files.readString(output)).contains("query_architecture_graph");
    }

    private ArchitectureModel model() {
        ArchitectureModel model = new ArchitectureModel("test");

        AppEntry app = new AppEntry();
        app.id = "app:orders";
        app.name = "orders";
        app.componentIds.add("comp:OrderService");
        app.componentIds.add("comp:OrderRepository");
        model.applications.add(app);

        Component service = new Component();
        service.id = "comp:OrderService";
        service.name = "OrderService";
        service.qualifiedName = "com.example.orders.OrderService";
        service.type = ComponentType.SERVICE;
        service.module = "orders-service";
        service.source = new SourceInfo("src/OrderService.java", 21, "annotation", 0.92);
        model.components.add(service);

        Component repository = new Component();
        repository.id = "comp:OrderRepository";
        repository.name = "OrderRepository";
        repository.qualifiedName = "com.example.shared.OrderRepository";
        repository.type = ComponentType.REPOSITORY;
        repository.module = "shared-domain";
        model.components.add(repository);

        Dependency dependency = new Dependency();
        dependency.id = "dep:service-repository";
        dependency.fromId = "comp:OrderService";
        dependency.toId = "comp:OrderRepository";
        dependency.kind = "field-reference";
        dependency.derivedFrom = "field";
        dependency.confidence = 0.85;
        model.dependencies.add(dependency);

        return model;
    }
}
