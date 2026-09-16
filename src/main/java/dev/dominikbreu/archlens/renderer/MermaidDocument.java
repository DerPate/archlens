package dev.dominikbreu.archlens.renderer;

import java.util.List;

/** Semantic presentation values shared by Mermaid renderers and hidden from JStachio templates. */
final class MermaidDocument {
    private MermaidDocument() {}

    /**
     * A shaped Mermaid node line, rendered as {@code <indent><id><open><label><close>}.
     *
     * @param indent leading whitespace placing the node inside its subgraph nesting
     * @param id Mermaid-safe node id referenced by edges and class assignments
     * @param open role-specific opening shape token (e.g. {@code "(("})
     * @param label escaped text shown inside the node shape
     * @param close role-specific closing shape token matching {@code open}
     */
    record Node(String indent, String id, String open, String label, String close) {}

    /**
     * A Mermaid flowchart edge line connecting two nodes, with the connector style (arrow,
     * dashed/solid, quoted label) chosen from its boolean flags rather than stored as text.
     *
     * @param indent leading whitespace placing the edge inside its subgraph nesting
     * @param from Mermaid-safe id of the source node
     * @param to Mermaid-safe id of the target node
     * @param label escaped text drawn on the edge; empty when {@code labeled} is false
     * @param labeled whether a label is emitted on the connector
     * @param conditional whether the connector renders the conditional (branch) arrow style
     * @param async whether the connector renders as a dashed, asynchronous arrow
     * @param quotedLabel whether the label is wrapped in quotes in the emitted source
     */
    record Edge(
            String indent,
            String from,
            String to,
            String label,
            boolean labeled,
            boolean conditional,
            boolean async,
            boolean quotedLabel) {
        /** An unlabeled, synchronous edge. */
        static Edge plain(String indent, String from, String to) {
            return new Edge(indent, from, to, "", false, false, false, false);
        }

        /** A labeled edge, dashed when {@code async} is true. */
        static Edge labeled(String indent, String from, String to, String label, boolean async) {
            return new Edge(indent, from, to, label, true, false, async, false);
        }

        /** An edge whose branch style (solid/dashed) is decided by {@code conditional}; quoted when labeled. */
        static Edge conditional(String indent, String from, String to, String label, boolean conditional) {
            boolean labeledAndQuoted = !label.isEmpty();
            return new Edge(indent, from, to, label, labeledAndQuoted, conditional, false, labeledAndQuoted);
        }
    }

    /** One line of flowchart source; exactly one of the permitted shapes below. */
    sealed interface Statement {
        /**
         * A flowchart line rendering a single shaped node.
         *
         * @param node node presentation data to render
         */
        record OfNode(Node node) implements Statement {}

        /**
         * A flowchart line rendering a single connector between two nodes.
         *
         * @param edge edge presentation data to render
         */
        record OfEdge(Edge edge) implements Statement {}

        /**
         * A subgraph-opening line grouping the statements that follow, until the matching
         * {@link End}, inside a visually boxed region.
         *
         * @param indent leading whitespace placing the subgraph inside its parent nesting
         * @param id Mermaid-safe id of the subgraph, referenced by nothing but required by Mermaid
         * @param label escaped text shown as the subgraph's title
         */
        record Subgraph(String indent, String id, String label) implements Statement {}

        /**
         * A {@code direction} directive line that overrides the layout flow (e.g. left-to-right)
         * for the enclosing subgraph.
         *
         * @param indent leading whitespace placing the directive inside its subgraph nesting
         * @param value Mermaid direction keyword (e.g. {@code "LR"})
         */
        record Direction(String indent, String value) implements Statement {}

        /**
         * An {@code end} line closing the innermost open subgraph.
         *
         * @param indent leading whitespace matching the {@link Subgraph} it closes
         */
        record End(String indent) implements Statement {}

        /**
         * An unquoted bracket-style annotation line, used for fallback text such as
         * "no results found" rather than a real graph node.
         *
         * @param indent leading whitespace placing the note inside its subgraph nesting
         * @param id Mermaid-safe id assigned to the note node
         * @param label text shown inside the note's brackets
         */
        record Note(String indent, String id, String label) implements Statement {}

        /**
         * An empty output line, used to visually separate sections of the rendered diagram.
         */
        record BlankLine() implements Statement {}

        static Statement node(Node value) {
            return new OfNode(value);
        }

        static Statement edge(Edge value) {
            return new OfEdge(value);
        }

        static Statement subgraph(String indent, String id, String label) {
            return new Subgraph(indent, id, label);
        }

        static Statement direction(String indent, String value) {
            return new Direction(indent, value);
        }

        static Statement end(String indent) {
            return new End(indent);
        }

        static Statement note(String indent, String id, String label) {
            return new Note(indent, id, label);
        }

        static Statement emptyLine() {
            return new BlankLine();
        }
    }

    /**
     * A {@code classDef} line declaring one reusable Mermaid style, emitted once per role
     * actually used in the diagram.
     *
     * @param css class name assigned by {@link ClassAssignment} to individual nodes
     * @param style Mermaid style declaration (fill/stroke/color) applied by that class
     */
    record ClassDefinition(String css, String style) {}

    /**
     * A {@code class} line attaching a previously defined style class to one node.
     *
     * @param nodeId Mermaid-safe id of the node receiving the style
     * @param css class name matching a {@link ClassDefinition#css()}
     */
    record ClassAssignment(String nodeId, String css) {}

    /**
     * One line of the diagram's trailing warning comment block, surfacing a rendering caveat
     * to the reader of the generated Mermaid source.
     *
     * @param text warning message text
     */
    record Warning(String text) {}

    /** One C4 element/relationship-block line; exactly one of the permitted shapes below. */
    sealed interface C4Element {
        /**
         * A single C4 macro invocation line (e.g. {@code Container(...)}), the most common
         * element line in a C4 diagram.
         *
         * @param macro C4 macro name (e.g. {@code "Container"})
         * @param id Mermaid-safe id of the element, referenced by {@link Relation}s
         * @param name escaped display name of the element
         * @param technology escaped technology label, or description text when no separate
         *     description argument is emitted
         * @param description escaped fourth macro argument; only meaningful when
         *     {@code fourArguments} is true
         * @param fourArguments whether the macro call emits {@code description} as a fourth argument
         * @param indent leading whitespace placing the line inside its boundary nesting
         */
        record Regular(
                String macro,
                String id,
                String name,
                String technology,
                String description,
                boolean fourArguments,
                String indent)
                implements C4Element {}

        /**
         * A {@code System_Boundary}-style opening line, grouping the elements that follow, until
         * the matching {@link BoundaryEnd}, inside one visual boundary box.
         *
         * @param id Mermaid-safe id of the boundary
         * @param name escaped display name shown as the boundary's title
         */
        record BoundaryStart(String id, String name) implements C4Element {}

        /** A closing line ending the innermost open C4 system boundary. */
        record BoundaryEnd() implements C4Element {}

        static C4Element element(
                String macro,
                String id,
                String name,
                String technology,
                String description,
                boolean fourArguments,
                String indent) {
            return new Regular(macro, id, name, technology, description, fourArguments, indent);
        }

        static C4Element boundary(String id, String name) {
            return new BoundaryStart(id, name);
        }

        static C4Element closingBoundary() {
            return new BoundaryEnd();
        }
    }

    /**
     * A {@code Rel(...)} line drawing one arrow between two C4 elements.
     *
     * @param from Mermaid-safe id of the source element
     * @param to Mermaid-safe id of the target element
     * @param label escaped text describing the relationship
     */
    record Relation(String from, String to, String label) {}

    /**
     * A block of sequence-diagram participants, optionally wrapped in a {@code box}
     * so that all participants belonging to one application render inside a shared frame.
     *
     * @param boxed whether to emit a surrounding {@code box}/{@code end} for this group
     * @param appName escaped application name shown as the box title; unused when {@code boxed}
     *     is false
     * @param indent leading whitespace applied to each participant declaration in the group
     * @param participants ordered participants declared inside this group
     */
    record ParticipantGroup(boolean boxed, String appName, String indent, List<Participant> participants) {}

    /**
     * One {@code participant} declaration line in a sequence diagram, representing a single
     * caller/callee actor in the runtime flow.
     *
     * @param pid Mermaid-safe participant id referenced by {@link Message}s and {@link Deactivation}s
     * @param display escaped display name shown for the participant
     * @param stereotype component stereotype used to decide async arrow styling for messages
     *     targeting this participant
     */
    record Participant(String pid, String display, String stereotype) {}

    /**
     * One arrow line in a sequence diagram representing a single call between two participants.
     *
     * @param from Mermaid-safe id of the calling participant
     * @param to Mermaid-safe id of the called participant
     * @param label escaped text describing the call
     * @param async whether the arrow renders as a dashed, asynchronous call
     * @param activate whether this call activates (raises the lifeline bar of) the target
     *     participant
     */
    record Message(String from, String to, String label, boolean async, boolean activate) {}

    /**
     * One {@code deactivate} line lowering a previously activated participant's lifeline bar,
     * emitted in reverse activation order once the flow unwinds.
     *
     * @param participant Mermaid-safe id of the participant to deactivate
     */
    record Deactivation(String participant) {}
}
