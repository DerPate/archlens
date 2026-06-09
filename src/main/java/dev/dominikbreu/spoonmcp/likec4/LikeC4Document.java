package dev.dominikbreu.spoonmcp.likec4;

import java.util.List;
import java.util.Objects;

/**
 * A complete LikeC4 document ready for rendering.
 *
 * @param elementKinds the element kind names declared in this document
 * @param elements the architecture elements
 * @param relationships the directed relationships between elements
 * @param views the static views defined in this document
 * @param warnings non-fatal messages produced during projection
 * @param dynamicViews the dynamic (sequence/flow) views defined in this document
 */
public record LikeC4Document(
        List<String> elementKinds,
        List<LikeC4Element> elements,
        List<LikeC4Relationship> relationships,
        List<LikeC4View> views,
        List<String> warnings,
        List<LikeC4DynamicView> dynamicViews) {

    /** Validates non-null fields and defensively copies all list fields. */
    public LikeC4Document {
        elementKinds = List.copyOf(Objects.requireNonNull(elementKinds, "elementKinds"));
        elements = List.copyOf(Objects.requireNonNull(elements, "elements"));
        relationships = List.copyOf(Objects.requireNonNull(relationships, "relationships"));
        views = List.copyOf(Objects.requireNonNull(views, "views"));
        warnings = List.copyOf(Objects.requireNonNull(warnings, "warnings"));
        dynamicViews = List.copyOf(Objects.requireNonNull(dynamicViews, "dynamicViews"));
    }
}
