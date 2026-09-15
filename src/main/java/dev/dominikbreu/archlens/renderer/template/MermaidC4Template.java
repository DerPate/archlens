package dev.dominikbreu.archlens.renderer.template;

import io.jstach.jstache.JStache;
import io.jstach.jstache.JStacheConfig;
import io.jstach.jstache.JStacheType;
import java.util.List;

/**
 * Typed presentation model for Mermaid's C4 dialect.
 *
 * @param context whether this is a system-context diagram
 * @param containerDiagram whether this is a container diagram
 * @param elements ordered C4 elements and boundaries
 * @param relations ordered C4 relationships
 */
@JStache(path = "templates/mermaid/c4.mustache")
@JStacheConfig(type = JStacheType.STACHE)
public record MermaidC4Template(
        boolean context, boolean containerDiagram, List<Element> elements, List<Relation> relations) {

    /** Makes collection components immutable. */
    public MermaidC4Template {
        elements = List.copyOf(elements);
        relations = List.copyOf(relations);
    }

    /**
     * One C4 element or boundary statement.
     *
     * @param macro C4 macro name
     * @param id Mermaid-safe element id
     * @param name escaped display name
     * @param technology escaped technology or description
     * @param description escaped fourth argument
     * @param fourArguments whether the fourth argument is emitted
     * @param boundaryStart whether this opens a system boundary
     * @param boundaryEnd whether this closes a system boundary
     * @param indent leading whitespace
     */
    public record Element(
            String macro,
            String id,
            String name,
            String technology,
            String description,
            boolean fourArguments,
            boolean boundaryStart,
            boolean boundaryEnd,
            String indent) {

        /**
         * Creates a regular C4 macro invocation.
         *
         * @param macro C4 macro name
         * @param id Mermaid-safe element id
         * @param name escaped display name
         * @param technology escaped technology or description
         * @param description escaped fourth argument
         * @param fourArguments whether the fourth argument is emitted
         * @param indent leading whitespace
         * @return C4 element presentation data
         */
        public static Element element(
                String macro,
                String id,
                String name,
                String technology,
                String description,
                boolean fourArguments,
                String indent) {
            return new Element(macro, id, name, technology, description, fourArguments, false, false, indent);
        }

        /**
         * Creates a system-boundary opening.
         *
         * @param id Mermaid-safe boundary id
         * @param name escaped boundary name
         * @return boundary-opening presentation data
         */
        public static Element boundary(String id, String name) {
            return new Element("", id, name, "", "", false, true, false, "    ");
        }

        /**
         * Creates a system-boundary closing.
         *
         * @return boundary-closing presentation data
         */
        public static Element closeBoundary() {
            return new Element("", "", "", "", "", false, false, true, "    ");
        }
    }

    /**
     * One C4 relationship.
     *
     * @param from source element id
     * @param to target element id
     * @param label escaped relationship label
     */
    public record Relation(String from, String to, String label) {}
}
