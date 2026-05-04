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
    void recordsSynthesisedParamMappingForTernaryArg() {
        // findTernary calls orderRepository.findById(id != null ? id : 0L)
        // — argument is a CtConditional wrapping CtVariableRead 'id', so buildParamMapping
        // descends into it and flags the callee param as synthesised.
        assertThat(model.callEdges)
            .as("OrderService.findTernary -> OrderRepository.findById has synthesised mapping")
            .anySatisfy(e -> {
                assertThat(e.fromMethod).isEqualTo("findTernary");
                assertThat(e.toMethod).isEqualTo("findById");
                assertThat(e.paramMapping).containsEntry("id", "id");
                assertThat(e.syntheticParamMappings).contains("id");
            });
    }

    @Test
    void recordsSynthesisedParamMappingForNestedInvocationArg() {
        // findWrapped calls orderRepository.findById(Long.valueOf(String.valueOf(raw)))
        // — argument is a nested CtInvocation, descend should reach 'raw'.
        assertThat(model.callEdges)
            .as("OrderService.findWrapped -> OrderRepository.findById has synthesised mapping")
            .anySatisfy(e -> {
                assertThat(e.fromMethod).isEqualTo("findWrapped");
                assertThat(e.toMethod).isEqualTo("findById");
                assertThat(e.paramMapping).containsEntry("raw", "id");
                assertThat(e.syntheticParamMappings).contains("id");
            });
    }

    @Test
    void scansOutboundFileSinkSites() {
        assertThat(model.outboundSinkSites)
            .as("OrderRepository.archive uses java.nio.file.Files → FILE_OUTBOUND site")
            .anySatisfy(s -> {
                assertThat(s.componentId).contains("OrderRepository");
                assertThat(s.method).isEqualTo("archive");
                assertThat(s.calleeQualifiedName).startsWith("java.nio.file.Files");
                assertThat(s.calleeMethod).isEqualTo("writeString");
                assertThat(s.kind.name()).isEqualTo("FILE_OUTBOUND");
            });
    }

    @Test
    void rerunDoesNotDuplicateEdges() {
        int beforeCount = model.callEdges.size();
        CtModel ctModel = scan("quarkus-sample");
        new CallGraphExtractor().extract(ctModel, model);
        assertThat(model.callEdges).hasSize(beforeCount);
    }
}
