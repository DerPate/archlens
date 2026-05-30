package dev.dominikbreu.spoonmcp.renderer;

import dev.dominikbreu.spoonmcp.likec4.LikeC4Document;
import dev.dominikbreu.spoonmcp.likec4.LikeC4Element;
import dev.dominikbreu.spoonmcp.likec4.LikeC4Relationship;
import dev.dominikbreu.spoonmcp.likec4.LikeC4View;
import dev.dominikbreu.spoonmcp.view.ArchitectureViewProjection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class LikeC4ModelRenderer {

    public String render(LikeC4Document document) {
        StringBuilder sb = new StringBuilder();
        Map<String, String> aliases = elementAliases(document);
        for (String warning : document.warnings()) {
            appendCommentLines(sb, "", "Warning: ", warning);
        }
        if (!document.warnings().isEmpty()) {
            sb.append("\n");
        }

        sb.append("specification {\n");
        for (String elementKind : document.elementKinds()) {
            sb.append("  element ").append(identifier(elementKind)).append("\n");
        }
        sb.append("}\n\n");

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
            sb.append("  }\n");
        }
        for (LikeC4Relationship relationship : document.relationships()) {
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
                sb.append("  }\n");
            }
        }
        sb.append("}\n\n");

        sb.append("views {\n");
        for (LikeC4View view : document.views()) {
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
            sb.append("  }\n");
        }
        sb.append("}\n");

        return sb.toString();
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
        sb.append("}\n\n");

        sb.append("model {\n");
        for (ArchitectureViewProjection.Node node : projection.nodes()) {
            sb.append("  ")
                    .append(aliases.get(node.id()))
                    .append(" = component '")
                    .append(escape(node.title()))
                    .append("' {\n");
            renderMetadata(sb, node.properties(), "    ");
            sb.append("  }\n");
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
        sb.append("}\n\n");

        sb.append("views {\n");
        sb.append("  view index {\n");
        sb.append("    title '").append(escape(projection.title())).append("'\n");
        sb.append("    include *\n");
        sb.append("  }\n");
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
                    .append(identifier(entry.getKey()))
                    .append(" '")
                    .append(escape(String.valueOf(entry.getValue())))
                    .append("'\n");
        }
        sb.append(indent).append("}\n");
    }

    private static Iterable<Map.Entry<String, Object>> sortedMetadataEntries(Map<String, Object> metadata) {
        return metadata.entrySet().stream()
                .sorted(Comparator.comparing((Map.Entry<String, Object> entry) -> identifier(entry.getKey()))
                        .thenComparing(Map.Entry::getKey))
                .toList();
    }

    private static void appendCommentLines(StringBuilder sb, String indent, String prefix, String value) {
        for (String line : value.split("\\R", -1)) {
            sb.append(indent).append("// ").append(prefix).append(line).append("\n");
        }
    }

    private static String identifier(String raw) {
        String clean = raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]+", "_");
        clean = clean.replaceAll("^_+", "").replaceAll("_+$", "");
        if (clean.isBlank() || Character.isDigit(clean.charAt(0))) {
            return "n_" + clean;
        }
        return clean;
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("'", "\\'");
    }
}
