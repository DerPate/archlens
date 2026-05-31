package dev.dominikbreu.spoonmcp.likec4;

import java.util.List;
import java.util.Objects;

public record LikeC4Document(
        List<String> elementKinds,
        List<LikeC4Element> elements,
        List<LikeC4Relationship> relationships,
        List<LikeC4View> views,
        List<String> warnings,
        List<LikeC4DynamicView> dynamicViews) {

    public LikeC4Document {
        elementKinds = List.copyOf(Objects.requireNonNull(elementKinds, "elementKinds"));
        elements = List.copyOf(Objects.requireNonNull(elements, "elements"));
        relationships = List.copyOf(Objects.requireNonNull(relationships, "relationships"));
        views = List.copyOf(Objects.requireNonNull(views, "views"));
        warnings = List.copyOf(Objects.requireNonNull(warnings, "warnings"));
        dynamicViews = List.copyOf(Objects.requireNonNull(dynamicViews, "dynamicViews"));
    }
}
