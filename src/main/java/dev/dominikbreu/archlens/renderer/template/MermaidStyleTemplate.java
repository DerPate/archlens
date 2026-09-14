package dev.dominikbreu.archlens.renderer.template;

import io.jstach.jstache.JStache;
import io.jstach.jstache.JStacheConfig;
import io.jstach.jstache.JStacheType;
import java.util.List;

/**
 * Typed presentation model for Mermaid class definitions and assignments.
 *
 * @param definitions ordered class definitions
 * @param assignments ordered node class assignments
 */
@JStache(path = "templates/mermaid/style.mustache")
@JStacheConfig(type = JStacheType.STACHE)
public record MermaidStyleTemplate(List<ClassDefinition> definitions, List<ClassAssignment> assignments) {

    /** Makes collection components immutable. */
    public MermaidStyleTemplate {
        definitions = List.copyOf(definitions);
        assignments = List.copyOf(assignments);
    }

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
}
