package dev.dominikbreu.spoonmcp.renderer;

import dev.dominikbreu.spoonmcp.likec4.LikeC4Document;
import dev.dominikbreu.spoonmcp.likec4.LikeC4DynamicStep;
import dev.dominikbreu.spoonmcp.likec4.LikeC4DynamicView;
import dev.dominikbreu.spoonmcp.likec4.LikeC4Element;
import dev.dominikbreu.spoonmcp.likec4.LikeC4Relationship;
import dev.dominikbreu.spoonmcp.likec4.LikeC4View;
import dev.dominikbreu.spoonmcp.view.ArchitectureViewProjection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;

public final class LikeC4ModelRenderer {

    private static final String BLOCK_END = "}\n\n";
    private static final String INDENT_BLOCK_END = "  }\n";

    public String render(LikeC4Document document) {
        StringBuilder sb = new StringBuilder();
        Map<String, String> aliases = elementAliases(document);
        appendWarnings(sb, document.warnings());

        sb.append("specification {\n");
        for (String elementKind : document.elementKinds()) {
            sb.append("  element ").append(identifier(elementKind)).append("\n");
        }
        sb.append(BLOCK_END);

        appendDocumentModel(sb, document, aliases);
        appendDocumentViews(sb, document, aliases);
        return sb.toString();
    }

    private void appendWarnings(StringBuilder sb, List<String> warnings) {
        for (String warning : warnings) {
            appendCommentLines(sb, "", "Warning: ", warning);
        }
        if (!warnings.isEmpty()) {
            sb.append("\n");
        }
    }

    private void appendDocumentModel(StringBuilder sb, LikeC4Document document, Map<String, String> aliases) {
        sb.append("model {\n");
        for (LikeC4Element element : document.elements()) {
            sb.append("  ")
                    .append(aliases.get(element.id()))
                    .append(" = ")
                    .append(identifier(element.kind()))
                    .append(" '")
                    .append(escape(element.title()))
                    .append("' {\n");
            renderMetadata(sb, elementMetadata(element), "    ");
            sb.append(INDENT_BLOCK_END);
        }
        for (LikeC4Relationship relationship : document.relationships()) {
            appendRelationship(sb, relationship, aliases);
        }
        sb.append(BLOCK_END);
    }

    private void appendRelationship(StringBuilder sb, LikeC4Relationship relationship, Map<String, String> aliases) {
        sb.append("  ")
                .append(aliasFor(relationship.sourceId(), aliases))
                .append(" -> ")
                .append(aliasFor(relationship.targetId(), aliases))
                .append(" '")
                .append(escape(relationship.title()))
                .append("'");
        Map<String, Object> metadata = relationshipMetadata(relationship);
        if (metadata.isEmpty()) {
            sb.append("\n");
        } else {
            sb.append(" {\n");
            renderMetadata(sb, metadata, "    ");
            sb.append(INDENT_BLOCK_END);
        }
    }

    private void appendDocumentViews(StringBuilder sb, LikeC4Document document, Map<String, String> aliases) {
        sb.append("views {\n");
        for (LikeC4View view : document.views()) {
            appendView(sb, view, aliases);
        }
        for (LikeC4DynamicView dynamicView : document.dynamicViews()) {
            appendDynamicView(sb, dynamicView, aliases);
        }
        sb.append("}\n");
    }

    private void appendDynamicView(StringBuilder sb, LikeC4DynamicView view, Map<String, String> aliases) {
        sb.append("  dynamic view ").append(identifier(view.id())).append(" {\n");
        sb.append("    title '").append(escape(view.title())).append("'\n");
        for (LikeC4DynamicStep step : view.steps()) {
            sb.append("    ")
                    .append(aliasFor(step.sourceId(), aliases))
                    .append(" -> ")
                    .append(aliasFor(step.targetId(), aliases))
                    .append(" '")
                    .append(escape(step.title()))
                    .append("'\n");
        }
        sb.append(INDENT_BLOCK_END);
    }

    private void appendView(StringBuilder sb, LikeC4View view, Map<String, String> aliases) {
        sb.append("  view ").append(identifier(view.id())).append(" {\n");
        sb.append("    title '").append(escape(view.title())).append("'\n");
        for (String note : view.notes()) {
            appendCommentLines(sb, "    ", "", note);
        }
        if (view.includes().isEmpty()) {
            sb.append("    include *\n");
        } else {
            for (String include : view.includes()) {
                sb.append("    include ").append(aliasFor(include, aliases)).append("\n");
            }
        }
        sb.append(INDENT_BLOCK_END);
    }

    public String render(ArchitectureViewProjection projection) {
        StringBuilder sb = new StringBuilder();
        Map<String, String> aliases = projectionAliases(projection);
        for (String warning : projection.warnings()) {
            appendCommentLines(sb, "", "Warning: ", warning);
        }
        if (!projection.warnings().isEmpty()) {
            sb.append("\n");
        }

        sb.append("specification {\n");
        sb.append("  element component\n");
        sb.append(BLOCK_END);

        sb.append("model {\n");
        for (ArchitectureViewProjection.Node node : projection.nodes()) {
            sb.append("  ")
                    .append(aliases.get(node.id()))
                    .append(" = component '")
                    .append(escape(node.title()))
                    .append("' {\n");
            renderMetadata(sb, node.properties(), "    ");
            sb.append(INDENT_BLOCK_END);
        }
        for (ArchitectureViewProjection.Edge edge : projection.edges()) {
            sb.append("  ")
                    .append(aliasFor(edge.sourceId(), aliases))
                    .append(" -> ")
                    .append(aliasFor(edge.targetId(), aliases))
                    .append(" '")
                    .append(escape(edge.title()))
                    .append("'\n");
        }
        sb.append(BLOCK_END);

        sb.append("views {\n");
        sb.append("  view index {\n");
        sb.append("    title '").append(escape(projection.title())).append("'\n");
        sb.append("    include *\n");
        sb.append(INDENT_BLOCK_END);
        sb.append("}\n");

        return sb.toString();
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

    private static void renderMetadata(StringBuilder sb, Map<String, Object> metadata, String indent) {
        if (metadata.isEmpty()) {
            return;
        }
        sb.append(indent).append("metadata {\n");
        for (Map.Entry<String, Object> entry : sortedMetadataEntries(metadata)) {
            sb.append(indent)
                    .append("  ")
                    .append(metadataKey(entry.getKey()))
                    .append(" '")
                    .append(escape(String.valueOf(entry.getValue())))
                    .append("'\n");
        }
        sb.append(indent).append("}\n");
    }

    private static Iterable<Map.Entry<String, Object>> sortedMetadataEntries(Map<String, Object> metadata) {
        return metadata.entrySet().stream()
                .sorted(Comparator.comparing((Map.Entry<String, Object> entry) -> metadataKey(entry.getKey()))
                        .thenComparing(Map.Entry::getKey))
                .toList();
    }

    private static String metadataKey(String raw) {
        String key = identifier(raw);
        if (LIKEC4_KEYWORDS.contains(key)) {
            return "meta_" + key;
        }
        return key;
    }

    private static void appendCommentLines(StringBuilder sb, String indent, String prefix, String value) {
        for (String line : value.split("\\R", -1)) {
            sb.append(indent).append("// ").append(prefix).append(line).append("\n");
        }
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
