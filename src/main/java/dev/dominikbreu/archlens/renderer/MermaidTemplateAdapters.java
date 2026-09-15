package dev.dominikbreu.archlens.renderer;

import dev.dominikbreu.archlens.renderer.template.MermaidC4Template;
import dev.dominikbreu.archlens.renderer.template.MermaidC4TemplateRenderer;
import dev.dominikbreu.archlens.renderer.template.MermaidFlowchartTemplate;
import dev.dominikbreu.archlens.renderer.template.MermaidFlowchartTemplateRenderer;
import dev.dominikbreu.archlens.renderer.template.MermaidHeaderTemplate;
import dev.dominikbreu.archlens.renderer.template.MermaidHeaderTemplateRenderer;
import dev.dominikbreu.archlens.renderer.template.MermaidNodeTemplate;
import dev.dominikbreu.archlens.renderer.template.MermaidNodeTemplateRenderer;
import dev.dominikbreu.archlens.renderer.template.MermaidSequenceTemplate;
import dev.dominikbreu.archlens.renderer.template.MermaidSequenceTemplateRenderer;
import dev.dominikbreu.archlens.renderer.template.MermaidStyleTemplate;
import dev.dominikbreu.archlens.renderer.template.MermaidStyleTemplateRenderer;
import java.util.List;

/** Direct entry points to the generated Mermaid STACHE renderers. */
final class MermaidTemplateAdapters {

    private MermaidTemplateAdapters() {}

    static String header() {
        return MermaidHeaderTemplateRenderer.of().execute(new MermaidHeaderTemplate());
    }

    static String node(MermaidDocument.Node node) {
        return MermaidNodeTemplateRenderer.of()
                .execute(new MermaidNodeTemplate(node.indent(), node.id(), node.open(), node.label(), node.close()));
    }

    static String style(
            List<MermaidDocument.ClassDefinition> definitions, List<MermaidDocument.ClassAssignment> assignments) {
        return MermaidStyleTemplateRenderer.of()
                .execute(new MermaidStyleTemplate(
                        definitions.stream()
                                .map(d -> new MermaidStyleTemplate.ClassDefinition(d.css(), d.style()))
                                .toList(),
                        assignments.stream()
                                .map(a -> new MermaidStyleTemplate.ClassAssignment(a.nodeId(), a.css()))
                                .toList()));
    }

    static String flowchart(
            String direction, List<MermaidDocument.Statement> statements, MermaidStyle.Tracker tracker) {
        return flowchart(direction, statements, tracker, List.of());
    }

    static String flowchart(
            String direction,
            List<MermaidDocument.Statement> statements,
            MermaidStyle.Tracker tracker,
            List<String> warnings) {
        MermaidFlowchartTemplate model = new MermaidFlowchartTemplate(
                direction,
                statements.stream()
                        .map(MermaidTemplateAdapters::flowchartStatement)
                        .toList(),
                tracker.definitions().stream()
                        .map(d -> new MermaidFlowchartTemplate.ClassDefinition(d.css(), d.style()))
                        .toList(),
                tracker.assignments().stream()
                        .map(a -> new MermaidFlowchartTemplate.ClassAssignment(a.nodeId(), a.css()))
                        .toList(),
                !warnings.isEmpty(),
                warnings.stream().map(MermaidFlowchartTemplate.Warning::new).toList());
        return header() + MermaidFlowchartTemplateRenderer.of().execute(model);
    }

    static String sequence(
            boolean empty,
            List<MermaidDocument.ParticipantGroup> groups,
            List<MermaidDocument.Message> messages,
            List<MermaidDocument.Deactivation> deactivations) {
        MermaidSequenceTemplate model = new MermaidSequenceTemplate(
                empty,
                groups.stream()
                        .map(g -> new MermaidSequenceTemplate.ParticipantGroup(
                                g.boxed(),
                                g.appName(),
                                g.indent(),
                                g.participants().stream()
                                        .map(p -> new MermaidSequenceTemplate.Participant(
                                                p.pid(), p.display(), p.stereotype()))
                                        .toList()))
                        .toList(),
                messages.stream()
                        .map(m -> new MermaidSequenceTemplate.Message(
                                m.from(), m.to(), m.label(), m.async(), m.activate()))
                        .toList(),
                deactivations.stream()
                        .map(d -> new MermaidSequenceTemplate.Deactivation(d.participant()))
                        .toList());
        return header() + MermaidSequenceTemplateRenderer.of().execute(model);
    }

    static String c4(MermaidC4Template model) {
        return MermaidC4TemplateRenderer.of().execute(model);
    }

    static String c4(
            boolean context,
            boolean containerDiagram,
            List<MermaidDocument.C4Element> elements,
            List<MermaidDocument.Relation> relations) {
        return c4(new MermaidC4Template(
                context,
                containerDiagram,
                elements.stream().map(MermaidTemplateAdapters::c4Element).toList(),
                relations.stream()
                        .map(r -> new MermaidC4Template.Relation(r.from(), r.to(), r.label()))
                        .toList()));
    }

    private static MermaidC4Template.Element c4Element(MermaidDocument.C4Element e) {
        return switch (e) {
            case MermaidDocument.C4Element.BoundaryStart(var id, var name) ->
                MermaidC4Template.Element.boundary(id, name);
            case MermaidDocument.C4Element.BoundaryEnd() -> MermaidC4Template.Element.closeBoundary();
            case MermaidDocument.C4Element.Regular(
                    var macro,
                    var id,
                    var name,
                    var technology,
                    var description,
                    var fourArguments,
                    var indent) ->
                MermaidC4Template.Element.element(macro, id, name, technology, description, fourArguments, indent);
        };
    }

    private static MermaidFlowchartTemplate.Statement flowchartStatement(MermaidDocument.Statement s) {
        return switch (s) {
            case MermaidDocument.Statement.OfNode(var node) ->
                MermaidFlowchartTemplate.Statement.node(new MermaidFlowchartTemplate.Node(
                        node.indent(), node.id(), node.open(), node.label(), node.close()));
            case MermaidDocument.Statement.OfEdge(var e) ->
                MermaidFlowchartTemplate.Statement.edge(new MermaidFlowchartTemplate.Edge(
                        e.indent(),
                        e.from(),
                        e.to(),
                        e.label(),
                        e.labeled(),
                        e.conditional(),
                        e.async(),
                        e.quotedLabel()));
            case MermaidDocument.Statement.Subgraph(var indent, var id, var label) ->
                MermaidFlowchartTemplate.Statement.subgraph(indent, id, label);
            case MermaidDocument.Statement.Direction(var indent, var value) ->
                MermaidFlowchartTemplate.Statement.direction(indent, value);
            case MermaidDocument.Statement.End(var indent) -> MermaidFlowchartTemplate.Statement.end(indent);
            case MermaidDocument.Statement.Note(var indent, var id, var label) ->
                MermaidFlowchartTemplate.Statement.note(indent, id, label);
            case MermaidDocument.Statement.BlankLine() -> MermaidFlowchartTemplate.Statement.emptyLine();
        };
    }
}
