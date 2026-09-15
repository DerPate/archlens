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
                elements.stream()
                        .map(e -> e.boundaryStart()
                                ? MermaidC4Template.Element.boundary(e.id(), e.name())
                                : e.closeBoundary()
                                        ? MermaidC4Template.Element.closeBoundary()
                                        : MermaidC4Template.Element.element(
                                                e.macro(),
                                                e.id(),
                                                e.name(),
                                                e.technology(),
                                                e.description(),
                                                e.fourArguments(),
                                                e.indent()))
                        .toList(),
                relations.stream()
                        .map(r -> new MermaidC4Template.Relation(r.from(), r.to(), r.label()))
                        .toList()));
    }

    private static MermaidFlowchartTemplate.Statement flowchartStatement(MermaidDocument.Statement s) {
        if (s.node() != null)
            return MermaidFlowchartTemplate.Statement.node(new MermaidFlowchartTemplate.Node(
                    s.node().indent(),
                    s.node().id(),
                    s.node().open(),
                    s.node().label(),
                    s.node().close()));
        if (s.edge() != null) {
            var e = s.edge();
            return MermaidFlowchartTemplate.Statement.edge(new MermaidFlowchartTemplate.Edge(
                    e.indent(), e.from(), e.to(), e.label(), e.labeled(), e.conditional(), e.async(), e.quotedLabel()));
        }
        if (s.note()) return MermaidFlowchartTemplate.Statement.note(s.indent(), s.id(), s.label());
        if (s.blank()) return MermaidFlowchartTemplate.Statement.emptyLine();
        if (s.end()) return MermaidFlowchartTemplate.Statement.end(s.indent());
        if (s.direction() != null) return MermaidFlowchartTemplate.Statement.direction(s.indent(), s.direction());
        return MermaidFlowchartTemplate.Statement.subgraph(s.indent(), s.id(), s.label());
    }
}
