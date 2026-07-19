package dev.dominikbreu.archlens.cache;

import dev.dominikbreu.archlens.model.SourceInfo;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Normalizes extractor-specific evidence fields at the graph projection boundary. */
final class EvidenceNormalizer {

    static final String AMBIGUOUS = "ambiguous";
    static final String CONFIDENCE = "confidence";
    static final String CONFIDENCE_BAND = "confidenceBand";
    static final String DERIVED_FROM = "derivedFrom";
    static final String EVIDENCE = "evidence";
    static final String SOURCE_FILE = "sourceFile";
    static final String SOURCE_LINE = "sourceLine";

    private EvidenceNormalizer() {}

    static Map<String, Object> fromSource(SourceInfo source) {
        Map<String, Object> properties = new LinkedHashMap<>();
        if (source == null) return properties;
        put(properties, SOURCE_FILE, source.file);
        if (source.line > 0) properties.put(SOURCE_LINE, source.line);
        put(properties, DERIVED_FROM, source.derivedFrom);
        properties.put(CONFIDENCE, source.confidence);
        return normalize(properties);
    }

    static Map<String, Object> normalize(Map<String, ?> input) {
        Map<String, Object> result = new LinkedHashMap<>();
        input.forEach((key, value) -> {
            if (value != null && !Objects.toString(value).isBlank()) result.put(key, value);
        });

        boolean evidenceBearing = result.containsKey(CONFIDENCE)
                || result.containsKey(DERIVED_FROM)
                || result.containsKey(SOURCE_FILE)
                || result.containsKey(AMBIGUOUS);
        if (!evidenceBearing) return result;

        String derivedFrom = firstText(
                result.get(DERIVED_FROM),
                result.get("receiverEvidence"),
                result.get("linkEvidence"),
                result.get(EVIDENCE),
                result.get("source"));
        put(result, DERIVED_FROM, derivedFrom);

        double confidence = number(result.get(CONFIDENCE));
        boolean ambiguous = booleanValue(result.get(AMBIGUOUS));
        String band = confidenceBand(confidence, ambiguous);
        result.put(CONFIDENCE, confidence);
        result.put(CONFIDENCE_BAND, band);
        result.put(AMBIGUOUS, "ambiguous".equals(band));
        if (!result.containsKey(EVIDENCE)) put(result, EVIDENCE, derivedFrom);
        return result;
    }

    static String confidenceBand(double confidence, boolean ambiguous) {
        if (ambiguous) return "ambiguous";
        if (confidence >= 0.9) return "known";
        if (confidence >= 0.6) return "inferred";
        if (confidence > 0.0) return "ambiguous";
        return "unknown";
    }

    private static String firstText(Object... values) {
        for (Object value : values) {
            if (value != null && !Objects.toString(value).isBlank()) return Objects.toString(value);
        }
        return null;
    }

    private static double number(Object value) {
        if (value instanceof Number number) return number.doubleValue();
        if (value == null) return 0.0;
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException _) {
            return 0.0;
        }
    }

    private static boolean booleanValue(Object value) {
        if (value instanceof Boolean bool) return bool;
        return value != null && Boolean.parseBoolean(value.toString());
    }

    private static void put(Map<String, Object> target, String key, Object value) {
        if (value != null && !Objects.toString(value).isBlank()) target.put(key, value);
    }
}
