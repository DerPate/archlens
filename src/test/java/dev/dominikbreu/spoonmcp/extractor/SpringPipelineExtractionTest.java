package dev.dominikbreu.spoonmcp.extractor;

import static org.assertj.core.api.Assertions.assertThat;

import dev.dominikbreu.spoonmcp.cache.GraphQuery;
import dev.dominikbreu.spoonmcp.model.ArchitectureModel;
import dev.dominikbreu.spoonmcp.model.DataFlowSink;
import dev.dominikbreu.spoonmcp.model.MessagingBroker;
import dev.dominikbreu.spoonmcp.renderer.MermaidPipelineRenderer;
import java.util.List;
import org.junit.jupiter.api.Test;

class SpringPipelineExtractionTest extends ExtractorTestBase {

    @Test
    void extractsKafkaTemplateOutboundSitesWithResolvedTopicsAndPayloadTypes() {
        ArchitectureModel model = new ArchitectureExtractor().extract(List.of(projectPath("spring-pipeline-sample")));

        assertThat(model.outboundSinkSites)
                .anySatisfy(site -> {
                    assertThat(site.kind).isEqualTo(DataFlowSink.Kind.MESSAGING);
                    assertThat(site.broker).isEqualTo(MessagingBroker.KAFKA);
                    assertThat(site.topic).isEqualTo("orders.created");
                    assertThat(site.topicPropertyKey).isEqualTo("topics.orders.created");
                    assertThat(site.payloadVarName).isEqualTo("order");
                    assertThat(site.payloadType).isEqualTo("com.example.pipeline.model.OrderEntity");
                    assertThat(site.linkEvidence).isEqualTo("spring-kafka-template-send");
                })
                .anySatisfy(site -> {
                    assertThat(site.kind).isEqualTo(DataFlowSink.Kind.MESSAGING);
                    assertThat(site.topic).isEqualTo("orders.ready");
                    assertThat(site.topicPropertyKey).isEqualTo("topics.orders.ready");
                });
    }

    @Test
    void springPipelineSampleProducesWorkflowLinkAcrossKafkaTopic() {
        ArchitectureModel model = new ArchitectureExtractor().extract(List.of(projectPath("spring-pipeline-sample")));

        assertThat(model.dataFlowPaths)
                .anySatisfy(path -> assertThat(path.sinks).anySatisfy(sink -> {
                    assertThat(sink.kind).isEqualTo(DataFlowSink.Kind.MESSAGING);
                    assertThat(sink.topic).isEqualTo("orders.created");
                    assertThat(sink.linkedPathIds).isNotEmpty();
                }));
    }

    @Test
    void springPipelineSampleRendersEndToEndPipeline() {
        ArchitectureModel model = new ArchitectureExtractor().extract(List.of(projectPath("spring-pipeline-sample")));

        List<PipelineGraphBuilder.Chain> chains = new PipelineGraphBuilder().build(model, 8);

        assertThat(chains).isNotEmpty();
        String mermaid = new MermaidPipelineRenderer().render(chains.getFirst(), GraphQuery.from(model));
        assertThat(mermaid).contains("orders.created");
        assertThat(mermaid).contains("OrderController.create");
        assertThat(mermaid).contains("OrderCreatedListener.onCreated");
    }

    @Test
    void listenerEntrypointsUseResolvedTopicNames() {
        ArchitectureModel model = new ArchitectureExtractor().extract(List.of(projectPath("spring-pipeline-sample")));

        assertThat(model.entrypoints).anySatisfy(entrypoint -> {
            assertThat(entrypoint.name).isEqualTo("onCreated");
            assertThat(entrypoint.channelName).isEqualTo("orders.created");
            assertThat(entrypoint.broker).isEqualTo(MessagingBroker.KAFKA);
        });
    }
}
