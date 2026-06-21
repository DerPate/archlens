package dev.dominikbreu.archlens.likec4;

import java.util.Map;
import java.util.Objects;

/**
 * A LikeC4 architecture element.
 *
 * @param id the element id
 * @param kind the element kind (maps to a specification kind name)
 * @param title the display title
 * @param sourceId the source component or entrypoint id this element was derived from
 * @param metadata additional key-value metadata for rendering
 */
public record LikeC4Element(String id, String kind, String title, String sourceId, Map<String, Object> metadata) {

    /** Validates non-null fields and defensively copies the metadata map. */
    public LikeC4Element {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(sourceId, "sourceId");
        metadata = Map.copyOf(Objects.requireNonNull(metadata, "metadata"));
    }
}
