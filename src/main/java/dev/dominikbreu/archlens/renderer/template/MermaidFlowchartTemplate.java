package dev.dominikbreu.archlens.renderer.template;

import io.jstach.jstache.JStache;
import io.jstach.jstache.JStacheConfig;
import io.jstach.jstache.JStacheType;
import java.util.List;

/**
 * Typed presentation model for a Mermaid flowchart document.
 *
 * @param direction diagram direction
 * @param statements ordered diagram statements
 * @param classDefinitions used role definitions
 * @param classAssignments node-to-role assignments
 * @param hasWarnings whether to emit the warning section
 * @param warnings warning comments
 */
@JStache(path = "templates/mermaid/flowchart.mustache")
@JStacheConfig(type = JStacheType.STACHE)
public record MermaidFlowchartTemplate(
        String direction,
        List<Statement> statements,
        List<ClassDefinition> classDefinitions,
        List<ClassAssignment> classAssignments,
        boolean hasWarnings,
        List<Warning> warnings) {

    /** Makes collection components immutable. */
    public MermaidFlowchartTemplate {
        statements = List.copyOf(statements);
        classDefinitions = List.copyOf(classDefinitions);
        classAssignments = List.copyOf(classAssignments);
        warnings = List.copyOf(warnings);
    }

    /**
     * One ordered flowchart statement; exactly one optional component is populated.
     *
     * @param node shaped node, if this is a node statement
     * @param edge edge, if this is an edge statement
     * @param subgraph subgraph opening, if present
     * @param directionStatement direction directive, if present
     * @param end subgraph closing, if present
     * @param note unquoted note node, if present
     * @param blankLine empty-line marker, if present
     */
    public record Statement(
            Node node,
            Edge edge,
            Subgraph subgraph,
            Direction directionStatement,
            End end,
            Note note,
            Blank blankLine) {

        /**
         * Creates a node statement.
         *
         * @param node node presentation data
         * @return node statement
         */
        public static Statement node(Node node) {
            return new Statement(node, null, null, null, null, null, null);
        }

        /**
         * Creates an edge statement.
         *
         * @param edge edge presentation data
         * @return edge statement
         */
        public static Statement edge(Edge edge) {
            return new Statement(null, edge, null, null, null, null, null);
        }

        /**
         * Opens a subgraph.
         *
         * @param indent leading whitespace
         * @param id Mermaid-safe group id
         * @param label escaped group label
         * @return subgraph-opening statement
         */
        public static Statement subgraph(String indent, String id, String label) {
            return new Statement(null, null, new Subgraph(indent, id, label), null, null, null, null);
        }

        /**
         * Adds a direction directive inside a subgraph.
         *
         * @param indent leading whitespace
         * @param direction Mermaid direction
         * @return direction statement
         */
        public static Statement direction(String indent, String direction) {
            return new Statement(null, null, null, new Direction(indent, direction), null, null, null);
        }

        /**
         * Closes a subgraph.
         *
         * @param indent leading whitespace
         * @return closing statement
         */
        public static Statement end(String indent) {
            return new Statement(null, null, null, null, new End(indent), null, null);
        }

        /**
         * Creates an unquoted note node.
         *
         * @param indent leading whitespace
         * @param id Mermaid-safe note id
         * @param label note text
         * @return note statement
         */
        public static Statement note(String indent, String id, String label) {
            return new Statement(null, null, null, null, null, new Note(indent, id, label), null);
        }

        /**
         * Creates an empty output line.
         *
         * @return empty-line statement
         */
        public static Statement emptyLine() {
            return new Statement(null, null, null, null, null, null, new Blank());
        }
    }

    /**
     * A shaped Mermaid node.
     *
     * @param indent leading whitespace
     * @param id Mermaid-safe node id
     * @param open opening shape token
     * @param label escaped label
     * @param close closing shape token
     */
    public record Node(String indent, String id, String open, String label, String close) {}

    /**
     * A Mermaid edge with typed connector choices.
     *
     * @param indent leading whitespace
     * @param from source node id
     * @param to target node id
     * @param label escaped edge label
     * @param labeled whether a label is present
     * @param conditional whether the edge is conditional
     * @param async whether the edge is asynchronous
     * @param quotedLabel whether Mermaid quotes the label
     */
    public record Edge(
            String indent,
            String from,
            String to,
            String label,
            boolean labeled,
            boolean conditional,
            boolean async,
            boolean quotedLabel) {}

    /**
     * A named subgraph opening.
     *
     * @param indent leading whitespace
     * @param id Mermaid-safe group id
     * @param label escaped label
     */
    public record Subgraph(String indent, String id, String label) {}

    /**
     * A subgraph-local direction directive.
     *
     * @param indent leading whitespace
     * @param value Mermaid direction
     */
    public record Direction(String indent, String value) {}

    /**
     * A subgraph closing statement.
     *
     * @param indent leading whitespace
     */
    public record End(String indent) {}

    /**
     * An unquoted bracket note.
     *
     * @param indent leading whitespace
     * @param id Mermaid-safe note id
     * @param label note text
     */
    public record Note(String indent, String id, String label) {}

    /** Marker for an empty line. */
    public record Blank() {}

    /**
     * One Mermaid class definition.
     *
     * @param css class name
     * @param style Mermaid style declaration
     */
    public record ClassDefinition(String css, String style) {}

    /**
     * One Mermaid class assignment.
     *
     * @param nodeId Mermaid-safe node id
     * @param css class name
     */
    public record ClassAssignment(String nodeId, String css) {}

    /**
     * One output warning comment.
     *
     * @param text warning text
     */
    public record Warning(String text) {}
}
