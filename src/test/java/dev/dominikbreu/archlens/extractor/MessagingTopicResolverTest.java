package dev.dominikbreu.archlens.extractor;

import static org.assertj.core.api.Assertions.assertThat;

import dev.dominikbreu.archlens.model.*;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import spoon.reflect.CtModel;

class MessagingTopicResolverTest extends ExtractorTestBase {

    @Test
    void resolverExpandsAllThreePatterns() {
        // Build ArchitectureModel (after Task 3, topics are null for non-literal sites)
        // After Task 9 wiring this test is idempotent — resolver already ran inside extract()
        ArchitectureModel model =
                new ArchitectureExtractor().extract(List.of(projectPath("kafka-topic-resolver-sample")));
        CtModel spoonModel = scan("kafka-topic-resolver-sample");

        new MessagingTopicResolver(15).resolve(model, spoonModel, 0);

        List<String> topics = model.outboundSinkSites.stream()
                .filter(s -> s.broker == MessagingBroker.KAFKA && s.topic != null)
                .map(s -> s.topic)
                .toList();

        assertThat(topics).contains("budgetControl", "sisPDFCreation", "pushNotification");
        assertThat(topics).noneMatch(t -> t.contains("(") || "topic".equals(t) || "message".equals(t));
    }

    @Test
    void literalSitesAreNotTouched() {
        ArchitectureModel model = new ArchitectureExtractor().extract(List.of(projectPath("spring-pipeline-sample")));
        CtModel spoonModel = scan("spring-pipeline-sample");

        int countBefore = model.outboundSinkSites.size();
        new MessagingTopicResolver(15).resolve(model, spoonModel, 0);

        assertThat(model.outboundSinkSites).hasSize(countBefore);
        assertThat(model.outboundSinkSites)
                .anySatisfy(site -> assertThat(site.topic).isEqualTo("orders.created"));
    }

    @Test
    void endToEndExtractorResolvesBothSamples() {
        // kafka-topic-resolver-sample: all three patterns resolved automatically
        ArchitectureModel kafkaModel =
                new ArchitectureExtractor().extract(List.of(projectPath("kafka-topic-resolver-sample")));

        assertThat(kafkaModel.outboundSinkSites.stream()
                        .filter(s -> s.broker == MessagingBroker.KAFKA)
                        .map(s -> s.topic)
                        .filter(Objects::nonNull)
                        .toList())
                .contains("budgetControl", "sisPDFCreation", "pushNotification")
                .noneMatch(t -> "topic".equals(t) || "message".equals(t) || t.contains("("));

        // spring-pipeline-sample regression: literal topics unchanged
        ArchitectureModel pipelineModel =
                new ArchitectureExtractor().extract(List.of(projectPath("spring-pipeline-sample")));

        assertThat(pipelineModel.outboundSinkSites).anySatisfy(site -> {
            assertThat(site.topic).isEqualTo("orders.created");
            assertThat(site.topicArgKind).isEqualTo(TopicArgKind.LITERAL);
        });
    }
}
