package dev.dominikbreu.spoonmcp.extractor;

import dev.dominikbreu.spoonmcp.model.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import spoon.reflect.CtModel;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CallGraphExtractorTest extends ExtractorTestBase {

    private static ArchitectureModel model;

    @BeforeAll
    static void buildModel() {
        CtModel ctModel = scan("quarkus-sample");
        model = emptyModel(QUARKUS_APP_ID);
        new QuarkusExtractor().extract(ctModel.getAllTypes(), model, QUARKUS_APP_ID);
        new DependencyExtractor().extract(ctModel, model);
        new CallGraphExtractor().extract(ctModel, model);
    }

    @Test
    void extractsEdgeFromResourceToService() {
        assertThat(model.callEdges)
            .as("OrderResource.get -> OrderService.find edge")
            .anySatisfy(e -> {
                assertThat(e.fromComponentId).isEqualTo("comp:com.example.api.OrderResource");
                assertThat(e.fromMethod).isEqualTo("get");
                assertThat(e.toComponentId).isEqualTo("comp:com.example.service.OrderService");
                assertThat(e.toMethod).isEqualTo("find");
                assertThat(e.callKind).isEqualTo("direct");
            });
    }

    @Test
    void extractsEdgeFromServiceToRepository() {
        assertThat(model.callEdges)
            .as("OrderService.find -> OrderRepository.findById edge")
            .anySatisfy(e -> {
                assertThat(e.fromComponentId).isEqualTo("comp:com.example.service.OrderService");
                assertThat(e.fromMethod).isEqualTo("find");
                assertThat(e.toComponentId).isEqualTo("comp:com.example.repository.OrderRepository");
                assertThat(e.toMethod).isEqualTo("findById");
                assertThat(e.callKind).isEqualTo("direct");
            });
    }

    @Test
    void edgeIdsAreUnique() {
        List<String> ids = model.callEdges.stream().map(e -> e.id).toList();
        assertThat(ids).doesNotHaveDuplicates();
    }

    @Test
    void doesNotRecordIntraComponentCalls() {
        assertThat(model.callEdges)
            .as("no self-referential edges")
            .noneMatch(e -> e.fromComponentId.equals(e.toComponentId));
    }

    @Test
    void noEdgesFromUnknownComponents() {
        var compIds = model.components.stream().map(c -> c.id).toList();
        assertThat(model.callEdges)
            .allSatisfy(e -> {
                assertThat(compIds).contains(e.fromComponentId);
                assertThat(compIds).contains(e.toComponentId);
            });
    }

    @Test
    void edgeSourceIsPopulated() {
        assertThat(model.callEdges)
            .as("all edges have source info")
            .allSatisfy(e -> assertThat(e.source).isNotNull());
    }

    @Test
    void rerunDoesNotDuplicateEdges() {
        int beforeCount = model.callEdges.size();
        CtModel ctModel = scan("quarkus-sample");
        new CallGraphExtractor().extract(ctModel, model);
        assertThat(model.callEdges).hasSize(beforeCount);
    }
}
