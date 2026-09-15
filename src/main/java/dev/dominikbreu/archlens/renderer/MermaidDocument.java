package dev.dominikbreu.archlens.renderer;

import java.util.List;

/** Semantic presentation values shared by Mermaid renderers and hidden from JStachio templates. */
final class MermaidDocument {
    private MermaidDocument() {}

    record Node(String indent, String id, String open, String label, String close) {}

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
        record OfNode(Node node) implements Statement {}

        record OfEdge(Edge edge) implements Statement {}

        record Subgraph(String indent, String id, String label) implements Statement {}

        record Direction(String indent, String value) implements Statement {}

        record End(String indent) implements Statement {}

        record Note(String indent, String id, String label) implements Statement {}

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

    record ClassDefinition(String css, String style) {}

    record ClassAssignment(String nodeId, String css) {}

    record Warning(String text) {}

    /** One C4 element/relationship-block line; exactly one of the permitted shapes below. */
    sealed interface C4Element {
        record Regular(
                String macro,
                String id,
                String name,
                String technology,
                String description,
                boolean fourArguments,
                String indent)
                implements C4Element {}

        record BoundaryStart(String id, String name) implements C4Element {}

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

    record Relation(String from, String to, String label) {}

    record ParticipantGroup(boolean boxed, String appName, String indent, List<Participant> participants) {}

    record Participant(String pid, String display, String stereotype) {}

    record Message(String from, String to, String label, boolean async, boolean activate) {}

    record Deactivation(String participant) {}
}
