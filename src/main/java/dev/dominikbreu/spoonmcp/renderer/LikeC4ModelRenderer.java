package dev.dominikbreu.spoonmcp.renderer;

import dev.dominikbreu.spoonmcp.view.ArchitectureViewProjection;
import java.util.Locale;
import java.util.Map;

public final class LikeC4ModelRenderer {

    public String render(ArchitectureViewProjection projection) {
        StringBuilder sb = new StringBuilder();
        sb.append("specification {\n");
        sb.append("  element component\n");
        sb.append("}\n\n");

        sb.append("model {\n");
        for (ArchitectureViewProjection.Node node : projection.nodes()) {
            sb.append("  ")
                    .append(identifier(node.title()))
                    .append(" = component '")
                    .append(escape(node.title()))
                    .append("' {\n");
            if (!node.properties().isEmpty()) {
                sb.append("    metadata {\n");
                for (Map.Entry<String, Object> entry : node.properties().entrySet()) {
                    sb.append("      ")
                            .append(identifier(entry.getKey()))
                            .append(" '")
                            .append(escape(String.valueOf(entry.getValue())))
                            .append("'\n");
                }
                sb.append("    }\n");
            }
            sb.append("  }\n");
        }
        for (ArchitectureViewProjection.Edge edge : projection.edges()) {
            sb.append("  ")
                    .append(identifier(edge.sourceId()))
                    .append(" -> ")
                    .append(identifier(edge.targetId()))
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

    private static String identifier(String raw) {
        String clean = raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]+", "_");
        clean = clean.replaceAll("^_+|_+$", "");
        if (clean.isBlank() || Character.isDigit(clean.charAt(0))) {
            return "n_" + clean;
        }
        return clean;
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("'", "\\'");
    }
}
