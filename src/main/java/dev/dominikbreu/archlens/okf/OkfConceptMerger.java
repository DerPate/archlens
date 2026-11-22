package dev.dominikbreu.archlens.okf;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

/**
 * Merges a regenerated concept into the copy on disk, so a refresh replaces what ArchLens owns and
 * nothing else.
 *
 * <p>Frontmatter is merged as text, one top-level key at a time. Loading and dumping it would lose
 * comments and change values ArchLens does not own ({@code NO} would become {@code false}).
 */
final class OkfConceptMerger {
    /** A line that starts a top-level mapping key. */
    private static final Pattern TOP_LEVEL_KEY = Pattern.compile("^(?![\\s#]|-\\s)([^:]+?)\\s*:(?:\\s.*)?$");

    private static final String STATUS = "status";
    private static final String STALE_AFTER = "stale_after";
    private static final String TAGS = "tags";

    private OkfConceptMerger() {}

    /**
     * Merged concept text.
     *
     * @param markdown concept to write
     * @param verificationOutdated whether changed content left an existing verification behind
     */
    record Merged(String markdown, boolean verificationOutdated) {}

    /**
     * Merges a regenerated concept into an existing one. Content counts as changed when the body or
     * the answer status differs; only then do {@code generated} and {@code status} move.
     *
     * @param existing concept currently on disk
     * @param regenerated freshly rendered concept
     * @return merged concept
     */
    static Merged merge(String existing, String regenerated) {
        Document before = Document.parse(existing);
        Document after = Document.parse(regenerated);
        boolean changed = !before.body().stripTrailing().equals(after.body().stripTrailing())
                || !Objects.equals(
                        OkfKeys.read(before.values(), OkfKeys.STATUS), OkfKeys.read(after.values(), OkfKeys.STATUS));

        Map<String, String> merged = new LinkedHashMap<>(before.blocks());
        after.blocks().forEach((key, block) -> {
            boolean replace = !merged.containsKey(key)
                    || STALE_AFTER.equals(key)
                    || key.startsWith(OkfKeys.PREFIX)
                    || (changed && "generated".equals(key))
                    || (changed && STATUS.equals(key) && !"deprecated".equals(before.value(STATUS)));
            if (key.startsWith(OkfKeys.PREFIX)) merged.remove(OkfKeys.legacyName(key));
            if (replace) merged.put(key, block);
        });
        replaceAnswerStatusTag(before, after, merged);

        String body = changed ? after.body() : before.body();
        String markdown = "---\n" + String.join("\n", merged.values()) + "\n---\n" + body;
        return new Merged(markdown, changed && before.blocks().containsKey("verified"));
    }

    /** Swaps the answer-status tag while keeping any tags added by hand. */
    private static void replaceAnswerStatusTag(Document before, Document after, Map<String, String> merged) {
        Object oldStatus = OkfKeys.read(before.values(), OkfKeys.STATUS);
        Object newStatus = OkfKeys.read(after.values(), OkfKeys.STATUS);
        if (Objects.equals(oldStatus, newStatus) || !(before.value(TAGS) instanceof List<?> tags)) return;

        List<Object> updated = new ArrayList<>(tags);
        updated.remove(oldStatus);
        if (newStatus != null && !updated.contains(newStatus)) updated.add(newStatus);
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        merged.put(TAGS, new Yaml(options).dump(Map.of(TAGS, updated)).stripTrailing());
    }

    /**
     * A concept split into raw frontmatter blocks and a body.
     *
     * @param blocks raw text per top-level key; comment lines get synthetic keys
     * @param values parsed frontmatter, used only for comparisons
     * @param body text after the closing delimiter
     */
    private record Document(Map<String, String> blocks, Map<?, ?> values, String body) {
        static Document parse(String markdown) {
            int end = markdown.indexOf("\n---\n", 4);
            if (!markdown.startsWith("---\n") || end < 0) {
                throw new IllegalArgumentException("Concept must have YAML frontmatter");
            }
            String header = markdown.substring(4, end);
            Map<String, String> blocks = new LinkedHashMap<>();
            String key = null;
            int lineNumber = 0;
            for (String line : header.lines().toList()) {
                lineNumber++;
                var matcher = TOP_LEVEL_KEY.matcher(line);
                if (matcher.matches()) {
                    key = matcher.group(1);
                    blocks.put(key, line);
                } else if (key == null || line.startsWith("#")) {
                    key = "#" + lineNumber;
                    blocks.put(key, line);
                } else {
                    blocks.merge(key, line, (block, next) -> block + "\n" + next);
                }
            }
            Object values = new Yaml().load(header);
            return new Document(blocks, values instanceof Map<?, ?> map ? map : Map.of(), markdown.substring(end + 5));
        }

        Object value(String key) {
            return values.get(key);
        }
    }
}
