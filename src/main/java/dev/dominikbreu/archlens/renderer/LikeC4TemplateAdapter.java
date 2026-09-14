package dev.dominikbreu.archlens.renderer;

import dev.dominikbreu.archlens.likec4.LikeC4Document;
import dev.dominikbreu.archlens.likec4.LikeC4Element;
import dev.dominikbreu.archlens.likec4.LikeC4Relationship;
import dev.dominikbreu.archlens.renderer.template.LikeC4Template;
import dev.dominikbreu.archlens.renderer.template.LikeC4TemplateRenderer;
import dev.dominikbreu.archlens.view.ArchitectureViewProjection;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;

/** Renders a {@link LikeC4Document} or {@link ArchitectureViewProjection} as LikeC4 DSL text. */
final class LikeC4TemplateAdapter {

    /** Creates a renderer with default settings. */
    LikeC4TemplateAdapter() {}

    /**
     * Renders the given LikeC4 document as a DSL string.
     *
     * @param document the document to render
     * @return the LikeC4 DSL text
     */
    public String render(LikeC4Document document) {
        Map<String, String> aliases = elementAliases(document);
        return renderTemplate(new LikeC4Template(
                commentLines(document.warnings()),
                document.elementKinds().stream()
                        .map(LikeC4TemplateAdapter::identifier)
                        .toList(),
                document.elements().stream()
                        .map(element -> new LikeC4Template.Element(
                                aliases.get(element.id()),
                                identifier(element.kind()),
                                escape(element.title()),
                                metadata(elementMetadata(element))))
                        .toList(),
                document.relationships().stream()
                        .map(relationship -> new LikeC4Template.Relationship(
                                aliasFor(relationship.sourceId(), aliases), aliasFor(relationship.targetId(), aliases),
                                escape(relationship.title()), metadata(relationshipMetadata(relationship))))
                        .toList(),
                document.views().stream()
                        .map(view -> new LikeC4Template.View(
                                identifier(view.id()),
                                escape(view.title()),
                                commentLines(view.notes()),
                                view.includes().isEmpty()
                                        ? List.of("*")
                                        : view.includes().stream()
                                                .map(include -> aliasFor(include, aliases))
                                                .toList()))
                        .toList(),
                document.dynamicViews().stream()
                        .map(view -> new LikeC4Template.DynamicView(
                                identifier(view.id()),
                                escape(view.title()),
                                view.steps().stream()
                                        .map(step -> new LikeC4Template.Relationship(
                                                aliasFor(step.sourceId(), aliases), aliasFor(step.targetId(), aliases),
                                                escape(step.title()), List.of()))
                                        .toList()))
                        .toList()));
    }

    /**
     * Renders the given architecture view projection as a LikeC4 view block.
     *
     * @param projection the view projection to render
     * @return the LikeC4 DSL text
     */
    public String render(ArchitectureViewProjection projection) {
        Map<String, String> aliases = projectionAliases(projection);
        return renderTemplate(new LikeC4Template(
                commentLines(projection.warnings()),
                List.of("component"),
                projection.nodes().stream()
                        .map(node -> new LikeC4Template.Element(
                                aliases.get(node.id()), "component", escape(node.title()), metadata(node.properties())))
                        .toList(),
                projection.edges().stream()
                        .map(edge -> new LikeC4Template.Relationship(
                                aliasFor(edge.sourceId(), aliases), aliasFor(edge.targetId(), aliases),
                                escape(edge.title()), List.of()))
                        .toList(),
                List.of(new LikeC4Template.View("index", escape(projection.title()), List.of(), List.of("*"))),
                List.of()));
    }

    private static String renderTemplate(LikeC4Template template) {
        return LikeC4TemplateRenderer.of().execute(template);
    }

    private static Map<String, String> elementAliases(LikeC4Document document) {
        return aliasesFor(document.elements().stream().map(LikeC4Element::id).toList());
    }

    private static Map<String, String> projectionAliases(ArchitectureViewProjection projection) {
        return aliasesFor(projection.nodes().stream()
                .map(ArchitectureViewProjection.Node::id)
                .toList());
    }

    private static Map<String, String> aliasesFor(Iterable<String> ids) {
        Map<String, String> aliases = new LinkedHashMap<>();
        Map<String, Integer> nextSuffixByBase = new LinkedHashMap<>();
        Set<String> used = new HashSet<>();
        for (String id : ids) {
            String base = identifier(id);
            int suffix = nextSuffixByBase.getOrDefault(base, 1);
            String alias;
            if (suffix == 1) {
                alias = base;
            } else {
                alias = base + "_" + suffix;
            }
            while (used.contains(alias)) {
                suffix++;
                alias = base + "_" + suffix;
            }
            aliases.put(id, alias);
            used.add(alias);
            nextSuffixByBase.put(base, suffix + 1);
        }
        return aliases;
    }

    private static String aliasFor(String rawId, Map<String, String> aliases) {
        return aliases.getOrDefault(rawId, identifier(rawId));
    }

    private static Map<String, Object> elementMetadata(LikeC4Element element) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("sourceId", element.sourceId());
        metadata.putAll(element.metadata());
        return metadata;
    }

    private static Map<String, Object> relationshipMetadata(LikeC4Relationship relationship) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (!relationship.sourceLabel().isBlank()) {
            metadata.put("sourceLabel", relationship.sourceLabel());
        }
        metadata.putAll(relationship.metadata());
        return metadata;
    }

    private static List<LikeC4Template.Metadata> metadata(Map<String, Object> metadata) {
        return metadata.entrySet().stream()
                .sorted(Comparator.comparing((Map.Entry<String, Object> entry) -> metadataKey(entry.getKey()))
                        .thenComparing(Map.Entry::getKey))
                .map(entry -> new LikeC4Template.Metadata(
                        metadataKey(entry.getKey()), escape(String.valueOf(entry.getValue()))))
                .toList();
    }

    private static String metadataKey(String raw) {
        String key = identifier(raw);
        if (LIKEC4_KEYWORDS.contains(key)) {
            return "meta_" + key;
        }
        return key;
    }

    private static List<String> commentLines(List<String> values) {
        return values.stream()
                .flatMap(value -> Arrays.stream(value.split("\\R", -1)))
                .toList();
    }

    private static String identifier(String raw) {
        // StringUtils.strip(…, "_") avoids the S5852 backtracking hotspot of an anchored ^_+/_+$ regex.
        String clean = StringUtils.strip(raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]+", "_"), "_");
        if (clean.isBlank() || Character.isDigit(clean.charAt(0))) {
            return "n_" + clean;
        }
        return clean;
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("'", "\\'");
    }

    private static final Set<String> LIKEC4_KEYWORDS = Set.of(
            "and",
            "as",
            "auto_layout",
            "color",
            "deployment",
            "description",
            "element",
            "exclude",
            "extends",
            "global",
            "group",
            "icon",
            "include",
            "kind",
            "link",
            "metadata",
            "model",
            "or",
            "relationship",
            "shape",
            "specification",
            "style",
            "summary",
            "technology",
            "title",
            "view",
            "views");
}
