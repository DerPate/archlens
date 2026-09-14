package dev.dominikbreu.archlens.okf;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Renders the loosely-typed answer/evidence values ArchLens attaches to a question result —
 * graph nodes, nested maps, lists, and scalars — as Markdown.
 */
final class AnswerValueRenderer {
    private AnswerValueRenderer() {}

    static String block(Object value) {
        if (value instanceof Map<?, ?> map) {
            return map(map);
        }
        if (value instanceof List<?> values) {
            return list(values);
        }
        return inline(value);
    }

    static String list(List<?> values) {
        if (values.isEmpty()) {
            return "None recorded.";
        }
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < values.size(); index++) {
            builder.append(index + 1)
                    .append(". ")
                    .append(inline(values.get(index)))
                    .append('\n');
        }
        return builder.toString().stripTrailing();
    }

    static String strings(List<String> values) {
        if (values.isEmpty()) {
            return "None recorded.";
        }
        StringBuilder builder = new StringBuilder();
        for (String value : values) {
            builder.append("- ").append(value).append('\n');
        }
        return builder.toString().stripTrailing();
    }

    private static String map(Map<?, ?> values) {
        if (values.isEmpty()) {
            return "None recorded.";
        }
        // Graph nodes are represented as maps with an "id" key; render those as one identifiable
        // citation line instead of dumping every property, since that's how evidence should read.
        if (values.get("id") instanceof String id) {
            return graphNode(values, id);
        }
        StringBuilder builder = new StringBuilder();
        values.entrySet().stream()
                .sorted(Comparator.comparing(entry -> String.valueOf(entry.getKey())))
                .forEach(entry -> builder.append("- **")
                        .append(entry.getKey())
                        .append("**: ")
                        .append(inline(entry.getValue()))
                        .append('\n'));
        return builder.toString().stripTrailing();
    }

    private static String graphNode(Map<?, ?> values, String id) {
        Object nameValue = values.containsKey("name") ? values.get("name") : id;
        Object labelValue = values.containsKey("label") ? values.get("label") : "node";
        String name = String.valueOf(nameValue);
        String label = String.valueOf(labelValue);
        StringBuilder builder = new StringBuilder("- `")
                .append(id)
                .append("` — ")
                .append(name)
                .append(" (")
                .append(label)
                .append(")");
        values.entrySet().stream()
                .filter(entry -> Set.of("evidence", "properties", "source").contains(String.valueOf(entry.getKey())))
                .sorted(Comparator.comparing(entry -> String.valueOf(entry.getKey())))
                .forEach(entry -> builder.append("\n  - **")
                        .append(entry.getKey())
                        .append("**: ")
                        .append(inline(entry.getValue())));
        return builder.toString();
    }

    private static String inline(Object value) {
        if (value instanceof Map<?, ?> nested) {
            return nested.isEmpty() ? "None recorded." : "\n" + indent(map(nested));
        }
        if (value instanceof List<?> nested) {
            return nested.isEmpty() ? "None recorded." : "\n" + indent(list(nested));
        }
        if (value == null) {
            return "`null`";
        }
        String text = String.valueOf(value);
        return safeInlineCode(text) ? "`" + text + "`" : text;
    }

    private static String indent(String value) {
        return value.lines().map(line -> "  " + line).collect(Collectors.joining("\n"));
    }

    private static boolean safeInlineCode(String value) {
        return !value.isBlank() && !value.contains("`") && !value.contains("\n");
    }
}
