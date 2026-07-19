package dev.dominikbreu.archlens.model;

import dev.dominikbreu.archlens.model.ids.AppId;
import dev.dominikbreu.archlens.model.ids.ComponentId;
import java.util.ArrayList;
import java.util.List;

/**
 * Source-level architecture component such as a REST resource, service, repository, or entity.
 */
public class Component {
    /** Stable component identifier — the component's fully-qualified class name. */
    public ComponentId id;
    /** Architectural role assigned to the component. */
    public ComponentType type;
    /** Simple display name, usually the Java type simple name. */
    public String name;
    /** Fully qualified Java type name when the component comes from source. */
    public String qualifiedName;
    /** Owning application or Maven module identifier. */
    public AppId module;
    /** Detected technology or framework associated with the component. */
    public String technology;
    /** Additional framework or architecture labels discovered during extraction. */
    public List<String> stereotypes = new ArrayList<>();
    /**
     * Explicit EJB name from {@code @Stateless}/{@code @Singleton}/{@code @Stateful}/
     * {@code @MessageDriven}({@code name=...}), when declared; null when the ejb-name defaults
     * to the simple class name.
     */
    public String ejbName;
    /** Source file and evidence metadata for this component. */
    public SourceInfo source;

    /** Creates an empty component for JSON deserialization. */
    public Component() {}
}
