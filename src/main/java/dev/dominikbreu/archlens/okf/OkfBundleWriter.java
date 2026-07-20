package dev.dominikbreu.archlens.okf;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.yaml.snakeyaml.Yaml;

/** Safely creates and refreshes OKF investigation bundle files. */
public final class OkfBundleWriter {
    private final FilePromoter promoter;
    private final OkfEntryValidator validator = new OkfEntryValidator();

    /** Creates a writer that promotes staged files with atomic moves when supported. */
    public OkfBundleWriter() {
        this(OkfBundleWriter::promote);
    }

    /**
     * Creates a writer with an injectable file promoter.
     *
     * @param promoter file promotion strategy
     */
    OkfBundleWriter(FilePromoter promoter) {
        this.promoter = promoter;
    }

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
        finalContent.put(indexPath, bytes(updateIndex(read(indexPath), request.familySlug(), indexEntry)));
        finalContent.put(logPath, bytes(updateLog(read(logPath), request.logDate(), logEntry)));

        promoteStaged(finalContent);
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

    private void promoteStaged(Map<Path, byte[]> finalContent) throws IOException {
        Map<Path, Optional<byte[]>> snapshots = new LinkedHashMap<>();
        Map<Path, Path> staged = new LinkedHashMap<>();
        List<Path> promoted = new ArrayList<>();
        List<Path> temps = new ArrayList<>();
        try {
            for (Map.Entry<Path, byte[]> entry : finalContent.entrySet()) {
                Path target = entry.getKey();
                snapshots.put(target, snapshot(target));
                Path parent = requireParent(target);
                Path fileName = requireFileName(target);
                Files.createDirectories(parent);
                Path temp = Files.createTempFile(parent, fileName.toString(), ".tmp");
                temps.add(temp);
                Files.write(temp, entry.getValue());
                staged.put(target, temp);
            }
            for (Map.Entry<Path, Path> entry : staged.entrySet()) {
                promoter.move(entry.getValue(), entry.getKey());
                promoted.add(entry.getKey());
            }
        } catch (IOException promotionFailure) {
            restore(promoted, snapshots, temps, promotionFailure);
        } finally {
            for (Path temp : temps) {
                Files.deleteIfExists(temp);
            }
        }
    }

    private static Optional<byte[]> snapshot(Path path) throws IOException {
        return Files.exists(path) ? Optional.of(Files.readAllBytes(path)) : Optional.empty();
    }

    private static void restore(
            List<Path> promoted, Map<Path, Optional<byte[]>> snapshots, List<Path> temps, IOException cause)
            throws IOException {
        List<Path> unrestored = new ArrayList<>();
        for (int index = promoted.size() - 1; index >= 0; index--) {
            Path target = promoted.get(index);
            try {
                Optional<byte[]> content = snapshots.get(target);
                if (content.isPresent()) {
                    Path fileName = requireFileName(target);
                    Path temp = Files.createTempFile(requireParent(target), fileName.toString(), ".restore");
                    temps.add(temp);
                    Files.write(temp, content.get());
                    promote(temp, target);
                } else {
                    Files.deleteIfExists(target);
                }
            } catch (IOException restoreFailure) {
                unrestored.add(target);
                cause.addSuppressed(restoreFailure);
            }
        }
        if (!unrestored.isEmpty()) {
            IOException failure = new IOException(
                    "Promotion failed and restoration failed for " + unrestored + ": " + cause.getMessage(), cause);
            throw failure;
        }
        throw cause;
    }

    private static void promote(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException unsupported) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static Path requireParent(Path path) {
        Path parent = path.getParent();
        if (parent == null) {
            throw new IllegalArgumentException("Path must have a parent: " + path);
        }
        return parent;
    }

    private static Path requireFileName(Path path) {
        Path fileName = path.getFileName();
        if (fileName == null) {
            throw new IllegalArgumentException("Path must have a file name: " + path);
        }
        return fileName;
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

@FunctionalInterface
interface FilePromoter {
    void move(Path source, Path target) throws IOException;
}
