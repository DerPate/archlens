package dev.dominikbreu.archlens.renderer.template;

import io.jstach.jstache.JStache;
import io.jstach.jstache.JStacheConfig;
import io.jstach.jstache.JStacheType;
import java.util.List;

/**
 * Presentation data for a LikeC4 document; identifiers and quoted values are already escaped.
 *
 * @param warnings individual warning lines
 * @param kinds element kind identifiers
 * @param elements element declarations
 * @param relationships relationship declarations
 * @param views static views
 * @param dynamicViews ordered interaction views
 */
@JStache(path = "templates/likec4/document.mustache")
@JStacheConfig(type = JStacheType.STACHE)
public record LikeC4Template(
        List<String> warnings,
        List<String> kinds,
        List<Element> elements,
        List<Relationship> relationships,
        List<View> views,
        List<DynamicView> dynamicViews) {

    /** Creates a document with immutable presentation collections. */
    public LikeC4Template {
        warnings = List.copyOf(warnings);
        kinds = List.copyOf(kinds);
        elements = List.copyOf(elements);
        relationships = List.copyOf(relationships);
        views = List.copyOf(views);
        dynamicViews = List.copyOf(dynamicViews);
    }

    /**
     * Whether warnings need a separating blank line.
     * @return true when at least one warning is present
     */
    public boolean hasWarnings() {
        return !warnings.isEmpty();
    }

    /**
     * An element declaration.
     * @param id unique identifier
     * @param kind declared element kind
     * @param title escaped title
     * @param metadata sorted metadata entries
     */
    public record Element(String id, String kind, String title, List<Metadata> metadata) {
        /** Creates an element with immutable metadata. */
        public Element {
            metadata = List.copyOf(metadata);
        }

        /** @return whether to emit a metadata block */
        public boolean hasMetadata() {
            return !metadata.isEmpty();
        }
    }

    /**
     * A directed relationship, also used for dynamic steps without metadata.
     * @param source source identifier
     * @param target target identifier
     * @param title escaped relationship title
     * @param metadata sorted metadata entries
     */
    public record Relationship(String source, String target, String title, List<Metadata> metadata) {
        /** Creates a relationship with immutable metadata. */
        public Relationship {
            metadata = List.copyOf(metadata);
        }

        /** @return whether to emit a relationship metadata block */
        public boolean hasMetadata() {
            return !metadata.isEmpty();
        }
    }

    /**
     * One metadata attribute.
     * @param key sanitized metadata key
     * @param value escaped string value
     */
    public record Metadata(String key, String value) {}

    /**
     * A static view with explicit includes, including a wildcard when needed.
     * @param id view identifier
     * @param title escaped title
     * @param notes individual comment lines
     * @param includes element identifiers or a wildcard
     */
    public record View(String id, String title, List<String> notes, List<String> includes) {
        /** Creates a view with immutable notes and includes. */
        public View {
            notes = List.copyOf(notes);
            includes = List.copyOf(includes);
        }
    }

    /**
     * A dynamic view.
     * @param id view identifier
     * @param title escaped title
     * @param steps ordered interactions
     */
    public record DynamicView(String id, String title, List<Relationship> steps) {
        /** Creates a dynamic view with immutable ordered steps. */
        public DynamicView {
            steps = List.copyOf(steps);
        }
    }
}
