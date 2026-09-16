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
    /** Promotes the concept, index, and log files as one atomic, all-or-nothing filesystem write. */
    private final AtomicFileWriter atomicFileWriter = new AtomicFileWriter();

    /** Validates rendered concept, index, and log snippets before they are written. */
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

    /**
     * Inspects the concept file at the target path, if any, to decide whether it may be refreshed.
     *
     * @param conceptPath absolute concept path
     * @return existing-file state, with {@code generated} and {@code semanticKey} unset when the
     *     file does not exist
     * @throws IOException when the existing concept file cannot be read
     */
    private ExistingConcept existingConcept(Path conceptPath) throws IOException {
        if (!Files.exists(conceptPath)) {
            return new ExistingConcept(false, false, null);
        }
        Map<String, Object> frontmatter = frontmatter(Files.readString(conceptPath));
        boolean generated = Boolean.TRUE.equals(frontmatter.get("archlens_generated"));
        Object key = frontmatter.get("archlens_semantic_key");
        return new ExistingConcept(true, generated, key instanceof String semanticKey ? semanticKey : null);
    }

    /**
     * Parses the leading {@code ---}-delimited YAML frontmatter block of a concept file, if present.
     *
     * @param markdown full concept file content, or {@code null}
     * @return frontmatter keys and values, or an empty map when there is no frontmatter block or it
     *     does not parse to a map
     */
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

    /**
     * Renders the index line for a bundle entry, prefixed with a hidden semantic-key marker comment
     * used to find and replace the same entry on a later refresh.
     *
     * @param request write request
     * @return marker comment followed by the Markdown list entry
     */
    private static String indexEntry(WriteRequest request) {
        return "<!-- archlens:" + request.semanticKey() + " -->\n" + "- [" + request.title() + "]("
                + slash(request.relativeConceptPath()) + ") - " + request.description();
    }

    /**
     * Renders the creation or refresh line appended to the bundle log for this write.
     *
     * @param request write request
     * @param refresh {@code true} when a generated concept already existed and was refreshed
     * @return Markdown log list entry
     */
    private static String logEntry(WriteRequest request, boolean refresh) {
        String action = refresh ? "Refresh" : "Creation";
        String verb = refresh ? "Refreshed" : "Added";
        return "- **" + action + "**: " + verb + " [" + request.title() + "](" + slash(request.relativeConceptPath())
                + ").";
    }

    /**
     * Inserts or updates one entry in the bundle index, keeping other entries and headings stable.
     *
     * <p>If the index is blank, a fresh index is created with the family heading and the entry. If a
     * line matching the entry's marker comment is already present, only the list line right after it
     * is replaced in place. Otherwise the entry is inserted under its family heading (appending that
     * heading, blank-line separated, at the end of the index if it doesn't exist yet), immediately
     * before the next {@code ##} heading or end of file.
     *
     * @param existing current index content, or blank/empty for a new index
     * @param familySlug hyphenated question family slug
     * @param entry marker comment plus list line to insert or update, as rendered by {@link
     *     #indexEntry}
     * @return updated index content, normalized to a single trailing newline
     */
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

    /**
     * Adds a default {@code okf_version} frontmatter block to index content that doesn't already
     * start with a frontmatter delimiter, leaving content that already has one untouched.
     *
     * @param content updated index content
     * @return content with {@code okf_version} frontmatter guaranteed to be present
     */
    private static String ensureOkfVersion(String content) {
        return content.startsWith("---\n") ? content : "---\nokf_version: \"0.2\"\n---\n\n" + content;
    }

    /**
     * Appends one log entry under its date heading, keeping other entries and headings stable.
     *
     * <p>If the log is blank, a fresh log is created with the date heading and the entry. If the date
     * heading already exists, the entry is inserted as the first line after it (skipping any blank
     * lines directly under the heading). Otherwise a new date heading is inserted right after the log
     * title, with the entry beneath it.
     *
     * @param existing current log content, or blank/empty for a new log
     * @param date date for the log entry's heading
     * @param entry log list line to append, as rendered by {@link #logEntry}
     * @return updated log content, normalized to a single trailing newline
     */
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

    /**
     * Reads a bundle file's current content, treating a missing file as empty.
     *
     * @param path bundle file path
     * @return file content, or {@code ""} when the file does not exist
     * @throws IOException when an existing file cannot be read
     */
    private static String read(Path path) throws IOException {
        return Files.exists(path) ? Files.readString(path) : "";
    }

    /**
     * Encodes text for writing to a bundle file.
     *
     * @param value text to encode
     * @return UTF-8 encoded bytes
     */
    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Normalizes bundle file content to a blank string or exactly one trailing newline, so line-based
     * insertion logic can rely on a consistent shape.
     *
     * @param value raw file content, possibly {@code null} or blank
     * @return {@code ""} when {@code value} is {@code null} or blank, otherwise {@code value} with
     *     trailing whitespace stripped and a single newline appended
     */
    private static String normalize(String value) {
        return value == null || value.isBlank() ? "" : value.stripTrailing() + "\n";
    }

    /**
     * Renders the index heading for a question family slug.
     *
     * @param familySlug hyphenated question family slug
     * @return {@code ##} heading with the slug's first letter capitalized and hyphens replaced by
     *     spaces
     */
    private static String familyHeading(String familySlug) {
        String words = familySlug.replace('-', ' ');
        return "## " + Character.toUpperCase(words.charAt(0)) + words.substring(1);
    }

    /**
     * Renders a path for use in a Markdown link, using forward slashes on every platform.
     *
     * @param path path to render
     * @return path string with backslashes replaced by forward slashes
     */
    private static String slash(Path path) {
        return path.toString().replace('\\', '/');
    }

    /**
     * State of a concept file at a target path, before a write.
     *
     * @param exists whether a file already exists at the target path
     * @param generated whether the existing file carries {@code archlens_generated: true}
     *     frontmatter; meaningless when {@code exists} is {@code false}
     * @param semanticKey existing file's {@code archlens_semantic_key} frontmatter value, or {@code
     *     null} when it does not exist, isn't set, or isn't a string
     */
    private record ExistingConcept(boolean exists, boolean generated, String semanticKey) {}
}
