package dev.dominikbreu.archlens.okf;

import io.modelcontextprotocol.json.McpJsonDefaults;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Derives stable, human-readable identities for compiled architecture investigations. */
public final class QuestionConceptIdentity {
    /** Creates an identity derivation service. */
    public QuestionConceptIdentity() {}

    /**
     * Derives an identity from a result's normalized family and request, excluding presentation wording.
     *
     * @param result validated architecture-question result
     * @return stable semantic identity and relative investigation document path
     */
    public ConceptIdentity derive(ArchitectureQuestionResult result) {
        String canonical = canonicalValue(Map.of("family", result.family(), "request", result.request()));
        String key = sha256(canonical);
        String familySlug = slug(result.family());
        String subjectSlug = subjectSlug(result.request());
        Path relative = Path.of("investigations", familySlug, subjectSlug + "-" + key.substring(0, 12) + ".md");
        return new ConceptIdentity(key, familySlug, subjectSlug, relative);
    }

    /**
     * Semantic identity and deterministic storage location for one investigation.
     *
     * @param semanticKey SHA-256 key of the canonical semantic scope
     * @param familySlug filesystem-safe question-family name
     * @param subjectSlug filesystem-safe primary-subject name
     * @param relativePath path relative to an OKF workspace root
     */
    public record ConceptIdentity(String semanticKey, String familySlug, String subjectSlug, Path relativePath) {}

    private static String canonicalValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            TreeMap<String, Object> sorted = new TreeMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!(entry.getKey() instanceof String key)) {
                    throw new IllegalArgumentException("Question result maps must have string keys");
                }
                sorted.put(key, entry.getValue());
            }
            return sorted.entrySet().stream()
                    .map(entry -> quote(entry.getKey()) + ":" + canonicalValue(entry.getValue()))
                    .collect(java.util.stream.Collectors.joining(",", "{", "}"));
        }
        if (value instanceof List<?> list) {
            return list.stream()
                    .map(QuestionConceptIdentity::canonicalValue)
                    .collect(java.util.stream.Collectors.joining(",", "[", "]"));
        }
        if (value instanceof String string) {
            return quote(string);
        }
        if (value instanceof Number || value instanceof Boolean) {
            return value.toString();
        }
        if (value == null) {
            return "null";
        }
        throw new IllegalArgumentException(
                "Unsupported question result value: " + value.getClass().getName());
    }

    private static String quote(String value) {
        try {
            return McpJsonDefaults.getMapper().writeValueAsString(value);
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("Unable to canonicalize question result", exception);
        }
    }

    private static String sha256(String canonical) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private static String subjectSlug(Map<String, Object> request) {
        for (String key : List.of("entrypoint", "component", "field", "query", "subject")) {
            Object value = request.get(key);
            if (value instanceof String subject && !subject.isBlank()) {
                return slug(stripPackagePrefix(subject));
            }
        }
        return "unresolved-subject";
    }

    private static String stripPackagePrefix(String subject) {
        return subject.replaceFirst("^(?:[a-z][A-Za-z0-9_]*\\.)+", "");
    }

    private static String slug(String value) {
        String slug = value.replaceAll("([a-z0-9])([A-Z])", "$1-$2")
                .replaceAll("[^A-Za-z0-9]+", "-")
                .replaceAll("^-+|-+$", "")
                .toLowerCase(java.util.Locale.ROOT);
        if (slug.isEmpty()) {
            return "unresolved-subject";
        }
        return slug.substring(0, Math.min(slug.length(), 64));
    }
}
