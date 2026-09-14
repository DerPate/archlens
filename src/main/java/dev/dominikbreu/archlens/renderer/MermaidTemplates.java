package dev.dominikbreu.archlens.renderer;

import dev.dominikbreu.archlens.renderer.template.MermaidC4Template;
import dev.dominikbreu.archlens.renderer.template.MermaidC4TemplateRenderer;
import dev.dominikbreu.archlens.renderer.template.MermaidFlowchartTemplate;
import dev.dominikbreu.archlens.renderer.template.MermaidFlowchartTemplateRenderer;
import dev.dominikbreu.archlens.renderer.template.MermaidSequenceTemplate;
import dev.dominikbreu.archlens.renderer.template.MermaidSequenceTemplateRenderer;
import java.util.List;

/** Direct entry points to the generated Mermaid STACHE renderers. */
final class MermaidTemplates {

    private MermaidTemplates() {}

    static String flowchart(
            String direction, List<MermaidFlowchartTemplate.Statement> statements, MermaidStyle.Tracker tracker) {
        return flowchart(direction, statements, tracker, List.of());
    }

    static String flowchart(
            String direction,
            List<MermaidFlowchartTemplate.Statement> statements,
            MermaidStyle.Tracker tracker,
            List<String> warnings) {
        MermaidFlowchartTemplate model = new MermaidFlowchartTemplate(
                direction,
                statements,
                tracker.definitions().stream()
                        .map(d -> new MermaidFlowchartTemplate.ClassDefinition(d.css(), d.style()))
                        .toList(),
                tracker.assignments().stream()
                        .map(a -> new MermaidFlowchartTemplate.ClassAssignment(a.nodeId(), a.css()))
                        .toList(),
                !warnings.isEmpty(),
                warnings.stream().map(MermaidFlowchartTemplate.Warning::new).toList());
        return MermaidFlowchartTemplateRenderer.of().execute(model);
    }

    static String sequence(MermaidSequenceTemplate model) {
        return MermaidSequenceTemplateRenderer.of().execute(model);
    }

    static String c4(MermaidC4Template model) {
        return MermaidC4TemplateRenderer.of().execute(model);
    }
}
