package dev.dominikbreu.archlens.okf;

import dev.dominikbreu.archlens.io.AtomicFileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.yaml.snakeyaml.Yaml;

/** Safely creates and refreshes OKF investigation bundle files. */
public final class OkfBundleWriter {
    private final AtomicFileWriter atomicFileWriter = new AtomicFileWriter();
    private final OkfEntryValidator validator = new OkfEntryValidator();

    /** Creates a writer that atomically promotes staged bundle files. */
    public OkfBundleWriter() {}

    /**
     * Writes or refreshes a generated OKF investigation bundle entry.
     *
     * @param request write request
     * @return write outcome with touched paths and warnings
     * @throws IOException when staged promotion or restoration fails
     */
    public WriteOutcome write(WriteRequest request) throws IOException {
        Path bundlePath = request.bundlePath().normalize();
        Path conceptPath = bundlePath.resolve(request.relativeConceptPath()).normalize();
        if (!conceptPath.startsWith(bundlePath)) {
            throw new IllegalArgumentException("Concept path must stay inside the OKF bundle");
        }

        Path indexPath = bundlePath.resolve("index.md");
        Path logPath = bundlePath.resolve("log.md");
        ExistingConcept existing = existingConcept(conceptPath);
        if (existing.exists()) {
            if (!existing.generated()) {
                throw new IllegalArgumentException("Existing concept is not ArchLens-generated");
            }
            if (!request.semanticKey().equals(existing.semanticKey())) {
                throw new IllegalArgumentException("Existing concept has a different semantic key");
            }
            if (!request.allowOverwrite()) {
                return new WriteOutcome(
                        "overwrite-required",
                        conceptPath,
                        indexPath,
                        logPath,
                        List.of("Existing generated concept requires allowOverwrite: true to refresh"));
            }
        }

        String status = existing.exists() ? "updated" : "created";
        String indexEntry = indexEntry(request);
        String logEntry = logEntry(request, existing.exists());
        validator.validateConcept(request.conceptMarkdown(), request.semanticKey());
        validator.validateIndexEntry(indexEntry);
        validator.validateLogEntry(logEntry);

        Map<Path, byte[]> finalContent = new LinkedHashMap<>();
        finalContent.put(conceptPath, bytes(request.conceptMarkdown()));
        finalContent.put(
                indexPath, bytes(ensureOkfVersion(updateIndex(read(indexPath), request.familySlug(), indexEntry))));
        finalContent.put(logPath, bytes(updateLog(read(logPath), request.logDate(), logEntry)));

        atomicFileWriter.writeAll(finalContent);
        return new WriteOutcome(status, conceptPath, indexPath, logPath, List.of());
    }

    /**
     * Request for one generated investigation write.
     *
     * @param bundlePath OKF bundle directory
     * @param relativeConceptPath concept path relative to the bundle
     * @param semanticKey full semantic identity key
     * @param familySlug hyphenated question family slug
     * @param title concept title
     * @param description concept description
     * @param conceptMarkdown complete rendered concept
     * @param logDate date for the log entry
     * @param allowOverwrite whether an existing generated concept may be refreshed
     */
    public record WriteRequest(
            Path bundlePath,
            Path relativeConceptPath,
            String semanticKey,
            String familySlug,
            String title,
            String description,
            String conceptMarkdown,
            LocalDate logDate,
            boolean allowOverwrite) {}

    /**
     * Result of a create, refresh, or overwrite-preview operation.
     *
     * @param status {@code created}, {@code updated}, or {@code overwrite-required}
     * @param conceptPath absolute concept path
     * @param indexPath bundle index path
     * @param logPath bundle log path
     * @param warnings non-fatal warnings
     */
    public record WriteOutcome(String status, Path conceptPath, Path indexPath, Path logPath, List<String> warnings) {
        /** Defensively copies warning entries. */
        public WriteOutcome {
            warnings = List.copyOf(warnings);
        }
    }

    private ExistingConcept existingConcept(Path conceptPath) throws IOException {
        if (!Files.exists(conceptPath)) {
            return new ExistingConcept(false, false, null);
        }
        Map<String, Object> frontmatter = frontmatter(Files.readString(conceptPath));
        boolean generated = Boolean.TRUE.equals(frontmatter.get("archlens_generated"));
        Object key = frontmatter.get("archlens_semantic_key");
        return new ExistingConcept(true, generated, key instanceof String semanticKey ? semanticKey : null);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> frontmatter(String markdown) {
        if (markdown == null || !markdown.startsWith("---\n")) {
            return Map.of();
        }
        int end = markdown.indexOf("\n---\n", 4);
        if (end < 0) {
            return Map.of();
        }
        Object loaded = new Yaml().load(markdown.substring(4, end));
        return loaded instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    private static String indexEntry(WriteRequest request) {
        return "<!-- archlens:" + request.semanticKey() + " -->\n" + "- [" + request.title() + "]("
                + slash(request.relativeConceptPath()) + ") - " + request.description();
    }

    private static String logEntry(WriteRequest request, boolean refresh) {
        String action = refresh ? "Refresh" : "Creation";
        String verb = refresh ? "Refreshed" : "Added";
        return "- **" + action + "**: " + verb + " [" + request.title() + "](" + slash(request.relativeConceptPath())
                + ").";
    }

    private static String updateIndex(String existing, String familySlug, String entry) {
        String normalized = normalize(existing);
        if (normalized.isBlank()) {
            return "# Architecture Investigations\n\n" + familyHeading(familySlug) + "\n\n" + entry + "\n";
        }
        List<String> lines = new ArrayList<>(normalized.lines().toList());
        String marker = entry.lines().findFirst().orElseThrow();
        for (int index = 0; index < lines.size(); index++) {
            if (lines.get(index).equals(marker)
                    && index + 1 < lines.size()
                    && lines.get(index + 1).startsWith("- ")) {
                lines.set(index + 1, entry.lines().skip(1).findFirst().orElseThrow());
                return String.join("\n", lines) + "\n";
            }
        }

        String heading = familyHeading(familySlug);
        int headingIndex = lines.indexOf(heading);
        if (headingIndex < 0) {
            if (!lines.getLast().isBlank()) {
                lines.add("");
            }
            lines.add(heading);
            lines.add("");
            lines.add(entry);
            return String.join("\n", lines) + "\n";
        }

        int insert = headingIndex + 1;
        while (insert < lines.size() && !lines.get(insert).startsWith("## ")) {
            insert++;
        }
        if (insert > 0 && !lines.get(insert - 1).isBlank()) {
            lines.add(insert++, "");
        }
        lines.add(insert++, entry);
        if (insert < lines.size() && !lines.get(insert).isBlank()) {
            lines.add(insert, "");
        }
        return String.join("\n", lines) + "\n";
    }

    private static String ensureOkfVersion(String content) {
        return content.startsWith("---\n") ? content : "---\nokf_version: \"0.2\"\n---\n\n" + content;
    }

    private static String updateLog(String existing, LocalDate date, String entry) {
        String normalized = normalize(existing);
        String heading = "## " + date;
        if (normalized.isBlank()) {
            return "# Architecture Investigation Log\n\n" + heading + "\n\n" + entry + "\n";
        }
        List<String> lines = new ArrayList<>(normalized.lines().toList());
        int headingIndex = lines.indexOf(heading);
        if (headingIndex >= 0) {
            int insert = headingIndex + 1;
            while (insert < lines.size() && lines.get(insert).isBlank()) {
                insert++;
            }
            lines.add(insert, entry);
            return String.join("\n", lines) + "\n";
        }
        int insert = lines.indexOf("# Architecture Investigation Log");
        insert = insert < 0 ? 0 : insert + 1;
        lines.add(insert++, "");
        lines.add(insert++, heading);
        lines.add(insert++, "");
        lines.add(insert, entry);
        return String.join("\n", lines) + "\n";
    }

    private static String read(Path path) throws IOException {
        return Files.exists(path) ? Files.readString(path) : "";
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? "" : value.stripTrailing() + "\n";
    }

    private static String familyHeading(String familySlug) {
        String words = familySlug.replace('-', ' ');
        return "## " + Character.toUpperCase(words.charAt(0)) + words.substring(1);
    }

    private static String slash(Path path) {
        return path.toString().replace('\\', '/');
    }

    private record ExistingConcept(boolean exists, boolean generated, String semanticKey) {}
}
