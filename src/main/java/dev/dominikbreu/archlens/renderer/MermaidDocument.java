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
            boolean quotedLabel) {}

    record Statement(
            Node node,
            Edge edge,
            String indent,
            String id,
            String label,
            String direction,
            boolean end,
            boolean note,
            boolean blank) {
        static Statement node(Node value) {
            return new Statement(value, null, null, null, null, null, false, false, false);
        }

        static Statement edge(Edge value) {
            return new Statement(null, value, null, null, null, null, false, false, false);
        }

        static Statement subgraph(String indent, String id, String label) {
            return new Statement(null, null, indent, id, label, null, false, false, false);
        }

        static Statement direction(String indent, String value) {
            return new Statement(null, null, indent, null, null, value, false, false, false);
        }

        static Statement end(String indent) {
            return new Statement(null, null, indent, null, null, null, true, false, false);
        }

        static Statement note(String indent, String id, String label) {
            return new Statement(null, null, indent, id, label, null, false, true, false);
        }

        static Statement emptyLine() {
            return new Statement(null, null, null, null, null, null, false, false, true);
        }
    }

    record ClassDefinition(String css, String style) {}

    record ClassAssignment(String nodeId, String css) {}

    record Warning(String text) {}

    record C4Element(
            String macro,
            String id,
            String name,
            String technology,
            String description,
            String indent,
            boolean fourArguments,
            boolean closeBoundary) {
        static C4Element element(
                String macro,
                String id,
                String name,
                String technology,
                String description,
                boolean fourArguments,
                String indent) {
            return new C4Element(macro, id, name, technology, description, indent, fourArguments, false);
        }

        static C4Element boundary(String id, String name) {
            return new C4Element("System_Boundary", id, name, "", "", "    ", false, false);
        }

        static C4Element closingBoundary() {
            return new C4Element("", "", "", "", "", "", false, true);
        }
    }

    record Relation(String from, String to, String label) {}

    record ParticipantGroup(boolean boxed, String appName, String indent, List<Participant> participants) {}

    record Participant(String pid, String display, String stereotype) {}

    record Message(String from, String to, String label, boolean async, boolean activate) {}

    record Deactivation(String participant) {}
}
