package dev.dominikbreu.archlens.cache;

import static org.assertj.core.api.Assertions.assertThat;

import dev.dominikbreu.archlens.model.SourceInfo;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EvidenceNormalizerTest {

    @Test
    void normalizesKnownSourceEvidence() {
        Map<String, Object> evidence =
                EvidenceNormalizer.fromSource(new SourceInfo("OrderService.java", 42, "annotation", 0.95));

        assertThat(evidence)
                .containsEntry("sourceFile", "OrderService.java")
                .containsEntry("sourceLine", 42)
                .containsEntry("derivedFrom", "annotation")
                .containsEntry("confidence", 0.95)
                .containsEntry("confidenceBand", "known")
                .containsEntry("ambiguous", false)
                .containsEntry("evidence", "annotation");
    }

    @Test
    void explicitAmbiguityOverridesHighConfidence() {
        Map<String, Object> evidence = EvidenceNormalizer.normalize(
                Map.of("derivedFrom", "accessor-name-fallback", "confidence", 0.95, "ambiguous", true));

        assertThat(evidence).containsEntry("confidenceBand", "ambiguous").containsEntry("ambiguous", true);
    }

    @Test
    void separatesInferredWeakAndUnknownEvidence() {
        assertThat(EvidenceNormalizer.confidenceBand(0.8, false)).isEqualTo("inferred");
        assertThat(EvidenceNormalizer.confidenceBand(0.4, false)).isEqualTo("ambiguous");
        assertThat(EvidenceNormalizer.confidenceBand(0.0, false)).isEqualTo("unknown");
    }
}
