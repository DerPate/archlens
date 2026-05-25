package dev.dominikbreu.spoonmcp.mcp.tools;

import static org.assertj.core.api.Assertions.assertThat;

import dev.dominikbreu.spoonmcp.cache.ModelCache;
import dev.dominikbreu.spoonmcp.model.ArchitectureModel;
import dev.dominikbreu.spoonmcp.model.Component;
import dev.dominikbreu.spoonmcp.model.ComponentType;
import dev.dominikbreu.spoonmcp.model.DataFlowPath;
import dev.dominikbreu.spoonmcp.model.DataFlowSink;
import dev.dominikbreu.spoonmcp.model.MessagingBroker;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class QueryArchitectureGraphToolTest {

    @Test
    void returnsGraphSummary(@TempDir Path tempDir) throws Exception {
        ModelCache cache = new ModelCache(tempDir.toString(), ModelCache.CacheBackend.GRAPH);
        cache.store(model());
        QueryArchitectureGraphTool tool = new QueryArchitectureGraphTool(cache);

        String result = tool.execute(Map.of("action", "summary"));

        assertThat(result).contains("Architecture graph");
        assertThat(result).contains("Component: 1");
    }

    @Test
    void findsGraphNodes(@TempDir Path tempDir) throws Exception {
        ModelCache cache = new ModelCache(tempDir.toString(), ModelCache.CacheBackend.JSON);
        cache.store(model());
        QueryArchitectureGraphTool tool = new QueryArchitectureGraphTool(cache);

        String result = tool.execute(Map.of("action", "find_nodes", "label", "Component", "query", "Payment"));

        assertThat(result).contains("comp:PaymentService");
        assertThat(result).contains("SERVICE");
    }

    @Test
    void findsGraphEdgesWithPropertyFilters(@TempDir Path tempDir) throws Exception {
        ModelCache cache = new ModelCache(tempDir.toString(), ModelCache.CacheBackend.GRAPH);
        ArchitectureModel model = model();
        Component repository = new Component();
        repository.id = "comp:PaymentRepository";
        repository.name = "PaymentRepository";
        repository.type = ComponentType.REPOSITORY;
        model.components.add(repository);
        dev.dominikbreu.spoonmcp.model.Dependency dependency = new dev.dominikbreu.spoonmcp.model.Dependency();
        dependency.fromId = "comp:PaymentService";
        dependency.toId = "comp:PaymentRepository";
        dependency.kind = "injection";
        dependency.confidence = 0.85;
        model.dependencies.add(dependency);
        cache.store(model);
        QueryArchitectureGraphTool tool = new QueryArchitectureGraphTool(cache);

        String result = tool.execute(Map.of(
                "action", "find_edges",
                "label", "DEPENDS_ON",
                "filters", Map.of("confidence", ">=0.8", "kind", "injection")));

        assertThat(result).contains("comp:PaymentService -[DEPENDS_ON]-> comp:PaymentRepository");
        assertThat(result).contains("isRuntimeRelevant=true");
    }

    @Test
    void rendersMessagingSinkChannelMetadata(@TempDir Path tempDir) throws Exception {
        ModelCache cache = new ModelCache(tempDir.toString(), ModelCache.CacheBackend.JSON);
        ArchitectureModel model = model();
        DataFlowPath path = new DataFlowPath();
        path.id = "df:payment#payload";
        path.entrypointId = "entry:payment";
        path.trackedParam = "payload";
        DataFlowSink sink =
                new DataFlowSink(DataFlowSink.Kind.MESSAGING, "comp:PaymentService", "PaymentService", "send", null);
        sink.broker = MessagingBroker.KAFKA;
        sink.channel = "payments.created";
        sink.topic = "payments.created";
        sink.payloadType = "com.example.Payment";
        sink.linkEvidence = "spring-kafka-template-send";
        path.sinks.add(sink);
        model.dataFlowPaths.add(path);
        cache.store(model);
        QueryArchitectureGraphTool tool = new QueryArchitectureGraphTool(cache);

        String result = tool.execute(Map.of(
                "action", "find_nodes",
                "label", "DataFlowSink",
                "filters", Map.of("sinkKind", "messaging")));

        assertThat(result)
                .contains("broker=KAFKA")
                .contains("channel=payments.created")
                .contains("topic=payments.created")
                .contains("payloadType=com.example.Payment")
                .contains("linkEvidence=spring-kafka-template-send");
    }

    private ArchitectureModel model() {
        ArchitectureModel model = new ArchitectureModel("test");
        Component component = new Component();
        component.id = "comp:PaymentService";
        component.name = "PaymentService";
        component.qualifiedName = "com.example.PaymentService";
        component.type = ComponentType.SERVICE;
        model.components.add(component);
        return model;
    }
}
