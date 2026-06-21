package dev.dominikbreu.archlens.likec4;

import java.util.Objects;

/**
 * A single step in a LikeC4 dynamic (sequence/flow) view.
 *
 * @param sourceId the id of the source element
 * @param targetId the id of the target element
 * @param title the label for this step
 */
public record LikeC4DynamicStep(String sourceId, String targetId, String title) {

    /** Validates that all fields are non-null. */
    public LikeC4DynamicStep {
        Objects.requireNonNull(sourceId, "sourceId");
        Objects.requireNonNull(targetId, "targetId");
        Objects.requireNonNull(title, "title");
    }
}
