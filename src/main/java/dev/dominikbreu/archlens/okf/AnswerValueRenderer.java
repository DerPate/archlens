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

    /** Renders a value as its own block: a section body or a top-level "Subject". */
    static String block(Object value) {
        return render(value, false);
    }

    /** Renders a flat list of already-Markdown strings, e.g. suggested questions. */
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

    /** Renders a numbered list whose items may themselves be structured values. */
    static String list(List<?> values) {
        if (values.isEmpty()) {
            return "None recorded.";
        }
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < values.size(); index++) {
            builder.append(index + 1)
                    .append(". ")
                    .append(nested(values.get(index)))
                    .append('\n');
        }
        return builder.toString().stripTrailing();
    }

    // Every value ArchLens renders is one of: an empty Map/List ("None recorded."), a non-empty
    // Map/List (structured; indented under its parent bullet when nested), null, or a scalar.
    // block() and nested() are the only two entry points into this dispatch, so the four shapes
    // are handled in exactly one place rather than once per entry point.
    private static String render(Object value, boolean nested) {
        return switch (value) {
            case Map<?, ?> map when map.isEmpty() -> "None recorded.";
            case Map<?, ?> map -> wrap(map(map), nested);
            case List<?> values when values.isEmpty() -> "None recorded.";
            case List<?> values -> wrap(list(values), nested);
            case null -> "`null`";
            default -> scalar(value);
        };
    }

    /** Renders a value sitting inside a parent bullet, e.g. after "- **key**: ". */
    private static String nested(Object value) {
        return render(value, true);
    }

    private static String wrap(String rendered, boolean nested) {
        return nested ? "\n" + indent(rendered) : rendered;
    }

    private static String scalar(Object value) {
        String text = String.valueOf(value);
        return safeInlineCode(text) ? "`" + text + "`" : text;
    }

    private static String map(Map<?, ?> values) {
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
                        .append(nested(entry.getValue()))
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
                        .append(nested(entry.getValue())));
        return builder.toString();
    }

    private static String indent(String value) {
        return value.lines().map(line -> "  " + line).collect(Collectors.joining("\n"));
    }

    private static boolean safeInlineCode(String value) {
        return !value.isBlank() && !value.contains("`") && !value.contains("\n");
    }
}
