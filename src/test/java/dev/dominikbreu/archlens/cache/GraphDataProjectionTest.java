package dev.dominikbreu.archlens.cache;

import static org.assertj.core.api.Assertions.assertThat;

import dev.dominikbreu.archlens.model.ids.EntrypointId;
import dev.dominikbreu.archlens.model.ids.GraphNodeId;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GraphDataProjectionTest {

    @Test
    void projectsPipelineChainsIntoViewerReadySummaries() {
        GraphQuery.GraphSnapshot snapshot = new GraphQuery.GraphSnapshot(
                new GraphQuery.GraphSnapshotMetadata(
                        5,
                        4,
                        5,
                        4,
                        false,
                        Map.of("PipelineChain", 1, "DataFlowPath", 2, "DataFlowSink", 1, "Component", 1),
                        Map.of("HAS_SEGMENT", 2, "REACHES", 1, "AT_COMPONENT", 1)),
                List.of(
                        new GraphQuery.PipelineChainNode(
                                GraphNodeId.of("chain:12"),
                                "chain:12",
                                2,
                                "de.homeinstead.phoenix.controller.ServiceRequestController#update:PUT:/serviceRequest/{id}",
                                List.of("messaging")),
                        new GraphQuery.DataFlowPathNode(
                                GraphNodeId.of("serviceRequest#serviceRequest"),
                                "serviceRequest#serviceRequest",
                                EntrypointId.deserialize(
                                        "de.homeinstead.phoenix.controller.ServiceRequestController#update:PUT:/serviceRequest/{id}"),
                                "serviceRequest",
                                1,
                                1),
                        new GraphQuery.DataFlowPathNode(
                                GraphNodeId.of("address#event"),
                                "address#event",
                                EntrypointId.deserialize(
                                        "de.homeinstead.phoenix.inbound.AddressMessageListener#listenCustomer:spring-listener:KAFKA:address"),
                                "event",
                                1,
                                1),
                        node(
                                "sink:serviceRequest:3",
                                "DataFlowSink",
                                "AddressMessage",
                                Map.of("componentId", "de.homeinstead.phoenix.inbound.AddressMessageListener")),
                        node(
                                "de.homeinstead.phoenix.inbound.AddressMessageListener",
                                "Component",
                                "AddressMessageListener",
                                Map.of())),
                List.of(
                        edge("chain:12", "serviceRequest#serviceRequest", "HAS_SEGMENT", Map.of("segmentIndex", 0)),
                        edge(
                                "chain:12",
                                "address#event",
                                "HAS_SEGMENT",
                                Map.of(
                                        "segmentIndex",
                                        1,
                                        "linkKind",
                                        "messaging",
                                        "viaChannel",
                                        "address",
                                        "incomingSinkId",
                                        "sink:serviceRequest:3")),
                        edge("serviceRequest#serviceRequest", "sink:serviceRequest:3", "REACHES", Map.of()),
                        edge(
                                "sink:serviceRequest:3",
                                "de.homeinstead.phoenix.inbound.AddressMessageListener",
                                "AT_COMPONENT",
                                Map.of())));

        GraphDataProjection.ViewerProjections projections = GraphDataProjection.from(snapshot);

        assertThat(projections.pipelines()).singleElement().satisfies(pipeline -> {
            assertThat(pipeline.id()).isEqualTo("chain:12");
            assertThat(pipeline.title()).isEqualTo("ServiceRequestController.update PUT /serviceRequest/{id}");
            assertThat(pipeline.subtitle()).isEqualTo("messaging, 2 segments");
            assertThat(pipeline.segmentIds()).containsExactly("serviceRequest#serviceRequest", "address#event");
            assertThat(pipeline.nodeIds())
                    .containsExactly(
                            "serviceRequest#serviceRequest",
                            "address#event",
                            "sink:serviceRequest:3",
                            "de.homeinstead.phoenix.inbound.AddressMessageListener");
            assertThat(pipeline.edgeKeys())
                    .containsExactly(
                            "serviceRequest#serviceRequest->sink:serviceRequest:3:REACHES:2",
                            "sink:serviceRequest:3->de.homeinstead.phoenix.inbound.AddressMessageListener:AT_COMPONENT:3");
            assertThat(pipeline.segments())
                    .extracting(GraphDataProjection.PipelineSegmentProjection::title)
                    .containsExactly(
                            "ServiceRequestController.update PUT /serviceRequest/{id} #serviceRequest",
                            "AddressMessageListener.listenCustomer KAFKA address #event");
            assertThat(pipeline.segments())
                    .extracting(GraphDataProjection.PipelineSegmentProjection::startNodeId)
                    .containsExactly("serviceRequest#serviceRequest", "address#event");
            assertThat(pipeline.segments().getFirst().endNodeIds()).containsExactly("sink:serviceRequest:3");
            assertThat(pipeline.segments().getFirst().nodeIds())
                    .containsExactly(
                            "serviceRequest#serviceRequest",
                            "sink:serviceRequest:3",
                            "de.homeinstead.phoenix.inbound.AddressMessageListener");
            assertThat(pipeline.segments().getFirst().edgeKeys())
                    .containsExactly(
                            "serviceRequest#serviceRequest->sink:serviceRequest:3:REACHES:2",
                            "sink:serviceRequest:3->de.homeinstead.phoenix.inbound.AddressMessageListener:AT_COMPONENT:3");
            assertThat(pipeline.segments().get(1).endNodeIds()).isEmpty();
            assertThat(pipeline.segments().get(1).nodeIds()).containsExactly("address#event");
            assertThat(pipeline.segments().get(1).edgeKeys()).isEmpty();
        });
    }

    @Test
    void doesNotExpandSelectedPipelineThroughSharedSinkComponents() {
        GraphQuery.GraphSnapshot snapshot = new GraphQuery.GraphSnapshot(
                new GraphQuery.GraphSnapshotMetadata(
                        7,
                        8,
                        7,
                        8,
                        false,
                        Map.of("PipelineChain", 1, "DataFlowPath", 3, "DataFlowSink", 2, "Component", 1),
                        Map.of("HAS_SEGMENT", 2, "REACHES", 2, "AT_COMPONENT", 2, "LINKS_TO", 1, "WORKFLOW_LINK", 1)),
                List.of(
                        new GraphQuery.PipelineChainNode(
                                GraphNodeId.of("chain:selected"),
                                "chain:selected",
                                2,
                                "example.CustomerController#update:PUT:/customer/{id}",
                                List.of("messaging")),
                        path("customer#customer", "example.CustomerController#update:PUT:/customer/{id}", "customer"),
                        path(
                                "address#event",
                                "example.AddressMessageListener#listenCustomer:spring-listener:KAFKA:address",
                                "event"),
                        path("other#customer", "example.OtherController#update:PUT:/other/{id}", "customer"),
                        node("sink:customer:0", "DataFlowSink", "KafkaJsonProducer", Map.of()),
                        node("sink:other:0", "DataFlowSink", "KafkaJsonProducer", Map.of()),
                        node("example.KafkaJsonProducer", "Component", "KafkaJsonProducer", Map.of())),
                List.of(
                        edge("chain:selected", "customer#customer", "HAS_SEGMENT", Map.of("segmentIndex", 0)),
                        edge(
                                "chain:selected",
                                "address#event",
                                "HAS_SEGMENT",
                                Map.of(
                                        "segmentIndex",
                                        1,
                                        "incomingSinkId",
                                        "sink:customer:0",
                                        "linkKind",
                                        "messaging",
                                        "viaChannel",
                                        "address")),
                        edge("customer#customer", "sink:customer:0", "REACHES", Map.of()),
                        edge("sink:customer:0", "example.KafkaJsonProducer", "AT_COMPONENT", Map.of()),
                        edge("sink:customer:0", "address#event", "LINKS_TO", Map.of("linkKind", "messaging")),
                        edge("other#customer", "sink:other:0", "REACHES", Map.of()),
                        edge("sink:other:0", "example.KafkaJsonProducer", "AT_COMPONENT", Map.of()),
                        edge("other#customer", "address#event", "WORKFLOW_LINK", Map.of())));

        GraphDataProjection.PipelineProjection pipeline =
                GraphDataProjection.from(snapshot).pipelines().getFirst();

        assertThat(pipeline.nodeIds())
                .containsExactly("customer#customer", "address#event", "sink:customer:0", "example.KafkaJsonProducer");
        assertThat(pipeline.edgeKeys())
                .containsExactly(
                        "customer#customer->sink:customer:0:REACHES:2",
                        "sink:customer:0->address#event:LINKS_TO:4",
                        "sink:customer:0->example.KafkaJsonProducer:AT_COMPONENT:3");
    }

    private static GraphQuery.GraphNode node(String id, String label, String name, Map<String, Object> properties) {
        return new GraphQuery.UnknownNode(GraphNodeId.of(id), label, name, properties);
    }

    private static GraphQuery.DataFlowPathNode path(String id, String entrypointId, String trackedParam) {
        return new GraphQuery.DataFlowPathNode(
                GraphNodeId.of(id), id, EntrypointId.deserialize(entrypointId), trackedParam, 1, 1);
    }

    private static GraphQuery.GraphEdge edge(String fromId, String toId, String label, Map<String, Object> properties) {
        return new GraphQuery.GraphEdge(GraphNodeId.of(fromId), GraphNodeId.of(toId), label, properties);
    }
}
