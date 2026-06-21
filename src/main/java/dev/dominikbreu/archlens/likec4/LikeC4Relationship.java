package dev.dominikbreu.archlens.likec4;

import java.util.Map;
import java.util.Objects;

/**
 * A directed LikeC4 relationship between two elements.
 *
 * @param sourceId the id of the source element
 * @param targetId the id of the target element
 * @param title the relationship label
 * @param sourceLabel the raw edge label from the architecture graph
 * @param metadata additional key-value metadata for rendering
 */
public record LikeC4Relationship(
        String sourceId, String targetId, String title, String sourceLabel, Map<String, Object> metadata) {

    /** Validates non-null fields and defensively copies the metadata map. */
    public LikeC4Relationship {
        Objects.requireNonNull(sourceId, "sourceId");
        Objects.requireNonNull(targetId, "targetId");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(sourceLabel, "sourceLabel");
        metadata = Map.copyOf(Objects.requireNonNull(metadata, "metadata"));
    }
}
