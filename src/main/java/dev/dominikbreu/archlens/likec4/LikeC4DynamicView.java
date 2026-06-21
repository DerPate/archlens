package dev.dominikbreu.archlens.likec4;

import java.util.List;
import java.util.Objects;

/**
 * A LikeC4 dynamic (sequence/flow) view.
 *
 * @param id the view id
 * @param title the view title
 * @param steps the ordered steps in this dynamic view
 */
public record LikeC4DynamicView(String id, String title, List<LikeC4DynamicStep> steps) {

    /** Validates non-null fields and defensively copies the step list. */
    public LikeC4DynamicView {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(title, "title");
        steps = List.copyOf(Objects.requireNonNull(steps, "steps"));
    }
}
