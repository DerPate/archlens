package dev.dominikbreu.archlens.extractor;

import dev.dominikbreu.archlens.model.*;
import org.junit.jupiter.api.Test;
import spoon.reflect.CtModel;
import java.util.List;
import java.util.Objects;
import static org.assertj.core.api.Assertions.assertThat;

class MessagingTopicResolverTest extends ExtractorTestBase {

    @Test
    void resolverExpandsAllThreePatterns() {
        // Build ArchitectureModel (after Task 3, topics are null for non-literal sites)
        // After Task 9 wiring this test is idempotent — resolver already ran inside extract()
        ArchitectureModel model = new ArchitectureExtractor()
                .extract(List.of(projectPath("kafka-topic-resolver-sample")));
        CtModel spoonModel = scan("kafka-topic-resolver-sample");

        new MessagingTopicResolver(15).resolve(model, spoonModel, 0);

        List<String> topics = model.outboundSinkSites.stream()
                .filter(s -> s.broker == MessagingBroker.KAFKA && s.topic != null)
                .map(s -> s.topic)
                .toList();

        assertThat(topics).contains("budgetControl", "sisPDFCreation", "pushNotification");
        assertThat(topics).noneMatch(t -> t.contains("(") || t.equals("topic") || t.equals("message"));
    }

    @Test
    void literalSitesAreNotTouched() {
        ArchitectureModel model = new ArchitectureExtractor()
                .extract(List.of(projectPath("spring-pipeline-sample")));
        CtModel spoonModel = scan("spring-pipeline-sample");

        int countBefore = model.outboundSinkSites.size();
        new MessagingTopicResolver(15).resolve(model, spoonModel, 0);

        assertThat(model.outboundSinkSites).hasSize(countBefore);
        assertThat(model.outboundSinkSites)
                .anySatisfy(site -> assertThat(site.topic).isEqualTo("orders.created"));
    }
}
